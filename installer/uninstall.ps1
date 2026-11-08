# Removes CleanHome and puts the TV back exactly as it was.   .\uninstall.ps1 192.168.1.50
param([Parameter(Mandatory = $true)][string]$TvIp)
# adb prints normal status lines on stderr, so don't treat those as errors (Windows PowerShell 5.1).
$ErrorActionPreference = "Continue"
$ProgressPreference = "SilentlyContinue"   # downloads are much faster without the progress bar
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
Set-Location $PSScriptRoot
if ($TvIp -notmatch ":") { $TvIp = "$($TvIp):5555" }
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) { $adb = Join-Path $PSScriptRoot "platform-tools\adb.exe" }
if (-not (Test-Path $adb)) { Write-Host "adb not found - run install.ps1 once (it downloads adb)."; exit 1 }

& $adb connect $TvIp | Out-Null
for ($i = 1; $i -le 30; $i++) { if ((& $adb -s $TvIp get-state 2>$null) -eq "device") { break }; Start-Sleep 2 }
& $adb -s $TvIp push tv /data/local/tmp/cleanhome | Out-Null
& $adb -s $TvIp shell sh /data/local/tmp/cleanhome/remove.sh
& $adb -s $TvIp shell rm -rf /data/local/tmp/cleanhome
Write-Host "Saving changes on the TV (40 seconds)..."; Start-Sleep 40
Write-Host "CleanHome removed. Your TV is back to Google TV's own home." -ForegroundColor Green
