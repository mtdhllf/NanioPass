# NanioPass 真机冒烟脚本（Windows PowerShell 7+）
#
# 用法：
#   .\tools\smoke.ps1 -Serial <adb 序列号>
#   .\tools\smoke.ps1 -Serial <序列号> -AdbPath 'E:\dev\sdk\platform-tools\adb.exe' -SkipUi
#
# 校验内容：
#   1. 设备在线
#   2. 应用 / 联调测试 App 已安装，版本号与 -ExpectedVersion 一致（默认 1.0.0）
#   3. 自动填充、凭据服务均指向 cc.nanio.pass
#   4. 应用可启动，首页四张入口卡片存在
#   5. 设置页存在「自动填充与凭据服务」行
#
# 退出码：0 全部通过；1 存在失败项。

param(
    [Parameter(Mandatory = $true)]
    [string]$Serial,

    [string]$AdbPath = 'E:\dev\sdk\platform-tools\adb.exe',

    [string]$AppPackage = 'cc.nanio.pass',

    [string]$TestPackage = 'cc.nanio.autofilltest',

    [string]$ExpectedVersion = '1.0.0',

    [switch]$SkipUi
)

$ErrorActionPreference = 'Stop'
$script:failures = New-Object System.Collections.Generic.List[string]
$script:checks = 0

function Assert-Any([string]$name, [string]$actual, [string[]]$candidates) {
    $script:checks++
    foreach ($candidate in $candidates) {
        if ($null -ne $actual -and $actual.Contains($candidate)) {
            Write-Host "  PASS  $name" -ForegroundColor Green
            return
        }
    }
    Write-Host "  FAIL  $name（期望包含：$($candidates -join ' / ')）" -ForegroundColor Red
    $script:failures.Add($name)
}

function Write-Step([string]$text) {
    Write-Host ""
    Write-Host "== $text" -ForegroundColor Cyan
}

function Assert-Contains([string]$name, [string]$actual, [string]$expected) {
    $script:checks++
    if ($null -ne $actual -and $actual.Contains($expected)) {
        Write-Host "  PASS  $name" -ForegroundColor Green
    } else {
        Write-Host "  FAIL  $name（期望包含 '$expected'，实际 '$actual'）" -ForegroundColor Red
        $script:failures.Add($name)
    }
}

function Invoke-Adb([string]$shellArgs) {
    return (& $AdbPath -s $Serial shell $shellArgs 2>&1 | Out-String).Trim()
}

# ---------------------------------------------------------------- 0. 设备
Write-Step '设备连接'
if (-not (Test-Path $AdbPath)) {
    Write-Host "  adb 不存在：$AdbPath" -ForegroundColor Red
    exit 1
}
$devices = & $AdbPath devices 2>&1 | Out-String
if ($devices -notmatch [regex]::Escape($Serial)) {
    Write-Host "  设备未连接：$Serial" -ForegroundColor Red
    Write-Host $devices
    exit 1
}
Write-Host "  PASS  设备在线：$Serial" -ForegroundColor Green
$script:checks++

# ---------------------------------------------------------------- 1. 安装与版本
Write-Step '安装与版本'
$packages = (& $AdbPath -s $Serial shell pm list packages 2>&1 | Out-String)
Assert-Contains "$AppPackage 已安装" $packages "package:$AppPackage"
Assert-Contains "$TestPackage 已安装" $packages "package:$TestPackage"

$versionDump = (& $AdbPath -s $Serial shell dumpsys package $AppPackage 2>&1 | Out-String)
$version = [regex]::Match($versionDump, 'versionName=([^\s]+)').Groups[1].Value
Assert-Contains "应用版本为 $ExpectedVersion" $version $ExpectedVersion
Write-Host "  （versionName=$version）"

# ---------------------------------------------------------------- 2. 系统服务指向
Write-Step '自动填充 / 凭据服务'
$autofill = Invoke-Adb 'settings get secure autofill_service'
Assert-Contains 'autofill_service 指向本应用' $autofill "$AppPackage/.autofill.NanioAutofillService"

