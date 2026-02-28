# FINAL DEPLOYMENT SCRIPT
# Right-click this file → "Run with PowerShell"
# Click "Yes" when asked for admin permission

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Portfolio Tracker - Localhost Deploy  " -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Paths
$projectPath = $PSScriptRoot
$webappSource = Join-Path $projectPath "webapp"
$tomcatHome = "C:\Program Files\Apache Software Foundation\Tomcat 11.0"
$webappsPath = Join-Path $tomcatHome "webapps"
$portfolioPath = Join-Path $webappsPath "portfolio"

# Check admin
$isAdmin = ([Security.Principal.WindowsPrincipal] [Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)

if (-not $isAdmin) {
    Write-Host "⚠️  This script needs Administrator privileges!" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Please:" -ForegroundColor White
    Write-Host "1. Right-click this file" -ForegroundColor White
    Write-Host "2. Select 'Run with PowerShell'" -ForegroundColor White
    Write-Host "3. Click 'Yes' when Windows asks" -ForegroundColor White
    Write-Host ""
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host "✅ Running as Administrator" -ForegroundColor Green
Write-Host ""

# Check Tomcat
if (-not (Test-Path $tomcatHome)) {
    Write-Host "❌ Tomcat not found at: $tomcatHome" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host "✅ Found Tomcat at: $tomcatHome" -ForegroundColor Green
Write-Host ""

# Stop Tomcat
Write-Host "🛑 Stopping Tomcat..." -ForegroundColor Yellow
try {
    Stop-Service -Name "Tomcat11" -ErrorAction Stop
    Write-Host "✅ Tomcat stopped" -ForegroundColor Green
} catch {
    Write-Host "⚠️  Tomcat was already stopped" -ForegroundColor Yellow
}
Start-Sleep -Seconds 2
Write-Host ""

# Remove old deployment
if (Test-Path $portfolioPath) {
    Write-Host "🗑️  Removing old deployment..." -ForegroundColor Yellow
    Remove-Item -Path $portfolioPath -Recurse -Force
    Write-Host "✅ Old deployment removed" -ForegroundColor Green
    Write-Host ""
}

# Copy webapp
Write-Host "📦 Deploying application..." -ForegroundColor Yellow
try {
    Copy-Item -Path $webappSource -Destination $portfolioPath -Recurse -Force
    Write-Host "✅ Application deployed successfully!" -ForegroundColor Green
} catch {
    Write-Host "❌ Deployment failed: $_" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}
Write-Host ""

# Start Tomcat
Write-Host "🚀 Starting Tomcat..." -ForegroundColor Yellow
try {
    Start-Service -Name "Tomcat11" -ErrorAction Stop
    Write-Host "✅ Tomcat started" -ForegroundColor Green
} catch {
    Write-Host "❌ Failed to start Tomcat: $_" -ForegroundColor Red
    Read-Host "Press Enter to exit"
    exit 1
}
Write-Host ""

# Wait for startup
Write-Host "⏳ Waiting for Tomcat to initialize (15 seconds)..." -ForegroundColor Cyan
for ($i = 15; $i -gt 0; $i--) {
    Write-Host "   $i seconds remaining..." -ForegroundColor Gray
    Start-Sleep -Seconds 1
}
Write-Host ""

# Success
Write-Host "========================================" -ForegroundColor Green
Write-Host "  🎉 DEPLOYMENT SUCCESSFUL! 🎉" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "Your portfolio tracker is now running on localhost!" -ForegroundColor White
Write-Host ""
Write-Host "📍 Open your browser and visit:" -ForegroundColor Cyan
Write-Host ""
Write-Host "   http://localhost:8080/portfolio/portfolio?action=view" -ForegroundColor Yellow
Write-Host ""
Write-Host "Or try:" -ForegroundColor Cyan
Write-Host "   http://localhost:8080/portfolio/" -ForegroundColor Yellow
Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

Read-Host "Press Enter to exit"
