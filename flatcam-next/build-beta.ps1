param(
    [string]$RuntimeJdk,
    [switch]$SkipTests,
    [switch]$AllowDirty,
    [switch]$PortableOnly
)
$ErrorActionPreference='Stop'
$ProgressPreference='SilentlyContinue'
$betaOldPath=$env:PATH
$betaOldMavenOpts=$env:MAVEN_OPTS
function Invoke-Checked([string]$Program,[string[]]$Arguments) {
    $command=Get-Command $Program -ErrorAction Stop
    $callPreference=$ErrorActionPreference
    try {
        # Windows PowerShell 5 wraps native stderr as ErrorRecords, including successful-tool warnings.
        $ErrorActionPreference='Continue'
        & $command.Source @Arguments
        $code=$LASTEXITCODE
    } finally {$ErrorActionPreference=$callPreference}
    if($code -ne 0) { throw "$Program failed: $code" }
}
function Write-Utf8([string]$Path,[string]$Value) {
    [IO.File]::WriteAllText($Path,$Value,[Text.UTF8Encoding]::new($false))
}
function Get-PinnedArchive([string]$Url,[string]$Path,[string]$Sha256) {
    if(!(Test-Path -LiteralPath $Path)) { Invoke-WebRequest -UseBasicParsing -Uri $Url -OutFile $Path }
    if((Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash -ne $Sha256) { throw "Archive checksum mismatch: $Path" }
}
Push-Location $PSScriptRoot
try {
    if(!$IsWindows -and $PSVersionTable.PSEdition -eq 'Core') { throw 'Build this package on Windows x64.' }
    [xml]$pom=Get-Content -LiteralPath pom.xml
    $version=[string]$pom.project.version
    if($version -notmatch '^(\d+)\.(\d+)\.(\d+)-beta\.(\d+)$') { throw 'Expected a beta SemVer in pom.xml.' }
    # Windows Installer has a numeric three-part version. Beta has its own upgrade family.
    $installerVersion="$($Matches[1]).$($Matches[2]).$([int]$Matches[3]*1000+[int]$Matches[4])"
    if(!(Get-Content -LiteralPath 'native-launcher\FlatCAMFX.rc' -Raw).Contains('VALUE "ProductVersion", "'+$version+'\0"')) {
        throw 'Native executable version and Maven version must match.'
    }
    $commit=(& git rev-parse HEAD).Trim()
    if($LASTEXITCODE -ne 0) { throw 'Git revision unavailable.' }
    $dirty=@(& git status --porcelain).Count -ne 0
    if($dirty -and !$AllowDirty) { throw 'Commit the source before releasing. -AllowDirty is only for local packaging development.' }
    $revision=$commit+$(if($dirty){'-dirty'}else{''})
    $tools=Join-Path $PSScriptRoot 'target\beta-tools'
    New-Item -ItemType Directory -Force -Path $tools | Out-Null
    $runtimeUrl='https://github.com/adoptium/temurin25-binaries/releases/download/jdk-25.0.5%2B8/OpenJDK25U-jdk_x64_windows_hotspot_25.0.5_8.zip'
    $runtimeSha='636bdc13dc649ddc7d5921c5c7d23cca6e44fd19d662b45cc9c02135a6c96c42'
    if(!$RuntimeJdk) {
        $archive=Join-Path $tools 'temurin25.0.5_8.zip'
        Get-PinnedArchive $runtimeUrl $archive $runtimeSha
        $RuntimeJdk=Join-Path $tools 'jdk-25.0.5+8'
        if(!(Test-Path -LiteralPath $RuntimeJdk)) { Invoke-Checked 'tar.exe' @('-xf',$archive,'-C',$tools) }
    }
    $RuntimeJdk=(Resolve-Path -LiteralPath $RuntimeJdk).Path
    $runtimeRelease=Get-Content -LiteralPath (Join-Path $RuntimeJdk 'release') -Raw
    if($runtimeRelease -notmatch 'JAVA_VERSION="25\.' -or $runtimeRelease -notmatch 'OS_ARCH="x86_64"') { throw 'Use a Windows x64 JDK 25 runtime source.' }
    if((Get-Content -LiteralPath (Join-Path $RuntimeJdk 'legal\java.base\LICENSE') -Raw) -notmatch 'GNU General Public License') {
        throw 'Bundling policy requires a GPL OpenJDK runtime. The development JDK is not redistributed automatically.'
    }
    $wix=Join-Path $tools 'wix314'
    if(!$PortableOnly) {
        $wixArchive=Join-Path $tools 'wix314-binaries.zip'
        Get-PinnedArchive 'https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip' $wixArchive '6ac824e1642d6f7277d0ed7ea09411a508f6116ba6fae0aa5f2c7daa2ff43d31'
        if(!(Test-Path -LiteralPath (Join-Path $wix 'candle.exe'))) {
            New-Item -ItemType Directory -Force -Path $wix | Out-Null
            Invoke-Checked 'tar.exe' @('-xf',$wixArchive,'-C',$wix)
        }
        $env:PATH="$wix;$env:PATH"
    }
    $env:MAVEN_OPTS="$env:MAVEN_OPTS -XX:+UnlockDiagnosticVMOptions -XX:DisableIntrinsic=_indexOfUL"
    $build=Join-Path $PSScriptRoot ("target\releases\$version-"+[DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss-fff'))
    New-Item -ItemType Directory -Path $build | Out-Null
    if(!$SkipTests) {
        & cmd.exe /d /c ".\mvnw.cmd verify -Dflatcam.build.revision=$revision 2>&1" *> (Join-Path $build 'verify.log')
        if($LASTEXITCODE -ne 0) { throw "Verification failed. See $build\verify.log" }
    }
    # build-native.cmd removes this one generated directory. Verify the target before invoking it.
    $dependency=Join-Path $PSScriptRoot 'flatcam-fx\target\dependency'
    if(Test-Path -LiteralPath $dependency) {
        $item=Get-Item -LiteralPath $dependency
        if($item.FullName -ne $dependency -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Unsafe dependency directory.' }
    }
    & cmd.exe /d /c ".\build-native.cmd -Dflatcam.build.revision=$revision 2>&1" *> (Join-Path $build 'native-build.log')
    if($LASTEXITCODE -ne 0) { throw "Native build failed. See $build\native-build.log" }
    $runtime=Join-Path $build 'runtime'
    $modules='java.se,jdk.jfr,jdk.management,jdk.management.jfr,jdk.unsupported,jdk.charsets,jdk.localedata,jdk.crypto.ec,jdk.crypto.mscapi,jdk.zipfs'
    Invoke-Checked (Join-Path $RuntimeJdk 'bin\jlink.exe') @('--add-modules',$modules,'--strip-debug','--no-header-files','--no-man-pages','--compress=zip-6','--output',$runtime)
    $packageInput=Join-Path $build 'input'
    New-Item -ItemType Directory -Path $packageInput | Out-Null
    Copy-Item -LiteralPath "flatcam-fx\target\flatcam-fx-$version.jar" -Destination $packageInput
    Get-ChildItem -LiteralPath $dependency -Filter '*.jar' | Where-Object {
        $_.Name -notlike 'javafx-*' -or $_.Name -like '*-win.jar'
    } | Copy-Item -Destination $packageInput
    $jpackage=Join-Path $RuntimeJdk 'bin\jpackage.exe'
    $imageParent=Join-Path $build 'image'
    Invoke-Checked $jpackage @('--type','app-image','--name','FlatCAM FX Beta','--app-version',$installerVersion,
        '--input',$packageInput,'--main-jar',"flatcam-fx-$version.jar",'--main-class','org.flatcam.fx.FlatCamLauncher',
        '--runtime-image',$runtime,'--icon',(Join-Path $PSScriptRoot '..\assets\resources\flatcam_icon256.ico'),'--dest',$imageParent)
    $image=Join-Path $imageParent 'FlatCAM FX Beta'
    # Keep the dedicated-GPU exports and process-wide DPI manifest in the executable that loads the JVM.
    $compiler='C:\msys64\ucrt64\bin\g++.exe'
    if(!(Test-Path -LiteralPath $compiler)) { $compiler=(Get-Command g++.exe).Source }
    $env:PATH="$(Split-Path -Parent $compiler);$env:PATH"
    # jpackage copies its launcher with ReadOnly set. Replace only this generated file.
    (Get-Item -LiteralPath (Join-Path $image 'FlatCAM FX Beta.exe')).IsReadOnly=$false
    Invoke-Checked $compiler @('-std=c++17','-O2','-Wall','-Wextra','-municode','-mwindows','-DFLATCAM_GUI_HOST','-static',
        "-I$env:JAVA_HOME\include","-I$env:JAVA_HOME\include\win32",'native-launcher\FlatCAMFX.cpp','target\native\FlatCAMFX-res.o',
        '-lshell32','-o',(Join-Path $image 'FlatCAM FX Beta.exe'))
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot '..\LICENSE') -Destination (Join-Path $image 'LICENSE.txt')
    Copy-Item -LiteralPath "release\BETA-$version.md" -Destination (Join-Path $image 'LEIA-ME-BETA.md')
    Copy-Item -LiteralPath 'USER_GUIDE.md' -Destination $image
    $manifest=[ordered]@{version=$version;channel='beta';installerVersion=$installerVersion;sourceCommit=$commit;
        dirty=$dirty;verification=$(if($SkipTests){'NOT_RUN'}else{'PASS'});builtUtc=[DateTime]::UtcNow.ToString('o');runtime='Temurin 25.0.5+8';runtimeSource=$runtimeUrl;
        runtimeArchiveSha256=$runtimeSha;platform='windows-x64';signed=$false}
    if($PSBoundParameters.ContainsKey('RuntimeJdk')) { $manifest.runtime=$runtimeRelease; $manifest.runtimeSource='provided OpenJDK';$manifest.runtimeArchiveSha256=$null }
    Write-Utf8 (Join-Path $image 'flatcam-release.json') ($manifest | ConvertTo-Json)
    Write-Utf8 (Join-Path $image 'Iniciar em Software.cmd') "@echo off`r`ncd /d `"%~dp0`"`r`nstart `"`" `"%~dp0FlatCAM FX Beta.exe`" --software`r`n"
    $sums=Get-ChildItem -LiteralPath $image -File -Recurse | Sort-Object FullName | ForEach-Object {
        (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()+'  '+$_.FullName.Substring($image.Length+1).Replace('\','/')
    }
    Write-Utf8 (Join-Path $image 'SHA256SUMS.txt') (($sums -join "`n")+"`n")
    $zip=Join-Path $build "FlatCAM-FX-$version-windows-x64.zip"
    Invoke-Checked 'tar.exe' @('-a','-cf',$zip,'-C',$imageParent,'FlatCAM FX Beta')
    if(!$PortableOnly) {
        foreach($kind in @('msi','exe')) {
            Invoke-Checked $jpackage @('--type',$kind,'--name','FlatCAM FX Beta','--app-image',$image,'--app-version',$installerVersion,
                '--vendor','FlatCAM FX contributors','--description',"FlatCAM FX $version - Beta testing",
                '--license-file',(Join-Path $image 'LICENSE.txt'),'--dest',$build,'--install-dir','FlatCAM FX Beta',
                '--win-per-user-install','--win-menu','--win-menu-group','FlatCAM FX Beta','--win-shortcut','--win-dir-chooser',
                '--win-upgrade-uuid','5AE0F49D-3943-495D-81E3-B4B945BE5356')
            $installer=Get-ChildItem -LiteralPath $build -Filter "*.$kind" | Select-Object -First 1
            if(!$installer) {throw "Missing $kind installer"}
            Rename-Item -LiteralPath $installer.FullName -NewName "FlatCAM-FX-$version-windows-x64-setup.$kind"
        }
    }
    $artifacts=Get-ChildItem -LiteralPath $build -File | Where-Object {$_.Extension -in '.zip','.exe','.msi'} | ForEach-Object {
        [ordered]@{file=$_.Name;bytes=$_.Length;sha256=(Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()}
    }
    Write-Utf8 (Join-Path $build 'artifacts.json') ($artifacts | ConvertTo-Json)
    Write-Utf8 (Join-Path $PSScriptRoot 'target\beta-latest.txt') $build
    Write-Host "Beta artifacts: $build"
} finally {
    $env:PATH=$betaOldPath;$env:MAVEN_OPTS=$betaOldMavenOpts
    Pop-Location
}
