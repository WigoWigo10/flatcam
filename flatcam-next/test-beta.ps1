param([string]$BuildDirectory,[switch]$SkipInstallation,[string]$PreviousMsi)
$ErrorActionPreference='Stop'
function Run-Isolated([string]$Program,[string[]]$Arguments,[string]$Log,[string]$WorkingDirectory) {
    $info=[Diagnostics.ProcessStartInfo]::new($Program)
    $info.Arguments=($Arguments | ForEach-Object {
        if($_ -match '^([A-Z_][A-Z0-9_]*)=(.*)$') { $Matches[1]+'="'+$Matches[2].Replace('"','\"')+'"' }
        elseif($_ -match '\s|"') {'"'+$_.Replace('"','\"')+'"'} else {$_}
    }) -join ' '
    $info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.WindowStyle='Hidden'
    $info.WorkingDirectory=$WorkingDirectory;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    $info.StandardOutputEncoding=[Text.UTF8Encoding]::new($false);$info.StandardErrorEncoding=[Text.UTF8Encoding]::new($false)
    foreach($key in 'JAVA_HOME','FLATCAM_FX_JAVA_HOME') {$info.EnvironmentVariables[$key]='Z:\intentionally-missing-java'}
    foreach($key in 'JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH') {$info.EnvironmentVariables.Remove($key)}
    $info.EnvironmentVariables['PATH']="$env:SystemRoot\System32;$env:SystemRoot"
    [IO.File]::WriteAllText($Log+'.command.txt',$Program+' '+$info.Arguments,[Text.UTF8Encoding]::new($false))
    $process=[Diagnostics.Process]::Start($info)
    $out=$process.StandardOutput.ReadToEndAsync();$err=$process.StandardError.ReadToEndAsync()
    if(!$process.WaitForExit(60000)) {$process.Kill();throw "Process timed out: $Program"}
    [IO.File]::WriteAllText($Log,($out.Result+$err.Result),[Text.UTF8Encoding]::new($false))
    if($process.ExitCode -ne 0) {throw "Process failed ($($process.ExitCode)): $Log"}
}
function Smoke-Image([string]$Image,[string]$Report,[string]$Classes,[string]$Version,[string]$Revision) {
    New-Item -ItemType Directory -Path $Report | Out-Null
    $exe=Join-Path $Image 'FlatCAM FX Beta.exe'
    Run-Isolated $exe @('--probe') (Join-Path $Report 'native-gpu.log') $Report
    if((Get-Content -LiteralPath (Join-Path $Report 'native-gpu.log') -Raw) -notmatch 'Windows process code page: 65001') {throw 'UTF-8 process manifest was not active.'}
    Run-Isolated $exe @('--probe','--software') (Join-Path $Report 'native-software.log') $Report
    $vmOptions=@('--module-path',(Join-Path $Image 'app'),'--add-modules','javafx.controls','--enable-native-access=javafx.graphics',
        '-XX:+UnlockDiagnosticVMOptions','-XX:DisableIntrinsic=_indexOfUL',
        '-Djava.util.prefs.PreferencesFactory=org.flatcam.fx.BetaSmokeProbe$MemoryFactory',
        '-cp',"$Classes;$(Join-Path $Image 'app\*')")
    $options=$vmOptions+@('org.flatcam.fx.BetaSmokeProbe',$Report,$Version,$Revision)
    Run-Isolated (Join-Path $Image 'runtime\bin\java.exe') $options (Join-Path $Report 'ui-cam.log') $Report
    $summary=Get-Content -LiteralPath (Join-Path $Report 'smoke.json') -Raw | ConvertFrom-Json
    if($summary.status -ne 'PASS') {throw 'Smoke did not produce a PASS report.'}
    $diagnostic=Join-Path $Report 'diagnostics'
    $options=$vmOptions+@("-Dflatcam.diagnostics.session=$diagnostic",'-Dflatcam.splash.enabled=false',
        '-Dflatcam.diagnostics.enabled=true','org.flatcam.fx.FlatCamLauncher','--diagnostics-probe')
    Run-Isolated (Join-Path $Image 'runtime\bin\java.exe') $options (Join-Path $Report 'diagnostics.log') $Report
    if(!(Test-Path -LiteralPath (Join-Path $diagnostic 'session.jfr')) -or
        (Get-Item -LiteralPath (Join-Path $diagnostic 'session.jfr')).Length -eq 0) {throw 'Bundled runtime did not capture JFR diagnostics.'}
}
function Read-MsiProperty([string]$Path,[string]$Property) {
    $installer=New-Object -ComObject WindowsInstaller.Installer
    $database=$installer.OpenDatabase($Path,0)
    $query=$database.OpenView('SELECT `Value` FROM `Property` WHERE `Property` = '''+$Property+'''')
    [void]$query.Execute();$record=$query.Fetch()
    if(!$record) {throw "Missing MSI property: $Property"}
    $value=$record.StringData(1);[void]$query.Close();return $value
}
function Test-Upgrade([string]$Previous,[string]$Current,[string]$Outside,[string]$Classes,$Manifest,[string]$Revision) {
    $oldProduct=Read-MsiProperty $Previous 'ProductCode'
    $newProduct=Read-MsiProperty $Current 'ProductCode'
    if($oldProduct -eq $newProduct -or
        (Read-MsiProperty $Previous 'UpgradeCode') -ne (Read-MsiProperty $Current 'UpgradeCode')) {
        throw 'Upgrade test requires different products from the same upgrade family.'
    }
    $report=Join-Path $Outside 'upgrade';New-Item -ItemType Directory -Path $report | Out-Null
    $installed=Join-Path $report 'instalado'
    $owned=[Collections.Generic.List[string]]::new()
    $installer=New-Object -ComObject WindowsInstaller.Installer
    $oldVersion=$null
    try {
        Run-Isolated "$env:SystemRoot\System32\msiexec.exe" @('/i',$Previous,'/qn','/norestart',"INSTALLDIR=$installed",'/l*v',(Join-Path $report 'install-old.log')) (Join-Path $report 'install-old-process.log') $report
        $owned.Add($oldProduct)
        if($installer.ProductInfo($oldProduct,'AssignmentType') -ne '0') {throw 'Previous test installation is not per-user.'}
        $oldManifest=Get-Content -LiteralPath (Join-Path $installed 'flatcam-release.json') -Raw | ConvertFrom-Json
        $oldVersion=$oldManifest.version
        if($oldVersion -eq $Manifest.version) {throw 'Previous installer must be an older Beta.'}
        # A test-owned project inside the install directory must survive both upgrade and uninstall.
        $project=Join-Path $installed 'tester-project.fcnproj'
        Copy-Item -LiteralPath (Join-Path $Outside 'portable-smoke\Painel-testado.fcnproj') -Destination $project
        $projectHash=(Get-FileHash -LiteralPath $project -Algorithm SHA256).Hash
        $owned.Add($newProduct)
        Run-Isolated "$env:SystemRoot\System32\msiexec.exe" @('/i',$Current,'/qn','/norestart',"INSTALLDIR=$installed",'/l*v',(Join-Path $report 'upgrade.log')) (Join-Path $report 'upgrade-process.log') $report
        if($installer.ProductState($oldProduct) -ne -1) {throw 'Previous MSI product remained registered after upgrade.'}
        if($installer.ProductState($newProduct) -ne 5) {throw 'New MSI product is not registered.'}
        if($installer.ProductInfo($newProduct,'AssignmentType') -ne '0') {throw 'Upgraded test installation is not per-user.'}
        if((Get-FileHash -LiteralPath $project -Algorithm SHA256).Hash -ne $projectHash) {throw 'Upgrade modified the test project.'}
        if(Test-Path -LiteralPath (Join-Path $installed "app\flatcam-fx-$oldVersion.jar")) {throw 'Upgrade left the previous application JAR installed.'}
        Smoke-Image $installed (Join-Path $report 'upgraded-smoke') $Classes $Manifest.version $Revision
    } finally {
        foreach($product in @($newProduct,$oldProduct)) {
            if($owned.Contains($product) -and $installer.ProductState($product) -ne -1) {
                Run-Isolated "$env:SystemRoot\System32\msiexec.exe" @('/x',$product,'/qn','/norestart','/l*v',(Join-Path $report "uninstall-$product.log")) (Join-Path $report "uninstall-$product-process.log") $report
            }
        }
    }
    if(Test-Path -LiteralPath (Join-Path $installed 'FlatCAM FX Beta.exe')) {throw 'Upgrade uninstall left the executable installed.'}
    if((Get-FileHash -LiteralPath $project -Algorithm SHA256).Hash -ne $projectHash) {throw 'Uninstall modified the test project.'}
    return $oldVersion
}
Push-Location $PSScriptRoot
try {
    if($PreviousMsi) {
        if($SkipInstallation) {throw '-PreviousMsi requires installation testing.'}
        $PreviousMsi=(Resolve-Path -LiteralPath $PreviousMsi).Path
    }
    if(!$BuildDirectory) {$BuildDirectory=(Get-Content -LiteralPath 'target\beta-latest.txt' -Raw).Trim()}
    $BuildDirectory=(Resolve-Path -LiteralPath $BuildDirectory).Path
    $artifacts=Get-Content -LiteralPath (Join-Path $BuildDirectory 'artifacts.json') -Raw | ConvertFrom-Json
    foreach($artifact in $artifacts) {
        if((Get-FileHash -LiteralPath (Join-Path $BuildDirectory $artifact.file) -Algorithm SHA256).Hash -ne $artifact.sha256) {throw "Artifact checksum mismatch: $($artifact.file)"}
    }
    $outside=Join-Path ([IO.Path]::GetTempPath()) ('FlatCAM FX Beta teste-'+[char]0x00e1+'-'+[Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $outside | Out-Null
    $zip=$artifacts | Where-Object {$_.file.EndsWith('.zip')} | Select-Object -First 1
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    [IO.Compression.ZipFile]::ExtractToDirectory((Join-Path $BuildDirectory $zip.file),$outside)
    $image=Join-Path $outside 'FlatCAM FX Beta'
    $manifest=Get-Content -LiteralPath (Join-Path $image 'flatcam-release.json') -Raw | ConvertFrom-Json
    $revision=$manifest.sourceCommit+$(if($manifest.dirty){'-dirty'}else{''})
    foreach($line in Get-Content -LiteralPath (Join-Path $image 'SHA256SUMS.txt')) {
        if($line -notmatch '^([a-f0-9]{64})  (.+)$') {throw 'Malformed package checksum inventory.'}
        $hash=$Matches[1];$relative=$Matches[2];$path=[IO.Path]::GetFullPath((Join-Path $image $relative))
        if(!$path.StartsWith($image+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) {throw 'Invalid path in package checksum inventory.'}
        if((Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash -ne $hash) {throw "Package file mismatch: $relative"}
    }
    $classes=Join-Path $outside 'smoke-classes';New-Item -ItemType Directory -Path $classes | Out-Null
    # Capture benign compiler notes without PowerShell 5 turning native stderr into a terminating error.
    Run-Isolated "$env:JAVA_HOME\bin\javac.exe" @('--release','25','--module-path',(Join-Path $image 'app'),
        '--add-modules','javafx.controls','-cp',(Join-Path $image 'app\*'),'-d',$classes,
        (Join-Path $PSScriptRoot 'tools\BetaSmokeProbe.java')) (Join-Path $outside 'probe-compile.log') $PSScriptRoot
    Smoke-Image $image (Join-Path $outside 'portable-smoke') $classes $manifest.version $revision
    $installResult='NOT_RUN'
    if(!$SkipInstallation) {
        $msi=$artifacts | Where-Object {$_.file.EndsWith('.msi')} | Select-Object -First 1
        if(!$msi) {throw 'MSI artifact missing. Use -SkipInstallation only for an explicit portable-only check.'}
        $installer=New-Object -ComObject WindowsInstaller.Installer
        $family=Read-MsiProperty (Join-Path $BuildDirectory $msi.file) 'UpgradeCode'
        foreach($registered in $installer.RelatedProducts($family)) {
            if($installer.ProductState([string]$registered) -ne -1) {throw 'An existing Beta is installed. Refusing to replace it for a smoke test.'}
        }
        $database=$installer.OpenDatabase((Join-Path $BuildDirectory $msi.file),0)
        $query=$database.OpenView('SELECT `Value` FROM `Property` WHERE `Property` = ''ProductCode''');$query.Execute()
        $product=$query.Fetch().StringData(1);$query.Close()
        $query=$database.OpenView('SELECT `Directory` FROM `Directory` WHERE `Directory` = ''INSTALLDIR''');$query.Execute()
        if(!$query.Fetch()) {throw 'Installer does not expose the expected INSTALLDIR. Review it before installation.'};$query.Close()
        $installed=Join-Path $outside 'instalado';$didInstall=$false
        try {
            $installArgs=@('/i',(Join-Path $BuildDirectory $msi.file),'/qn','/norestart',"INSTALLDIR=$installed",'/l*v',(Join-Path $outside 'install.log'))
            Run-Isolated "$env:SystemRoot\System32\msiexec.exe" $installArgs (Join-Path $outside 'install-process.log') $outside
            $didInstall=$true
            if($installer.ProductState($product) -ne 5 -or $installer.ProductInfo($product,'AssignmentType') -ne '0') {throw 'Test MSI did not install per-user.'}
            if(!(Test-Path -LiteralPath (Join-Path $installed 'FlatCAM FX Beta.exe'))) {throw 'Installer did not use the isolated installation directory.'}
            Smoke-Image $installed (Join-Path $outside 'installed-smoke') $classes $manifest.version $revision
        } finally {
            if($didInstall) {
                Run-Isolated "$env:SystemRoot\System32\msiexec.exe" @('/x',$product,'/qn','/norestart','/l*v',(Join-Path $outside 'uninstall.log')) (Join-Path $outside 'uninstall-process.log') $outside
            }
        }
        if(Test-Path -LiteralPath (Join-Path $installed 'FlatCAM FX Beta.exe')) {throw 'Uninstaller left the executable installed.'}
        $installResult='PASS'
    }
    $upgradeResult='NOT_RUN';$previousVersion=$null
    if($PreviousMsi) {
        $previousVersion=Test-Upgrade $PreviousMsi (Join-Path $BuildDirectory $msi.file) $outside $classes $manifest $revision
        $upgradeResult='PASS'
    }
    $report=[ordered]@{status='PASS';version=$manifest.version;sourceCommit=$manifest.sourceCommit;dirty=$manifest.dirty;
        checkedUtc=[DateTime]::UtcNow.ToString('o');externalDirectory=$outside;portable='PASS';perUserInstallAndUninstall=$installResult;
        nativeGpuAndSoftware='PASS';uiCamAndProjectReopen='PASS';diagnosticsAndJfr='PASS';
        camDraftAndDiameterEditing='PASS';perUserUpgrade=$upgradeResult;previousVersion=$previousVersion;
        fullVerification=$(if($manifest.verification){$manifest.verification}else{'NOT_RUN'});
        environment='Windows x64 on build machine, outside repository; no VM/second PC or physical CNC test'}
    [IO.File]::WriteAllText((Join-Path $BuildDirectory 'validation.json'),($report | ConvertTo-Json),[Text.UTF8Encoding]::new($false))
    Write-Host "Beta validation passed: $outside"
} finally {Pop-Location}
