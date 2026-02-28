# 🚀 Deploy to Localhost - Complete Guide

## ✅ Yes, it's 100% possible!

Your Tomcat is installed and configured for port 8080. Here's exactly what to do:

---

## 📋 Step-by-Step Deployment

### Step 1: Start Tomcat (As Administrator)

**Option A: Using Services**
1. Press `Win + R`
2. Type: `services.msc`
3. Press Enter
4. Find "Apache Tomcat 11.0 Tomcat11"
5. Right-click → Start
6. Wait for status to show "Running"

**Option B: Using Command Prompt**
1. Press `Win + X`
2. Select "Command Prompt (Admin)" or "PowerShell (Admin)"
3. Type: `net start Tomcat11`
4. Press Enter

---

### Step 2: Deploy Your Application

**Copy these files manually:**

1. **Open File Explorer as Admin:**
   - Press `Win + R`
   - Type: `explorer`
   - Press `Ctrl + Shift + Enter`
   - Click "Yes"

2. **Navigate to your project:**
   - Go to: `C:\Users\boltr\Desktop\New folder\webapp`

3. **Copy the entire webapp folder:**
   - Select the `webapp` folder
   - Press `Ctrl + C`

4. **Navigate to Tomcat:**
   - Go to: `C:\Program Files\Apache Software Foundation\Tomcat 11.0\webapps`

5. **Paste and rename:**
   - Press `Ctrl + V`
   - Rename the pasted folder from `webapp` to `portfolio`

---

### Step 3: Restart Tomcat

1. Go back to Services (`services.msc`)
2. Find "Apache Tomcat 11.0 Tomcat11"
3. Right-click → Restart
4. Wait 10-15 seconds

---

### Step 4: Access Your Application

Open your browser and go to:

**http://localhost:8080/portfolio/portfolio?action=view**

---

## 🎯 What You'll See

Your portfolio tracker will load with:
- 💰 Total Investment card
- 📊 Current Value card
- 📈 Profit/Loss card
- 📋 Stock table
- ➕ Add Stock button
- 🔄 Refresh Prices button
- Modern dark theme UI

---

## 🔍 Verify Deployment

Check if this file exists:
```
C:\Program Files\Apache Software Foundation\Tomcat 11.0\webapps\portfolio\WEB-INF\web.xml
```

If yes, deployment is successful!

---

## 🐛 Troubleshooting

### "404 Not Found"
- Wait 15-20 seconds after restart
- Check if folder is named `portfolio` (not `webapp`)
- Try: http://localhost:8080/portfolio/

### "Service won't start"
- Check if port 8080 is free
- Check Tomcat logs: `C:\Program Files\Apache Software Foundation\Tomcat 11.0\logs\catalina.log`

### "Access Denied"
- Make sure you opened File Explorer as Administrator
- Use `Ctrl + Shift + Enter` when opening Explorer

### "Can't copy files"
- Close any programs that might be using the files
- Make sure Tomcat service is stopped before copying
- Try copying again

---

## ✨ Alternative: Automated Script

If manual copying is tedious, run this in **PowerShell as Administrator**:

```powershell
# Stop Tomcat
Stop-Service Tomcat11

# Copy webapp
Copy-Item "C:\Users\boltr\Desktop\New folder\webapp" "C:\Program Files\Apache Software Foundation\Tomcat 11.0\webapps\portfolio" -Recurse -Force

# Start Tomcat
Start-Service Tomcat11

# Wait
Start-Sleep -Seconds 10

Write-Host "Done! Visit: http://localhost:8080/portfolio/portfolio?action=view"
```

---

## 🎉 Success!

Once deployed, your portfolio tracker will be running on **localhost:8080** with full functionality:
- Add stocks
- Refresh prices from API
- View profit/loss
- Database persistence
- Modern web UI

Everything works on localhost! 🌐
