# CleanHome installer for Windows (PowerShell).
#   .\install.ps1 192.168.1.50            install CleanHome on the TV at that address
#   .\install.ps1 192.168.1.50 -Debloat   ...and switch off tracking/bloat apps (undo with uninstall.ps1)
param([Parameter(Mandatory = $true)][string]$TvIp, [switch]$Debloat)
# adb prints normal status lines on stderr, so don't treat those as errors (Windows PowerShell 5.1).
$ErrorActionPreference = "Continue"
$ProgressPreference = "SilentlyContinue"   # downloads are much faster without the progress bar
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
Set-Location $PSScriptRoot
$Repo = "SamirHarawa/CleanHome"
if ($TvIp -notmatch ":") { $TvIp = "$($TvIp):5555" }
function Step($t) { Write-Host "`n$t" -ForegroundColor Cyan }

Step "1/5  Getting adb"
$adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adb) {
  $adb = Join-Path $PSScriptRoot "platform-tools\adb.exe"
  if (-not (Test-Path $adb)) {
    Write-Host "  Downloading Android platform-tools from Google..."
    Invoke-WebRequest "https://dl.google.com/android/repository/platform-tools-latest-windows.zip" -OutFile platform-tools.zip
    Expand-Archive platform-tools.zip -DestinationPath . -Force
    Remove-Item platform-tools.zip
  }
}
Write-Host "  Using $adb"

Step "2/5  Getting CleanHome.apk"
$apk = @("CleanHome.apk", "..\CleanHome.apk", "..\app\CleanHome.apk") | Where-Object { Test-Path $_ } | Select-Object -First 1
if (-not $apk) {
  Write-Host "  Downloading the latest release..."
  Invoke-WebRequest "https://github.com/$Repo/releases/latest/download/CleanHome.apk" -OutFile CleanHome.apk
  $apk = "CleanHome.apk"
}
Write-Host "  Using $apk"

Step "3/5  Connecting to the TV at $TvIp"
Write-Host "  If the TV asks to allow debugging, tick 'Always allow from this computer' and press OK."
& $adb connect $TvIp | Out-Null
$state = ""
for ($i = 1; $i -le 60; $i++) {
  $state = (& $adb -s $TvIp get-state 2>$null)
  if ($state -eq "device") { break }
  if ($i % 10 -eq 0) { & $adb connect $TvIp | Out-Null }
  Start-Sleep 2
}
if ($state -ne "device") { Write-Host "  Could not connect. Check the IP and that Network debugging is on (see README)."; exit 1 }
$make = (& $adb -s $TvIp shell getprop ro.product.manufacturer).Trim()
$model = (& $adb -s $TvIp shell getprop ro.product.model).Trim()
Write-Host "  Connected: $make $model"

Step "4/5  Installing CleanHome"
& $adb -s $TvIp install -r $apk

Step "5/5  Setting up"
& $adb -s $TvIp shell rm -rf /data/local/tmp/cleanhome
& $adb -s $TvIp push tv /data/local/tmp/cleanhome | Out-Null
$arg = ""; if ($Debloat) { $arg = "debloat" }
& $adb -s $TvIp shell sh /data/local/tmp/cleanhome/setup.sh $arg
if ($Debloat) { Write-Host "  Saving changes on the TV (40 seconds - please don't turn it off)..."; Start-Sleep 40 }
Write-Host "`nCleanHome is installed. Press Home on the remote. To undo everything: .\uninstall.ps1 $($TvIp -replace ':5555','')" -ForegroundColor Green
