# 🚀 START HERE - Portfolio Tracker

## Welcome! Choose Your Path:

### 🖥️ Path 1: Desktop Application (Easiest)
**Best for**: Quick start, all features working immediately

```bash
java -cp ".;lib/*" com.portfolio.MainUI
```

**You get**:
- ✅ Modern dark theme UI
- ✅ Real-time stock prices
- ✅ Database persistence
- ✅ Charts and visualizations
- ✅ Works immediately

---

### 🌐 Path 2: Web Application (Tomcat)
**Best for**: Learning servlets, JSP, web deployment

**Steps**:
1. Right-click `deploy-tomcat11.bat`
2. Select "Run as administrator"
3. Wait 10 seconds
4. Open: http://localhost:8080/portfolio/portfolio?action=view

**You get**:
- ✅ Web-based interface
- ✅ Jakarta Servlets (Tomcat 11)
- ✅ JSP dynamic pages
- ✅ HTTP request/response handling
- ✅ Session management

---

## 📚 Documentation

- **README.md** - Main overview
- **PROJECT-STRUCTURE.md** - Code organization
- **docs/DEPLOY-NOW.md** - Tomcat deployment details
- **docs/DATABASE-README.md** - Database guide
- **docs/UI-README.md** - Desktop UI guide

---

## 🎯 What This Project Demonstrates

### Core Java:
- ✅ Object-Oriented Programming
- ✅ Collections Framework (ArrayList, List)
- ✅ Exception Handling (try-catch)
- ✅ Multi-threading (SwingWorker)
- ✅ File I/O

### Database:
- ✅ JDBC connectivity
- ✅ SQL queries (CREATE, INSERT, SELECT, UPDATE)
- ✅ DAO pattern
- ✅ Connection management
- ✅ PreparedStatement (SQL injection prevention)

### Web Development:
- ✅ Jakarta Servlets (Tomcat 11)
- ✅ JSP (JavaServer Pages)
- ✅ HTTP GET/POST requests
- ✅ Session management
- ✅ MVC pattern
- ✅ Web deployment (WAR)

### GUI:
- ✅ Swing components
- ✅ Event handling
- ✅ Custom painting
- ✅ Layout managers
- ✅ Modern UI design

### Integration:
- ✅ REST API consumption (Alpha Vantage)
- ✅ JSON parsing
- ✅ HTTP client
- ✅ Real-time data

---

## 🔧 Quick Commands

### Desktop App:
```bash
java -cp ".;lib/*" com.portfolio.MainUI
```

### Web App:
```bash
# Deploy (as Administrator)
deploy-tomcat11.bat

# Access
http://localhost:8080/portfolio/portfolio?action=view
```

### Compile (if needed):
```bash
javac -encoding UTF-8 -cp "lib/*" -d . src/com/portfolio/**/*.java
```

---

## 💡 First Time Setup

1. **Check Java**: `java -version` (need JDK 8+)
2. **Try Desktop**: `java -cp ".;lib/*" com.portfolio.MainUI`
3. **Add a stock**: Click "Add Stock", enter AAPL, 10, 150
4. **See it work**: Stock appears in table with real-time price!

---

## 🎉 You're Ready!

Everything is compiled and ready to run. Just choose your path above and start!

For detailed guides, check the `docs/` folder.

**Happy coding! 💼📊**
