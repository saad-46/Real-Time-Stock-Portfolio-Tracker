# 📂 Project Structure

## Clean & Organized Layout

```
portfolio-tracker/
│
├── 📁 src/com/portfolio/          # Source code
│   ├── 📁 model/                  # Data models
│   │   ├── Stock.java
│   │   ├── PortfolioItem.java
│   │   └── Transaction.java
│   │
│   ├── 📁 service/                # Business logic
│   │   ├── StockPriceService.java (interface)
│   │   ├── AlphaVantageService.java
│   │   ├── PortfolioService.java
│   │   └── StockValidator.java
│   │
│   ├── 📁 database/               # Data access layer
│   │   ├── DatabaseManager.java
│   │   └── PortfolioDAO.java
│   │
│   ├── 📁 ui/                     # Desktop GUI
│   │   ├── ModernPortfolioUI.java
│   │   ├── ModernChartWindow.java
│   │   ├── PortfolioUI.java
│   │   └── ChartWindow.java
│   │
│   ├── 📁 servlet/                # Web layer
│   │   └── PortfolioServlet.java
│   │
│   ├── Main.java                  # Console application
│   ├── MainUI.java                # GUI launcher
│   └── WebServer.java             # Simple web server
│
├── 📁 webapp/                     # Web application
│   ├── index.html                 # Landing page
│   └── 📁 WEB-INF/
│       ├── web.xml                # Servlet configuration
│       ├── portfolio.jsp          # Main web page
│       ├── 📁 classes/            # Compiled .class files
│       │   └── com/portfolio/...
│       └── 📁 lib/                # JAR libraries
│           ├── jfreechart-1.5.4.jar
│           ├── sqlite-jdbc-3.45.1.0.jar
│           ├── slf4j-api-2.0.9.jar
│           ├── slf4j-simple-2.0.9.jar
│           └── jakarta.servlet-api-6.0.0.jar
│
├── 📁 lib/                        # Development libraries
│   ├── jfreechart-1.5.4.jar
│   ├── sqlite-jdbc-3.45.1.0.jar
│   ├── slf4j-api-2.0.9.jar
│   ├── slf4j-simple-2.0.9.jar
│   └── jakarta.servlet-api-6.0.0.jar
│
├── 📁 docs/                       # Documentation
│   ├── DEPLOY-NOW.md              # Tomcat deployment
│   ├── DATABASE-README.md         # Database guide
│   ├── UI-README.md               # Desktop UI guide
│   ├── PROJECT-SUMMARY.md         # Complete overview
│   ├── QUICK-START.md             # Quick start
│   ├── API-TROUBLESHOOTING.md     # API issues
│   └── README.md                  # Main documentation
│
├── 📁 com/portfolio/              # Compiled classes (for desktop)
│   ├── model/
│   ├── service/
│   ├── database/
│   ├── ui/
│   └── servlet/
│
├── 📁 bin/                        # Additional compiled files
│
├── deploy-tomcat11.bat            # Tomcat deployment script
├── portfolio.db                   # SQLite database
├── README.md                      # Main README
└── PROJECT-STRUCTURE.md           # This file
```

---

## 🎯 Key Directories

### `/src` - Source Code
All Java source files organized by layer:
- **model**: Data structures (Stock, PortfolioItem, Transaction)
- **service**: Business logic and API integration
- **database**: JDBC and DAO pattern
- **ui**: Swing GUI components
- **servlet**: Web layer (Jakarta EE)

### `/webapp` - Web Application
Ready-to-deploy web application:
- **WEB-INF/classes**: Compiled servlet classes
- **WEB-INF/lib**: Runtime JAR dependencies
- **WEB-INF/web.xml**: Servlet configuration
- **WEB-INF/portfolio.jsp**: Dynamic web page

### `/lib` - Libraries
External dependencies:
- JFreeChart for charts
- SQLite JDBC driver
- SLF4J for logging
- Jakarta Servlet API for Tomcat 11

### `/docs` - Documentation
All documentation files organized in one place

---

## 🔄 Build Process

### Desktop Application:
```bash
# Compile
javac -encoding UTF-8 -cp "lib/*" -d . src/com/portfolio/**/*.java

# Run
java -cp ".;lib/*" com.portfolio.MainUI
```

### Web Application:
```bash
# Compile
javac -encoding UTF-8 -cp "lib/*" -d webapp/WEB-INF/classes src/com/portfolio/**/*.java

# Deploy
deploy-tomcat11.bat (as Administrator)
```

---

## 📦 Deployment Artifacts

### Desktop:
- Compiled classes in `/com/portfolio/`
- Libraries in `/lib/`
- Database file: `portfolio.db`

### Web:
- Complete webapp in `/webapp/`
- Deployed to Tomcat's webapps folder
- Accessible at: http://localhost:8080/portfolio

---

## 🎨 Design Patterns Used

- **MVC**: Model-View-Controller separation
- **DAO**: Data Access Object for database
- **Service Layer**: Business logic isolation
- **Singleton**: DatabaseManager connection
- **Factory**: Service creation

---

## 📊 Data Flow

```
User Interface (Swing/JSP)
    ↓
Controller (MainUI/Servlet)
    ↓
Service Layer (PortfolioService)
    ↓
DAO Layer (PortfolioDAO)
    ↓
Database (SQLite)
```

---

This structure follows enterprise Java best practices and makes the codebase easy to navigate and maintain!
