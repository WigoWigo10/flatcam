param(
    [Parameter(Mandatory=$true)][string]$Project,
    [Parameter(Mandatory=$true)][string]$ObjectName,
    [Parameter(Mandatory=$true)][string]$PresentMon,
    [string]$GeometryObjectName = '',
    [ValidateRange(4,3600)][int]$Steps = 120,
    [ValidateRange(2,20)][int]$Repeats = 3,
    [switch]$IncludeSoftware,
    [switch]$Jfr
)
$ErrorActionPreference = 'Stop'
$Project = (Resolve-Path -LiteralPath $Project).Path
$PresentMon = (Resolve-Path -LiteralPath $PresentMon).Path
$suiteDirectory = Join-Path $PSScriptRoot ('target/plot-suite-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
New-Item -ItemType Directory -Path $suiteDirectory | Out-Null
$manifestPath = Join-Path $suiteDirectory 'suite.json'
$encoding = [Text.UTF8Encoding]::new($false)
$manifest = [ordered]@{Schema=1; Status='RUNNING'; Steps=$Steps; Repeats=$Repeats; Results=@();
    Limits=@('OMIT controls intentionally change the image; not delivered optimizations.',
        'FPS is ETW displayed-frame cadence, not UI pulse rate or optical input latency.',
        'Run serially, with the Plot visible; keep monitor/scale/power/driver unchanged.')}
function Save-Manifest { [IO.File]::WriteAllText($manifestPath, ($manifest | ConvertTo-Json -Depth 10), $encoding) }
Save-Manifest
$cases = @(
    @{Name='gpu-full'; Options=@{}},
    @{Name='gpu-no-pass-lines'; Options=@{CncProbe='OMIT_PASS_LINES'}},
    @{Name='gpu-no-raster-draw'; Options=@{RasterProbe='OMIT_DRAW'}},
    @{Name='gpu-no-lines-no-raster-draw'; Options=@{CncProbe='OMIT_PASS_LINES';RasterProbe='OMIT_DRAW'}},
    @{Name='gpu-pixelwriter'; Options=@{PixelWriter=$true}},
    @{Name='gpu-no-cnc-floor'; Options=@{CncProbe='OMIT_CNC'}},
    @{Name='gpu-full-recheck'; Options=@{}}
)
if ($IncludeSoftware) { $cases += @{Name='software-full'; Options=@{Software=$true}} }
if ($GeometryObjectName) { $cases += @{Name='gpu-geometry'; Options=@{ObjectName=$GeometryObjectName}} }
$reference = $null
foreach ($case in $cases) {
    $entry = [ordered]@{Name=$case.Name; Status='FAILED'; Report=$null; Error=$null}
    try {
        Write-Host "Suite: $($case.Name)"
        $options = @{Project=$Project;ObjectName=$ObjectName;PresentMon=$PresentMon;Native=$true;
            Steps=$Steps;Repeats=$Repeats;Jfr=$Jfr.IsPresent}
        foreach ($key in $case.Options.Keys) { $options[$key] = $case.Options[$key] }
        $paths = @(& (Join-Path $PSScriptRoot 'benchmark-plot.ps1') @options)
        $reportPath = [string]$paths[-1]
        if (-not (Test-Path -LiteralPath $reportPath -PathType Leaf)) { throw 'No completed benchmark report returned.' }
        $entry.Report = $reportPath
        $report = Get-Content -LiteralPath $reportPath -Raw -Encoding UTF8 | ConvertFrom-Json
        $fps = Get-Content -LiteralPath (Join-Path (Split-Path -Parent $reportPath) 'presentmon-analysis.json') -Raw -Encoding UTF8 | ConvertFrom-Json
        if ($report.status -ne 'COMPLETED' -or -not $report.sourceUnchanged -or $fps.status -ne 'MEASURED') { throw 'Incomplete or invalid benchmark/capture.' }
        if ($null -eq $reference) { $reference = $report }
        foreach ($key in @('sourceSha256','steps','repeats','theme','java','javafx','revision','workingTree')) {
            if ($report.$key -ne $reference.$key) { throw "Inconsistent comparison metadata: $key" }
        }
        foreach ($key in @('widthLogical','heightLogical','outputScaleX','outputScaleY')) {
            if ($report.viewport.$key -ne $reference.viewport.$key) { throw "Viewport changed: $key" }
        }
        if (-not $options.Software -and ($report.graphics.adapter -ne $reference.graphics.adapter -or $report.graphics.pipeline -ne $reference.graphics.pipeline)) {
            throw 'The active graphics adapter/pipeline changed; GPU controls are not comparable.'
        }
        $entry.Status = 'COMPLETED'
    } catch {
        $entry.Error = $_.Exception.Message
        $failedOutput = $_.Exception.Data['BenchmarkOutput']
        if ($failedOutput) { $entry.Report = Join-Path $failedOutput 'report.json' }
        Write-Warning "$($case.Name): $($entry.Error)"
    }
    $manifest.Results += $entry
    Save-Manifest
}
$failed = @($manifest.Results | Where-Object { $_.Status -ne 'COMPLETED' })
$manifest.Status = if ($failed.Count -eq 0) { 'COMPLETED' } else { 'INCOMPLETE' }
Save-Manifest
Write-Host "Suite: $manifestPath"
if ($failed.Count -gt 0) { throw "$($failed.Count) runs failed or lack presented FPS. See suite.json; never count as passed." }
Write-Output $manifestPath
