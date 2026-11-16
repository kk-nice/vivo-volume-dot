# 音量小白点 —— ADB 一键修复/放行脚本
#
# 作用（针对 Android 15 / OriginOS 的侧载限制）：
#   1. 放行「受限设置」         —— 不再反复弹受限提示框
#   2. 放行「显示在其他应用上层」—— 悬浮窗权限被系统打成 ignore 时修复
#   3. 重新绑定无障碍服务        —— 已启用但未绑定时修复（保留其它已启用的服务）
#
# 用法：
#   powershell -ExecutionPolicy Bypass -File tools\adb-fix.ps1
#   powershell -ExecutionPolicy Bypass -File tools\adb-fix.ps1 -Serial 10AG1P0GTN008BG
#
# 说明：本机（vivo V2507A / Android 16）上 `pm set-app-allowed-restricted-settings`
#       这条命令被移除了，实际可用的是 appop `ACCESS_RESTRICTED_SETTINGS`。

param(
    [string]$Serial = "",
    [string]$Package = "org.xx.volumedot"
)

$ErrorActionPreference = "Stop"

function Run-Adb([string]$Command) {
    if ($Serial) {
        return (& adb.exe -s $Serial shell $Command 2>&1 | Out-String).Trim()
    }
    return (& adb.exe shell $Command 2>&1 | Out-String).Trim()
}

Write-Host "== 设备 ==" -ForegroundColor Cyan
& adb.exe devices

# 1) 受限设置放行
Write-Host "`n== 1/3 放行受限设置 ==" -ForegroundColor Cyan
Run-Adb "appops set $Package ACCESS_RESTRICTED_SETTINGS allow" | Out-Null
$r1 = Run-Adb "cmd appops get $Package" | Select-String "ACCESS_RESTRICTED_SETTINGS"
Write-Host $r1

# 2) 悬浮窗放行
Write-Host "`n== 2/3 放行悬浮窗 ==" -ForegroundColor Cyan
Run-Adb "appops set $Package SYSTEM_ALERT_WINDOW allow" | Out-Null
$r2 = Run-Adb "appops get $Package SYSTEM_ALERT_WINDOW"
Write-Host $r2

# 3) 无障碍服务重新绑定（保留列表里其它服务）
Write-Host "`n== 3/3 重新绑定无障碍服务 ==" -ForegroundColor Cyan
$svc = "$Package/$Package.TapService"
$cur = Run-Adb "settings get secure enabled_accessibility_services"
if ($cur -eq "null") { $cur = "" }

$list = @()
if ($cur) { $list = $cur -split ":" | Where-Object { $_ -and ($_ -ne "null") } }
if ($list -notcontains $svc) { $list += $svc }

Run-Adb "settings put secure enabled_accessibility_services \"\"" | Out-Null
Start-Sleep -Seconds 2
Run-Adb ("settings put secure enabled_accessibility_services " + ($list -join ":")) | Out-Null
Run-Adb "settings put secure accessibility_enabled 1" | Out-Null
Start-Sleep -Seconds 3

Write-Host "`n== 结果 ==" -ForegroundColor Cyan
Write-Host ("已启用服务: " + (Run-Adb "settings get secure enabled_accessibility_services"))
$bound = Run-Adb "dumpsys accessibility" | Select-String "音量小白点|capabilities=160"
Write-Host $bound
Write-Host "`n完成。若 App 内仍显示「小白点：已隐藏」，在 App 里点一下「显示小白点」即可。" -ForegroundColor Green
