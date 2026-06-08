param(
    [string]$BaseUrl = "http://localhost:8080/api",
    [string]$Username = "planner",
    [string]$Password = "123456",
    [int]$Rounds = 3,
    [int]$Samples = 20,
    [int]$ImportCount = 1000,
    [string]$TraceLotNo = "LOT202406001",
    [int]$OrderListThresholdMs = 500,
    [int]$LotListThresholdMs = 500,
    [int]$YieldDashboardThresholdMs = 2000,
    [int]$LotTraceThresholdMs = 1000,
    [double]$MaxP95DriftPercent = 75,
    [switch]$FailOnStabilityWarning,
    [string]$ReportPath = "",
    [string]$JsonPath = "",
    [string]$RoundReportDir = ""
)

$ErrorActionPreference = "Stop"
$ProjectRoot = Split-Path -Parent $PSScriptRoot
$SmokeScript = Join-Path -Path $PSScriptRoot -ChildPath "run-pilot-performance-smoke.ps1"

function Resolve-OutputPath {
    param(
        [string]$Path,
        [string]$DefaultFileName
    )
    if (-not [string]::IsNullOrWhiteSpace($Path)) {
        return $Path
    }
    return Join-Path -Path (Join-Path -Path $ProjectRoot -ChildPath "docs") -ChildPath $DefaultFileName
}

function Ensure-Directory {
    param([string]$Path)
    if (-not [string]::IsNullOrWhiteSpace($Path) -and -not (Test-Path $Path)) {
        New-Item -ItemType Directory -Path $Path | Out-Null
    }
}

function Get-PowerShellExe {
    $currentProcess = Get-Process -Id $PID
    if ($null -ne $currentProcess.Path -and (Test-Path $currentProcess.Path)) {
        return $currentProcess.Path
    }
    $fallback = Join-Path -Path $PSHOME -ChildPath "powershell.exe"
    if (Test-Path $fallback) {
        return $fallback
    }
    return "powershell.exe"
}

function Get-NumberStats {
    param([double[]]$Values)
    if ($null -eq $Values -or $Values.Count -eq 0) {
        return [pscustomobject]@{
            Min = 0
            Average = 0
            Max = 0
            StdDev = 0
            DriftPercent = 0
        }
    }

    $sorted = $Values | Sort-Object
    $avg = ($Values | Measure-Object -Average).Average
    $variance = 0
    foreach ($value in $Values) {
        $variance += [Math]::Pow(($value - $avg), 2)
    }
    $stdDev = [Math]::Sqrt($variance / [Math]::Max(1, $Values.Count))
    $drift = 0
    if ($avg -gt 0) {
        $drift = (([double]$sorted[$sorted.Count - 1] - [double]$sorted[0]) / [Math]::Max(1, $avg)) * 100
    }

    return [pscustomobject]@{
        Min = [Math]::Round([double]$sorted[0], 2)
        Average = [Math]::Round([double]$avg, 2)
        Max = [Math]::Round([double]$sorted[$sorted.Count - 1], 2)
        StdDev = [Math]::Round([double]$stdDev, 2)
        DriftPercent = [Math]::Round([double]$drift, 2)
    }
}

