@echo off
setlocal
pushd "%~dp0" || exit /b 1
set "FLATCAM_UI_FLUIDITY=1"
set "PROFILE_PYTHON=%FLATCAM_PROFILE_PYTHON%"
if not defined PROFILE_PYTHON if exist "%~dp0.venv312\Scripts\python.exe" set "PROFILE_PYTHON=%~dp0.venv312\Scripts\python.exe"
if not defined PROFILE_PYTHON if exist "%~dp0..\flatcam-8994\.venv312\Scripts\python.exe" set "PROFILE_PYTHON=%~dp0..\flatcam-8994\.venv312\Scripts\python.exe"
if not defined PROFILE_PYTHON set "PROFILE_PYTHON=python"
echo FlatCAM Python UI fluidity profiling enabled.
echo Using Python: %PROFILE_PYTHON%
"%PROFILE_PYTHON%" -c "import simplejson, vispy, PyQt5"
if errorlevel 1 (
    echo The selected Python environment is missing FlatCAM dependencies.
    echo Set FLATCAM_PROFILE_PYTHON to the full path of the working python.exe.
    popd
    exit /b 1
)
"%PROFILE_PYTHON%" FlatCAM.py
set "FLATCAM_PYTHON_EXIT=%errorlevel%"
popd
exit /b %FLATCAM_PYTHON_EXIT%
