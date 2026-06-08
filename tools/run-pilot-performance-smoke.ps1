param(
    [string]$BaseUrl = "http://localhost:8080/api",
    [string]$Username = "planner",
    [string]$Password = "123456",
    [int]$ImportCount = 1000,
    [int]$Samples = 20,
    [string]$TraceLotNo = "LOT202406001",
    [int]$OrderListThresholdMs = 500,
    [int]$LotListThresholdMs = 500,
    [int]$YieldDashboardThresholdMs = 2000,
    [int]$LotTraceThresholdMs = 1000,
    [string]$ReportPath = "",
    [string]$JsonPath = ""
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot

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

    $uri = Join-ApiPath -Base $BaseUrl -Path $Path
    $params = @{
        Method = $Method
        Uri = $uri
        Headers = $Headers
    }
    if ($null -ne $Body) {
        $params["ContentType"] = "application/json; charset=utf-8"
        $params["Body"] = ($Body | ConvertTo-Json -Depth 20)
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

function Measure-Api {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Path,
        [object]$Body = $null,
        [int]$ThresholdMs,
        [int]$Repeat = $Samples
    )

    $values = New-Object System.Collections.Generic.List[double]
    for ($i = 0; $i -lt $Repeat; $i++) {
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        [void](Invoke-MesJson -Method $Method -Path $Path -Body $Body -Headers $script:AuthHeaders)
        $sw.Stop()
        $values.Add($sw.Elapsed.TotalMilliseconds)
    }
    $sorted = $values | Sort-Object
    $p95Index = [Math]::Max(0, [Math]::Ceiling($sorted.Count * 0.95) - 1)
    $p95 = [Math]::Round([double]$sorted[$p95Index], 2)
    $avg = [Math]::Round((($values | Measure-Object -Average).Average), 2)
    $min = [Math]::Round([double]$sorted[0], 2)
    $max = [Math]::Round([double]$sorted[$sorted.Count - 1], 2)
    $passed = $p95 -le $ThresholdMs
    return [pscustomobject]@{
        Name = $Name
        Samples = $Repeat
        MinMs = $min
        AverageMs = $avg
        P95Ms = $p95
        MaxMs = $max
        ThresholdMs = $ThresholdMs
        Passed = $passed
        Details = "P95 <= $ThresholdMs ms"
    }
}

function Resolve-ReportPath {
    param(
        [string]$Path,
        [string]$DefaultFileName
    )
    if (-not [string]::IsNullOrWhiteSpace($Path)) {
        return $Path
    }
    $defaultDir = Join-Path -Path $ProjectRoot -ChildPath "docs"
    return Join-Path -Path $defaultDir -ChildPath $DefaultFileName
}

function Write-PerformanceReports {
    param(
        [object[]]$Results,
        [string]$ResolvedReportPath,
        [string]$ResolvedJsonPath,
        [hashtable]$Context
    )

    $failed = @($Results | Where-Object { -not $_.Passed })
    $status = if ($failed.Count -eq 0) { "PASSED" } else { "FAILED" }

    $reportDir = Split-Path -Parent $ResolvedReportPath
    if (-not [string]::IsNullOrWhiteSpace($reportDir) -and -not (Test-Path $reportDir)) {
        New-Item -ItemType Directory -Path $reportDir | Out-Null
    }
    $jsonDir = Split-Path -Parent $ResolvedJsonPath
    if (-not [string]::IsNullOrWhiteSpace($jsonDir) -and -not (Test-Path $jsonDir)) {
        New-Item -ItemType Directory -Path $jsonDir | Out-Null
    }

    $payload = [ordered]@{
        status = $status
        generatedAt = $Context.GeneratedAt
        baseUrl = $Context.BaseUrl
        username = $Context.Username
        importCount = $Context.ImportCount
        samples = $Context.Samples
        traceLotNo = $Context.TraceLotNo
        thresholds = $Context.Thresholds
        results = $Results
    }
    $payload | ConvertTo-Json -Depth 20 | Set-Content -Path $ResolvedJsonPath -Encoding UTF8

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# SmartDisplay MES Performance Smoke Report")
    $lines.Add("")
    $lines.Add("- Generated at: $($Context.GeneratedAt)")
    $lines.Add("- Base URL: $($Context.BaseUrl)")
    $lines.Add("- Username: $($Context.Username)")
    $lines.Add("- ERP import count: $($Context.ImportCount)")
    $lines.Add("- Samples: $($Context.Samples)")
    $lines.Add("- Trace Lot: $($Context.TraceLotNo)")
    $lines.Add("- Verdict: $status")
    $lines.Add("")
    $lines.Add("| Check | Samples | Min(ms) | Avg(ms) | P95(ms) | Max(ms) | Threshold(ms) | Result | Details |")
    $lines.Add("| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- |")
    foreach ($row in $Results) {
        $resultText = if ($row.Passed) { "PASS" } else { "FAIL" }
        $lines.Add("| $($row.Name) | $($row.Samples) | $($row.MinMs) | $($row.AverageMs) | $($row.P95Ms) | $($row.MaxMs) | $($row.ThresholdMs) | $resultText | $($row.Details) |")
    }
    $lines.Add("")
    if ($failed.Count -gt 0) {
        $lines.Add("## Failed Checks")
        foreach ($row in $failed) {
            $lines.Add("- $($row.Name): P95=$($row.P95Ms)ms, threshold=$($row.ThresholdMs)ms, details=$($row.Details)")
        }
    } else {
        $lines.Add("All performance smoke checks passed.")
    }
    $lines | Set-Content -Path $ResolvedReportPath -Encoding UTF8

    Write-Host "Markdown report: $ResolvedReportPath"
    Write-Host "JSON report: $ResolvedJsonPath"
}

if ($ImportCount -lt 1 -or $ImportCount -gt 1000) {
    throw "ImportCount must be between 1 and 1000."
}
if ($Samples -lt 1) {
    throw "Samples must be greater than 0."
}

Write-Host "SmartDisplay MES performance smoke"
Write-Host "BaseUrl=$BaseUrl Username=$Username ImportCount=$ImportCount Samples=$Samples"

$login = Invoke-MesJson -Method "POST" -Path "/v1/auth/login" -Body @{
    username = $Username
    password = $Password
}
if ($null -eq $login.token -or [string]::IsNullOrWhiteSpace([string]$login.token)) {
    throw "Login did not return token."
}
$script:AuthHeaders = @{
    Authorization = "Bearer $($login.token)"
}

$batchStamp = Get-Date -Format "yyyyMMddHHmmss"
$importBody = @{
    batchNo = "PERF-ERP-$batchStamp"
    count = $ImportCount
    orderPrefix = "MOERP-PERF-$batchStamp"
    plannedQty = 50
    lineCode = "LINE_01"
    sourceSystem = "PERFORMANCE_SMOKE"
}
$importSw = [System.Diagnostics.Stopwatch]::StartNew()
$importResult = Invoke-MesJson -Method "POST" -Path "/v1/adapters/erp/orders" -Body $importBody -Headers $script:AuthHeaders
$importSw.Stop()
$importPassed = ($importResult.receivedCount -eq $ImportCount -and $importResult.failedCount -eq 0)

$results = New-Object System.Collections.Generic.List[object]
$results.Add([pscustomobject]@{
    Name = "ERP 1000 order import"
    Samples = 1
    MinMs = [Math]::Round($importSw.Elapsed.TotalMilliseconds, 2)
    AverageMs = [Math]::Round($importSw.Elapsed.TotalMilliseconds, 2)
    P95Ms = [Math]::Round($importSw.Elapsed.TotalMilliseconds, 2)
    MaxMs = [Math]::Round($importSw.Elapsed.TotalMilliseconds, 2)
    ThresholdMs = 0
    Passed = $importPassed
    Details = "received=$($importResult.receivedCount), failed=$($importResult.failedCount), skipped=$($importResult.skippedCount)"
})
$results.Add((Measure-Api -Name "Order list" -Method "GET" -Path "/v1/orders?current=1&size=20" -ThresholdMs $OrderListThresholdMs))
$results.Add((Measure-Api -Name "Lot list" -Method "GET" -Path "/v1/lots?current=1&size=20" -ThresholdMs $LotListThresholdMs))
$results.Add((Measure-Api -Name "Yield dashboard" -Method "GET" -Path "/v1/dashboard/yield" -ThresholdMs $YieldDashboardThresholdMs))
$results.Add((Measure-Api -Name "Lot trace" -Method "GET" -Path "/v1/trace/lots/$TraceLotNo" -ThresholdMs $LotTraceThresholdMs))

$results | Format-Table -AutoSize

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$resolvedReportPath = Resolve-ReportPath -Path $ReportPath -DefaultFileName "SmartDisplay-MES-performance-smoke-$stamp.md"
$resolvedJsonPath = Resolve-ReportPath -Path $JsonPath -DefaultFileName "SmartDisplay-MES-performance-smoke-$stamp.json"
$context = @{
    GeneratedAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    BaseUrl = $BaseUrl
    Username = $Username
    ImportCount = $ImportCount
    Samples = $Samples
    TraceLotNo = $TraceLotNo
    Thresholds = @{
        OrderListMs = $OrderListThresholdMs
        LotListMs = $LotListThresholdMs
        YieldDashboardMs = $YieldDashboardThresholdMs
        LotTraceMs = $LotTraceThresholdMs
    }
}
Write-PerformanceReports -Results $results.ToArray() -ResolvedReportPath $resolvedReportPath -ResolvedJsonPath $resolvedJsonPath -Context $context

$failed = $results | Where-Object { -not $_.Passed }
if ($failed) {
    Write-Host "Performance smoke failed:" -ForegroundColor Red
    $failed | Format-Table -AutoSize
    exit 1
}

Write-Host "Performance smoke passed." -ForegroundColor Green
exit 0
