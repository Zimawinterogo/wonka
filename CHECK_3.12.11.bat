@echo off
setlocal
cd /d "%~dp0"
rem Java 21 and Python 3; compile/tests only, never builds a release JAR.
call gradlew.bat --no-daemon --console=plain -I qa/foundation.gradle check foundationTests
if errorlevel 1 exit /b %ERRORLEVEL%
python tests/connected_barricades_test.py
if errorlevel 1 exit /b %ERRORLEVEL%
python tests/connected_collision_assets_test.py
if errorlevel 1 exit /b %ERRORLEVEL%
python -X utf8 tools/audit_resources.py --output build/resources-3.12.11.json
exit /b %ERRORLEVEL%
