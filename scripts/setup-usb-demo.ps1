param(
    [Parameter(Mandatory = $true)][string]$GlassesSerial,
    [Parameter(Mandatory = $true)][string]$PhoneSerial
)

$ErrorActionPreference = "Stop"

& adb -s $PhoneSerial get-state | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Phone is not available through ADB." }
& adb -s $GlassesSerial get-state | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Glasses are not available through ADB." }

# Phone localhost:1235 -> laptop localhost:1235 (optional local VLM).
& adb -s $PhoneSerial reverse tcp:1235 tcp:1235
# Laptop localhost:8765 -> phone companion, then glasses localhost:8765 -> laptop.
& adb -s $PhoneSerial forward tcp:8765 tcp:8765
& adb -s $GlassesSerial reverse tcp:8765 tcp:8765

Write-Host "USB demo routing is ready."
Write-Host "Phone model base URL: http://127.0.0.1:1235"
Write-Host "Glasses companion URL: http://127.0.0.1:8765"
