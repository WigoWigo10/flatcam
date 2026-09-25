@echo off
setlocal
pushd "%~dp0" || exit /b 1

rem Install the current reactor first: javafx:run with -pl flatcam-fx alone
rem otherwise resolves flatcam-cam/application from stale local SNAPSHOT jars.
call ".\mvnw.cmd" -q install -DskipTests
if errorlevel 1 (
    popd
    exit /b 1
)

call ".\mvnw.cmd" -q -pl flatcam-fx org.openjfx:javafx-maven-plugin:0.0.8:run
set "FLATCAM_FX_EXIT=%errorlevel%"
popd
exit /b %FLATCAM_FX_EXIT%
