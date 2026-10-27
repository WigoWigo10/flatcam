param(
    [string]$Project = '',
    [string]$ObjectName = '',
    [ValidateSet('ICE_DARK', 'ICE_LIGHT', 'CLASSIC_DARK', 'CLASSIC_LIGHT')]
    [string]$Theme = 'ICE_DARK',
    [ValidateRange(4, 3600)][int]$Steps = 240,
    [ValidateRange(1, 20)][int]$Repeats = 3,
    [ValidateRange(1, 600)][int]$TimeoutSeconds = 60,
    [ValidateRange(400, 7680)][int]$Width = 1280,
    [ValidateRange(300, 4320)][int]$Height = 800,
    [switch]$Software,
    [switch]$PixelWriter,
    [switch]$Native,
    [switch]$Snapshots,
    [switch]$DisableWideDensity,
    [ValidateSet('FULL', 'OMIT_DRAW')][string]$RasterProbe = 'FULL',
    [string]$PresentMon = '',
    [string]$Python = '',
    [ValidateSet('FULL', 'OMIT_BODY', 'OMIT_WIDE_BODY', 'OMIT_PASS_LINES', 'OMIT_DECORATIONS', 'OMIT_CNC')]
    [string]$CncProbe = 'FULL',
    [switch]$Jfr
)

