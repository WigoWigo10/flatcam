@echo off
setlocal
pushd "%~dp0" || exit /b 1

rem Prefer the app-specific executable when a native compiler is available.
rem FLATCAM_FX_JAVA_ONLY=1 keeps the original shared-java.exe launcher.
if /I "%FLATCAM_FX_JAVA_ONLY%"=="1" goto java_launcher
if exist "C:\msys64\ucrt64\bin\g++.exe" goto native_launcher
where g++.exe >nul 2>&1
if not errorlevel 1 goto native_launcher
goto java_launcher

:native_launcher
call ".\build-native.cmd"
if errorlevel 1 (
    echo Native launcher build failed; using the Java launcher instead.
    goto java_launcher
)
"target\native\FlatCAMFX.exe" %*
set "FLATCAM_FX_EXIT=%errorlevel%"
popd
exit /b %FLATCAM_FX_EXIT%

:java_launcher
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
