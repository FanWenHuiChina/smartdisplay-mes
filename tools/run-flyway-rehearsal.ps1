param(
    [string]$ProjectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path,
    [string]$ApiDir = "",
    [string]$ReportDir = "",
    [string]$DbImage = "postgres:16-alpine",
    [string]$DbName = "smartdisplay_mes_rehearsal",
    [string]$DbUser = "postgres",
    [string]$DbPassword = "mes123456",
    [int]$StartupTimeoutSec = 120,
    [switch]$SkipPackage
)

$ErrorActionPreference = "Stop"

if (-not $ApiDir) {
    $ApiDir = Join-Path $ProjectRoot "smartdisplay-mes-api"
}
if (-not $ReportDir) {
    $ReportDir = Join-Path $ProjectRoot "docs"
}

$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$containerName = "smartdisplay-mes-flyway-rehearsal-$timestamp"
$restoreDbName = "${DbName}_restore"
$reportMdPath = Join-Path $ReportDir "SmartDisplay-MES-flyway-rehearsal-$timestamp.md"
$reportJsonPath = Join-Path $ReportDir "SmartDisplay-MES-flyway-rehearsal-$timestamp.json"
$javaLogPath = Join-Path ([System.IO.Path]::GetTempPath()) "smartdisplay-mes-flyway-rehearsal-$timestamp-java.log"
$javaCmdPath = Join-Path ([System.IO.Path]::GetTempPath()) "smartdisplay-mes-flyway-rehearsal-$timestamp.cmd"
$checks = New-Object System.Collections.ArrayList
$javaLog = New-Object System.Collections.ArrayList
$status = "FAIL"
$errorMessage = ""
$hostPort = ""
$expectedVersion = ""
$expectedDescription = ""
$migrationCount = 0
$latestVersion = ""
$latestDescription = ""
$latestSuccess = ""
$flywayHistoryTail = ""
$tableCount = 0
$userCount = 0
$routeStepCount = 0
$restoreLatestVersion = ""
$restoreTableCount = 0
$appProcess = $null
$containerStarted = $false

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