$ErrorActionPreference = 'Stop'
$benchmarkRoot = $PSScriptRoot
if ($Project) {
    $Project = (Resolve-Path -LiteralPath $Project).Path
    if ([IO.Path]::GetExtension($Project) -notin @('.FlatPrj', '.fcnproj')) {
        throw 'Use um projeto .FlatPrj ou .fcnproj.'
    }
}
if ($ObjectName -and -not $Project) { throw 'ObjectName requer Project.' }
if ($RasterProbe -ne 'FULL' -and (-not $Project -or -not $ObjectName)) { throw 'Raster omission requires an explicit project/object.' }
if ($PresentMon) {
    if (-not $Native) { throw 'Presented FPS requires -Native for process isolation and QPC calibration.' }
    $PresentMon = (Resolve-Path -LiteralPath $PresentMon).Path
    if (-not $Python) { $Python = Join-Path (Split-Path -Parent $benchmarkRoot) '.venv/Scripts/python.exe' }
    if (-not (Test-Path -LiteralPath $Python -PathType Leaf)) { $Python = (Get-Command python -ErrorAction Stop).Source }
}
if ($CncProbe -ne 'FULL' -and (-not $Project -or -not $ObjectName)) {
    throw 'Ablacao CNC requer Project e ObjectName explicitos; nao comparar tela vazia.'
}
$output = Join-Path $benchmarkRoot ('target/plot-benchmark-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $output | Out-Null
$revision = (& git -C $benchmarkRoot rev-parse HEAD).Trim()
if ($LASTEXITCODE -ne 0) { throw 'Nao foi possivel identificar o commit.' }
$dirty = [bool](& git -C $benchmarkRoot status --porcelain)
$state = if ($dirty) { 'dirty' } else { 'clean' }
# JVM arguments apply to the forked visible benchmark only. They do not select a dedicated GPU.
# Surefire loads JavaFX on the classpath; named-module exports are unnecessary here.
$jvmArgs = '-Dprism.verbose=true'
if ($Software) { $jvmArgs += ' -Dprism.order=sw' }
if ($PixelWriter) { $jvmArgs += ' -Dflatcam.plot.density.pixelBuffer=false' }
if ($DisableWideDensity) { $jvmArgs += ' -Dflatcam.plot.density.wide=false' }
if ($Jfr) {
    $recording = (Join-Path $output 'recording.jfr').Replace('\', '/')
    $jvmArgs += (' -XX:StartFlightRecording=settings=profile,dumponexit=true,filename="' + $recording + '"')
}
$arguments = @('-q', '-pl', 'flatcam-fx', '-am', 'test',
    '-Dtest=MainPlotRenderBenchmarkTest', '-Dsurefire.failIfNoSpecifiedTests=false',
    '-Dflatcam.plot.benchmark=true', "-Dflatcam.plot.benchmark.output=$output",
    "-Dflatcam.plot.benchmark.fixture=$Project", "-Dflatcam.plot.benchmark.object=$ObjectName",
    "-Dflatcam.plot.benchmark.theme=$Theme", "-Dflatcam.plot.benchmark.steps=$Steps",
    "-Dflatcam.plot.benchmark.repeats=$Repeats", "-Dflatcam.plot.benchmark.timeoutSeconds=$TimeoutSeconds",
    "-Dflatcam.plot.benchmark.width=$Width", "-Dflatcam.plot.benchmark.height=$Height",
    "-Dflatcam.plot.benchmark.revision=$revision", "-Dflatcam.plot.benchmark.workingTree=$state",
    "-Dflatcam.plot.benchmark.cncProbe=$CncProbe",
    "-Dflatcam.plot.benchmark.omitRasterDrawing=$($RasterProbe -eq 'OMIT_DRAW')",
    "-Dflatcam.plot.benchmark.snapshots=$($Snapshots.IsPresent.ToString().ToLowerInvariant())",
    "-Dflatcam.plot.benchmark.jfr=$($Jfr.IsPresent.ToString().ToLowerInvariant())",
    "-DargLine=$jvmArgs")
Write-Host 'Benchmark visivel: nao minimize nem interaja com a janela durante o ensaio.'
Write-Host 'Mede comandos/filas/preparo e cadencia UI; FPS apresentado requer -PresentMon.'
if ($CncProbe -ne 'FULL') { Write-Host "DIAGNOSTICO ${CncProbe}: omite desenho propositalmente; nao conta como paridade/otimizacao." }
if ($RasterProbe -ne 'FULL') { Write-Host 'DIAGNOSTICO raster: mantem calculo/publicacao, mas omite drawImage. Nao e paridade visual.' }
Write-Host "Saida: $output"
# stderr remains in the console; the separate Maven log avoids PowerShell 5 NativeCommandError.
Push-Location $benchmarkRoot
try {
    if ($Native) {
        # Compile the same test normally, but don't run it in java.exe on the integrated GPU.
        & .\mvnw.cmd -q -pl flatcam-fx -am test '-Dtest=MainPlotRenderBenchmarkTest' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dflatcam.plot.benchmark=false' -l (Join-Path $output 'maven.log')
        if ($LASTEXITCODE -ne 0) { throw 'Native benchmark test compilation failed.' }
        [xml]$testReport = Get-Content -LiteralPath 'flatcam-fx/target/surefire-reports/TEST-org.flatcam.fx.MainPlotRenderBenchmarkTest.xml'
        $classPath = ($testReport.testsuite.properties.property | Where-Object { $_.name -eq 'java.class.path' }).value
        if (-not $classPath) { throw 'No test classpath was recorded.' }
        $nativeHome = $env:JAVA_HOME
        if (-not $nativeHome -or -not (Test-Path -LiteralPath (Join-Path $nativeHome 'include/jni.h'))) { throw 'JAVA_HOME must point to the JDK.' }
        $compiler = 'C:/msys64/ucrt64/bin/g++.exe'
        if (-not (Test-Path -LiteralPath $compiler)) { $compiler = (Get-Command g++.exe -ErrorAction Stop).Source }
        $previousBenchmarkPath = $env:PATH
        try {
            $env:PATH = (Split-Path -Parent $compiler) + ';' + $previousBenchmarkPath
            & $compiler -std=c++17 -O2 -Wall -Wextra -Wno-cast-function-type -municode -static ('-I' + (Join-Path $nativeHome 'include')) ('-I' + (Join-Path $nativeHome 'include/win32')) tools/PlotBenchmarkHost.cpp -o (Join-Path $output 'PlotBenchmarkHost.exe')
            if ($LASTEXITCODE -ne 0) { throw 'Native benchmark host compilation failed.' }
        } finally { $env:PATH = $previousBenchmarkPath }
        $encoding = [Text.UTF8Encoding]::new($false)
        $classFile = Join-Path $output 'classpath.txt'
        $optionFile = Join-Path $output 'jvm-options.txt'
        [IO.File]::WriteAllText($classFile, $classPath, $encoding)
        $nativeOptions = @($arguments | Where-Object { $_.StartsWith('-D') -and -not $_.StartsWith('-DargLine=') })
        $nativeOptions += '-Dflatcam.plot.benchmark.launchMode=NATIVE_GPU_REQUEST', '-Dprism.verbose=true'
        $nativeOptions += if ($Software) { '-Dprism.order=sw' } else { '-Dprism.order=d3d,sw' }
        if ($PixelWriter) { $nativeOptions += '-Dflatcam.plot.density.pixelBuffer=false' }
        if ($DisableWideDensity) { $nativeOptions += '-Dflatcam.plot.density.wide=false' }
        if ($Jfr) { $nativeOptions += '-XX:StartFlightRecording=settings=profile,dumponexit=true,filename=' + (Join-Path $output 'recording.jfr') }
        if ($PresentMon) { $nativeOptions += '-Dflatcam.plot.benchmark.captureGate=' + (Join-Path $output 'capture-ready') }
        [IO.File]::WriteAllLines($optionFile, [string[]]$nativeOptions, $encoding)
        Write-Host 'Host nativo solicita GPU de alto desempenho; confirme o adaptador no report.json.'
        if ($PresentMon) {
            $nativeProcess = $null; $captureProcess = $null
            try {
                # The Plot window must actually be visible. No hidden-window startup override for this process.
                $nativeArguments = @($nativeHome, $classFile, $optionFile) | ForEach-Object { '"' + $_ + '"' }
                $nativeProcess = Start-Process -FilePath (Join-Path $output 'PlotBenchmarkHost.exe') -ArgumentList $nativeArguments -PassThru -NoNewWindow -RedirectStandardOutput (Join-Path $output 'native.stdout.log') -RedirectStandardError (Join-Path $output 'native.stderr.log')
                $nativeHandle = $nativeProcess.Handle
                $sessionName = 'FlatCAMFX-' + $nativeProcess.Id + '-' + [Guid]::NewGuid().ToString('N')
                # Process-exit attribution is unreliable without elevation, even for our PID.
                # Keep recording until we explicitly stop this unique session after JVM exit.
                $captureArguments = @('--process_id', $nativeProcess.Id, '--session_name', $sessionName, '--v2_metrics', '--qpc_time', '--no_track_gpu', '--no_track_input', '--no_console_stats', '--output_file', ('"' + (Join-Path $output 'presentmon.csv') + '"'))
                $captureProcess = Start-Process -FilePath $PresentMon -ArgumentList $captureArguments -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $output 'presentmon.stdout.log') -RedirectStandardError (Join-Path $output 'presentmon.stderr.log')
                $captureHandle = $captureProcess.Handle
                $deadline = [DateTime]::UtcNow.AddSeconds(15)
                $captureReady = $false
                do {
                    if ($captureProcess.HasExited) { throw 'PresentMon exited before capture; see presentmon.stderr.log.' }
                    # Redirected stdout can be buffered. Query our unique ETW session instead
                    # of treating a missing console message (or a sleep) as capture readiness.
                    $previousCaptureErrorAction = $ErrorActionPreference
                    try {
                        $ErrorActionPreference = 'Continue'
                        $sessionQuery = & "$env:SystemRoot/System32/logman.exe" query $sessionName -ets 2>&1
                        $captureReady = $LASTEXITCODE -eq 0
                    } finally { $ErrorActionPreference = $previousCaptureErrorAction }
                    if ($captureReady) {
                        [IO.File]::WriteAllText((Join-Path $output 'presentmon-session.txt'), ($sessionQuery -join [Environment]::NewLine), $encoding)
                        break
                    }
                    Start-Sleep -Milliseconds 100
                } while ([DateTime]::UtcNow -lt $deadline)
                if (-not $captureReady) { throw 'PresentMon ETW session readiness was not confirmed; benchmark not released.' }
                [IO.File]::WriteAllText((Join-Path $output 'capture-ready'), 'ready', $encoding)
                if (-not $nativeProcess.WaitForExit(600000)) { throw 'Native benchmark exceeded 10 minutes.' }
                $nativeProcess.Refresh(); $code = $nativeProcess.ExitCode
                if (-not $captureProcess.WaitForExit(2000)) {
                    # Ctrl+C lets PresentMon finish its consumers and flush. The helper
                    # refuses a shared console, so it cannot interrupt the user's shell.
                    $stopProcess = Start-Process -FilePath (Join-Path $output 'PlotBenchmarkHost.exe') -ArgumentList @('--stop-presentmon', $captureProcess.Id, ('"' + $PresentMon + '"')) -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $output 'presentmon-stop.stdout.log') -RedirectStandardError (Join-Path $output 'presentmon-stop.stderr.log')
                    $stopHandle = $stopProcess.Handle
                    try {
                        if (-not $stopProcess.WaitForExit(10000)) { throw 'Timed out stopping our PresentMon session.' }
                        $stopProcess.Refresh()
                        if ($stopProcess.ExitCode -ne 0) { throw 'Could not stop our PresentMon session cleanly.' }
                    } finally {
                        if (-not $stopProcess.HasExited) { $stopProcess.Kill(); $stopProcess.WaitForExit() }
                    }
                    if (-not $captureProcess.WaitForExit(15000)) { throw 'PresentMon did not flush after session stop; capture incomplete.' }
                }
                $captureProcess.Refresh()
                if ($captureProcess.ExitCode -ne 0) { throw 'PresentMon capture failed; see capture logs.' }
                [IO.File]::WriteAllText((Join-Path $output 'capture.json'), (@{Schema=1;ProcessId=$nativeProcess.Id;Session=$sessionName;Executable=$PresentMon;Sha256=(Get-FileHash -LiteralPath $PresentMon -Algorithm SHA256).Hash;CaptureExit=$captureProcess.ExitCode;DisplayTracking=$true;GpuWorkTracking=$false;InputTracking=$false;StopMode='ISOLATED_CONSOLE_CTRL_C'} | ConvertTo-Json), $encoding)
            } finally {
                # Only handles created by this invocation; never terminate another ETW session/process.
                if ($null -ne $nativeProcess -and -not $nativeProcess.HasExited) { $nativeProcess.Kill(); $nativeProcess.WaitForExit() }
                if ($null -ne $captureProcess -and -not $captureProcess.HasExited) {
                    $captureProcess.Kill(); $captureProcess.WaitForExit()
                    # Forced failure cleanup may leave ETW alive. This name is our GUID,
                    # never the default session or another profiler's session.
                    $cleanup = Start-Process -FilePath $PresentMon -ArgumentList @('--session_name', $sessionName, '--terminate_existing_session') -PassThru -WindowStyle Hidden -RedirectStandardOutput (Join-Path $output 'presentmon-cleanup.stdout.log') -RedirectStandardError (Join-Path $output 'presentmon-cleanup.stderr.log')
                    $cleanupHandle = $cleanup.Handle
                    if (-not $cleanup.WaitForExit(10000)) { $cleanup.Kill(); $cleanup.WaitForExit() }
                }
            }
        } else {
            $previousNativeErrorAction = $ErrorActionPreference
            try {
                $ErrorActionPreference = 'Continue'
                & (Join-Path $output 'PlotBenchmarkHost.exe') $nativeHome $classFile $optionFile *> (Join-Path $output 'native.log')
                $code = $LASTEXITCODE
            } finally { $ErrorActionPreference = $previousNativeErrorAction }
        }
    } else {
        & .\mvnw.cmd @arguments -l (Join-Path $output 'maven.log')
        $code = $LASTEXITCODE
    }
    if ($code -ne 0) { throw "Benchmark falhou (exit $code). Consulte maven.log/native.log em $output e report.json, se criado." }
    $reportPath = Join-Path $output 'report.json'
    if (-not (Test-Path -LiteralPath $reportPath)) { throw 'Benchmark nao gerou relatorio; nao contar como aprovado.' }
    $report = Get-Content -LiteralPath $reportPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($report.status -ne 'COMPLETED') { throw "Benchmark terminou com status $($report.status)." }
    if ($PresentMon) {
        & $Python tools/analyze_presentmon.py --report $reportPath --csv (Join-Path $output 'presentmon.csv') --capture-log (Join-Path $output 'presentmon.stderr.log') --output (Join-Path $output 'presentmon-analysis.json')
        if ($LASTEXITCODE -ne 0) { throw 'Presented FPS unavailable/incomplete; never substitute UI pulses. See presentmon-analysis.json.' }
        $captureErrors = Get-Content -LiteralPath (Join-Path $output 'presentmon.stderr.log') -Raw -ErrorAction SilentlyContinue
        if ($captureErrors -match 'ETW (events|buffers) were lost|overflowed present events detected') { throw 'PresentMon reported lost ETW events; FPS capture rejected. Raw evidence retained.' }
    }
    Write-Host "Concluido: $reportPath"
    Write-Output $reportPath
} catch {
    $_.Exception.Data['BenchmarkOutput'] = $output
    throw
} finally { Pop-Location }
