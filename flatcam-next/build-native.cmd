@echo off
setlocal
pushd "%~dp0" || exit /b 1

rem Keep all three modules and the copied runtime jars in sync with the source.
call ".\mvnw.cmd" -q install -DskipTests
if errorlevel 1 goto failure
call ".\mvnw.cmd" -q -pl flatcam-fx org.apache.maven.plugins:maven-dependency-plugin:3.8.1:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/dependency
if errorlevel 1 goto failure

set "FLATCAM_CXX=C:\msys64\ucrt64\bin\g++.exe"
if exist "%FLATCAM_CXX%" (
    rem cc1plus.exe needs the UCRT64 DLLs while compiling.
    set "PATH=C:\msys64\ucrt64\bin;%PATH%"
) else (
    where g++.exe >nul 2>&1
    if errorlevel 1 (
        echo MSYS2 UCRT64 g++ was not found. Install it or add g++.exe to PATH.
        goto failure
    )
    set "FLATCAM_CXX=g++.exe"
)
if not exist "target\native" mkdir "target\native"
set "FLATCAM_JNI_INCLUDE=%JAVA_HOME%\include"
if not exist "%FLATCAM_JNI_INCLUDE%\jni.h" (
    echo JAVA_HOME must point to a JDK with include\jni.h.
    goto failure
)
"%FLATCAM_CXX%" -std=c++17 -O2 -Wall -Wextra -municode -static -I"%FLATCAM_JNI_INCLUDE%" -I"%FLATCAM_JNI_INCLUDE%\win32" "native-launcher\FlatCAMFX.cpp" -o "target\native\FlatCAMFX.exe"
if errorlevel 1 goto failure
"target\native\FlatCAMFX.exe" --probe
if errorlevel 1 goto failure
echo Built target\native\FlatCAMFX.exe
popd
exit /b 0

:failure
popd
exit /b 1
