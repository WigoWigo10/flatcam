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
    "-DargLine=$jvmArgs")
Write-Host 'Benchmark visivel: nao minimize nem interaja com a janela durante o ensaio.'
Write-Host 'Mede comandos/filas/preparo e intervalos UI, nao FPS apresentado pela GPU.'
Write-Host "Saida: $output"
# stderr remains in the console; the separate Maven log avoids PowerShell 5 NativeCommandError.
Push-Location $benchmarkRoot
try {
    & .\mvnw.cmd @arguments -l (Join-Path $output 'maven.log')
    $code = $LASTEXITCODE
    if ($code -ne 0) { throw "Benchmark falhou (exit $code). Consulte $output\maven.log e report.json, se criado." }
    $reportPath = Join-Path $output 'report.json'
    if (-not (Test-Path -LiteralPath $reportPath)) { throw 'Benchmark nao gerou relatorio; nao contar como aprovado.' }
    $report = Get-Content -LiteralPath $reportPath -Raw -Encoding UTF8 | ConvertFrom-Json
    if ($report.status -ne 'COMPLETED') { throw "Benchmark terminou com status $($report.status)." }
    Write-Host "Concluido: $reportPath"
} finally { Pop-Location }
