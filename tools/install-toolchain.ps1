# Réinstalle la chaîne de compilation portable dans ..\toolchain (JDK 17, Gradle 8.7, SDK Android 34).
# En lançant ce script, vous acceptez les licences du SDK Android (https://developer.android.com/studio/terms).
$ErrorActionPreference = 'Stop'; $ProgressPreference = 'SilentlyContinue'
$root = Split-Path $PSScriptRoot
$t = Join-Path $root 'toolchain'; $dl = "$t\dl"
New-Item -ItemType Directory -Force $dl | Out-Null
function Get($u, $f) { if (-not (Test-Path "$dl\$f")) { Invoke-WebRequest $u -OutFile "$dl\$f" -UseBasicParsing } }
Get 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' 'jdk17.zip'
Get 'https://services.gradle.org/distributions/gradle-8.7-bin.zip' 'gradle.zip'
Get 'https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip' 'cmdtools.zip'
if (-not (Test-Path "$t\jdk")) { Expand-Archive "$dl\jdk17.zip" "$t\jdk-tmp"; Move-Item (Get-ChildItem "$t\jdk-tmp")[0].FullName "$t\jdk" }
if (-not (Test-Path "$t\gradle")) { Expand-Archive "$dl\gradle.zip" "$t\g-tmp"; Move-Item (Get-ChildItem "$t\g-tmp")[0].FullName "$t\gradle" }
if (-not (Test-Path "$t\sdk\cmdline-tools\latest")) {
    Expand-Archive "$dl\cmdtools.zip" "$t\c-tmp"
    New-Item -ItemType Directory "$t\sdk\cmdline-tools" -Force | Out-Null
    Move-Item "$t\c-tmp\cmdline-tools" "$t\sdk\cmdline-tools\latest"
}
$env:JAVA_HOME = "$t\jdk"; $env:ANDROID_HOME = "$t\sdk"
$sm = "$t\sdk\cmdline-tools\latest\bin\sdkmanager.bat"
(1..40 | ForEach-Object { 'y' }) | & $sm --licenses | Out-Null
& $sm "platforms;android-34" "build-tools;34.0.0" "platform-tools" "ndk;26.1.10909125" "cmake;3.22.1" | Out-Null   # NDK et CMake : code natif des décodeurs (FT8, FreeDV RADE)
$sdk = ("$t\sdk").Replace('\', '\\').Replace(':', '\:')
"sdk.dir=$sdk" | Set-Content "$root\local.properties"
'Chaine installee. (les dossiers jdk-tmp, g-tmp et c-tmp, s''ils restent, peuvent etre supprimes)'
