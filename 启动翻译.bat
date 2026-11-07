@echo off
cd /d "%~dp0"

rem Switch console to UTF-8 so Java's Chinese log shows correctly.
chcp 65001 >nul

if not exist out (
    echo [ERROR] Not compiled yet. Run build.bat first.
    pause
    exit /b 1
)

rem Pass through arguments, e.g. --verbose
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp out com.steamzh.Main %*

echo.
echo Program exited.
pause
