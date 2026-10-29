param([string]$OutputDirectory)
$ErrorActionPreference='Stop'
Push-Location (Split-Path -Parent $PSScriptRoot)
try {
    $report='flatcam-fx\target\surefire-reports\TEST-org.flatcam.fx.MainToolPersistenceTest.xml'
    if(!(Test-Path -LiteralPath $report)) {
        throw 'Run mvnw.cmd -pl flatcam-fx -am test -Dtest=MainToolPersistenceTest -Dsurefire.failIfNoSpecifiedTests=false first.'
    }
    if(!$OutputDirectory){$OutputDirectory=Join-Path $PWD ('target\ux-reports-audit-'+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff'))}
    New-Item -ItemType Directory -Path $OutputDirectory | Out-Null
    $OutputDirectory=(Resolve-Path -LiteralPath $OutputDirectory).Path
    [xml]$test=Get-Content -LiteralPath $report -Encoding UTF8
    $classpath=[string]($test.testsuite.properties.property | Where-Object {$_.name -eq 'java.class.path'}).value
    if(!$classpath){throw 'Test classpath unavailable in Surefire report.'}
    $classes=Join-Path $OutputDirectory 'classes';New-Item -ItemType Directory -Path $classes | Out-Null
    function Run-AuditProcess([string]$Program,[string[]]$Arguments,[string]$Log) {
        $info=[Diagnostics.ProcessStartInfo]::new($Program)
        $info.Arguments=($Arguments | ForEach-Object {'"'+$_.Replace('"','\"')+'"'}) -join ' '
        $info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.WindowStyle='Hidden'
        $info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
        $info.StandardOutputEncoding=[Text.UTF8Encoding]::new($false);$info.StandardErrorEncoding=[Text.UTF8Encoding]::new($false)
        foreach($key in 'JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS'){$info.EnvironmentVariables.Remove($key)}
        $process=[Diagnostics.Process]::Start($info)
        $stdout=$process.StandardOutput.ReadToEndAsync();$stderr=$process.StandardError.ReadToEndAsync()
        if(!$process.WaitForExit(120000)){$process.Kill();throw "Audit process timed out: $Program"}
        [IO.File]::WriteAllText($Log,$stdout.Result+$stderr.Result,[Text.UTF8Encoding]::new($false))
        if($process.ExitCode -ne 0){throw "Audit interrupted: $Log"}
    }
    Run-AuditProcess "$env:JAVA_HOME\bin\javac.exe" @('-encoding','UTF-8','--release','25','-cp',$classpath,'-d',$classes,
        (Join-Path $PSScriptRoot 'UxReportsAuditProbe.java')) (Join-Path $OutputDirectory 'compile.log')
    foreach($locale in 'pt-BR','en-US') {
        $output=Join-Path $OutputDirectory $locale;New-Item -ItemType Directory -Path $output | Out-Null
        $arguments=@('-XX:+UnlockDiagnosticVMOptions','-XX:DisableIntrinsic=_indexOfUL','--enable-native-access=ALL-UNNAMED',
            '-Dstdout.encoding=UTF-8','-Dstderr.encoding=UTF-8',
            '-Djava.util.prefs.PreferencesFactory=org.flatcam.fx.StartupSplashTest$MemoryFactory',
            "-Duser.language=$($locale.Split('-')[0])","-Duser.country=$($locale.Split('-')[1])",
            '-cp',"$classes;$classpath",'org.flatcam.fx.UxReportsAuditProbe',$output)
        if($locale -eq 'en-US'){$arguments+='numeric-only'}
        Run-AuditProcess "$env:JAVA_HOME\bin\java.exe" $arguments (Join-Path $output 'probe.log')
    }
    $record=[ordered]@{status='OBSERVATIONS_COLLECTED';commit=(& git rev-parse HEAD).Trim();
        probeSha256=(Get-FileHash -LiteralPath (Join-Path $PSScriptRoot 'UxReportsAuditProbe.java') -Algorithm SHA256).Hash;
        runnerSha256=(Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash;
        checkedUtc=[DateTime]::UtcNow.ToString('o');output=$OutputDirectory;
        preferences='memory only';inputs='synthetic';
        limits='Programmatic JavaFX scenes; not a physical mouse/keyboard, installed MSI, CNC or full UI audit. Exit 0 means observations collected, not bugs absent.'}
    [IO.File]::WriteAllText((Join-Path $OutputDirectory 'session.json'),($record|ConvertTo-Json),[Text.UTF8Encoding]::new($false))
    Write-Host "Audit observations: $OutputDirectory"
} finally {Pop-Location}
