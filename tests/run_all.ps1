param(
    [switch]$SkipAndroid
)

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$rustDir = Join-Path $root "app/src/main/rust"

function Step($Name) {
    Write-Host ""
    Write-Host "==> $Name" -ForegroundColor Cyan
}

function HasCommand($Name) {
    $null -ne (Get-Command $Name -ErrorAction SilentlyContinue)
}

Set-Location $root

Step "Repository hygiene"
if (HasCommand "rg") {
    $matches = rg -n "wireshark|Wireshark|nmap|Nmap|NetSentry|netsentry|Gemini|firebase" app/src/main README.md docs LICENSE metadata.json
    if ($LASTEXITCODE -eq 0) {
        Write-Host $matches
        throw "Repository hygiene scan found blocked legacy references."
    }
    if ($LASTEXITCODE -gt 1) {
        throw "Repository hygiene scan failed."
    }
} else {
    Write-Warning "ripgrep is not installed; skipping repository text scan."
}

Step "Rust formatting"
Set-Location $rustDir
if (HasCommand "cargo") {
    cargo fmt -- --check
    cargo check
    cargo test
} else {
    throw "cargo is required for Rust checks."
}

Set-Location $root

if (-not $SkipAndroid) {
    Step "Android Gradle checks"
    $hasSdk = $env:ANDROID_HOME -or $env:ANDROID_SDK_ROOT
    if (-not $hasSdk -and (Test-Path (Join-Path $root "local.properties"))) {
        $sdkLine = Select-String -Path (Join-Path $root "local.properties") -Pattern "^sdk.dir=(.+)$" -ErrorAction SilentlyContinue
        if ($sdkLine) {
            $sdkPath = $sdkLine.Matches[0].Groups[1].Value -replace "\\\\", "\"
            $hasSdk = Test-Path $sdkPath
        }
    }

    if ($hasSdk) {
        if (Test-Path (Join-Path $root "gradlew.bat")) {
            & (Join-Path $root "gradlew.bat") ":app:compileDebugKotlin" ":app:testDebugUnitTest"
        } elseif (HasCommand "gradle") {
            gradle ":app:compileDebugKotlin" ":app:testDebugUnitTest"
        } else {
            throw "Gradle is not installed and no Gradle wrapper is present."
        }
    } else {
        Write-Warning "Android SDK not configured; skipping Android Gradle checks."
    }
}

Write-Host ""
Write-Host "All available checks passed." -ForegroundColor Green
