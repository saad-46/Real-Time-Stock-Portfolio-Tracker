package com.portfolio.service; // This file belongs to the "service" folder

import com.portfolio.model.Stock;          // Import Stock class
import com.portfolio.model.PortfolioItem;  // Import PortfolioItem class
import com.portfolio.model.Transaction;    // Import Transaction class
import java.util.ArrayList;                // Import ArrayList to store lists of items
import java.util.List;                     // Import List interface

// This class manages your entire portfolio - all your stocks and transactions
// Think of it like a portfolio manager who tracks everything you own
public class PortfolioService {
    // Private variables - the data this service manages
    private List<PortfolioItem> portfolioItems;  // List of all stocks you own (ex: [Apple x10, Tesla x5])
    private List<Transaction> transactions;      // List of all buy/sell transactions (history)
    private StockPriceService priceService;      // The service that gets real stock prices
    private com.portfolio.database.PortfolioDAO portfolioDAO;  // Database access object for saving/loading data

    // Constructor - creates a new portfolio manager
    // Example: new PortfolioService(alphaVantageService)
    public PortfolioService(StockPriceService priceService) {
        this.portfolioItems = new ArrayList<>();  // Create empty list for portfolio items
        this.transactions = new ArrayList<>();    // Create empty list for transactions
        this.priceService = priceService;         // Store the price service to use later
        this.portfolioDAO = new com.portfolio.database.PortfolioDAO();  // Create database access object

        // Load existing data from database when service starts
        loadFromDatabase();
    }

    // Loads all portfolio data from database
    // This runs automatically when the service starts
    // Example: Loads your saved stocks from database file
    private void loadFromDatabase() {
        try {
            // Load portfolio items from database
            portfolioItems = portfolioDAO.loadAllPortfolioItems();
            System.out.println("✅ Loaded " + portfolioItems.size() + " stocks from database");

            // Load transaction history from database
            transactions = portfolioDAO.loadAllTransactions();
            System.out.println("✅ Loaded " + transactions.size() + " transactions from database");

        } catch (Exception e) {
            // If loading fails, start with empty lists
            System.err.println("⚠️ Could not load from database: " + e.getMessage());
            portfolioItems = new ArrayList<>();
            transactions = new ArrayList<>();
        }
    }

    // Method to buy stock with full details
    // Example: buyStock("AAPL", "Apple Inc.", 10, 150.0) means "Buy 10 Apple shares at $150 each"
    public void buyStock(String symbol, String name, int quantity, double price) {
        Stock stock = new Stock(symbol, name);  // Create a new stock object (ex: Apple)
        stock.setCurrentPrice(price);           // Set its current price (ex: $150)

        PortfolioItem item = new PortfolioItem(stock, quantity, price);  // Create portfolio item (10 shares @ $150)
        portfolioItems.add(item);  // Add this item to your portfolio list

        Transaction transaction = new Transaction(symbol, "BUY", quantity, price);  // Record the purchase
        transactions.add(transaction);  // Add to transaction history

        // Save to database
        try {
            portfolioDAO.savePortfolioItem(item);  // Save stock to database
            portfolioDAO.saveTransaction(transaction);  // Save transaction to database
            System.out.println("✅ Bought " + quantity + " shares of " + symbol + " @ $" + price);
        } catch (Exception e) {
            System.err.println("❌ Error saving to database: " + e.getMessage());
        }
    }

    // Simplified buy method - just needs symbol, quantity, and price
    // Example: buy("AAPL", 10, 150.0) means "Buy 10 AAPL shares at $150 each"
    public void buy(String symbol, int quantity, double price) {
        buyStock(symbol, symbol, quantity, price);  // Call full method, using symbol as name too
    }

    // Method to sell stock
    // Example: sellStock("AAPL", 5) means "Sell 5 Apple shares"
    public boolean sellStock(String symbol, int quantity) {
        // Find the stock in portfolio
        PortfolioItem itemToSell = null;
        for (PortfolioItem item : portfolioItems) {
            if (item.getStock().getSymbol().equalsIgnoreCase(symbol)) {
                itemToSell = item;
                break;
            }
        }

        if (itemToSell == null) {
            System.err.println("❌ Stock " + symbol + " not found in portfolio");
            return false;
        }

        if (itemToSell.getQuantity() < quantity) {
            System.err.println("❌ Not enough shares. You have " + itemToSell.getQuantity() + " but trying to sell " + quantity);
            return false;
        }

        double currentPrice = itemToSell.getStock().getCurrentPrice();

        // Record the transaction
        Transaction transaction = new Transaction(symbol, "SELL", quantity, currentPrice);
        transactions.add(transaction);

        // Update or remove the portfolio item
        if (itemToSell.getQuantity() == quantity) {
            // Selling all shares - remove from portfolio
            portfolioItems.remove(itemToSell);
            try {
                portfolioDAO.deletePortfolioItem(symbol);
                System.out.println("✅ Sold all " + quantity + " shares of " + symbol + " @ ₹" + currentPrice);
            } catch (Exception e) {
                System.err.println("❌ Error deleting from database: " + e.getMessage());
            }
        } else {
            // Selling partial shares - update quantity
            int newQuantity = itemToSell.getQuantity() - quantity;
            itemToSell.setQuantity(newQuantity);
            try {
                portfolioDAO.updatePortfolioItemQuantity(symbol, newQuantity);
                System.out.println("✅ Sold " + quantity + " shares of " + symbol + " @ ₹" + currentPrice + " (" + newQuantity + " remaining)");
            } catch (Exception e) {
                System.err.println("❌ Error updating database: " + e.getMessage());
            }
        }

        // Save transaction
        try {
            portfolioDAO.saveTransaction(transaction);
        } catch (Exception e) {
            System.err.println("❌ Error saving transaction: " + e.getMessage());
        }

        return true;
    }

