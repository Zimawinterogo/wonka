@echo off
setlocal
cd /d "%~dp0"
rem Java 21 + Python 3 required. Compile and tests only; never creates a release JAR.
set "JAVA_TOOL_OPTIONS=%JAVA_TOOL_OPTIONS% -Djdk.net.unixdomain.tmpdir=%SystemRoot%/Temp"
call gradlew.bat --no-daemon --console=plain -I qa/visual-3.12.9.gradle visualRegression
if errorlevel 1 exit /b %ERRORLEVEL%
python tests/connected_barricades_test.py
if errorlevel 1 exit /b %ERRORLEVEL%
python tools/audit_resources.py
exit /b %ERRORLEVEL%
