param(
    [Parameter(Mandatory=$true)][string[]]$Projects,
    [string]$Python,
    [string]$DependencyPath,
    [string]$OutputDirectory,
    [switch]$SkipPublic,
    [switch]$Strict
)

# Read-only .FlatPrj sources; serial Maven/oracle runs, outputs only in a new target directory.
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$matrixRoot=$PSScriptRoot
$matrixShell=(Get-Process -Id $PID).Path
$matrixSources=@()
$matrixRuns=@()
$matrixPublicSuites=[Collections.Generic.HashSet[string]]::new()

function Save-MatrixSummary {
    $counts=[ordered]@{}
    foreach($run in $script:matrixRuns) {
        foreach($property in $run.Counts.PSObject.Properties) {
            if(-not $counts.Contains($property.Name)) { $counts[$property.Name]=0 }
            $counts[$property.Name]+=[int]$property.Value
        }
    }
    $sources=@($script:matrixSources | ForEach-Object {
        [ordered]@{Name=$_.Name;Sha256=$_.Sha256;Unchanged=((Get-FileHash -LiteralPath $_.Path -Algorithm SHA256).Hash -eq $_.Sha256)}
    })
    $different=0
    foreach($key in $counts.Keys) { if($key -ne 'MATCH_SAMPLED') { $different+=$counts[$key] } }
    $failed=@($script:matrixRuns | Where-Object { -not $_.CompletedComparison }).Count
    $plannedRuns=$script:matrixSources.Count*3
    $complete=$script:matrixRuns.Count -eq $plannedRuns
    $summary=[ordered]@{Schema=1;Scope='Read-only source projects; numerical sampled CAM/G-code and FX flow assertions, not visual/physical certification';
        PlannedRuns=$plannedRuns;RecordedRuns=$script:matrixRuns.Count;MatrixComplete=$complete;
        SourceFiles=$sources.Count;DistinctSourceHashes=@($sources.Sha256|Select-Object -Unique).Count;
        Sources=$sources;StrictRequested=[bool]$Strict;Counts=$counts;NonMatchingCases=$different;FailedRuns=$failed;Runs=$script:matrixRuns}
    $summary|ConvertTo-Json -Depth 16|Out-File -LiteralPath (Join-Path $OutputDirectory 'matrix-summary.json') -Encoding utf8
    $rows=''
    foreach($run in $script:matrixRuns) {
        $label=[Net.WebUtility]::HtmlEncode($run.Project+' / '+$run.Layout+' / '+$run.Suite)
        $status=if($run.CompletedComparison) { 'Comparacao concluida; conferir divergencias' } else { 'Sem conclusao: falha do fluxo/oraculo' }
        $detail=[Net.WebUtility]::HtmlEncode(($run.Counts|ConvertTo-Json -Compress))
        $rows+='<tr><td>'+ $label +'</td><td>'+ $status +'</td><td><pre>'+ $detail +'</pre></td><td><a href="'+$run.Directory+'/export.log">FX</a> <a href="'+$run.Directory+'/comparison-summary.json">Detalhes</a></td></tr>'
    }
    $state=if($complete) { 'Execucao encerrada; conferir resultados.' } else { 'PARCIAL: execucao incompleta ou em andamento.' }
    ('<!doctype html><meta charset="utf-8"><title>Main flow matrix</title><h1>Matriz dos fluxos principais</h1><p>'+ $state +' '+$script:matrixRuns.Count+'/'+$plannedRuns+' execucoes registradas.</p><p>'+[Net.WebUtility]::HtmlEncode($summary.Scope)+
        '</p><p>Fontes: '+$sources.Count+'; hashes distintos: '+$summary.DistinctSourceHashes+'; casos divergentes: '+$different+'; execucoes sem conclusao: '+$failed+
        '</p><table border="1"><tr><th>Fonte / layout / receitas</th><th>Resultado</th><th>Contagens</th><th>Relatorios</th></tr>'+$rows+'</table>')|
        Out-File -LiteralPath (Join-Path $OutputDirectory 'index.html') -Encoding utf8
    return $summary
}

