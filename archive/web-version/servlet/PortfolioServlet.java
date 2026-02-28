package com.portfolio.servlet;

import com.portfolio.model.*;
import com.portfolio.service.*;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.util.List;

public class PortfolioServlet extends HttpServlet {
    
    private PortfolioService portfolioService;
    
    @Override
    public void init() throws ServletException {
        StockPriceService priceService = new AlphaVantageService();
        this.portfolioService = new PortfolioService(priceService);
        System.out.println("✅ Portfolio Servlet initialized!");
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        String action = request.getParameter("action");
        
        if (action == null || action.equals("view")) {
            showPortfolio(request, response);
        } else if (action.equals("dashboard")) {
            showDashboard(request, response);
        } else if (action.equals("refresh")) {
            refreshPrices(request, response);
        } else if (action.equals("market")) {
            showMarket(request, response);
        } else if (action.equals("chart")) {
            showChart(request, response);
        } else if (action.equals("watchlist")) {
            showWatchlist(request, response);
        } else if (action.equals("transactions")) {
            showTransactions(request, response);
        } else if (action.equals("analytics")) {
            showAnalytics(request, response);
        } else if (action.equals("news")) {
            showNews(request, response);
        } else if (action.equals("settings")) {
            showSettings(request, response);
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        String action = request.getParameter("action");
        
        if (action != null && action.equals("add")) {
            addStock(request, response);
        }
    }
    
    private void showPortfolio(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        List<PortfolioItem> items = portfolioService.getPortfolioItems();
        double totalInvestment = portfolioService.calculateTotalInvestment();
        double currentValue = portfolioService.calculateCurrentValue();
        double profitLoss = portfolioService.calculateProfitLoss();
        
        request.setAttribute("portfolioItems", items);
        request.setAttribute("totalInvestment", totalInvestment);
        request.setAttribute("currentValue", currentValue);
        request.setAttribute("profitLoss", profitLoss);
        
        RequestDispatcher dispatcher = request.getRequestDispatcher("/WEB-INF/portfolio.jsp");
        dispatcher.forward(request, response);
    }
    
    private void showDashboard(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        List<PortfolioItem> items = portfolioService.getPortfolioItems();
        double totalInvestment = portfolioService.calculateTotalInvestment();
        double currentValue = portfolioService.calculateCurrentValue();
        double profitLoss = portfolioService.calculateProfitLoss();
        
        request.setAttribute("portfolioItems", items);
        request.setAttribute("totalInvestment", totalInvestment);
        request.setAttribute("currentValue", currentValue);
        request.setAttribute("profitLoss", profitLoss);
        
        RequestDispatcher dispatcher = request.getRequestDispatcher("/WEB-INF/dashboard.jsp");
        dispatcher.forward(request, response);
    }
    
    private void addStock(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        try {
            String symbol = request.getParameter("symbol").toUpperCase();
            int quantity = Integer.parseInt(request.getParameter("quantity"));
            double price = Double.parseDouble(request.getParameter("price"));
            
            portfolioService.buy(symbol, quantity, price);
            response.sendRedirect("portfolio?action=view");
            
        } catch (Exception e) {
            request.setAttribute("error", "Error adding stock: " + e.getMessage());
            showPortfolio(request, response);
        }
    }
    
    private void refreshPrices(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        try {
            portfolioService.updateAllPrices();
            response.sendRedirect("portfolio?action=view");
        } catch (Exception e) {
            request.setAttribute("error", "Error refreshing prices: " + e.getMessage());
            showPortfolio(request, response);
        }
    }
    
    private void showMarket(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        RequestDispatcher dispatcher = request.getRequestDispatcher("/WEB-INF/market.jsp");
        dispatcher.forward(request, response);
    }
    
    private void showChart(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        String symbol = request.getParameter("symbol");
        
        if (symbol == null || symbol.isEmpty()) {
            response.sendRedirect("portfolio?action=market");
            return;
        }
        
        try {
            // Get historical data from Alpha Vantage
            AlphaVantageService alphaService = (AlphaVantageService) portfolioService.getPriceService();
            String historicalData = alphaService.getHistoricalData(symbol);
            
            System.out.println("📊 Chart data fetched for " + symbol);
            System.out.println("Data length: " + (historicalData != null ? historicalData.length() : 0));
            System.out.println("First 200 chars: " + (historicalData != null ? historicalData.substring(0, Math.min(200, historicalData.length())) : "null"));
            
            request.setAttribute("symbol", symbol);
            request.setAttribute("historicalData", historicalData);
            
            RequestDispatcher dispatcher = request.getRequestDispatcher("/WEB-INF/chart.jsp");
            dispatcher.forward(request, response);
            
        } catch (Exception e) {
            request.setAttribute("error", "Error loading chart: " + e.getMessage());
            showMarket(request, response);
        }
    }
    
    private void showWatchlist(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        RequestDispatcher dispatcher = request.getRequestDispatcher("/WEB-INF/watchlist.jsp");
        dispatcher.forward(request, response);
    }
    
    private void showTransactions(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        List<Transaction> transactions = portfolioService.getTransactions();
        request.setAttribute("transactions", transactions);
        RequestDispatcher dispatcher = request.getRequestDispatcher("/WEB-INF/transactions.jsp");
        dispatcher.forward(request, response);
    }
    
    private void showAnalytics(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        RequestDispatcher dispatcher = request.getRequestDispatcher("/WEB-INF/analytics.jsp");
        dispatcher.forward(request, response);
    }
    
    private void showNews(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        RequestDispatcher dispatcher = request.getRequestDispatcher("/WEB-INF/news.jsp");
        dispatcher.forward(request, response);
    }
    
    private void showSettings(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        RequestDispatcher dispatcher = request.getRequestDispatcher("/WEB-INF/settings.jsp");
        dispatcher.forward(request, response);
    }
}
