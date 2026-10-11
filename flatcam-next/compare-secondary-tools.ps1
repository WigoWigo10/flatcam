param(
    [string]$Project,
    [string]$Python,
    [string]$DependencyPath,
    [string[]]$Cases,
    [string]$OutputDirectory,
    [switch]$LegacyCompatible,
    [switch]$Strict
)

# Rules Check, Copper Thieving and Calibration against the original Python routines.
# Numerical headless comparison only: no UI, preference writes or dependency installation.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$comparisonRoot = $PSScriptRoot
$legacyRoot = Split-Path -Parent $comparisonRoot

function Invoke-ComparisonNative([string]$Executable, [string[]]$Arguments, [string]$Log) {
    # In Windows PowerShell 5, stderr is a NativeCommandError even for warnings.
    # Judge the native exit code, not the stream used by Maven/Python.
    $ErrorActionPreference = 'Continue'
    $nativeOutput = @(& $Executable @Arguments 2>&1 | ForEach-Object { $_.ToString() })
    $nativeCode = $LASTEXITCODE
    $nativeOutput | Out-File -LiteralPath $Log -Encoding utf8
    if ($nativeOutput.Count -gt 0) { Write-Host $nativeOutput[-1] }
    return $nativeCode
}

try {
    if ($LegacyCompatible -and -not $Python) {
        $Python = Join-Path $comparisonRoot 'target/oracle-py311/Scripts/python.exe'
        if (-not (Test-Path -LiteralPath $Python -PathType Leaf)) {
            throw 'Prepare target/oracle-py311 conforme COMPARACAO_CAM.md, ou informe -Python compativel. O runner nao instala dependencias.'
        }
    }
    if (-not $Python) { $Python = $env:FLATCAM_PROFILE_PYTHON }
    if (-not $Python) {
        foreach ($candidate in @((Join-Path $legacyRoot '.venv312/Scripts/python.exe'),
                (Join-Path (Split-Path -Parent $legacyRoot) 'flatcam-8994/.venv312/Scripts/python.exe'),
                (Join-Path $legacyRoot '.venv/Scripts/python.exe'))) {
            if (Test-Path -LiteralPath $candidate -PathType Leaf) { $Python = $candidate; break }
        }
    }
    if (-not $Python) { $Python = (Get-Command python -ErrorAction Stop).Source }
    if ($Project) { $Project = (Resolve-Path -LiteralPath $Project).Path }
    if ($DependencyPath) { $DependencyPath = (Resolve-Path -LiteralPath $DependencyPath).Path }
    if (-not $OutputDirectory) {
        $OutputDirectory = Join-Path $comparisonRoot ('target/tools-comparison-' + (Get-Date -Format 'yyyyMMdd-HHmmss-fff'))
    }
    $OutputDirectory = [IO.Path]::GetFullPath($OutputDirectory)
    $targetRoot = [IO.Path]::GetFullPath((Join-Path $comparisonRoot 'target')) + [IO.Path]::DirectorySeparatorChar
    if (-not $OutputDirectory.StartsWith($targetRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Relatorios devem ficar dentro de flatcam-next/target para nao publicar geometria privada no Git.'
    }
    if (Test-Path -LiteralPath $OutputDirectory) { throw 'Escolha uma pasta nova de resultados; relatorios existentes nao serao sobrescritos.' }
    New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
    if ($LegacyCompatible) {
        $validationCode = "import sys; sys.path.insert(0,sys.argv[1]) if sys.argv[1] else None; import shapely; from shapely.geos import geos_version_string; print(sys.version.split()[0],shapely.__version__,geos_version_string); sys.exit(0 if sys.version_info[:2]==(3,11) and shapely.__version__=='1.8.5.post1' and geos_version_string.startswith('3.10.3') else 2)"
        # An empty dependency argument is dropped by Windows PowerShell native
        # invocation; use a harmless existing directory when no override was set.
        $validationPath = if ($DependencyPath) { $DependencyPath } else { $comparisonRoot }
        $validationExit = Invoke-ComparisonNative $Python @('-c',$validationCode,$validationPath) (Join-Path $OutputDirectory 'oracle-environment.log')
        if ($validationExit -ne 0) { throw 'Referencia incompativel: esperado Python 3.11 / Shapely 1.8.5.post1 / GEOS 3.10.3.' }
    }
    Push-Location $comparisonRoot
    try {
        $buildArgs = @('-q','-pl','flatcam-application','-am','-Dtest=PythonToolsComparisonExportTest',
            '-Dsurefire.failIfNoSpecifiedTests=false', ('-Dflatcam.tools.comparison.output=' + $OutputDirectory),
            'test','-l',(Join-Path $OutputDirectory 'export.log'))
        if ($Project) { $buildArgs += '-Dflatcam.python.project.fixture=' + $Project }
        Write-Host 'Exportando resultados FX de Rules Check, Copper Thieving e Calibration...'
        $buildCode = Invoke-ComparisonNative (Join-Path $comparisonRoot 'mvnw.cmd') $buildArgs (Join-Path $OutputDirectory 'maven-launcher.log')
        if ($buildCode -ne 0) { throw "Exportacao Java falhou (codigo $buildCode). Consulte export.log." }
        $comparisonCode = 0
        foreach ($entry in @(@{ File='fx-tools.json'; Name='report' })) {
            $reportDirectory = Join-Path $OutputDirectory $entry.Name
            $oracleArgs = @((Join-Path $comparisonRoot 'tools/compare_tools_python.py'), '--legacy-root',$legacyRoot,
                '--fx-export',(Join-Path $OutputDirectory $entry.File),'--output',$reportDirectory)
            if ($DependencyPath) { $oracleArgs += @('--dependency-path',$DependencyPath) }
            if ($Cases) { $oracleArgs += @('--cases',($Cases -join ',')) }
            if ($Strict) { $oracleArgs += '--strict' }
            if ($Project) { $oracleArgs += @('--project',$Project) }
            Write-Host "Comparando $($entry.Name) com Python..."
            $oracleCode = Invoke-ComparisonNative $Python $oracleArgs (Join-Path $OutputDirectory ($entry.Name + '.log'))
            $reportFile = Join-Path $reportDirectory 'report.json'
            if (-not (Test-Path -LiteralPath $reportFile) -or $oracleCode -notin @(0,1)) {
                throw "Falha ao executar o oraculo ($oracleCode). Consulte $($entry.Name).log."
            }
            if ($oracleCode -ne 0) { $comparisonCode = 1 }
            Write-Host "Relatorio: $(Join-Path $reportDirectory 'index.html')"
        }
        if ($comparisonCode) { Write-Host 'Modo strict reprovado: ha divergencias ou erros do legado. Eles nao contam como paridade.' }
        exit $comparisonCode
    } finally { Pop-Location }
} catch {
    Write-Error $_ -ErrorAction Continue
    exit 2
}
