<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<!DOCTYPE html>
<html>
<head>
    <title>Chart Test</title>
</head>
<body>
    <%@ include file="/WEB-INF/includes/sidebar.jsp" %>
    
    <div class="main-content" id="mainContent">
        <div style="padding: 30px;">
            <h1>Chart Test Page</h1>
            
            <div class="test-box" style="background: #2a2a2a; padding: 30px; border-radius: 10px; margin: 20px 0;">
                <h2>✅ Page Loaded Successfully</h2>
                <p>Symbol: <%= request.getAttribute("symbol") %></p>
                <p>Data exists: <%= request.getAttribute("historicalData") != null ? "Yes" : "No" %></p>
                <% if (request.getAttribute("historicalData") != null) { %>
                    <p>Data length: <%= ((String)request.getAttribute("historicalData")).length() %></p>
                    <p>First 100 chars: <%= ((String)request.getAttribute("historicalData")).substring(0, Math.min(100, ((String)request.getAttribute("historicalData")).length())) %></p>
                <% } %>
            </div>
        </div>
    </div>
</body>
</html>
