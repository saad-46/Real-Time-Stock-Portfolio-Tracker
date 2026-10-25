<#
.SYNOPSIS
  Starts StockVault from the built JAR, from any folder.

.DESCRIPTION
  Normal mode (default) uses your real database and configuration (.env / stockvault.local.properties), exactly like
  `java -jar target\stock-portfolio-tracker-1.0.0-SNAPSHOT.jar`.

  -Diagnostic uses a brand-new database in a unique folder under %TEMP% and points the configuration at files that do
  not exist, so no API keys, e-mail or real data are involved. The folder is removed after the program exits.

.EXAMPLE
  .\run-stockvault.ps1                 # normal start (landing page, then sign-in)
  .\run-stockvault.ps1 -Diagnostic     # disposable database, no credentials
  .\run-stockvault.ps1 -Mode Dashboard # Landing | Home (no sign-in) | Dashboard | Classic
#>
param(
    [switch]$Diagnostic,
    [ValidateSet('Landing', 'Home', 'Dashboard', 'Classic', 'Demo')][string]$Mode = 'Landing'
)

$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$jar = Join-Path $root 'target\stock-portfolio-tracker-1.0.0-SNAPSHOT.jar'

function Fail($message) {
    Write-Host "StockVault cannot start: $message" -ForegroundColor Red
    exit 1
}

if (-not (Test-Path -LiteralPath $jar)) {
    Fail "the program file is missing: $jar`n  Build it first (mvn -o clean verify), or restore the target folder."
}
if (-not (Test-Path -LiteralPath (Join-Path $root 'target\lib'))) {
    Fail "the folder target\lib (libraries) must stay next to the JAR, but it is missing."
}

# Find a Java 17+ runtime: JAVA_HOME, then PATH, then the usual install folders. The program needs Java 17 or newer;
# an older java earlier on PATH (e.g. Java 8) fails with an "UnsupportedClassVersionError" and no window.
function Get-JavaMajor($exe) {
    $ErrorActionPreference = 'Continue' # java -version writes to stderr, which PowerShell 5 would treat as an error
    try {
        $text = (& $exe -version 2>&1 | Out-String)
        if ($text -match 'version "(\d+)(?:\.(\d+))?') {
            $major = [int]$Matches[1]
            if ($major -eq 1 -and $Matches[2]) { $major = [int]$Matches[2] }
            return $major
        }
    } catch { }
    return 0
}

$candidates = @()
if ($env:JAVA_HOME) { $candidates += (Join-Path $env:JAVA_HOME 'bin\java.exe') }
$onPath = Get-Command java -All -ErrorAction SilentlyContinue
if ($onPath) { $candidates += $onPath.Source }
foreach ($base in @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:ProgramFiles\Microsoft", "$env:ProgramFiles\Zulu")) {
    if (Test-Path $base) {
        $candidates += (Get-ChildItem $base -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending |
            ForEach-Object { Join-Path $_.FullName 'bin\java.exe' })
    }
}
$java = $null
foreach ($c in $candidates) {
    if ($c -and (Test-Path -LiteralPath $c) -and (Get-JavaMajor $c) -ge 17) { $java = $c; break }
}
if (-not $java) { Fail "no Java 17 or newer was found (JAVA_HOME, PATH and the usual install folders were checked)." }

$javaArgs = @()
$diagDir = $null
if ($Diagnostic) {
    $diagDir = Join-Path $env:TEMP ('stockvault-diag-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
    New-Item -ItemType Directory -Path $diagDir | Out-Null
    $javaArgs += "-Dportfolio.db.path=$(Join-Path $diagDir 'diagnostic.db')"
    $javaArgs += "-Dstockvault.env=$(Join-Path $diagDir 'no-env-file')"
    $javaArgs += "-Dstockvault.config=$(Join-Path $diagDir 'no-config-file')"
    $javaArgs += "-Dstockvault.ui=$(Join-Path $diagDir 'ui.properties')"
    $javaArgs += "-Dstockvault.marketcache=$(Join-Path $diagDir 'market-cache.properties')"
    Write-Host "DIAGNOSTIC mode: disposable database and no credentials in $diagDir" -ForegroundColor Yellow
} else {
    Write-Host 'Normal mode: using your real database and configuration.' -ForegroundColor Cyan
}
$javaArgs += '-jar', $jar
if ($Mode -eq 'Home') { $javaArgs += '--home' }
if ($Mode -eq 'Dashboard') { $javaArgs += '--dashboard' }
if ($Mode -eq 'Classic') { $javaArgs += '--classic' }
if ($Mode -eq 'Demo') { $javaArgs += '--demo' } # sample data in its own temporary folder; no keys, no network, nothing real is touched

Write-Host "Java: $java"
Set-Location -LiteralPath $root
try {
    & $java @javaArgs
    $code = $LASTEXITCODE
} finally {
    # remove only the unique folder this run created
    if ($diagDir -and $diagDir.StartsWith($env:TEMP, [StringComparison]::OrdinalIgnoreCase) -and
        (Split-Path $diagDir -Leaf).StartsWith('stockvault-diag-') -and (Test-Path -LiteralPath $diagDir)) {
        Remove-Item -LiteralPath $diagDir -Recurse -Force -ErrorAction SilentlyContinue
    }
}
if ($code -ne 0) { Write-Host "StockVault exited with code $code." -ForegroundColor Red }
exit $code