function New-AggregateResults {
    param(
        [object[]]$RoundRecords,
        [object[]]$RoundSummaries
    )

    $results = New-Object System.Collections.Generic.List[object]
    $names = $RoundRecords | Select-Object -ExpandProperty Name -Unique
    foreach ($name in $names) {
        $rows = @($RoundRecords | Where-Object { $_.Name -eq $name })
        $p95Values = @($rows | ForEach-Object { [double]$_.P95Ms })
        $stats = Get-NumberStats -Values $p95Values
        $threshold = [int]($rows | Select-Object -First 1 -ExpandProperty ThresholdMs)
        $completedRounds = $rows.Count
        $roundFailures = @($rows | Where-Object { -not $_.Passed }).Count
        $thresholdPassed = $true
        if ($threshold -gt 0) {
            $thresholdPassed = $stats.Max -le $threshold
        }
        $stabilityWarning = $false
        if ($threshold -gt 0 -and $completedRounds -gt 1) {
            $stabilityWarning = $stats.DriftPercent -gt $MaxP95DriftPercent
        }
        $passed = ($roundFailures -eq 0 -and $thresholdPassed)
        if ($FailOnStabilityWarning -and $stabilityWarning) {
            $passed = $false
        }

        $stability = if ($stabilityWarning) { "WARN" } else { "STABLE" }
        $details = "roundFailures=$roundFailures"
        if ($stabilityWarning) {
            $details = "$details, p95Drift=$($stats.DriftPercent)% > $MaxP95DriftPercent%"
        }

        $results.Add([pscustomobject]@{
            Name = $name
            Rounds = $Rounds
            CompletedRounds = $completedRounds
            MinP95Ms = $stats.Min
            AverageP95Ms = $stats.Average
            MaxP95Ms = $stats.Max
            StdDevP95Ms = $stats.StdDev
            DriftPercent = $stats.DriftPercent
            ThresholdMs = $threshold
            Stability = $stability
            Passed = $passed
            Details = $details
        })
    }

    $missingRounds = @($RoundSummaries | Where-Object { -not $_.JsonLoaded }).Count
    if ($missingRounds -gt 0) {
        $results.Add([pscustomobject]@{
            Name = "Round report completeness"
            Rounds = $Rounds
            CompletedRounds = $Rounds - $missingRounds
            MinP95Ms = 0
            AverageP95Ms = 0
            MaxP95Ms = 0
            StdDevP95Ms = 0
            DriftPercent = 0
            ThresholdMs = 0
            Stability = "WARN"
            Passed = $false
            Details = "missingJsonReports=$missingRounds"
        })
    }

    return $results.ToArray()
}

