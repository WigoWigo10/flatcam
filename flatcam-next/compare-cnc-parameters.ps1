param(
    [string]$Python,
    [string]$DependencyPath,
    [string]$OutputDirectory,
    [ValidateSet('GRBL_11_NO_M6','GRBL_11','DEFAULT','DEFAULT_NO_M6','MARLIN','LINE_XYZ','ISEL_CNC')]
    [string]$Profile = 'GRBL_11_NO_M6',
    [switch]$Strict
)

# Public synthetic fixtures only; no UI, dependency installation or preference writes.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$comparisonRoot = $PSScriptRoot
$legacyRoot = Split-Path -Parent $comparisonRoot

function Invoke-CncNative([string]$Executable, [string[]]$Arguments, [string]$Log) {
    $ErrorActionPreference = 'Continue'
    $lines = @(& $Executable @Arguments 2>&1 | ForEach-Object { $_.ToString() })
    $code = $LASTEXITCODE
    $lines | Out-File -LiteralPath $Log -Encoding utf8
    if ($lines.Count -gt 0) { Write-Host $lines[-1] }
    return $code
}

try {
    if (-not $Python) { $Python = Join-Path $comparisonRoot 'target/oracle-py311/Scripts/python.exe' }
    if (-not (Test-Path -LiteralPath $Python -PathType Leaf)) {
        throw 'Informe -Python ou prepare target/oracle-py311 conforme COMPARACAO_CAM.md. Nao sao instaladas dependencias.'
    }
    $Python = (Resolve-Path -LiteralPath $Python).Path
    if ($DependencyPath) { $DependencyPath = (Resolve-Path -LiteralPath $DependencyPath).Path }
    if (-not $OutputDirectory) {
        $OutputDirectory = Join-Path $comparisonRoot ('target/cnc-parameters-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
    }
    $OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
    $targetRoot = [IO.Path]::GetFullPath((Join-Path $comparisonRoot 'target')) + [IO.Path]::DirectorySeparatorChar
    if (-not $OutputDirectory.StartsWith($targetRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Relatorios devem ficar dentro de flatcam-next/target.'
    }
    if (Test-Path -LiteralPath $OutputDirectory) { throw 'Escolha uma pasta nova; resultados existentes nao serao sobrescritos.' }
    New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
    $validationPath = if ($DependencyPath) { $DependencyPath } else { $comparisonRoot }
    $validationCode = "import sys; sys.path.insert(0,sys.argv[1]); import shapely,ortools; from shapely.geos import geos_version_string; print(sys.version.split()[0],shapely.__version__,geos_version_string,ortools.__version__); sys.exit(0 if sys.version_info[:2]==(3,11) and shapely.__version__=='1.8.5.post1' and geos_version_string.startswith('3.10.3') else 2)"
    $code = Invoke-CncNative $Python @('-c',$validationCode,$validationPath) (Join-Path $OutputDirectory 'oracle-environment.log')
    if ($code -ne 0) { throw 'Esperado Python 3.11 / Shapely 1.8.5.post1 / GEOS 3.10.3 e OR-Tools disponivel.' }
    Push-Location $comparisonRoot
    try {
        Write-Host 'Exportando casos publicos CNC XYZ em MM e IN...'
        $buildArgs = @('-q','-pl','flatcam-application','-am','-Dtest=PythonCncParametersExportTest',
            ('-Dflatcam.cnc.comparison.profile=' + $Profile),
            '-Dsurefire.failIfNoSpecifiedTests=false',('-Dflatcam.cnc.comparison.output=' + $OutputDirectory),
            'test','-l',(Join-Path $OutputDirectory 'export.log'))
        $code = Invoke-CncNative (Join-Path $comparisonRoot 'mvnw.cmd') $buildArgs (Join-Path $OutputDirectory 'maven-launcher.log')
        if ($code -ne 0) { throw "Exportacao FX falhou ($code). Consulte export.log." }
        $oracleArgs = @((Join-Path $comparisonRoot 'tools/compare_cnc_python.py'),'--legacy-root',$legacyRoot,
            '--fx-export',(Join-Path $OutputDirectory 'fx-cnc.json'),'--output',(Join-Path $OutputDirectory 'python'))
        if ($DependencyPath) { $oracleArgs += @('--dependency-path',$DependencyPath) }
        if ($Strict) { $oracleArgs += '--strict' }
        Write-Host 'Comparando movimentos XYZ, feed, spindle, pausas e ferramentas com o Python original...'
        $code = Invoke-CncNative $Python $oracleArgs (Join-Path $OutputDirectory 'comparison.log')
        if ($code -notin @(0,1) -or -not (Test-Path -LiteralPath (Join-Path $OutputDirectory 'python/report.json'))) {
            throw "Comparacao falhou ($code). Consulte comparison.log."
        }
        Write-Host "Relatorio: $(Join-Path $OutputDirectory 'python/index.html')"
        if ($code -eq 1) { Write-Host 'Strict reprovado: concordancia de usinagem isolada nao significa execucao completa equivalente.' }
        exit $code
    } finally { Pop-Location }
} catch {
    Write-Error $_ -ErrorAction Continue
    exit 2
}
