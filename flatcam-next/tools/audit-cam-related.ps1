param([string]$BuildDirectory,[ValidateRange(1,100)][int]$Repeats=10,[string]$OutputDirectory,[switch]$Lines,[string]$CaseFilter='.*',[string[]]$ClassOverrides,[ValidateSet('STANDARD','LINES','REGION_LINES','SPIRAL')][string]$Method='LINES')
$ErrorActionPreference='Stop'
$reactor=Split-Path -Parent $PSScriptRoot
if(!$BuildDirectory){$BuildDirectory=(Get-Content -LiteralPath (Join-Path $reactor 'target\beta-latest.txt') -Raw).Trim()}
$BuildDirectory=(Resolve-Path -LiteralPath $BuildDirectory).Path
$image=Join-Path $BuildDirectory 'image\FlatCAM FX Beta'
if(!$OutputDirectory){$OutputDirectory=Join-Path $reactor ('target\cam-related-audit-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff'))}
New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
$OutputDirectory=(Resolve-Path -LiteralPath $OutputDirectory).Path
$classes=Join-Path $OutputDirectory 'classes';New-Item -ItemType Directory -Force -Path $classes | Out-Null
$app=Join-Path $image 'app'
$overrides=if($ClassOverrides){($ClassOverrides | ForEach-Object {(Resolve-Path -LiteralPath $_).Path}) -join ';'}else{''}
$appClasspath=$(if($overrides){$overrides+';'})+(Join-Path $app '*')
$info=[Diagnostics.ProcessStartInfo]::new((Join-Path $env:JAVA_HOME 'bin\javac.exe'))
$source=if($Lines){'LinesRelatedAuditProbe.java'}else{'CamRelatedAuditProbe.java'}
$main=if($Lines){'org.flatcam.fx.LinesRelatedAuditProbe'}else{'org.flatcam.fx.CamRelatedAuditProbe'}
$args=@('--release','25','--module-path',$app,'--add-modules','javafx.controls','-cp',$appClasspath,'-d',$classes,(Join-Path $PSScriptRoot $source))
function Run([Diagnostics.ProcessStartInfo]$Info,[string[]]$Arguments,[string]$Log){
    $Info.Arguments=($Arguments | ForEach-Object {'"'+$_.Replace('"','\"')+'"'}) -join ' '
    $Info.UseShellExecute=$false;$Info.CreateNoWindow=$true;$Info.RedirectStandardOutput=$true;$Info.RedirectStandardError=$true
    $Info.StandardOutputEncoding=[Text.UTF8Encoding]::new($false);$Info.StandardErrorEncoding=[Text.UTF8Encoding]::new($false)
    foreach($key in 'JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH'){$Info.EnvironmentVariables.Remove($key)}
    $process=[Diagnostics.Process]::Start($Info);$stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
    $timedOut=!$process.WaitForExit(300000)
    if($timedOut){$process.Kill();$process.WaitForExit()}
    [IO.File]::WriteAllText($Log,$stdout.Result+$stderr.Result,[Text.UTF8Encoding]::new($false))
    if($timedOut){throw "Audit process exceeded five minutes; log: $Log"}
    return $process.ExitCode
}
if((Run $info $args (Join-Path $OutputDirectory 'compile.log')) -ne 0){throw "Probe compilation failed: $OutputDirectory"}
$info=[Diagnostics.ProcessStartInfo]::new((Join-Path $image 'runtime\bin\java.exe'))
$args=@('--module-path',$app,'--add-modules','javafx.controls','--enable-native-access=javafx.graphics',
    '-XX:+UnlockDiagnosticVMOptions','-XX:DisableIntrinsic=_indexOfUL',
    ('-Dflatcam.audit.overrides='+$overrides),'-cp',($classes+';'+$appClasspath))
if($Lines){$args+=@(('-Dflatcam.audit.method='+$Method),$main,(Join-Path $OutputDirectory 'lines-audit.json'))}
else{$args+=@('-Djava.util.prefs.PreferencesFactory=org.flatcam.fx.CamRelatedAuditProbe$MemoryFactory',('-Dflatcam.audit.cases='+$CaseFilter),$main,$OutputDirectory,[string]$Repeats)}
$code=Run $info $args (Join-Path $OutputDirectory 'audit.log')
Write-Output "Audit report: $OutputDirectory"
Get-Content -LiteralPath (Join-Path $OutputDirectory 'audit.log') -Encoding UTF8 | Select-String $(if($Lines){': FAIL|ORACLE_ERROR|Lines distance audit:'}else{'PASS|FAIL|ORACLE_ERROR|UI audit:'}) | ForEach-Object {$_.Line}
exit $code
