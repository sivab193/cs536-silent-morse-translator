param(
    [Parameter(Mandatory = $true)][string]$GlassesSerial,
    [Parameter(Mandatory = $true)][string]$PhoneSerial
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot
$adbCommand = Get-Command adb -ErrorAction SilentlyContinue
$adb = if ($adbCommand) { $adbCommand.Source } else { "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" }
if (-not (Test-Path $adb)) { throw "ADB was not found. Install Android SDK Platform-Tools or add adb to PATH." }

if (-not $env:JAVA_HOME) {
    $javaHomes = @(
        (Get-ChildItem "C:\Program Files\Eclipse Adoptium" -Directory -Filter "jdk-17*" -ErrorAction SilentlyContinue |
            Select-Object -First 1 -ExpandProperty FullName),
        "C:\Program Files\Android\Android Studio\jbr"
    ) | Where-Object { $_ -and (Test-Path (Join-Path $_ "bin\java.exe")) }
    if (-not $javaHomes) { throw "JDK 17 was not found. Install it or set JAVA_HOME." }
    $env:JAVA_HOME = $javaHomes[0]
}

Push-Location $repoRoot
try {
    & .\gradlew.bat testDebugUnitTest assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Build failed." }

    & $adb -s $GlassesSerial install -r app\build\outputs\apk\debug\app-debug.apk
    if ($LASTEXITCODE -ne 0) { throw "Glasses APK installation failed." }

    & $adb -s $PhoneSerial install -r phone-companion\build\outputs\apk\debug\phone-companion-debug.apk
    if ($LASTEXITCODE -ne 0) { throw "Phone APK installation failed." }

    Write-Host "Installed Silent Morse on the glasses and the companion on the phone."
} finally {
    Pop-Location
}
