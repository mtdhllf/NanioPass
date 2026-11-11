param(
    [switch]$Release,
    [switch]$Install,
    [switch]$Run
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $root
try {
    $task = if ($Release) { ":app:assembleRelease" } else { ":app:assembleDebug" }
    Write-Host "==> Building $task ..." -ForegroundColor Cyan
    & .\gradlew.bat $task --console=plain
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed ($LASTEXITCODE)" }

    $apk = if ($Release) {
        Join-Path $root "app\build\outputs\apk\release\app-release.apk"
    } else {
        Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
    }
    $size = [math]::Round((Get-Item $apk).Length / 1MB, 2)
    Write-Host "==> APK: $apk ($size MB)" -ForegroundColor Green

    if ($Install -or $Run) {
        $adb = if ($env:ANDROID_HOME) { Join-Path $env:ANDROID_HOME "platform-tools\adb.exe" } else { "adb" }
        Write-Host "==> Installing..." -ForegroundColor Cyan
        & $adb install -r $apk
        if ($LASTEXITCODE -ne 0) {
            Write-Host "==> 安装失败：debug/release 签名不同，需先卸载旧版本（会丢数据，请先导出备份）" -ForegroundColor Yellow
            throw "adb install failed ($LASTEXITCODE)"
        }
        if ($Run) {
            & $adb shell am force-stop cc.nanio.pass
            & $adb shell am start -n cc.nanio.pass/.MainActivity
        }
    }
}
finally {
    Pop-Location
}
