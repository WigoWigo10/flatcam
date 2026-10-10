param(
    [Parameter(Mandatory=$true)][string]$Project,
    [string]$Python,
    [string]$OutputDirectory,
    [switch]$Strict
)

# Read-only private source; all generated project/code/reports stay in ignored target.
# No dependency installation, preferences, GUI Stage or CNC machine operations.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$comparisonRoot = $PSScriptRoot
$legacyRoot = Split-Path -Parent $comparisonRoot
function Invoke-PanelNative([string]$Executable, [string[]]$Arguments, [string]$Log) {
    $ErrorActionPreference = 'Continue'
    $nativeOutput = @(& $Executable @Arguments 2>&1 | ForEach-Object { $_.ToString() })
    $nativeCode = $LASTEXITCODE
    $nativeOutput | Out-File -LiteralPath $Log -Encoding utf8
    if ($nativeOutput.Count -gt 0) { Write-Host $nativeOutput[-1] }
    return $nativeCode
}
try {
    $Project = (Resolve-Path -LiteralPath $Project).Path
    if (-not $Python) { $Python = Join-Path $comparisonRoot 'target/oracle-py311/Scripts/python.exe' }
    if (-not (Test-Path -LiteralPath $Python -PathType Leaf)) {
        throw 'Informe -Python ou prepare target/oracle-py311 conforme COMPARACAO_CAM.md. Nada sera instalado.'
    }
    $Python = (Resolve-Path -LiteralPath $Python).Path
    if (-not $OutputDirectory) {
        $OutputDirectory = Join-Path $comparisonRoot ('target/panelized-flow-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
    }
    $OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
    $targetRoot = [IO.Path]::GetFullPath((Join-Path $comparisonRoot 'target')) + [IO.Path]::DirectorySeparatorChar
    if (-not $OutputDirectory.StartsWith($targetRoot,[StringComparison]::OrdinalIgnoreCase)) {
        throw 'Relatorios privados devem ficar dentro de flatcam-next/target.'
    }
    if (Test-Path -LiteralPath $OutputDirectory) { throw 'Escolha uma pasta nova; resultados anteriores nao serao sobrescritos.' }
    New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
    $before = (Get-FileHash -LiteralPath $Project -Algorithm SHA256).Hash
    $validation = "import sys,shapely; from shapely.geos import geos_version_string; print(sys.version.split()[0],shapely.__version__,geos_version_string); sys.exit(0 if sys.version_info[:2]==(3,11) and shapely.__version__=='1.8.5.post1' and geos_version_string.startswith('3.10.3') else 2)"
    if ((Invoke-PanelNative $Python @('-c',$validation) (Join-Path $OutputDirectory 'oracle-environment.log')) -ne 0) {
        throw 'Referencia esperada: Python 3.11 / Shapely 1.8.5.post1 / GEOS 3.10.3.'
    }
    Push-Location $comparisonRoot
    try {
        $exportDirectory = Join-Path $OutputDirectory 'fx'
        Write-Host 'Validando panelizacao 2 x 2 / espacamento 5 mm, CAM, CNC e reabertura nativa...'
        $buildArgs = @('-q','-pl','flatcam-fx','-am','-Dtest=MainPanelizedCamFlowTest',
            '-Dsurefire.failIfNoSpecifiedTests=false',('-Dflatcam.python.project.fixture=' + $Project),
            ('-Dflatcam.panelized.output=' + $exportDirectory),'test','-l',(Join-Path $OutputDirectory 'export.log'))
        if ((Invoke-PanelNative (Join-Path $comparisonRoot 'mvnw.cmd') $buildArgs (Join-Path $OutputDirectory 'maven-launcher.log')) -ne 0) {
            throw 'Fluxo FX reprovado. Consulte export.log e fx/flow-report.json; resultados parciais nao aprovam o fluxo.'
        }
        $comparisonCode = 0
        foreach ($entry in @(@{File='fx-f-cu.json';Name='f-cu';Independent=$true},
                @{File='fx-b-cu.json';Name='b-cu';Independent=$true},
                @{File='fx-cutout.json';Name='cutout';Independent=$false})) {
            Write-Host "Comparando $($entry.Name) com os handlers originais Python..."
            $reportDirectory = Join-Path $OutputDirectory $entry.Name
            $oracleArgs = @((Join-Path $comparisonRoot 'tools/compare_cam_python.py'),'--legacy-root',$legacyRoot,
                '--fx-export',(Join-Path $exportDirectory $entry.File),'--output',$reportDirectory)
            if ($entry.Independent) { $oracleArgs += @('--project',$Project) }
            if ($Strict) { $oracleArgs += '--strict' }
            $code = Invoke-PanelNative $Python $oracleArgs (Join-Path $OutputDirectory ($entry.Name + '.log'))
            if ($code -notin @(0,1) -or -not (Test-Path -LiteralPath (Join-Path $reportDirectory 'report.json'))) {
                throw "Oraculo $($entry.Name) falhou. Consulte seu log."
            }
            if ($code -ne 0) { $comparisonCode = 1 }
            Write-Host "Relatorio: $(Join-Path $reportDirectory 'index.html')"
        }
        Write-Host 'Cutout compara area preenchida compartilhada: nao valida reconstrucao Python do Edge_Cuts nem recortes internos.'
        if ($comparisonCode) { Write-Host 'Strict reprovado; divergencias nao contam como paridade.' }
        exit $comparisonCode
    } finally {
        Pop-Location
        if ((Get-FileHash -LiteralPath $Project -Algorithm SHA256).Hash -ne $before) { throw 'O projeto original mudou durante o teste.' }
    }
} catch {
    Write-Error $_ -ErrorAction Continue
    exit 2
}
