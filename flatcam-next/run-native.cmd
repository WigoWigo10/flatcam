@echo off
setlocal
pushd "%~dp0" || exit /b 1
call ".\build-native.cmd"
if errorlevel 1 (
    popd
    exit /b 1
)
"target\native\FlatCAMFX.exe" %*
set "FLATCAM_FX_EXIT=%errorlevel%"
popd
exit /b %FLATCAM_FX_EXIT%
