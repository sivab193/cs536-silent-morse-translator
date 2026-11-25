$ErrorActionPreference = "Stop"

$repoRoot = Split-Path -Parent $PSScriptRoot
$gradle = Join-Path $repoRoot "gradlew.bat"
$python = Join-Path $repoRoot ".venv\Scripts\python.exe"

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
    & $gradle testDebugUnitTest assembleDebug
    if ($LASTEXITCODE -ne 0) { throw "Gradle verification failed." }

    if (Test-Path $python) {
        & $python -m unittest discover -s prototype\python -p "test_*.py"
    } else {
        python -m unittest discover -s prototype\python -p "test_*.py"
    }
    if ($LASTEXITCODE -ne 0) { throw "Python verification failed." }

    Write-Host "Verification passed: Android tests, Python tests, and both debug APK builds."
} finally {
    Pop-Location
}
