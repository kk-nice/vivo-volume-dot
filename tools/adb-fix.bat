@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ==========================================
echo  音量小白点 - ADB 一键放行/修复
echo  1) 受限设置  2) 悬浮窗  3) 无障碍重绑
echo ==========================================
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0adb-fix.ps1" %*
echo.
pause
