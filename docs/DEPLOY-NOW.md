# 🚀 Deploy to Tomcat 11 - Final Steps

## ✅ What's Done:
- ✅ Servlet updated for Jakarta EE (Tomcat 11)
- ✅ All classes compiled successfully
- ✅ JAR files copied to webapp
- ✅ Tomcat 11 is running

## 📋 Final Step: Deploy

### Right-click on `deploy-tomcat11.bat` and select "Run as Administrator"

This will:
1. Stop Tomcat service
2. Copy your webapp to Tomcat
3. Start Tomcat service
4. Wait 10 seconds

### Then open your browser:
**http://localhost:8080/portfolio/portfolio?action=view**

---

## ⚠️ Important: Run as Administrator

The batch file MUST be run as Administrator because:
- Tomcat is installed in Program Files (protected folder)
- We need to stop/start the Tomcat service
- We need to copy files to Program Files

### How to Run as Administrator:
1. Find `deploy-tomcat11.bat` in your project folder
2. RIGHT-CLICK on it
3. Select "Run as administrator"
4. Click "Yes" when Windows asks for permission

---

## 🎉 What You'll See

Once deployed, your portfolio tracker will have:

- 💰 Total Investment card
- 📊 Current Value card
- 📈 Profit/Loss card
- 📋 Stock table with all holdings
- ➕ Add Stock button (modal form)
- 🔄 Refresh Prices button
- Modern dark theme UI

---

## 🐛 If Something Goes Wrong

### "Access Denied"
- Make sure you ran as Administrator
- Right-click → "Run as administrator"

### "Service not found"
- Tomcat service is running (we confirmed this)
- Just run the batch file as admin

### "404 Not Found"
- Wait 15-20 seconds for Tomcat to fully start
- Check URL: `http://localhost:8080/portfolio/portfolio?action=view`
- Try just: `http://localhost:8080/portfolio`

### Still not working?
Check Tomcat logs at:
`C:\Program Files\Apache Software Foundation\Tomcat 11.0\logs\catalina.log`

---

## 🎯 Quick Test

After deployment, test these:

1. **View Portfolio**: Should show summary cards and table
2. **Add Stock**: Click button, enter AAPL, 10, 150.00
3. **Refresh Prices**: Click button, wait for API calls
4. **Database**: Add stocks, restart Tomcat, stocks still there

---

## ✨ You're Almost There!

Just run `deploy-tomcat11.bat` as Administrator and you're done!

Your portfolio tracker will be running on localhost with full servlet/JSP functionality! 🎊
