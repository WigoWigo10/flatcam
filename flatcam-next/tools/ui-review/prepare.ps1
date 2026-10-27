param(
    [Parameter(Mandatory=$true)][string]$Project,
    [Parameter(Mandatory=$true)][string]$Python,
    [string]$Destination = 'target/ui-review'
)
$ErrorActionPreference = 'Stop'
$reactor = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$legacy = (Resolve-Path (Join-Path $reactor '..')).Path
$source = (Resolve-Path -LiteralPath $Project).Path
$pythonExe = (Resolve-Path -LiteralPath $Python).Path
$review = [IO.Path]::GetFullPath((Join-Path $reactor $Destination))
if (-not $review.StartsWith($reactor + [IO.Path]::DirectorySeparatorChar)) { throw 'Review destination must be inside the reactor' }
New-Item -ItemType Directory -Force -Path $review | Out-Null
$classes = Join-Path $review 'classes'
New-Item -ItemType Directory -Force -Path $classes | Out-Null
& javac -encoding UTF-8 -d $classes (Join-Path $PSScriptRoot 'ReviewPreferencesFactory.java')
if ($LASTEXITCODE -ne 0) { throw 'Review factory compilation failed' }
$compiler = Join-Path $env:WINDIR 'Microsoft.NET/Framework64/v4.0.30319/csc.exe'
$fxLauncher = Join-Path $review 'FxReview.exe'
& $compiler /nologo /target:winexe "/out:$fxLauncher" (Join-Path $PSScriptRoot 'ReviewAppLauncher.cs')
if ($LASTEXITCODE -ne 0) { throw 'Review GUI launcher compilation failed' }
Copy-Item -LiteralPath $fxLauncher -Destination (Join-Path $review 'PythonReview.exe') -Force
$utf8 = New-Object System.Text.UTF8Encoding($false)
$runtime = Join-Path $reactor 'flatcam-fx/target/dependency'
if (-not (Test-Path -LiteralPath $runtime)) { throw 'Run build-native.cmd to synchronize runtime dependencies first' }
$java = Join-Path $env:JAVA_HOME 'bin/javaw.exe'
if (-not (Test-Path -LiteralPath $java)) { throw 'JAVA_HOME must point to a JDK with javaw.exe' }
$classPath = @($classes, (Join-Path $reactor 'flatcam-fx/target/classes'), (Join-Path $reactor 'flatcam-application/target/classes'), (Join-Path $reactor 'flatcam-cam/target/classes'), (Join-Path $runtime '*')) -join ';'
$fxArgs = @($java,$reactor,'--module-path',$runtime,'--add-modules','javafx.controls',
    '--enable-native-access=javafx.graphics','--add-exports=javafx.graphics/com.sun.prism=ALL-UNNAMED',
    '--add-exports=javafx.graphics/com.sun.javafx.tk=ALL-UNNAMED',
    '--add-exports=javafx.graphics/com.sun.javafx.stage=ALL-UNNAMED',
    '--add-exports=javafx.graphics/com.sun.javafx.tk.quantum=ALL-UNNAMED',
    '--add-exports=javafx.graphics/com.sun.glass.ui=ALL-UNNAMED',
    '--add-opens=javafx.graphics/com.sun.prism.d3d=ALL-UNNAMED',
    '-XX:+UnlockDiagnosticVMOptions','-XX:DisableIntrinsic=_indexOfUL',
    '-Djava.util.prefs.PreferencesFactory=org.flatcam.review.ReviewPreferencesFactory',
    '-cp',$classPath,'org.flatcam.fx.FlatCamLauncher','--no-diagnostics')
[IO.File]::WriteAllLines((Join-Path $review 'FxReview.launch'),$fxArgs,$utf8)
$pyArgs = @($pythonExe,$legacy,(Join-Path $PSScriptRoot 'python_window.py'),'--legacy-root',$legacy,'--settings',(Join-Path $review 'python-settings'))
[IO.File]::WriteAllLines((Join-Path $review 'PythonReview.launch'),$pyArgs,$utf8)
Copy-Item -LiteralPath $source -Destination (Join-Path $review 'project-fx.FlatPrj') -Force
Copy-Item -LiteralPath $source -Destination (Join-Path $review 'project-python.FlatPrj') -Force
@{source=$source;sourceSha256=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash;prepared=(Get-Date).ToString('o')} |
    ConvertTo-Json | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $review 'manifest.json')
[IO.File]::WriteAllText((Join-Path $review 'short-lz.drl'),"M48`nINCH,LZ,00.0000`nT1C0.01`n%`nT1`nX0012Y0034`nM30`n",$utf8)
Write-Output "Prepared isolated review launchers and project copies in $review"
Write-Output 'Launch FxReview.exe/PythonReview.exe; open only the copies through the application UI.'
