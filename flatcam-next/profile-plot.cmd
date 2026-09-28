@echo off
setlocal
set "JAVA_TOOL_OPTIONS=-Dflatcam.plot.profile=true %JAVA_TOOL_OPTIONS%"
echo Plot Area profiling enabled. Slow redraws will appear as [PLOT-PROFILE] in this terminal.
call "%~dp0run.cmd"
exit /b %errorlevel%
