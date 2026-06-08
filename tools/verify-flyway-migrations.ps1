param(
    [string]$MigrationDir = (Join-Path $PSScriptRoot "..\smartdisplay-mes-api\src\main\resources\db\migration")
)

$ErrorActionPreference = "Stop"

function New-VersionItem {
    param([System.IO.FileInfo]$File)

    if ($File.Name -notmatch '^V(?<version>\d+(?:\.\d+)*)__[A-Za-z0-9_]+\.sql$') {
        throw "Invalid Flyway migration file name: $($File.Name)"
    }

    $segments = $Matches.version.Split(".") | ForEach-Object { [int]$_ }
    [pscustomobject]@{
        File = $File
        Version = $Matches.version
        Major = $segments[0]
        Minor = if ($segments.Count -gt 1) { $segments[1] } else { 0 }
        Length = $File.Length
    }
}

$resolvedDir = Resolve-Path -LiteralPath $MigrationDir
$files = Get-ChildItem -LiteralPath $resolvedDir -File -Filter "V*.sql"
if (-not $files) {
    throw "No Flyway migration files found: $resolvedDir"
}

$items = $files | ForEach-Object { New-VersionItem -File $_ } | Sort-Object Major, Minor
$duplicates = $items | Group-Object Version | Where-Object { $_.Count -gt 1 }
if ($duplicates) {
    $versions = ($duplicates | ForEach-Object { $_.Name }) -join ", "
    throw "Duplicate migration versions: $versions"
}

for ($i = 0; $i -lt $items.Count; $i++) {
    if ($items[$i].Length -le 0) {
        throw "Empty migration file: $($items[$i].File.Name)"
    }
    if ($i -gt 0 -and $items[$i].Major -eq $items[$i - 1].Major) {
        $expectedMinor = $items[$i - 1].Minor + 1
        if ($items[$i].Minor -ne $expectedMinor) {
            throw "Migration versions are not continuous: expected V$($items[$i].Major).$expectedMinor after V$($items[$i - 1].Version), actual V$($items[$i].Version)"
        }
    }
}

Write-Host "Flyway migration static verification passed: $($items.Count) files"
$items | ForEach-Object {
    Write-Host (" - V{0} {1} ({2} bytes)" -f $_.Version, $_.File.Name, $_.Length)
}
