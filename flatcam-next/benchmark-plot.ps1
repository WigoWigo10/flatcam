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
    [switch]$DisableWideDensity,
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
    "-Dflatcam.plot.benchmark.jfr=$($Jfr.IsPresent.ToString().ToLowerInvariant())",
    "-DargLine=$jvmArgs")
Write-Host 'Benchmark visivel: nao minimize nem interaja com a janela durante o ensaio.'
Write-Host 'Mede comandos/filas/preparo e intervalos UI, nao FPS apresentado pela GPU.'
if ($CncProbe -ne 'FULL') { Write-Host "DIAGNOSTICO ${CncProbe}: omite desenho propositalmente; nao conta como paridade/otimizacao." }
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
        [IO.File]::WriteAllLines($optionFile, [string[]]$nativeOptions, $encoding)
        Write-Host 'Host nativo solicita GPU de alto desempenho; confirme o adaptador no report.json.'
        $previousNativeErrorAction = $ErrorActionPreference
        try {
            # PowerShell 5 must not treat ordinary JVM stderr logging as a failed native program.
            $ErrorActionPreference = 'Continue'
            & (Join-Path $output 'PlotBenchmarkHost.exe') $nativeHome $classFile $optionFile *> (Join-Path $output 'native.log')
            $code = $LASTEXITCODE
        } finally { $ErrorActionPreference = $previousNativeErrorAction }
    } else {
        & .\mvnw.cmd @arguments -l (Join-Path $output 'maven.log')
        $code = $LASTEXITCODE
    }
    if ($code -ne 0) { throw "Benchmark falhou (exit $code). Consulte maven.log/native.log em $output e report.json, se criado." }
    $reportPath = Join-Path $output 'report.json'
    if (-not (Test-Path -LiteralPath $reportPath)) { throw 'Benchmark nao gerou relatorio; nao contar como aprovado.' }
    $report = Get-Content -LiteralPath $reportPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($report.status -ne 'COMPLETED') { throw "Benchmark terminou com status $($report.status)." }
    Write-Host "Concluido: $reportPath"
} finally { Pop-Location }