function Invoke-CommandChecked {
    param([string]$FilePath, [string[]]$Arguments, [string]$WorkingDirectory = $ProjectRoot)
    Push-Location $WorkingDirectory
    $oldErrorActionPreference = $ErrorActionPreference
    try {
        $ErrorActionPreference = "Continue"
        $output = & $FilePath @Arguments 2>&1
        $exitCode = $LASTEXITCODE
        if ($exitCode -ne 0) {
            throw "$FilePath $($Arguments -join ' ') failed with exit code $exitCode. $($output -join "`n")"
        }
        return ($output -join "`n")
    } finally {
        $ErrorActionPreference = $oldErrorActionPreference
        Pop-Location
    }
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
    param([string]$Database, [string]$Sql)
    $result = Invoke-Docker -Arguments @("exec", $containerName, "psql", "-U", $DbUser, "-d", $Database, "-At", "-c", $Sql)
    return ($result.output -split "`n" | Select-Object -First 1).Trim()
}

function Get-MigrationSummary {
    $migrationDir = Join-Path $ApiDir "src\main\resources\db\migration"
    $files = Get-ChildItem -LiteralPath $migrationDir -File -Filter "V*.sql"
    if (-not $files) {
        throw "No Flyway migrations found: $migrationDir"
    }
    $items = foreach ($file in $files) {
        if ($file.Name -notmatch '^V(?<version>\d+(?:\.\d+)*)__[A-Za-z0-9_]+\.sql$') {
            throw "Invalid Flyway migration name: $($file.Name)"
        }
        $segments = $Matches.version.Split(".") | ForEach-Object { [int]$_ }
        [pscustomobject]@{
            File = $file
            Version = $Matches.version
            Major = $segments[0]
            Minor = if ($segments.Count -gt 1) { $segments[1] } else { 0 }
            Description = ([System.IO.Path]::GetFileNameWithoutExtension($file.Name) -replace '^V[^_]+__', '' -replace '_', ' ')
        }
    }
    $sorted = $items | Sort-Object Major, Minor
    return [ordered]@{
        count = @($sorted).Count
        latest = $sorted[-1]
    }
}

function Start-AppProcess {
    param([System.IO.FileInfo]$JarFile, [string]$JdbcUrl)

    $cmdLines = @(
        "@echo off",
        "set ""SPRING_DATASOURCE_URL=$JdbcUrl""",
        "set ""SPRING_DATASOURCE_USERNAME=$DbUser""",
        "set ""SPRING_DATASOURCE_PASSWORD=$DbPassword""",
        "set ""SERVER_PORT=0""",
        "set ""TZ=Asia/Shanghai""",
        "set ""LOGGING_LEVEL_COM_VISIONOX_MES=info""",
        "set ""LOGGING_LEVEL_COM_BAOMIDOU_MYBATISPLUS=info""",
        "java -jar ""$($JarFile.FullName)"" >> ""$javaLogPath"" 2>&1"
    )
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($javaCmdPath, (($cmdLines -join "`r`n") + "`r`n"), $utf8NoBom)

    $psi = New-Object System.Diagnostics.ProcessStartInfo
    $psi.FileName = "cmd.exe"
    $psi.Arguments = ('/d /c "{0}"' -f $javaCmdPath)
    $psi.WorkingDirectory = $ApiDir
    $psi.UseShellExecute = $false

    $process = New-Object System.Diagnostics.Process
    $process.StartInfo = $psi

    [void]$process.Start()
    return $process
}

function Refresh-JavaLog {
    if (-not (Test-Path -LiteralPath $javaLogPath)) {
        return
    }
    $script:javaLog.Clear()
    foreach ($line in (Get-Content -LiteralPath $javaLogPath -Tail 200 -ErrorAction SilentlyContinue)) {
        [void]$script:javaLog.Add($line)
    }
}

function Test-AppStarted {
    Refresh-JavaLog
    foreach ($line in $script:javaLog) {
        if ($line -match 'Started .* in ') {
            return $true
        }
    }
    return $false
}

function Write-Report {
    param([string]$FinalStatus)

    New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null
    $report = [ordered]@{
        generatedAt = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss zzz")
        status = $FinalStatus
        dbImage = $DbImage
        expectedVersion = $expectedVersion
        expectedDescription = $expectedDescription
        migrationCount = $migrationCount
        latestVersion = $latestVersion
        latestDescription = $latestDescription
        latestSuccess = $latestSuccess
        flywayHistoryTail = $flywayHistoryTail
        tableCount = $tableCount
        userCount = $userCount
        routeStepCount = $routeStepCount
        restoreLatestVersion = $restoreLatestVersion
        restoreTableCount = $restoreTableCount
        error = $errorMessage
        checks = @($checks)
        javaLogTail = @($javaLog | Select-Object -Last 40)
    }

    $json = $report | ConvertTo-Json -Depth 8
    $utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    [System.IO.File]::WriteAllText($reportJsonPath, $json + "`n", $utf8NoBom)

    $lines = New-Object System.Collections.Generic.List[string]
    $lines.Add("# SmartDisplay MES Flyway Rehearsal Report")
    $lines.Add("")
    $lines.Add("- Generated at: $($report.generatedAt)")
    $lines.Add("- Status: $FinalStatus")
    $lines.Add("- Database image: $DbImage")
    $lines.Add("- Expected latest migration: V$expectedVersion $expectedDescription")
    $lines.Add("- Actual latest migration: V$latestVersion $latestDescription")
    $lines.Add("- Actual latest success: $latestSuccess")
    $lines.Add("- Migration files: $migrationCount")
    $lines.Add("- Public tables: $tableCount")
    $lines.Add("- Seed users: $userCount")
    $lines.Add("- Route steps: $routeStepCount")
    $lines.Add("- Restore latest migration: V$restoreLatestVersion")
    $lines.Add("- Restore public tables: $restoreTableCount")
    if ($errorMessage) {
        $lines.Add("- Error: $errorMessage")
    }
    $lines.Add("")
    $lines.Add("## Checks")
    $lines.Add("")
    $lines.Add("| Check | Status | Detail |")
    $lines.Add("| --- | --- | --- |")
    foreach ($check in $checks) {
        $detail = [string]$check.detail
        $detail = $detail.Replace("|", "\|").Replace("`r", "").Replace("`n", "<br>")
        $lines.Add("| $($check.name) | $($check.status) | $detail |")
    }
    $lines.Add("")
    $lines.Add("## Flyway History Tail")
    $lines.Add("")
    $lines.Add('```text')
    if ($flywayHistoryTail) {
        foreach ($line in ($flywayHistoryTail -split "`n")) {
            $lines.Add($line)
        }
    }
    $lines.Add('```')
    $lines.Add("")
    $lines.Add("## Java Log Tail")
    $lines.Add("")
    $lines.Add('```text')
    foreach ($line in @($javaLog | Select-Object -Last 40)) {
        $lines.Add($line)
    }
    $lines.Add('```')
    $lines.Add("")

    [System.IO.File]::WriteAllText($reportMdPath, (($lines -join "`n") + "`n"), $utf8NoBom)
}

try {
    $summary = Get-MigrationSummary
    $migrationCount = $summary.count
    $expectedVersion = $summary.latest.Version
    $expectedDescription = $summary.latest.Description

    $staticOutput = Invoke-CommandChecked -FilePath "powershell" -Arguments @("-ExecutionPolicy", "Bypass", "-File", (Join-Path $PSScriptRoot "verify-flyway-migrations.ps1")) -WorkingDirectory $ProjectRoot
    Assert-Check -Condition ($staticOutput -match "passed") -Name "static migration verification" -Detail "V1.1-V$expectedVersion, $migrationCount files"

    if (-not $SkipPackage) {
        Invoke-CommandChecked -FilePath "mvn.cmd" -Arguments @("-DskipTests", "package") -WorkingDirectory $ApiDir | Out-Null
        Add-Check -Name "maven package" -Result "PASS" -Detail "backend package generated"
    } else {
        Add-Check -Name "maven package" -Result "SKIP" -Detail "SkipPackage enabled"
    }

    $jar = Get-ChildItem -LiteralPath (Join-Path $ApiDir "target") -File -Filter "*-exec.jar" |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    Assert-Check -Condition ($null -ne $jar) -Name "executable jar" -Detail $jar.FullName

    Invoke-Docker -Arguments @(
        "run", "-d", "--rm",
        "--name", $containerName,
        "-e", "POSTGRES_DB=$DbName",
        "-e", "POSTGRES_USER=$DbUser",
        "-e", "POSTGRES_PASSWORD=$DbPassword",
        "-e", "TZ=Asia/Shanghai",
        "-p", "127.0.0.1::5432",
        $DbImage
    ) | Out-Null
    $containerStarted = $true
    Add-Check -Name "temporary postgres container" -Result "PASS" -Detail $containerName

    $deadline = (Get-Date).AddSeconds($StartupTimeoutSec)
    while ((Get-Date) -lt $deadline) {
        $portResult = Invoke-Docker -Arguments @("port", $containerName, "5432/tcp") -AllowFailure
        if ($portResult.code -eq 0 -and $portResult.output -match ':(\d+)$') {
            $hostPort = $Matches[1]
        }
        $ready = Invoke-Docker -Arguments @("exec", $containerName, "pg_isready", "-U", $DbUser, "-d", $DbName) -AllowFailure
        if ($hostPort -and $ready.code -eq 0) {
            break
        }
        Start-Sleep -Seconds 1
    }
    Assert-Check -Condition ([bool]$hostPort) -Name "postgres port allocation" -Detail "127.0.0.1:$hostPort"

    $jdbcUrl = "jdbc:postgresql://127.0.0.1:$hostPort/$DbName"
    $appProcess = Start-AppProcess -JarFile $jar -JdbcUrl $jdbcUrl

    $deadline = (Get-Date).AddSeconds($StartupTimeoutSec)
    $appStarted = $false
    while ((Get-Date) -lt $deadline) {
        if ($appProcess.HasExited) {
            throw "Application exited before migration completed. ExitCode=$($appProcess.ExitCode)"
        }
        $latest = Invoke-Docker -Arguments @("exec", $containerName, "psql", "-U", $DbUser, "-d", $DbName, "-At", "-c", "select version || '|' || description || '|' || success from flyway_schema_history order by installed_rank desc limit 5;") -AllowFailure
        if ($latest.code -eq 0 -and $latest.output) {
            $flywayHistoryTail = $latest.output
            $firstLine = ($latest.output -split "`n" | Select-Object -First 1).Trim()
            if ($firstLine -match '^(?<version>[^|]+)\|(?<description>[^|]+)\|(?<success>[^|]+)') {
                $latestVersion = $Matches.version
                $latestDescription = $Matches.description
                $latestSuccess = $Matches.success
            }
        }
        $appStarted = Test-AppStarted
        if ($latestVersion -eq $expectedVersion -and $latestSuccess -in @("t", "true") -and $appStarted) {
            break
        }
        Start-Sleep -Seconds 1
    }
    Assert-Check -Condition ($latestVersion -eq $expectedVersion -and $latestSuccess -in @("t", "true")) -Name "flyway migrated to latest" -Detail "latest=V$latestVersion $latestDescription success=$latestSuccess"
    Assert-Check -Condition $appStarted -Name "application starts after migration" -Detail "Spring Boot startup completed"

    $tableCount = [int](Invoke-PsqlScalar -Database $DbName -Sql "select count(*) from information_schema.tables where table_schema='public';")
    $userCount = [int](Invoke-PsqlScalar -Database $DbName -Sql "select count(*) from sys_user;")
    $routeStepCount = [int](Invoke-PsqlScalar -Database $DbName -Sql "select count(*) from md_route_step;")
    Assert-Check -Condition ($tableCount -ge 40) -Name "public table count" -Detail "$tableCount tables"
    Assert-Check -Condition ($userCount -ge 6) -Name "seed users" -Detail "$userCount users"
    Assert-Check -Condition ($routeStepCount -ge 10) -Name "route seed data" -Detail "$routeStepCount route steps"

    $restoreCommand = "pg_dump -U $DbUser -d $DbName -Fc -f /tmp/rehearsal.dump && createdb -U $DbUser $restoreDbName && pg_restore -U $DbUser -d $restoreDbName /tmp/rehearsal.dump"
    Invoke-Docker -Arguments @("exec", $containerName, "sh", "-lc", $restoreCommand) | Out-Null
    $restoreLatestVersion = Invoke-PsqlScalar -Database $restoreDbName -Sql "select version from flyway_schema_history order by installed_rank desc limit 1;"
    $restoreTableCount = [int](Invoke-PsqlScalar -Database $restoreDbName -Sql "select count(*) from information_schema.tables where table_schema='public';")
    Assert-Check -Condition ($restoreLatestVersion -eq $expectedVersion) -Name "backup restore latest migration" -Detail "restored latest=V$restoreLatestVersion"
    Assert-Check -Condition ($restoreTableCount -eq $tableCount) -Name "backup restore table count" -Detail "restored tables=$restoreTableCount"

    $status = "PASS"
} catch {
    $errorMessage = $_.Exception.Message
    $status = "FAIL"
} finally {
    if ($appProcess -and -not $appProcess.HasExited) {
        try {
            $appProcess.Kill()
            [void]$appProcess.WaitForExit(10000)
        } catch {}
    }
    Refresh-JavaLog
    if ($containerStarted) {
        try {
            Invoke-Docker -Arguments @("stop", $containerName) -AllowFailure | Out-Null
        } catch {}
    }
    Write-Report -FinalStatus $status
}

if ($status -ne "PASS") {
    Write-Host "Flyway rehearsal failed"
    Write-Host "Report: $reportMdPath"
    Write-Host $errorMessage
    [Environment]::Exit(1)
}

Write-Host "Flyway rehearsal passed"
Write-Host "Report: $reportMdPath"
[Environment]::Exit(0)
