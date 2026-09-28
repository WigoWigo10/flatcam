@echo off
setlocal
set "JAVA_TOOL_OPTIONS=%JAVA_TOOL_OPTIONS% -Dflatcam.ui.fluidity=true"
echo FlatCAM FX UI fluidity profiling enabled.
call "%~dp0run.cmd"
exit /b %errorlevel%