function Write-BaselineReports {
    param(
        [object[]]$RoundSummaries,
        [object[]]$AggregateResults,
        [string]$ResolvedReportPath,
        [string]$ResolvedJsonPath,
        [hashtable]$Context
    )

    $failed = @($AggregateResults | Where-Object { -not $_.Passed })
    $warnings = @($AggregateResults | Where-Object { $_.Stability -eq "WARN" })
    $status = "PASSED"
    if ($failed.Count -gt 0) {
        $status = "FAILED"
    } elseif ($warnings.Count -gt 0) {
        $status = "PASSED_WITH_WARNINGS"
    }

    Ensure-Directory -Path (Split-Path -Parent $ResolvedReportPath)
    Ensure-Directory -Path (Split-Path -Parent $ResolvedJsonPath)

    $payload = [ordered]@{
        status = $status
        generatedAt = $Context.GeneratedAt
        baseUrl = $Context.BaseUrl
        username = $Context.Username
        rounds = $Context.Rounds
        samples = $Context.Samples
        importCount = $Context.ImportCount
        traceLotNo = $Context.TraceLotNo
        maxP95DriftPercent = $Context.MaxP95DriftPercent
        failOnStabilityWarning = $Context.FailOnStabilityWarning
        thresholds = $Context.Thresholds
        roundReports = $RoundSummaries
        aggregateResults = $AggregateResults
    }
    $payload | ConvertTo-Json -Depth 30 | Set-Content -Path $ResolvedJsonPath -Encoding UTF8

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# SmartDisplay MES Performance Baseline Report")
    $lines.Add("")
    $lines.Add("- Generated at: $($Context.GeneratedAt)")
    $lines.Add("- Base URL: $($Context.BaseUrl)")
    $lines.Add("- Username: $($Context.Username)")
    $lines.Add("- Rounds: $($Context.Rounds)")
    $lines.Add("- Samples per round: $($Context.Samples)")
    $lines.Add("- ERP import count per round: $($Context.ImportCount)")
    $lines.Add("- Trace Lot: $($Context.TraceLotNo)")
    $lines.Add("- Max P95 drift warning: $($Context.MaxP95DriftPercent)%")
    $lines.Add("- Verdict: $status")
    $lines.Add("")
    $lines.Add("## Aggregate")
    $lines.Add("")
    $lines.Add("| Check | Completed | Avg P95(ms) | Min P95(ms) | Max P95(ms) | StdDev | Drift(%) | Threshold(ms) | Stability | Result | Details |")
    $lines.Add("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- | --- | --- |")
    foreach ($row in $AggregateResults) {
        $resultText = if ($row.Passed) { "PASS" } else { "FAIL" }
        $lines.Add("| $($row.Name) | $($row.CompletedRounds)/$($row.Rounds) | $($row.AverageP95Ms) | $($row.MinP95Ms) | $($row.MaxP95Ms) | $($row.StdDevP95Ms) | $($row.DriftPercent) | $($row.ThresholdMs) | $($row.Stability) | $resultText | $($row.Details) |")
    }
    $lines.Add("")
    $lines.Add("## Rounds")
    $lines.Add("")
    $lines.Add("| Round | ExitCode | JSON Loaded | Status | Markdown Report | JSON Report |")
    $lines.Add("| ---: | ---: | --- | --- | --- | --- |")
    foreach ($round in $RoundSummaries) {
        $jsonLoaded = if ($round.JsonLoaded) { "YES" } else { "NO" }
        $lines.Add("| $($round.Round) | $($round.ExitCode) | $jsonLoaded | $($round.Status) | $($round.ReportPath) | $($round.JsonPath) |")
    }
    $lines.Add("")
    if ($failed.Count -gt 0) {
        $lines.Add("## Failed Checks")
        foreach ($row in $failed) {
            $lines.Add("- $($row.Name): maxP95=$($row.MaxP95Ms)ms, threshold=$($row.ThresholdMs)ms, details=$($row.Details)")
        }
    } elseif ($warnings.Count -gt 0) {
        $lines.Add("## Stability Warnings")
        foreach ($row in $warnings) {
            $lines.Add("- $($row.Name): drift=$($row.DriftPercent)%, maxP95=$($row.MaxP95Ms)ms")
        }
    } else {
        $lines.Add("All performance baseline checks passed without stability warnings.")
    }
    $lines | Set-Content -Path $ResolvedReportPath -Encoding UTF8

    Write-Host "Markdown report: $ResolvedReportPath"
    Write-Host "JSON report: $ResolvedJsonPath"

    return $status
}

