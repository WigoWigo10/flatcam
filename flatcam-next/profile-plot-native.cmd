@echo off
setlocal
set "PLOT_SLOW_MS=%~1"
if not defined PLOT_SLOW_MS set "PLOT_SLOW_MS=50"
set "JAVA_TOOL_OPTIONS=%JAVA_TOOL_OPTIONS% -Dflatcam.plot.profile=true -Dflatcam.ui.fluidity=true -Dflatcam.plot.profile.slowMs=%PLOT_SLOW_MS%"
echo Native GPU launch and Plot Area profiling enabled ^(slow redraw threshold: %PLOT_SLOW_MS% ms^).
call "%~dp0run-native.cmd" --verbose-gpu
exit /b %errorlevel%
