$enc = [System.Text.UTF8Encoding]::new($false)
[Console]::OutputEncoding = $enc
$OutputEncoding = $enc

# Default UTF-8 for common file cmdlets
$PSDefaultParameterValues['Out-File:Encoding'] = 'utf8'
$PSDefaultParameterValues['Set-Content:Encoding'] = 'utf8'
$PSDefaultParameterValues['Add-Content:Encoding'] = 'utf8'

Write-Output "PowerShell IO encoding set to UTF-8 (no BOM)."
