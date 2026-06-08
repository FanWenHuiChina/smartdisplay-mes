param(
    [string]$BaseUrl = "http://localhost:8080/api",
    [string]$Username = "admin",
    [string]$Password = "123456",
    [string]$DbContainer = "smartdisplay-mes-postgres",
    [string]$DbName = "smartdisplay_mes",
    [string]$DbUser = "postgres",
    [string]$DbPassword = "mes123456",
    [string]$ReportDir = "",
    [switch]$SkipDbChecks
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
if (-not $ReportDir) {
    $ReportDir = Join-Path $ProjectRoot "docs"
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportMdPath = Join-Path $ReportDir "SmartDisplay-MES-real-db-api-flow-$timestamp.md"
$reportJsonPath = Join-Path $ReportDir "SmartDisplay-MES-real-db-api-flow-$timestamp.json"
$checks = New-Object System.Collections.ArrayList
$status = "FAIL"
$errorMessage = ""
$authHeaders = @{}
$orderNo = "MOINT$((Get-Date).ToString('yyyyMMddHHmmssfff'))"
$lotNo = ""
$firstStep = ""
$secondStep = ""
$firstEquipment = ""
$secondEquipment = ""
$aiReportNo = ""

function Get-ExpectedFlywayVersion {
    $migrationDir = Join-Path $ProjectRoot "smartdisplay-mes-api\src\main\resources\db\migration"
    if (-not (Test-Path -LiteralPath $migrationDir)) {
        return ""
    }
    $versions = Get-ChildItem -LiteralPath $migrationDir -File -Filter "V*__*.sql" |
        ForEach-Object {
            if ($_.Name -match '^V([0-9]+(?:\.[0-9]+)*)__') {
                [pscustomobject]@{
                    VersionText = $Matches[1]
                    Version = [version]$Matches[1]
                }
            }
        } |
        Sort-Object Version
    if ($versions.Count -eq 0) {
        return ""
    }
    return [string]$versions[-1].VersionText
}

function Add-Check {
    param([string]$Name, [string]$Result, [string]$Detail)
    [void]$script:checks.Add([ordered]@{
        name = $Name
        status = $Result
        detail = $Detail
    })
}

function Assert-Check {
    param([bool]$Condition, [string]$Name, [string]$Detail)
    if ($Condition) {
        Add-Check -Name $Name -Result "PASS" -Detail $Detail
        return
    }
    Add-Check -Name $Name -Result "FAIL" -Detail $Detail
    throw $Detail
}

function Join-ApiPath {
    param([string]$Base, [string]$Path)
    return $Base.TrimEnd("/") + "/" + $Path.TrimStart("/")
}

function Invoke-MesJson {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [hashtable]$Headers = @{}
    )

    $params = @{
        Method = $Method
        Uri = (Join-ApiPath -Base $BaseUrl -Path $Path)
        Headers = $Headers
    }
    if ($null -ne $Body) {
        $params["ContentType"] = "application/json; charset=utf-8"
        $params["Body"] = ($Body | ConvertTo-Json -Depth 30 -Compress)
    }

    $response = Invoke-RestMethod @params
    if ($null -ne $response.code -and $response.code -ne 200) {
        throw "API failed: $Method $Path code=$($response.code) message=$($response.message)"
    }
    if ($null -ne $response.data) {
        return $response.data
    }
    return $response
}

function Invoke-Docker {
    param([string[]]$Arguments, [switch]$AllowFailure)
    $oldErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = & docker @Arguments 2>&1
        $exitCode = $LASTEXITCODE
        if ($exitCode -ne 0 -and -not $AllowFailure) {
            throw "docker $($Arguments -join ' ') failed with exit code $exitCode. $($output -join "`n")"
        }
    } finally {
        $ErrorActionPreference = $oldErrorActionPreference
    }
    return [ordered]@{
        code = $exitCode
        output = ($output -join "`n")
    }
}

function Invoke-PsqlScalar {
    param([string]$Sql)
    if ($SkipDbChecks) {
        return ""
    }
    $result = Invoke-Docker -Arguments @(
        "exec", "-e", "PGPASSWORD=$DbPassword", $DbContainer,
        "psql", "-v", "ON_ERROR_STOP=1", "-U", $DbUser, "-d", $DbName, "-At", "-c", $Sql
    )
    return ($result.output -split "`n" | Select-Object -First 1).Trim()
}

function Escape-SqlLiteral {
    param([string]$Value)
    return $Value.Replace("'", "''")
}

function Get-LotByNo {
    param([string]$TargetLotNo)
    $encoded = [System.Uri]::EscapeDataString($TargetLotNo)
    $page = Invoke-MesJson -Method "GET" -Path "/v1/lots?current=1&size=20&lotNo=$encoded" -Headers $script:authHeaders
    $records = @($page.records)
    $lot = $records | Where-Object { $_.lotNo -eq $TargetLotNo } | Select-Object -First 1
    if ($null -eq $lot) {
        throw "Lot not found by API: $TargetLotNo"
    }
    return $lot
}

function Get-EquipmentForStep {
    param([string]$StepCode, [string]$ProductCode = "AMOLED_65")
    $equipments = @(Invoke-MesJson -Method "GET" -Path "/v1/master/equipments" -Headers $script:authHeaders)
    $recipePage = Invoke-MesJson -Method "GET" -Path "/v1/recipes?current=1&size=200" -Headers $script:authHeaders
    $activeRecipeEquipmentCodes = @($recipePage.records |
        Where-Object {
            $_.productCode -eq $ProductCode -and
            $_.stepCode -eq $StepCode -and
            $_.status -eq "ACTIVE" -and
            -not [string]::IsNullOrWhiteSpace([string]$_.equipmentCode)
        } |
        ForEach-Object { [string]$_.equipmentCode })
    $candidate = $equipments |
        Where-Object {
            ($_.status -eq "IDLE" -or $_.status -eq "RUNNING") -and
            ([string]$_.capabilitySteps).Contains($StepCode) -and
            ($activeRecipeEquipmentCodes -contains [string]$_.equipmentCode)
        } |
        Select-Object -First 1
    if ($null -ne $candidate) {
        return [string]$candidate.equipmentCode
    }
    $recipeEquipment = $activeRecipeEquipmentCodes | Select-Object -First 1
    if (-not [string]::IsNullOrWhiteSpace([string]$recipeEquipment)) {
        return [string]$recipeEquipment
    }
    switch ($StepCode) {
        "CLEAN" { return "CLEANER_01" }
        "COATING" { return "COATER_01" }
        "EXPOSURE" { return "EXPOSURE_01" }
        "ETCH" { return "ETCH_02" }
        "EVAPORATION" { return "EVAP_01" }
        "ENCAPSULATION" { return "ENCAP_01" }
        "INSPECTION" { return "INSPECT_01" }
        "AGING" { return "AGING_01" }
        default { return "COATER_01" }
    }
}

function Get-OkParamsJson {
    param([string]$StepCode)
    $params = switch ($StepCode) {
        "CLEAN" { @{ CLEAN_TIME = 60 } }
        "COATING" { @{ TEMP_COATING = 150; SPEED_COATING = 300; THICKNESS = 2.0 } }
        "EXPOSURE" { @{ EXPOSURE_DOSE = 120 } }
        "ETCH" { @{ ETCH_TIME = 80 } }
        "EVAPORATION" { @{ TEMP_EVAP = 280; VACUUM = 0.0001 } }
        "ENCAPSULATION" { @{ ENCAP_PRESSURE = 0.6 } }
        "INSPECTION" { @{ AOI_SAMPLE_RATE = 100 } }
        "AGING" { @{ AGING_TEMP = 60 } }
        default { @{ RESULT = "OK" } }
    }
    return ($params | ConvertTo-Json -Compress)
}

function Get-NgParamsJson {
    param([string]$StepCode)
    $params = switch ($StepCode) {
        "COATING" { @{ TEMP_COATING = 150; SPEED_COATING = 300; THICKNESS = 2.4 } }
        "CLEAN" { @{ CLEAN_TIME = 180 } }
        "EXPOSURE" { @{ EXPOSURE_DOSE = 150 } }
        "ETCH" { @{ ETCH_TIME = 140 } }
        "EVAPORATION" { @{ TEMP_EVAP = 290; VACUUM = 0.001 } }
        "ENCAPSULATION" { @{ ENCAP_PRESSURE = 1.0 } }
        "INSPECTION" { @{ AOI_SAMPLE_RATE = 0 } }
        "AGING" { @{ AGING_TEMP = 90 } }
        default { @{ RESULT = "NG" } }
    }
    return ($params | ConvertTo-Json -Compress)
}

function Write-Reports {
    param([string]$FinalStatus)

    New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null
    $report = [ordered]@{
        generatedAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss zzz")
        status = $FinalStatus
        baseUrl = $BaseUrl
        username = $Username
        dbContainer = if ($SkipDbChecks) { "" } else { $DbContainer }
        dbName = if ($SkipDbChecks) { "" } else { $DbName }
        orderNo = $orderNo
        lotNo = $lotNo
        firstStep = $firstStep
        firstEquipment = $firstEquipment
        secondStep = $secondStep
        secondEquipment = $secondEquipment
        aiReportNo = $aiReportNo
        error = $errorMessage
        checks = @($checks)
    }

    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($reportJsonPath, (($report | ConvertTo-Json -Depth 10) + "`n"), $utf8NoBom)

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# SmartDisplay MES Real DB API Flow Report")
    $lines.Add("")
    $lines.Add("- Generated at: $($report.generatedAt)")
    $lines.Add("- Status: $FinalStatus")
    $lines.Add("- Base URL: $BaseUrl")
    $lines.Add("- User: $Username")
    if (-not $SkipDbChecks) {
        $lines.Add("- Database: $DbContainer/$DbName")
    }
    $lines.Add("- Order: $orderNo")
    $lines.Add("- Lot: $lotNo")
    $lines.Add("- Normal step: $firstStep / $firstEquipment")
    $lines.Add("- NG auto-hold step: $secondStep / $secondEquipment")
    if ($aiReportNo) {
        $lines.Add("- AI report: $aiReportNo")
    }
    if ($errorMessage) {
        $lines.Add("- Error: $errorMessage")
    }
    $lines.Add("")
    $lines.Add("## Checks")
    $lines.Add("")
    $lines.Add("| Check | Status | Detail |")
    $lines.Add("| --- | --- | --- |")
    foreach ($check in $checks) {
        $detail = ([string]$check.detail).Replace("|", "\|").Replace("`r", "").Replace("`n", "<br>")
        $lines.Add("| $($check.name) | $($check.status) | $detail |")
    }
    $lines.Add("")
    [System.IO.File]::WriteAllText($reportMdPath, (($lines -join "`n") + "`n"), $utf8NoBom)
}

try {
    $login = Invoke-MesJson -Method "POST" -Path "/v1/auth/login" -Body @{
        username = $Username
        password = $Password
    }
    Assert-Check -Condition (-not [string]::IsNullOrWhiteSpace([string]$login.token)) -Name "auth login" -Detail "role=$($login.role)"
    $script:authHeaders = @{ Authorization = "Bearer $($login.token)" }

    if (-not $SkipDbChecks) {
        $ready = Invoke-Docker -Arguments @("exec", $DbContainer, "pg_isready", "-U", $DbUser, "-d", $DbName) -AllowFailure
        Assert-Check -Condition ($ready.code -eq 0) -Name "postgres container ready" -Detail ($ready.output -replace "`r", " " -replace "`n", " ")
        $latestMigration = Invoke-PsqlScalar -Sql "select version from flyway_schema_history where success = true order by installed_rank desc limit 1;"
        $expectedMigration = Get-ExpectedFlywayVersion
        $migrationMatched = if ([string]::IsNullOrWhiteSpace($expectedMigration)) {
            -not [string]::IsNullOrWhiteSpace($latestMigration)
        } else {
            $latestMigration -eq $expectedMigration
        }
        $migrationDetail = if ([string]::IsNullOrWhiteSpace($expectedMigration)) {
            "latest=V$latestMigration"
        } else {
            "latest=V$latestMigration, expected=V$expectedMigration"
        }
        Assert-Check -Condition $migrationMatched -Name "database migrated" -Detail $migrationDetail
    }

    $order = Invoke-MesJson -Method "POST" -Path "/v1/orders" -Headers $script:authHeaders -Body @{
        orderNo = $orderNo
        productCode = "AMOLED_65"
        productName = "AMOLED 6.5 integration panel"
        plannedQty = 100
        priority = 8
        lineCode = "LINE_01"
    }
    Assert-Check -Condition ($order.orderNo -eq $orderNo -and $order.status -eq "CREATED") -Name "order created by API" -Detail "order=$orderNo status=$($order.status)"
    if (-not $SkipDbChecks) {
        $dbOrderStatus = Invoke-PsqlScalar -Sql "select status from prod_order where order_no = '$(Escape-SqlLiteral $orderNo)';"
        Assert-Check -Condition ($dbOrderStatus -eq "CREATED") -Name "order persisted in postgres" -Detail "status=$dbOrderStatus"
    }

    $release = Invoke-MesJson -Method "POST" -Path "/v1/orders/$orderNo/release" -Headers $script:authHeaders -Body @{
        lotQty = 100
        operator = $Username
    }
    $createdLots = @($release.createdLots)
    Assert-Check -Condition ($createdLots.Count -ge 1) -Name "order released by API" -Detail "createdLots=$($createdLots.Count)"
    $lotNo = [string]$createdLots[0].lotNo
    $lot = Get-LotByNo -TargetLotNo $lotNo
    Assert-Check -Condition ($lot.status -eq "READY" -and -not [string]::IsNullOrWhiteSpace([string]$lot.currentStepCode)) -Name "released lot ready" -Detail "lot=$lotNo step=$($lot.currentStepCode)"
    if (-not $SkipDbChecks) {
        $dbLotState = Invoke-PsqlScalar -Sql "select status || '|' || current_step_code || '|' || coalesce(current_equipment_code, '') from prod_lot where lot_no = '$(Escape-SqlLiteral $lotNo)';"
        Assert-Check -Condition ($dbLotState.StartsWith("READY|")) -Name "released lot persisted in postgres" -Detail $dbLotState
    }

    $firstStep = [string]$lot.currentStepCode
    $firstEquipment = Get-EquipmentForStep -StepCode $firstStep -ProductCode ([string]$lot.productCode)
    Invoke-MesJson -Method "POST" -Path "/v1/lots/$lotNo/track-in" -Headers $script:authHeaders -Body @{
        stepCode = $firstStep
        equipmentCode = $firstEquipment
        operator = "it-op-01"
    } | Out-Null
    $lot = Get-LotByNo -TargetLotNo $lotNo
    Assert-Check -Condition ($lot.status -eq "PROCESSING" -and $lot.currentEquipmentCode -eq $firstEquipment) -Name "first track in" -Detail "$firstStep/$firstEquipment"
    if (-not $SkipDbChecks) {
        $openStepCount = [int](Invoke-PsqlScalar -Sql "select count(*) from prod_lot_step_record where lot_no = '$(Escape-SqlLiteral $lotNo)' and step_code = '$(Escape-SqlLiteral $firstStep)' and track_out_time is null;")
        Assert-Check -Condition ($openStepCount -eq 1) -Name "first track in step record" -Detail "openRecords=$openStepCount"
    }

    $firstTrackOut = Invoke-MesJson -Method "POST" -Path "/v1/lots/$lotNo/track-out" -Headers $script:authHeaders -Body @{
        operator = "it-op-01"
        result = "OK"
        processParams = (Get-OkParamsJson -StepCode $firstStep)
        remark = "real db api flow first OK track out"
    }
    $lot = Get-LotByNo -TargetLotNo $lotNo
    $secondStep = [string]$lot.currentStepCode
    Assert-Check -Condition ($firstTrackOut.result -eq "OK" -and $lot.status -eq "READY" -and $secondStep -ne $firstStep) -Name "first track out advances route" -Detail "result=$($firstTrackOut.result), nextStep=$secondStep"
    if (-not $SkipDbChecks) {
        $okStepResult = Invoke-PsqlScalar -Sql "select result from prod_lot_step_record where lot_no = '$(Escape-SqlLiteral $lotNo)' and step_code = '$(Escape-SqlLiteral $firstStep)' order by id desc limit 1;"
        Assert-Check -Condition ($okStepResult -eq "OK") -Name "first track out persisted" -Detail "stepResult=$okStepResult"
    }

    $secondEquipment = Get-EquipmentForStep -StepCode $secondStep -ProductCode ([string]$lot.productCode)
    Invoke-MesJson -Method "POST" -Path "/v1/lots/$lotNo/track-in" -Headers $script:authHeaders -Body @{
        stepCode = $secondStep
        equipmentCode = $secondEquipment
        operator = "it-op-02"
    } | Out-Null
    $lot = Get-LotByNo -TargetLotNo $lotNo
    Assert-Check -Condition ($lot.status -eq "PROCESSING" -and $lot.currentEquipmentCode -eq $secondEquipment) -Name "second track in" -Detail "$secondStep/$secondEquipment"

    $ngTrackOut = Invoke-MesJson -Method "POST" -Path "/v1/lots/$lotNo/track-out" -Headers $script:authHeaders -Body @{
        operator = "it-op-02"
        result = "OK"
        processParams = (Get-NgParamsJson -StepCode $secondStep)
        remark = "real db api flow key parameter out of limit"
    }
    $lot = Get-LotByNo -TargetLotNo $lotNo
    Assert-Check -Condition ($ngTrackOut.result -eq "NG" -and $lot.status -eq "HOLD" -and [int]$lot.holdFlag -eq 1) -Name "ng track out auto hold" -Detail "result=$($ngTrackOut.result), status=$($lot.status), holdFlag=$($lot.holdFlag)"

    $exceptions = @(Invoke-MesJson -Method "GET" -Path "/v1/quality/exceptions?lotNo=$([System.Uri]::EscapeDataString($lotNo))" -Headers $script:authHeaders)
    $inspections = @(Invoke-MesJson -Method "GET" -Path "/v1/quality/inspections?lotNo=$([System.Uri]::EscapeDataString($lotNo))" -Headers $script:authHeaders)
    Assert-Check -Condition ($exceptions.Count -ge 1 -and $inspections.Count -ge 1) -Name "quality evidence by API" -Detail "exceptions=$($exceptions.Count), inspections=$($inspections.Count)"
    if (-not $SkipDbChecks) {
        $qualityCounts = Invoke-PsqlScalar -Sql "select (select count(*) from quality_inspection where lot_no = '$(Escape-SqlLiteral $lotNo)') || '|' || (select count(*) from exception_event where lot_no = '$(Escape-SqlLiteral $lotNo)') || '|' || (select count(*) from lot_hold_record where lot_no = '$(Escape-SqlLiteral $lotNo)' and status = 'HOLD');"
        $parts = $qualityCounts.Split("|")
        Assert-Check -Condition ([int]$parts[0] -ge 1 -and [int]$parts[1] -ge 1 -and [int]$parts[2] -ge 1) -Name "quality evidence persisted in postgres" -Detail "inspection|exception|openHold=$qualityCounts"
    }

    Invoke-MesJson -Method "POST" -Path "/v1/lots/$lotNo/release" -Headers $script:authHeaders -Body @{
        releaseBy = "qe-int-01"
        disposition = "integration check release after NG evidence"
    } | Out-Null
    $lot = Get-LotByNo -TargetLotNo $lotNo
    Assert-Check -Condition ($lot.status -eq "READY" -and [int]$lot.holdFlag -eq 0) -Name "lot release after hold" -Detail "status=$($lot.status), holdFlag=$($lot.holdFlag)"
    if (-not $SkipDbChecks) {
        $releasedHoldCount = [int](Invoke-PsqlScalar -Sql "select count(*) from lot_hold_record where lot_no = '$(Escape-SqlLiteral $lotNo)' and status = 'RELEASED';")
        Assert-Check -Condition ($releasedHoldCount -ge 1) -Name "release persisted in postgres" -Detail "releasedHoldRecords=$releasedHoldCount"
    }

    $trace = Invoke-MesJson -Method "GET" -Path "/v1/trace/lots/$lotNo" -Headers $script:authHeaders
    Assert-Check -Condition (@($trace.stepRecords).Count -ge 2 -and @($trace.qualityRecords).Count -ge 1 -and @($trace.holdRecords).Count -ge 1) -Name "trace returns full chain" -Detail "steps=$(@($trace.stepRecords).Count), quality=$(@($trace.qualityRecords).Count), holds=$(@($trace.holdRecords).Count)"

    $dashboard = Invoke-MesJson -Method "GET" -Path "/v1/dashboard/overview" -Headers $script:authHeaders
    $yield = Invoke-MesJson -Method "GET" -Path "/v1/dashboard/yield" -Headers $script:authHeaders
    Assert-Check -Condition (@($dashboard.metrics).Count -gt 0 -and @($yield.trend).Count -gt 0) -Name "dashboard reads current database" -Detail "overviewMetrics=$(@($dashboard.metrics).Count), yieldTrend=$(@($yield.trend).Count)"

    $aiReport = Invoke-MesJson -Method "POST" -Path "/v1/ai/reports/yield" -Headers $script:authHeaders -Body @{
        generatedBy = $Username
        window = "real-db-api-flow"
    }
    $aiReportNo = [string]$aiReport.reportNo
    Assert-Check -Condition (-not [string]::IsNullOrWhiteSpace($aiReportNo)) -Name "ai yield report generated" -Detail "reportNo=$aiReportNo"
    if (-not $SkipDbChecks) {
        $aiReportCount = [int](Invoke-PsqlScalar -Sql "select count(*) from ai_report_record where report_no = '$(Escape-SqlLiteral $aiReportNo)';")
        Assert-Check -Condition ($aiReportCount -eq 1) -Name "ai report persisted in postgres" -Detail "rows=$aiReportCount"
    }

    $auditRows = @(Invoke-MesJson -Method "GET" -Path "/v1/system/audit-logs?bizNo=$([System.Uri]::EscapeDataString($lotNo))" -Headers $script:authHeaders)
    $auditActions = @($auditRows | ForEach-Object { [string]$_.action })
    Assert-Check -Condition ($auditActions -contains "TRACK_IN" -and $auditActions -contains "TRACK_OUT" -and $auditActions -contains "LOT_RELEASE") -Name "audit trail by API" -Detail ($auditActions -join ",")
    if (-not $SkipDbChecks) {
        $auditCount = [int](Invoke-PsqlScalar -Sql "select count(*) from sys_audit_log where biz_no = '$(Escape-SqlLiteral $lotNo)' and action in ('TRACK_IN','TRACK_OUT','LOT_RELEASE','QUALITY_INSPECTION');")
        Assert-Check -Condition ($auditCount -ge 5) -Name "audit trail persisted in postgres" -Detail "matchingRows=$auditCount"
    }

    $status = "PASS"
} catch {
    $errorMessage = $_.Exception.Message
    $status = "FAIL"
} finally {
    Write-Reports -FinalStatus $status
}

if ($status -ne "PASS") {
    Write-Host "Real DB API flow failed"
    Write-Host "Report: $reportMdPath"
    Write-Host $errorMessage
    exit 1
}

Write-Host "Real DB API flow passed"
Write-Host "Report: $reportMdPath"
exit 0
