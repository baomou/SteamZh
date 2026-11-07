@echo off
cd /d "%~dp0"

tasklist /FI "IMAGENAME eq steam.exe" 2>nul | find /I "steam.exe" >nul
if not errorlevel 1 (
    echo Steam is still running.
    echo Right-click the Steam tray icon and choose Exit, then run this again.
    pause
    exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -Command ^
  "$p = (Get-ItemProperty 'HKCU:\Software\Valve\Steam').SteamPath -replace '/','\';" ^
  "Write-Host ('Starting Steam from: ' + $p);" ^
  "Start-Process -FilePath (Join-Path $p 'steam.exe') -ArgumentList '-cef-enable-debugging'"

echo Steam is starting with debugging enabled. Wait a few seconds, then run the translator.
pause
