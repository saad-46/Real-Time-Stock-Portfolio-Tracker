@echo off
echo ========================================
echo   StockVault Premium Dashboard
echo   Pure Java - No HTML/CSS/JS
echo ========================================
echo.
echo Starting application...
echo.

java --enable-native-access=ALL-UNNAMED -cp ".;lib/*" com.portfolio.ui.PremiumStockDashboard

echo.
echo Application closed.
pause
