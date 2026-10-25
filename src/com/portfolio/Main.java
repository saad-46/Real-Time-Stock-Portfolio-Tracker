package com.portfolio;

import com.portfolio.service.PortfolioService;
import com.portfolio.model.PortfolioItem;
import java.util.Scanner;

/**
 * Main console application for portfolio tracking.
 * Simple text-based interface for command-line usage.
 */
public class Main {
    private static PortfolioService portfolioService;

    public static void main(String[] args) {
        System.out.println("🚀 Stock Portfolio Tracker - Console Version");
        System.out.println("==========================================\n");

        // Initialize service
        try {
            portfolioService = new PortfolioService();
        } catch (IllegalStateException e) {
            System.err.println("Cannot start: " + e.getMessage());
            System.exit(1);
            return;
        }

        // Get scanner for user input
        Scanner scanner = new Scanner(System.in, java.nio.charset.StandardCharsets.UTF_8);

        // Main loop
        boolean running = true;
        while (running) {
            System.out.println("\nOptions:");
            System.out.println("1. Add stock");
            System.out.println("2. View portfolio");
            System.out.println("3. Refresh prices");
            System.out.println("4. View summary");
            System.out.println("5. Sell stock");
            System.out.println("6. Exit");
            System.out.println("7. Wallet (balance / add funds)");
            System.out.print("Choose: ");

            String choice = scanner.nextLine();

            switch (choice) {
                case "1":
                    addStock(scanner);
                    break;
                case "2":
                    viewPortfolio();
                    break;
                case "3":
                    refreshPrices();
                    break;
                case "4":
                    viewSummary();
                    break;
                case "5":
                    sellStock(scanner);
                    break;
                case "6":
                    running = false;
                    System.out.println("Goodbye!");
                    break;
                case "7":
                    wallet(scanner);
                    break;
                default:
                    System.out.println("Invalid choice");
            }
        }

        scanner.close();
    }

    /**
     * Add a stock to portfolio
     */
    private static void addStock(Scanner scanner) {
        System.out.print("Enter stock symbol (ex: AAPL): ");
        String symbol = scanner.nextLine().toUpperCase();

        System.out.print("Enter quantity: ");
        int quantity;
        double price;
        try {
            quantity = Integer.parseInt(scanner.nextLine().trim());
            System.out.print("Enter purchase price: ");
            price = Double.parseDouble(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            System.out.println("Invalid number.");
            return;
        }

        PortfolioItem item = new PortfolioItem(symbol, symbol, quantity, price, price);
        try {
            portfolioService.buyStock(item);
            System.out.println("Stock added (wallet debited).");
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.out.println("Not added: " + e.getMessage());
        }
    }

    /** Sells at a validated live quote that the user confirms; no quote means no sale. */
    private static void sellStock(Scanner scanner) {
        try {
            System.out.print("Symbol to sell: ");
            String symbol = scanner.nextLine().trim().toUpperCase();
            System.out.print("Quantity: ");
            int qty = Integer.parseInt(scanner.nextLine().trim());
            com.portfolio.service.Quote quote = portfolioService.fetchValidatedQuote(symbol);
            String effect = portfolioService.describeWalletEffect(symbol, qty, quote, false);
            System.out.printf("Live price %.2f %s (valid for %d s). %s Confirm sale (y/n)? ", quote.getPrice(), quote.getCurrency(),
                    com.portfolio.service.QuotePolicy.TRADE_MAX_AGE.toSeconds(), effect);
            if (!"y".equalsIgnoreCase(scanner.nextLine().trim())) {
                System.out.println("Cancelled.");
                return;
            }
            System.out.println("Sold. Realized P/L: " + portfolioService.sellStock(symbol, qty, quote).toPlainString() + " " + quote.getCurrency());
        } catch (NumberFormatException e) {
            System.out.println("Invalid number.");
        } catch (com.portfolio.service.PriceUnavailableException e) {
            System.out.println("Not sold: no valid live price (" + e.getMessage() + ")");
        } catch (IllegalArgumentException | IllegalStateException e) {
            System.out.println("Not sold: " + e.getMessage());
        }
    }

    private static void wallet(Scanner scanner) {
        var wallet = portfolioService.getWalletService();
        System.out.printf("Wallet balance: %.2f%n", wallet.getBalance());
        System.out.print("Amount to add (blank to skip): ");
        String text = scanner.nextLine().trim();
        if (text.isEmpty()) {
            return;
        }
        try {
            System.out.println(wallet.addFunds(Double.parseDouble(text), "console")
                    ? String.format("Added. Balance: %.2f", wallet.getBalance()) : "Not added (amount must be positive).");
        } catch (NumberFormatException e) {
            System.out.println("Invalid number.");
        }
    }

    /**
     * View all stocks in portfolio
     */
    private static void viewPortfolio() {
        System.out.println("\n📊 Your Portfolio:");
        System.out.println("==================");

        var items = portfolioService.getAllPortfolioItems();
        if (items.isEmpty()) {
            System.out.println("No stocks yet");
            return;
        }

        System.out.printf("%-10s%-10s%-15s%-15s%-15s%n", "Symbol", "Qty", "Purchase", "Current", "Value");
        System.out.println("--------------------------------------------------------------");

        for (var item : items) {
            System.out.printf("%-10s%-10d$%-14.2f$%-14.2f$%-14.2f%n",
                    item.getSymbol(),
                    item.getQuantity(),
                    item.getPurchasePrice(),
                    item.getCurrentPrice(),
                    item.getCurrentValue()
            );
        }
    }

    /**
     * Refresh prices from API
     */
    private static void refreshPrices() {
        System.out.println("Updating prices...");
        int failed = portfolioService.updateAllPrices();
        System.out.println(failed == 0 ? "Prices updated." : failed + " holding(s) could not be updated (showing last known price). Is ALPHAVANTAGE_API_KEY set?");
    }

    /**
     * View portfolio summary
     */
    private static void viewSummary() {
        System.out.println("\n💰 Portfolio Summary:");
        System.out.println("====================");
        System.out.printf("Total Investment: $%.2f%n", portfolioService.getTotalInvestment());
        System.out.printf("Current Value: $%.2f%n", portfolioService.getTotalPortfolioValue());
        System.out.printf("Profit/Loss: $%.2f%n", portfolioService.getTotalProfitLoss());
        System.out.printf("Return: %.2f%%%n", portfolioService.getReturnPercentage());
        System.out.println("Realized P/L (from sales): $" + portfolioService.getTotalRealizedProfitLoss().toPlainString());
    }
}
