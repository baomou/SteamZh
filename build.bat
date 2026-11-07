@echo off
cd /d "%~dp0"

if not exist out mkdir out

echo [1/2] Compiling .java files...
javac -encoding UTF-8 -d out src\com\steamzh\*.java
if errorlevel 1 (
    echo BUILD FAILED - check the errors above.
    exit /b 1
)

echo [2/2] Build OK. Run run.bat to start.
