@echo off
setlocal
cd /d "%~dp0"
rem Build full Whiteout 3.12.18 release for Minecraft 1.21.1.
rem Java 21 workaround for redirected Windows TEMP directories.
set "JAVA_TOOL_OPTIONS=%JAVA_TOOL_OPTIONS% -Djdk.net.unixdomain.tmpdir=%SystemRoot%/Temp"
call gradlew.bat --no-daemon --console=plain clean build
if errorlevel 1 exit /b %ERRORLEVEL%
if not exist "build\release\Whiteout-3.12.18-1.21.1.jar" (
  echo ERROR: full merged Whiteout 3.12.18 JAR not found.
  exit /b 1
)
echo Full release built: build\release\Whiteout-3.12.18-1.21.1.jar
exit /b 0