$credential = Invoke-Adb 'settings get secure credential_service'
$credentialPrimary = Invoke-Adb 'settings get secure credential_service_primary'
Assert-Contains 'credential_service 指向本应用' $credential "$AppPackage/$AppPackage.credential.NanioCredentialProviderService"
Assert-Contains 'credential_service_primary 指向本应用' $credentialPrimary "$AppPackage/$AppPackage.credential.NanioCredentialProviderService"

# ---------------------------------------------------------------- 3. 界面
if (-not $SkipUi) {
    Write-Step '启动应用并检查首页'
    # 清掉可能残留的联调 App / 自动填充弹层，避免 uiautomator 抓到别的窗口
    & $AdbPath -s $Serial shell am force-stop $TestPackage | Out-Null
    & $AdbPath -s $Serial shell am force-stop $AppPackage | Out-Null
    & $AdbPath -s $Serial shell am start -W -n "$AppPackage/.MainActivity" | Out-Null
    Start-Sleep -Seconds 3

    $focus = (& $AdbPath -s $Serial shell dumpsys window 2>&1 | Out-String)
    # 若仍有系统自动填充弹层等遗留窗口，按返回一次再确认
    for ($i = 0; $i -lt 2; $i++) {
        if ($focus -match "$AppPackage/$AppPackage.MainActivity") { break }
        & $AdbPath -s $Serial shell input keyevent 4 | Out-Null
        Start-Sleep -Milliseconds 600
        $focus = (& $AdbPath -s $Serial shell dumpsys window 2>&1 | Out-String)
    }
    Assert-Contains '前台为本应用' $focus "$AppPackage/$AppPackage.MainActivity"

    & $AdbPath -s $Serial shell uiautomator dump /sdcard/smoke-home.xml | Out-Null
    $homeXml = (& $AdbPath -s $Serial shell cat /sdcard/smoke-home.xml 2>&1 | Out-String)
    foreach ($pair in @(
            @('全部密码', 'All passwords'),
            @('动态令牌', 'Authenticator'),
            @('安全性', 'Security'),
            @('最近删除', 'Recently deleted')
        )) {
        Assert-Any "首页包含「$($pair[0])」" $homeXml $pair
    }

    Write-Step '打开设置页'
    $m = [regex]::Match($homeXml, 'content-desc="(?:设置|Settings)"[^>]*bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"')
    if ($m.Success) {
        $cx = [int](([int]$m.Groups[1].Value + [int]$m.Groups[3].Value) / 2)
        $cy = [int](([int]$m.Groups[2].Value + [int]$m.Groups[4].Value) / 2)
        & $AdbPath -s $Serial shell input tap $cx $cy | Out-Null
        Start-Sleep -Seconds 2
        & $AdbPath -s $Serial shell uiautomator dump /sdcard/smoke-settings.xml | Out-Null
        $settings = (& $AdbPath -s $Serial shell cat /sdcard/smoke-settings.xml 2>&1 | Out-String)
        Assert-Any '设置页包含「自动填充与凭据服务」' $settings @('自动填充与凭据服务', 'Autofill and credential service')
        Assert-Any '设置页包含「导出加密备份」' $settings @('导出加密备份', 'Export encrypted backup')
        & $AdbPath -s $Serial shell input keyevent 4 | Out-Null
    } else {
        $script:checks++
        Write-Host '  FAIL  未能定位设置入口' -ForegroundColor Red
        $script:failures.Add('设置入口定位')
    }
}

# ---------------------------------------------------------------- 汇总
Write-Host ""
if ($script:failures.Count -eq 0) {
    Write-Host "SMOKE PASS（$script:checks 项检查全部通过）" -ForegroundColor Green
    exit 0
} else {
    Write-Host "SMOKE FAIL（$($script:failures.Count)/$script:checks 项失败）" -ForegroundColor Red
    $script:failures | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
    exit 1
}
