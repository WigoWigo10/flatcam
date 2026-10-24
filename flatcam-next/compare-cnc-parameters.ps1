param(
    [string]$Python,
    [string]$DependencyPath,
    [string]$OutputDirectory,
    [ValidateSet('GRBL_11_NO_M6','GRBL_11','DEFAULT','DEFAULT_NO_M6','MARLIN','LINE_XYZ','ISEL_CNC','ISEL_ICP_CNC','TOOLCHANGE_MANUAL','TOOLCHANGE_CUSTOM','REPETIER','BERTA_CNC','GRBL_LASER','Z_LASER','MARLIN_LASER_FAN_PIN','MARLIN_LASER_SPINDLE_PIN','ROLAND_MDX_20','HPGL','TOOLCHANGE_PROBE_MACH3')]
    [string]$Profile = 'GRBL_11_NO_M6',
    [ValidateSet('shared','variable','fixed')]
    [string]$MarlinRapidMode = 'shared',
    [double]$MarlinFixedRapidMm = 0,
    [ValidateSet('remote','optional-enabled','optional-disabled')]
    [string]$IselM01Mode = 'remote',
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
    if ($Profile -ne 'ISEL_CNC' -and $IselM01Mode -ne 'remote') {
        throw 'IselM01Mode exige -Profile ISEL_CNC.'
    }
    if ($Profile -notin @('MARLIN','MARLIN_LASER_FAN_PIN','MARLIN_LASER_SPINDLE_PIN') -and ($MarlinRapidMode -ne 'shared' -or $MarlinFixedRapidMm -ne 0)) {
        throw 'As opcoes MarlinRapidMode/MarlinFixedRapidMm exigem um perfil MARLIN.'
    }
    if (($MarlinRapidMode -eq 'fixed' -and ($MarlinFixedRapidMm -le 0 -or [double]::IsNaN($MarlinFixedRapidMm) -or [double]::IsInfinity($MarlinFixedRapidMm))) -or
        ($MarlinRapidMode -ne 'fixed' -and $MarlinFixedRapidMm -ne 0)) {
        throw 'Modo fixed exige -MarlinFixedRapidMm positivo e finito; outros modos nao aceitam esse valor.'
    }
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
        Write-Host 'Exportando casos publicos do perfil (unidades e operacoes suportadas)...'
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
        if ($Profile -eq 'ISEL_CNC') { $oracleArgs += @('--isel-m01-mode',$IselM01Mode) }
        if ($Profile -in @('MARLIN','MARLIN_LASER_FAN_PIN','MARLIN_LASER_SPINDLE_PIN')) {
            $oracleArgs += @('--marlin-rapid-mode',$MarlinRapidMode)
            if ($MarlinRapidMode -eq 'fixed') {
                $oracleArgs += @('--marlin-fixed-rapid-mm',$MarlinFixedRapidMm.ToString([Globalization.CultureInfo]::InvariantCulture))
            }
        }
        Write-Host 'Comparando comandos, trajetos e parametros do perfil com o Python original...'
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
