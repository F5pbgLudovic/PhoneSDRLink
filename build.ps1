# Compile PhoneSDRLink : .\build.ps1  (APK signé dans dist\PhoneSDRLink-<version>.apk)
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$env:JAVA_HOME = "$root\toolchain\jdk"
Set-Location $root
& "$root\toolchain\gradle\bin\gradle.bat" --no-daemon -q assembleRelease
if ($LASTEXITCODE -ne 0) { throw 'Echec de la compilation' }
$ver = ([regex]::Match((Get-Content "$root\app\build.gradle" -Raw), "versionName '([^']+)'")).Groups[1].Value
New-Item -ItemType Directory -Force "$root\dist" | Out-Null
Copy-Item "$root\app\build\outputs\apk\release\app-release.apk" "$root\dist\PhoneSDRLink-$ver.apk" -Force
"OK : dist\PhoneSDRLink-$ver.apk"
