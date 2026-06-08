param(
    [string]$BaseUrl = "http://localhost:8080/api",
    [string]$Username = "admin",
    [string]$Password = "123456",
    [ValidateSet("Short", "Full")]
    [string]$Mode = "Short",
    [string]$ReportDir = ""
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
if (-not $ReportDir) {
    $ReportDir = Join-Path $ProjectRoot "docs"
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$reportMdPath = Join-Path $ReportDir "SmartDisplay-MES-demo-script-$($Mode.ToLower())-$timestamp.md"
$reportJsonPath = Join-Path $ReportDir "SmartDisplay-MES-demo-script-$($Mode.ToLower())-$timestamp.json"
$checks = New-Object System.Collections.ArrayList
$artifacts = [ordered]@{
    mode = $Mode
    orderNo = ""
    lotNo = ""
    firstStep = ""
    firstEquipment = ""
    secondStep = ""
    secondEquipment = ""
    reworkLotNo = ""
    scrapLotNo = ""
    carrierNo = ""
    aiYieldReportNo = ""
    aiRagReportNo = ""
    aiEquipmentReportNo = ""
    kbIndexJobNo = ""
}
$status = "FAIL"
$errorMessage = ""
$script:AuthHeaders = @{}

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
        $params["Body"] = ($Body | ConvertTo-Json -Depth 40 -Compress)
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

function Get-LotByNo {
    param([string]$TargetLotNo)
    $encoded = [System.Uri]::EscapeDataString($TargetLotNo)
    $page = Invoke-MesJson -Method "GET" -Path "/v1/lots?current=1&size=20&lotNo=$encoded" -Headers $script:AuthHeaders
    $records = @($page.records)
    $lot = $records | Where-Object { $_.lotNo -eq $TargetLotNo } | Select-Object -First 1
    if ($null -eq $lot) {
        throw "Lot not found by API: $TargetLotNo"
    }
    return $lot
}

function Get-EquipmentForStep {
    param([string]$StepCode, [string]$ProductCode = "AMOLED_65")
    $equipments = @(Invoke-MesJson -Method "GET" -Path "/v1/master/equipments" -Headers $script:AuthHeaders)
    $recipePage = Invoke-MesJson -Method "GET" -Path "/v1/recipes?current=1&size=200" -Headers $script:AuthHeaders
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

function New-DemoLot {
    param([string]$Purpose)
    $suffix = (Get-Date).ToString("yyyyMMddHHmmssfff")
    $orderNo = "MODEMO-$Purpose-$suffix"
    $order = Invoke-MesJson -Method "POST" -Path "/v1/orders" -Headers $script:AuthHeaders -Body @{
        orderNo = $orderNo
        productCode = "AMOLED_65"
        productName = "AMOLED 6.5 pilot demo panel"
        plannedQty = 60
        priority = 8
        lineCode = "LINE_01"
    }
    Assert-Check -Condition ($order.orderNo -eq $orderNo -and $order.status -eq "CREATED") -Name "$Purpose order created" -Detail "order=$orderNo"

    $release = Invoke-MesJson -Method "POST" -Path "/v1/orders/$orderNo/release" -Headers $script:AuthHeaders -Body @{
        lotQty = 60
        operator = $Username
    }
    $createdLots = @($release.createdLots)
    Assert-Check -Condition ($createdLots.Count -gt 0) -Name "$Purpose order released" -Detail "createdLots=$($createdLots.Count)"
    $lotNo = [string]$createdLots[0].lotNo
    $lot = Get-LotByNo -TargetLotNo $lotNo
    Assert-Check -Condition ($lot.status -eq "READY") -Name "$Purpose lot ready" -Detail "lot=$lotNo step=$($lot.currentStepCode)"
    return [ordered]@{
        orderNo = $orderNo
        lotNo = $lotNo
        lot = $lot
    }
}

function Get-RouteForProduct {
    param([string]$ProductCode)
    $routes = @(Invoke-MesJson -Method "GET" -Path "/v1/routes" -Headers $script:AuthHeaders)
    $route = $routes | Where-Object { $_.productCode -eq $ProductCode } | Select-Object -First 1
    if ($null -eq $route) {
        $route = $routes | Select-Object -First 1
    }
    if ($null -eq $route) {
        throw "No active route returned by API."
    }
    return $route
}

function Get-FirstRouteStepCode {
    param([object]$Route, [string]$DefaultStep)
    $steps = @($Route.steps)
    foreach ($step in $steps) {
        if ($step -is [string]) {
            return [string]$step
        }
        if ($null -ne $step.stepCode -and -not [string]::IsNullOrWhiteSpace([string]$step.stepCode)) {
            return [string]$step.stepCode
        }
    }
    return $DefaultStep
}

function Escape-MarkdownCell {
    param([object]$Value)
    if ($null -eq $Value) {
        return ""
    }
    return ([string]$Value).Replace("|", "\|").Replace("`r", "").Replace("`n", "<br>")
}

function Write-DemoReports {
    param([string]$FinalStatus)

    New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null
    $report = [ordered]@{
        generatedAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss zzz")
        status = $FinalStatus
        mode = $Mode
        baseUrl = $BaseUrl
        username = $Username
        artifacts = $artifacts
        error = $errorMessage
        checks = @($checks)
    }

    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($reportJsonPath, (($report | ConvertTo-Json -Depth 20) + "`n"), $utf8NoBom)

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# SmartDisplay MES Runnable Pilot Demo Report")
    $lines.Add("")
    $lines.Add("- Generated at: $($report.generatedAt)")
    $lines.Add("- Status: $FinalStatus")
    $lines.Add("- Mode: $Mode")
    $lines.Add("- API: $BaseUrl")
    $lines.Add("- User: $Username")
    if ($errorMessage) {
        $lines.Add("- Error: $errorMessage")
    }
    $lines.Add("")
    $lines.Add("## Artifacts")
    $lines.Add("")
    $lines.Add("| Artifact | Value |")
    $lines.Add("| --- | --- |")
    foreach ($key in $artifacts.Keys) {
        $lines.Add("| $key | $(Escape-MarkdownCell $artifacts[$key]) |")
    }
    $lines.Add("")
    $lines.Add("## Checks")
    $lines.Add("")
    $lines.Add("| Check | Status | Evidence |")
    $lines.Add("| --- | --- | --- |")
    foreach ($check in $checks) {
        $lines.Add("| $(Escape-MarkdownCell $check.name) | $($check.status) | $(Escape-MarkdownCell $check.detail) |")
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
    $script:AuthHeaders = @{ Authorization = "Bearer $($login.token)" }

    $permissions = Invoke-MesJson -Method "GET" -Path "/v1/system/me/permissions" -Headers $script:AuthHeaders
    Assert-Check -Condition ($null -ne $permissions) -Name "permission snapshot" -Detail "current user permission snapshot is readable"

    $overview = Invoke-MesJson -Method "GET" -Path "/v1/dashboard/overview" -Headers $script:AuthHeaders
    $yield = Invoke-MesJson -Method "GET" -Path "/v1/dashboard/yield" -Headers $script:AuthHeaders
    Assert-Check -Condition (@($overview.metrics).Count -gt 0 -and @($yield.trend).Count -gt 0) -Name "dashboard overview and yield" -Detail "overviewMetrics=$(@($overview.metrics).Count), yieldTrend=$(@($yield.trend).Count)"

    $main = New-DemoLot -Purpose "FLOW"
    $artifacts.orderNo = $main.orderNo
    $artifacts.lotNo = $main.lotNo
    $lot = $main.lot
    $firstStep = [string]$lot.currentStepCode
    $firstEquipment = Get-EquipmentForStep -StepCode $firstStep -ProductCode ([string]$lot.productCode)
    $artifacts.firstStep = $firstStep
    $artifacts.firstEquipment = $firstEquipment

    Invoke-MesJson -Method "POST" -Path "/v1/lots/$($main.lotNo)/track-in" -Headers $script:AuthHeaders -Body @{
        stepCode = $firstStep
        equipmentCode = $firstEquipment
        operator = "demo-op-01"
    } | Out-Null
    $lot = Get-LotByNo -TargetLotNo $main.lotNo
    Assert-Check -Condition ($lot.status -eq "PROCESSING" -and $lot.currentEquipmentCode -eq $firstEquipment) -Name "track in validation chain" -Detail "$firstStep/$firstEquipment status=$($lot.status)"

    $trackOut = Invoke-MesJson -Method "POST" -Path "/v1/lots/$($main.lotNo)/track-out" -Headers $script:AuthHeaders -Body @{
        operator = "demo-op-01"
        result = "OK"
        processParams = (Get-OkParamsJson -StepCode $firstStep)
        remark = "pilot demo short flow OK track out"
    }
    $lot = Get-LotByNo -TargetLotNo $main.lotNo
    $secondStep = [string]$lot.currentStepCode
    $artifacts.secondStep = $secondStep
    Assert-Check -Condition ($trackOut.result -eq "OK" -and $lot.status -eq "READY" -and $secondStep -ne $firstStep) -Name "track out advances route" -Detail "result=$($trackOut.result), nextStep=$secondStep"

    $trace = Invoke-MesJson -Method "GET" -Path "/v1/trace/lots/$($main.lotNo)" -Headers $script:AuthHeaders
    Assert-Check -Condition (@($trace.stepRecords).Count -ge 1 -and $null -ne $trace.order -and $null -ne $trace.route) -Name "lot full-chain trace" -Detail "steps=$(@($trace.stepRecords).Count), audit=$(@($trace.auditLogs).Count)"

    $aiYield = Invoke-MesJson -Method "POST" -Path "/v1/ai/reports/yield" -Headers $script:AuthHeaders -Body @{
        generatedBy = $Username
        window = "pilot-demo-$Mode"
    }
    $artifacts.aiYieldReportNo = [string]$aiYield.reportNo
    Assert-Check -Condition (-not [string]::IsNullOrWhiteSpace([string]$aiYield.reportNo) -and $aiYield.output.writeActionAllowed -eq $false) -Name "AI yield report" -Detail "reportNo=$($aiYield.reportNo), writeActionAllowed=$($aiYield.output.writeActionAllowed)"

    if ($Mode -eq "Full") {
        $secondEquipment = Get-EquipmentForStep -StepCode $secondStep -ProductCode ([string]$lot.productCode)
        $artifacts.secondEquipment = $secondEquipment
        Invoke-MesJson -Method "POST" -Path "/v1/lots/$($main.lotNo)/track-in" -Headers $script:AuthHeaders -Body @{
            stepCode = $secondStep
            equipmentCode = $secondEquipment
            operator = "demo-op-02"
        } | Out-Null
        $lot = Get-LotByNo -TargetLotNo $main.lotNo
        Assert-Check -Condition ($lot.status -eq "PROCESSING" -and $lot.currentEquipmentCode -eq $secondEquipment) -Name "second step track in" -Detail "$secondStep/$secondEquipment"

        $ngTrackOut = Invoke-MesJson -Method "POST" -Path "/v1/lots/$($main.lotNo)/track-out" -Headers $script:AuthHeaders -Body @{
            operator = "demo-op-02"
            result = "OK"
            processParams = (Get-NgParamsJson -StepCode $secondStep)
            remark = "pilot demo key parameter out of limit"
        }
        $lot = Get-LotByNo -TargetLotNo $main.lotNo
        Assert-Check -Condition ($ngTrackOut.result -eq "NG" -and $lot.status -eq "HOLD" -and [int]$lot.holdFlag -eq 1) -Name "parameter limit auto hold" -Detail "result=$($ngTrackOut.result), status=$($lot.status), holdFlag=$($lot.holdFlag)"

        $exceptions = @(Invoke-MesJson -Method "GET" -Path "/v1/quality/exceptions?lotNo=$([System.Uri]::EscapeDataString($main.lotNo))" -Headers $script:AuthHeaders)
        $inspections = @(Invoke-MesJson -Method "GET" -Path "/v1/quality/inspections?lotNo=$([System.Uri]::EscapeDataString($main.lotNo))" -Headers $script:AuthHeaders)
        Assert-Check -Condition ($exceptions.Count -gt 0 -and $inspections.Count -gt 0) -Name "quality exception evidence" -Detail "exceptions=$($exceptions.Count), inspections=$($inspections.Count)"

        Invoke-MesJson -Method "POST" -Path "/v1/lots/$($main.lotNo)/release" -Headers $script:AuthHeaders -Body @{
            releaseBy = "demo-qe-01"
            disposition = "pilot demo MRB release after NG evidence"
        } | Out-Null
        $lot = Get-LotByNo -TargetLotNo $main.lotNo
        Assert-Check -Condition ($lot.status -eq "READY" -and [int]$lot.holdFlag -eq 0) -Name "release restores execution" -Detail "status=$($lot.status), holdFlag=$($lot.holdFlag)"

        $route = Get-RouteForProduct -ProductCode "AMOLED_65"
        $reworkStep = Get-FirstRouteStepCode -Route $route -DefaultStep $firstStep
        $rework = New-DemoLot -Purpose "REWORK"
        $artifacts.reworkLotNo = $rework.lotNo
        Invoke-MesJson -Method "POST" -Path "/v1/lots/$($rework.lotNo)/hold" -Headers $script:AuthHeaders -Body @{
            holdReason = "pilot demo rework disposition"
            holdType = "QUALITY"
            holdBy = "demo-qe-01"
        } | Out-Null
        Invoke-MesJson -Method "POST" -Path "/v1/lots/$($rework.lotNo)/rework" -Headers $script:AuthHeaders -Body @{
            reworkRouteCode = [string]$route.routeCode
            reworkStepCode = $reworkStep
            operator = "demo-qe-01"
        } | Out-Null
        $reworkLot = Get-LotByNo -TargetLotNo $rework.lotNo
        Assert-Check -Condition ($reworkLot.status -eq "REWORK" -and $reworkLot.currentStepCode -eq $reworkStep) -Name "rework disposition" -Detail "lot=$($rework.lotNo), route=$($route.routeCode), step=$reworkStep"

        $scrap = New-DemoLot -Purpose "SCRAP"
        $artifacts.scrapLotNo = $scrap.lotNo
        Invoke-MesJson -Method "POST" -Path "/v1/lots/$($scrap.lotNo)/hold" -Headers $script:AuthHeaders -Body @{
            holdReason = "pilot demo scrap disposition"
            holdType = "QUALITY"
            holdBy = "demo-qe-01"
        } | Out-Null
        Invoke-MesJson -Method "POST" -Path "/v1/lots/$($scrap.lotNo)/scrap" -Headers $script:AuthHeaders -Body @{
            scrapConfirmed = $true
            confirmText = "SCRAP:$($scrap.lotNo)"
            reason = "pilot demo nonconforming lot scrap"
            responsibilityModule = "QUALITY"
            approver = "demo-qe-manager"
            operator = "demo-qe-01"
        } | Out-Null
        $scrapLot = Get-LotByNo -TargetLotNo $scrap.lotNo
        Assert-Check -Condition ($scrapLot.status -eq "SCRAP") -Name "scrap second confirmation" -Detail "lot=$($scrap.lotNo), status=$($scrapLot.status)"

        $wmsReady = Invoke-MesJson -Method "POST" -Path "/v1/adapters/wms/material-readiness" -Headers $script:AuthHeaders -Body @{
            lotNo = $main.lotNo
            lineCode = "LINE_01"
            operator = "demo-wms-01"
        }
        Assert-Check -Condition ($null -ne $wmsReady.readiness) -Name "WMS material-readiness adapter" -Detail "readiness=$($wmsReady.readiness), batches=$(@($wmsReady.batches).Count)"

        $batchNo = "MB-DEMO-$((Get-Date).ToString('yyyyMMddHHmmssfff'))"
        $wmsTxn = Invoke-MesJson -Method "POST" -Path "/v1/adapters/wms/inventory-transactions" -Headers $script:AuthHeaders -Body @{
            transactionType = "RECEIVE"
            batchNo = $batchNo
            materialCode = "MAT-PI-001"
            materialName = "PI glue demo batch"
            qty = 1.0
            unit = "g"
            supplierCode = "SUP-DEMO"
            qualityStatus = "PASS"
            operator = "demo-wms-01"
        }
        Assert-Check -Condition ($wmsTxn.result -eq "ACCEPTED" -and $wmsTxn.batchNo -eq $batchNo) -Name "WMS inventory transaction" -Detail "transactionType=$($wmsTxn.transactionType), batch=$batchNo"

        $carriers = @(Invoke-MesJson -Method "GET" -Path "/v1/carriers" -Headers $script:AuthHeaders)
        Assert-Check -Condition ($carriers.Count -gt 0) -Name "carrier list" -Detail "carrierCount=$($carriers.Count)"
        $carrier = $carriers | Where-Object { $_.status -ne "BOUND" } | Select-Object -First 1
        if ($null -eq $carrier) {
            $carrier = $carriers | Select-Object -First 1
            Invoke-MesJson -Method "POST" -Path "/v1/carriers/$($carrier.carrierNo)/unbind" -Headers $script:AuthHeaders -Body @{
                operator = "demo-mat-01"
                location = "BUFFER"
            } | Out-Null
        }
        $carrierNo = [string]$carrier.carrierNo
        $artifacts.carrierNo = $carrierNo
        $boundCarrier = Invoke-MesJson -Method "POST" -Path "/v1/carriers/$carrierNo/bind" -Headers $script:AuthHeaders -Body @{
            lotNo = $main.lotNo
            stepCode = $lot.currentStepCode
            equipmentCode = $secondEquipment
            location = "LINE_01"
            operator = "demo-mat-01"
        }
        Assert-Check -Condition ($boundCarrier.status -eq "BOUND" -and $boundCarrier.lotNo -eq $main.lotNo) -Name "carrier bound to lot" -Detail "carrier=$carrierNo, lot=$($main.lotNo)"
        $traceAfterCarrier = Invoke-MesJson -Method "GET" -Path "/v1/trace/lots/$($main.lotNo)" -Headers $script:AuthHeaders
        Assert-Check -Condition (@($traceAfterCarrier.carriers).Count -gt 0 -and [int]$traceAfterCarrier.impactSummary.carrierCount -gt 0) -Name "trace includes carrier" -Detail "carriers=$(@($traceAfterCarrier.carriers).Count), impactCarrierCount=$($traceAfterCarrier.impactSummary.carrierCount)"
        $unboundCarrier = Invoke-MesJson -Method "POST" -Path "/v1/carriers/$carrierNo/unbind" -Headers $script:AuthHeaders -Body @{
            operator = "demo-mat-01"
            location = "BUFFER"
        }
        Assert-Check -Condition ($unboundCarrier.status -eq "IDLE") -Name "carrier unbound" -Detail "carrier=$carrierNo, status=$($unboundCarrier.status)"

        $gateways = @(Invoke-MesJson -Method "GET" -Path "/v1/equipment/gateways" -Headers $script:AuthHeaders)
        Assert-Check -Condition ($gateways.Count -gt 0) -Name "EAP gateway config" -Detail "gateways=$($gateways.Count)"
        $gatewayCode = [string]($gateways | Select-Object -First 1).gatewayCode
        $health = Invoke-MesJson -Method "POST" -Path "/v1/equipment/gateways/$gatewayCode/health-check" -Headers $script:AuthHeaders -Body @{
            operator = "demo-ee-01"
        }
        Assert-Check -Condition ($null -ne $health.check.resultStatus) -Name "EAP gateway health check" -Detail "gateway=$gatewayCode, result=$($health.check.resultStatus)"
        $eap = Invoke-MesJson -Method "POST" -Path "/v1/adapters/eap/messages" -Headers $script:AuthHeaders -Body @{
            gatewayCode = $gatewayCode
            messageType = "STATUS"
            correlationId = "DEMO-$timestamp"
            operator = "demo-ee-01"
            payload = @{
                equipmentCode = $secondEquipment
                status = "RUNNING"
                changeReason = "pilot demo EAP status report"
            }
        }
        Assert-Check -Condition ($eap.message.processStatus -eq "PROCESSED") -Name "EAP inbound message" -Detail "messageNo=$($eap.message.messageNo), type=$($eap.message.messageType)"

        $kbIndex = Invoke-MesJson -Method "POST" -Path "/v1/ai/kb/index-jobs" -Headers $script:AuthHeaders -Body @{
            retrievalStrategy = "HYBRID_LOCAL"
            jobType = "MANUAL_REINDEX"
        }
        $artifacts.kbIndexJobNo = [string]$kbIndex.jobNo
        Assert-Check -Condition ($kbIndex.status -eq "COMPLETED" -and [int]$kbIndex.indexedChunkCount -gt 0) -Name "Hybrid Local KB index" -Detail "job=$($kbIndex.jobNo), chunks=$($kbIndex.indexedChunkCount)"

        $rag = Invoke-MesJson -Method "POST" -Path "/v1/ai/kb/ask" -Headers $script:AuthHeaders -Body @{
            question = "EVAP vacuum fluctuation troubleshooting SOP"
        }
        $artifacts.aiRagReportNo = [string]$rag.reportNo
        Assert-Check -Condition (-not [string]::IsNullOrWhiteSpace([string]$rag.reportNo) -and @($rag.sources).Count -gt 0) -Name "RAG SOP Q&A" -Detail "reportNo=$($rag.reportNo), evidence=$($rag.evidenceLevel), sources=$(@($rag.sources).Count)"

        $aiEquipment = Invoke-MesJson -Method "POST" -Path "/v1/ai/equipment/analyze" -Headers $script:AuthHeaders -Body @{
            equipmentCode = $secondEquipment
            lotNo = $main.lotNo
            generatedBy = $Username
        }
        $artifacts.aiEquipmentReportNo = [string]$aiEquipment.reportNo
        Assert-Check -Condition (-not [string]::IsNullOrWhiteSpace([string]$aiEquipment.reportNo) -and $aiEquipment.writeActionAllowed -eq $false) -Name "AI equipment analysis" -Detail "reportNo=$($aiEquipment.reportNo), risk=$($aiEquipment.riskLevel)"
    }

    $auditRows = @(Invoke-MesJson -Method "GET" -Path "/v1/system/audit-logs?bizNo=$([System.Uri]::EscapeDataString($main.lotNo))" -Headers $script:AuthHeaders)
    $auditActions = @($auditRows | ForEach-Object { [string]$_.action })
    Assert-Check -Condition ($auditActions -contains "TRACK_IN" -and $auditActions -contains "TRACK_OUT") -Name "critical operation audit" -Detail ($auditActions -join ",")

    $status = "PASS"
} catch {
    $errorMessage = $_.Exception.Message
    $status = "FAIL"
} finally {
    Write-DemoReports -FinalStatus $status
}

if ($status -ne "PASS") {
    Write-Host "Pilot demo script failed"
    Write-Host "Report: $reportMdPath"
    Write-Host $errorMessage
    exit 1
}

Write-Host "Pilot demo script passed"
Write-Host "Report: $reportMdPath"
Write-Host "JSON: $reportJsonPath"
exit 0