    // Sell all shares of a stock
    // Example: sellAllStock("GOOGL") means "Sell all Google shares"
    public boolean sellAllStock(String symbol) {
        PortfolioItem item = null;
        for (PortfolioItem pi : portfolioItems) {
            if (pi.getStock().getSymbol().equalsIgnoreCase(symbol)) {
                item = pi;
                break;
            }
        }

        if (item == null) {
            System.err.println("❌ Stock " + symbol + " not found in portfolio");
            return false;
        }

        return sellStock(symbol, item.getQuantity());
    }

    // Calculates total money you invested (what you paid)
    // Example: Bought 10 AAPL @ $150 + 5 TSLA @ $200 = $1,500 + $1,000 = $2,500
    public double calculateTotalInvestment() {
        double total = 0;  // Start with $0
        // Loop through each item in your portfolio
        for (PortfolioItem item : portfolioItems) {
            total += item.getPurchasePrice() * item.getQuantity();  // Add (price × quantity) for each stock
        }
        return total;  // Return the total investment
    }

    // Calculates current total value of your portfolio (what it's worth now)
    // Example: 10 AAPL @ $278 + 5 TSLA @ $200 = $2,780 + $1,000 = $3,780
    public double calculateCurrentValue() {
        double total = 0;  // Start with $0
        // Loop through each item in your portfolio
        for (PortfolioItem item : portfolioItems) {
            total += item.getTotalValue();  // Add current value of each holding
        }
        return total;  // Return the total current value
    }

    // Calculates your profit or loss (current value - what you paid)
    // Example: Current $3,780 - Invested $2,500 = $1,280 profit
    public double calculateProfitLoss() {
        return calculateCurrentValue() - calculateTotalInvestment();  // Subtract investment from current value
    }

    // Updates all stock prices by fetching from the internet
    // Example: Updates Apple from $150 to real price $278, Tesla from $200 to real price $250
    public void updateAllPrices() {
        System.out.println("\n📊 Updating stock prices...");  // Show we're starting
        // Loop through each stock in your portfolio
        for (PortfolioItem item : portfolioItems) {
            try {
                priceService.updateStockPrice(item.getStock());  // Fetch and update real price

                // Save updated price to database
                portfolioDAO.updateStockPrice(item.getStock().getSymbol(), item.getStock().getCurrentPrice());

                System.out.println("Updated " + item.getStock().getSymbol() +
                                 ": $" + item.getStock().getCurrentPrice());  // Show new price
            } catch (Exception e) {
                // If update fails (ex: no internet), show error
                System.err.println("Failed to update " + item.getStock().getSymbol());
            }
        }
    }

    // Displays your entire portfolio in a nice format
    // Shows each stock, its value, and profit/loss
    public void displayPortfolio() {
        System.out.println("\n💼 Your Portfolio:");
        System.out.println("==================");

        double totalValue = 0;     // Track total portfolio value
        double totalGainLoss = 0;  // Track total profit/loss

        // Loop through each item and display it
        for (PortfolioItem item : portfolioItems) {
            System.out.println(item);  // Print item (ex: "AAPL x10 @ $150.0 (Current: $278.12)")
            System.out.println("   Value: $" + String.format("%.2f", item.getTotalValue()));  // Show total value
            System.out.println("   Gain/Loss: $" + String.format("%.2f", item.getGainLoss()));  // Show profit/loss
            System.out.println();  // Blank line for spacing

            totalValue += item.getTotalValue();    // Add to running total
            totalGainLoss += item.getGainLoss();   // Add to running profit/loss
        }

        // Display summary totals
        System.out.println("==================");
        System.out.println("Total Portfolio Value: $" + String.format("%.2f", totalValue));
        System.out.println("Total Gain/Loss: $" + String.format("%.2f", totalGainLoss));
    }

    // Displays all your transaction history
    // Shows every buy/sell you've made
    public void displayTransactions() {
        System.out.println("\n📜 Transaction History:");
        System.out.println("======================");
        // Loop through each transaction and print it
        for (Transaction transaction : transactions) {
            System.out.println(transaction);  // Print transaction (ex: "BUY 10 AAPL @ $150.0 on 2024-01-15...")
        }
    }

    // Getter - returns the list of portfolio items
    // Example: service.getPortfolioItems() returns [Apple x10, Tesla x5]
    public List<PortfolioItem> getPortfolioItems() {
        return portfolioItems;  // Return the list
    }

    // Getter - returns the list of transactions
    // Example: service.getTransactions() returns all buy/sell history
    public List<Transaction> getTransactions() {
        return transactions;  // Return the list
    }

    // Getter - returns the price service
    // Used by servlet to access historical data methods
    public StockPriceService getPriceService() {
        return priceService;  // Return the price service
    }
}