if (-not (Test-Path $SmokeScript)) {
    throw "Missing smoke script: $SmokeScript"
}
if ($Rounds -lt 2) {
    throw "Rounds must be at least 2 for a stability baseline."
}
if ($Samples -lt 1) {
    throw "Samples must be greater than 0."
}
if ($ImportCount -lt 1 -or $ImportCount -gt 1000) {
    throw "ImportCount must be between 1 and 1000."
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
if ([string]::IsNullOrWhiteSpace($RoundReportDir)) {
    $RoundReportDir = Join-Path -Path (Join-Path -Path $ProjectRoot -ChildPath "docs") -ChildPath "performance-baseline-rounds"
}
Ensure-Directory -Path $RoundReportDir

$resolvedReportPath = Resolve-OutputPath -Path $ReportPath -DefaultFileName "SmartDisplay-MES-performance-baseline-$stamp.md"
$resolvedJsonPath = Resolve-OutputPath -Path $JsonPath -DefaultFileName "SmartDisplay-MES-performance-baseline-$stamp.json"
$powerShellExe = Get-PowerShellExe

Write-Host "SmartDisplay MES performance baseline"
Write-Host "BaseUrl=$BaseUrl Username=$Username Rounds=$Rounds Samples=$Samples ImportCount=$ImportCount"
Write-Host "SmokeScript=$SmokeScript"

$roundSummaries = New-Object System.Collections.Generic.List[object]
$roundRecords = New-Object System.Collections.Generic.List[object]

for ($round = 1; $round -le $Rounds; $round++) {
    $roundText = "{0:D2}" -f $round
    $roundReport = Join-Path -Path $RoundReportDir -ChildPath "SmartDisplay-MES-performance-smoke-$stamp-round$roundText.md"
    $roundJson = Join-Path -Path $RoundReportDir -ChildPath "SmartDisplay-MES-performance-smoke-$stamp-round$roundText.json"
    Write-Host "Running baseline round $round/$Rounds"

    $args = @(
        "-NoProfile",
        "-ExecutionPolicy",
        "Bypass",
        "-File",
        $SmokeScript,
        "-BaseUrl",
        $BaseUrl,
        "-Username",
        $Username,
        "-Password",
        $Password,
        "-ImportCount",
        ([string]$ImportCount),
        "-Samples",
        ([string]$Samples),
        "-TraceLotNo",
        $TraceLotNo,
        "-OrderListThresholdMs",
        ([string]$OrderListThresholdMs),
        "-LotListThresholdMs",
        ([string]$LotListThresholdMs),
        "-YieldDashboardThresholdMs",
        ([string]$YieldDashboardThresholdMs),
        "-LotTraceThresholdMs",
        ([string]$LotTraceThresholdMs),
        "-ReportPath",
        $roundReport,
        "-JsonPath",
        $roundJson
    )

    & $powerShellExe @args
    $exitCode = $LASTEXITCODE
    $jsonLoaded = $false
    $roundStatus = "NO_JSON"
    if (Test-Path $roundJson) {
        $jsonLoaded = $true
        $roundPayload = Get-Content -Path $roundJson -Raw -Encoding UTF8 | ConvertFrom-Json
        $roundStatus = $roundPayload.status
        foreach ($result in $roundPayload.results) {
            $roundRecords.Add([pscustomobject]@{
                Round = $round
                Name = $result.Name
                Samples = $result.Samples
                MinMs = $result.MinMs
                AverageMs = $result.AverageMs
                P95Ms = $result.P95Ms
                MaxMs = $result.MaxMs
                ThresholdMs = $result.ThresholdMs
                Passed = [bool]$result.Passed
                Details = $result.Details
            })
        }
    }

    $roundSummaries.Add([pscustomobject]@{
        Round = $round
        ExitCode = $exitCode
        JsonLoaded = $jsonLoaded
        Status = $roundStatus
        ReportPath = $roundReport
        JsonPath = $roundJson
    })
}

$aggregateResults = New-AggregateResults -RoundRecords $roundRecords.ToArray() -RoundSummaries $roundSummaries.ToArray()
$context = @{
    GeneratedAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    BaseUrl = $BaseUrl
    Username = $Username
    Rounds = $Rounds
    Samples = $Samples
    ImportCount = $ImportCount
    TraceLotNo = $TraceLotNo
    MaxP95DriftPercent = $MaxP95DriftPercent
    FailOnStabilityWarning = [bool]$FailOnStabilityWarning
    Thresholds = @{
        OrderListMs = $OrderListThresholdMs
        LotListMs = $LotListThresholdMs
        YieldDashboardMs = $YieldDashboardThresholdMs
        LotTraceMs = $LotTraceThresholdMs
    }
}
$status = Write-BaselineReports -RoundSummaries $roundSummaries.ToArray() -AggregateResults $aggregateResults -ResolvedReportPath $resolvedReportPath -ResolvedJsonPath $resolvedJsonPath -Context $context

if ($status -eq "FAILED") {
    Write-Host "Performance baseline failed." -ForegroundColor Red
    exit 1
}

if ($status -eq "PASSED_WITH_WARNINGS") {
    Write-Host "Performance baseline passed with stability warnings." -ForegroundColor Yellow
    exit 0
}

Write-Host "Performance baseline passed." -ForegroundColor Green
exit 0