try {
    $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
    foreach($requested in $Projects) {
        $source=(Resolve-Path -LiteralPath $requested).Path
        if([IO.Path]::GetExtension($source) -ine '.FlatPrj') { throw 'Esta matriz exige fontes .FlatPrj para decodificacao independente pelo Python.' }
        if($seen.Add($source)) { $matrixSources+= [pscustomobject]@{Path=$source;Name=[IO.Path]::GetFileName($source);Sha256=(Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash} }
    }
    if($matrixSources.Count -eq 0) { throw 'Informe ao menos um projeto.' }
    if(-not $OutputDirectory) { $OutputDirectory=Join-Path $matrixRoot ('target/main-flow-matrix-'+(Get-Date -Format 'yyyyMMdd-HHmmss-fff')) }
    elseif(-not [IO.Path]::IsPathRooted($OutputDirectory)) { $OutputDirectory=Join-Path $matrixRoot $OutputDirectory }
    $OutputDirectory=[IO.Path]::GetFullPath($OutputDirectory)
    $targetRoot=[IO.Path]::GetFullPath((Join-Path $matrixRoot 'target'))+[IO.Path]::DirectorySeparatorChar
    if(-not $OutputDirectory.StartsWith($targetRoot,[StringComparison]::OrdinalIgnoreCase)) { throw 'Saida deve ficar dentro de flatcam-next/target.' }
    if(Test-Path -LiteralPath $OutputDirectory) { throw 'Escolha uma pasta nova; resultados anteriores nao serao sobrescritos.' }
    New-Item -ItemType Directory -Path $OutputDirectory|Out-Null
    $layouts=@(
        @{Id='2x2-gap5';Columns=2;Rows=2;X=5;Y=5;Advanced=$false},
        @{Id='3x1-gap0';Columns=3;Rows=1;X=0;Y=0;Advanced=$false},
        @{Id='1x2-gap2-7-advanced';Columns=1;Rows=2;X=2;Y=7;Advanced=$true})
    for($i=0;$i -lt $matrixSources.Count;$i++) {
        $source=$matrixSources[$i]
        foreach($layout in $layouts) {
            $suite=if($layout.Advanced) { 'advanced' } else { 'standard-rest-connect' }
            $relative='project-'+($i+1)+'-'+$layout.Id
            $directory=Join-Path $OutputDirectory $relative
            $arguments=@('-NoProfile','-File',(Join-Path $matrixRoot 'compare-panelized-flows.ps1'),'-Project',$source.Path,
                '-OutputDirectory',$directory,'-Columns',$layout.Columns,'-Rows',$layout.Rows,'-SpacingXmm',$layout.X,'-SpacingYmm',$layout.Y)
            if($Python) { $arguments+=@('-Python',$Python) }
            if($DependencyPath) { $arguments+=@('-DependencyPath',$DependencyPath) }
            if($Strict) { $arguments+='-Strict' }
            if($layout.Advanced) { $arguments+='-AdvancedRecipes' }
            if($SkipPublic -or $matrixPublicSuites.Contains($suite)) { $arguments+='-SkipPublic' }
            Write-Host ('Matriz: fonte '+($i+1)+'/'+$matrixSources.Count+'; '+$layout.Id)
            $oldPreference=$ErrorActionPreference
            try {
                $ErrorActionPreference='Continue'
                & $matrixShell @arguments 2>&1 | ForEach-Object { $line=$_.ToString(); Write-Host $line; Add-Content -LiteralPath (Join-Path $OutputDirectory ($relative+'-launcher.log')) -Value $line -Encoding utf8 }
                $code=$LASTEXITCODE
            } finally { $ErrorActionPreference=$oldPreference }
            if((Get-FileHash -LiteralPath $source.Path -Algorithm SHA256).Hash -ne $source.Sha256) { throw 'O projeto original mudou durante a matriz.' }
            $child=Join-Path $directory 'comparison-summary.json'
            $completed=($code -in @(0,1)) -and (Test-Path -LiteralPath $child)
            $counts=[ordered]@{}
            if($completed) {
                $data=Get-Content -LiteralPath $child -Raw -Encoding utf8|ConvertFrom-Json
                foreach($report in $data.Reports) { foreach($property in $report.Counts.PSObject.Properties) {
                    if(-not $counts.Contains($property.Name)) { $counts[$property.Name]=0 }
                    $counts[$property.Name]+=[int]$property.Value
                } }
                $null=$matrixPublicSuites.Add($suite)
            }
            $matrixRuns+=[pscustomobject]@{Project=$source.Name;Layout=$layout.Id;Suite=$suite;Directory=$relative;
                CompletedComparison=[bool]$completed;ExitCode=$code;Counts=[pscustomobject]$counts}
            $null=Save-MatrixSummary
        }
    }
    $summary=Save-MatrixSummary
    Write-Host ('Relatorio: '+(Join-Path $OutputDirectory 'index.html'))
    if($summary.FailedRuns) { exit 2 }
    if($Strict -and $summary.NonMatchingCases) { exit 1 }
    exit 0
} catch {
    Write-Error $_ -ErrorAction Continue
    exit 2
} finally {
    foreach($source in $matrixSources) {
        if((Get-FileHash -LiteralPath $source.Path -Algorithm SHA256).Hash -ne $source.Sha256) { throw 'Hash da fonte mudou; a matriz nao esta aprovada.' }
    }
}
