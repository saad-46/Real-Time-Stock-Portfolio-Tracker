package com.portfolio.service; // Service package for business logic

import java.net.URI;                // For creating web addresses
import java.net.http.HttpClient;   // For making HTTP requests
import java.net.http.HttpRequest;  // For building HTTP requests
import java.net.http.HttpResponse; // For handling HTTP responses

/**
 * StockValidator - Validates if a stock symbol exists and fetches its real-time info
 * This uses Alpha Vantage API to check if the stock is valid before adding it
 * 
 * Example: Before adding "AAPL", this checks if Apple stock exists and gets its current price
 */
public class StockValidator {
    
    // API key for Alpha Vantage - your personal key to access their service
    private static final String API_KEY = "M60K5JGJIO11K5QS";
    
    // Base URL for Alpha Vantage API - the website address
    private static final String BASE_URL = "https://www.alphavantage.co/query";
    
    // HTTP client - tool to make web requests (like a web browser)
    private final HttpClient httpClient;
    
    // Constructor - creates a new validator
    // Example: StockValidator validator = new StockValidator();
    public StockValidator() {
        this.httpClient = HttpClient.newHttpClient();  // Create HTTP client
    }
    
    /**
     * Validates if a stock symbol exists and returns its current information
     * 
     * @param symbol The stock symbol to validate (ex: "AAPL", "TSLA")
     * @return StockInfo object with symbol, name, and current price
     * @throws Exception if stock doesn't exist or API call fails
     * 
     * Example: 
     *   StockInfo info = validator.validateAndGetInfo("AAPL");
     *   // Returns: symbol="AAPL", name="Apple Inc.", price=278.12
     */
    public StockInfo validateAndGetInfo(String symbol) throws Exception {
        // Step 1: Build the API URL to fetch stock information
        // Example URL: https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=AAPL&apikey=YOUR_KEY
        String url = String.format("%s?function=GLOBAL_QUOTE&symbol=%s&apikey=%s",
                BASE_URL, symbol.toUpperCase(), API_KEY);
        
        System.out.println("🔍 Validating stock symbol: " + symbol);  // Show what we're checking
        
        // Step 2: Create HTTP request (like typing URL in browser)
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))  // Set the web address
                .GET()                 // Use GET method (just reading data)
                .build();              // Finish building request
        
        // Step 3: Send request and get response (like loading a webpage)
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());  // Get response as text
        
        // Step 4: Check if request was successful (200 = OK)
        if (response.statusCode() != 200) {
            // If not OK, throw exception with error message
            throw new Exception("API request failed with status code: " + response.statusCode());
        }
        
        // Step 5: Parse the response to extract stock information
        String jsonResponse = response.body();  // Get the response text (JSON format)
        
        // Check if the response contains valid stock data
        // If stock doesn't exist, API returns error message
        if (jsonResponse.contains("Error Message") || jsonResponse.contains("Invalid API call")) {
            // Stock symbol is invalid or doesn't exist
            throw new Exception("Invalid stock symbol: " + symbol + ". Stock not found!");
        }
        
        // Check if we hit API rate limit (too many requests)
        if (jsonResponse.contains("Thank you for using Alpha Vantage")) {
            throw new Exception("API rate limit reached. Please wait a moment and try again.");
        }
        
        // Step 6: Extract stock information from JSON response
        try {
            // Extract current price
            double price = extractPrice(jsonResponse);
            
            // Extract company name (if available)
            String name = extractName(jsonResponse, symbol);
            
            // Create and return StockInfo object
            System.out.println("✅ Stock validated: " + symbol + " - $" + price);
            return new StockInfo(symbol.toUpperCase(), name, price);
            
        } catch (Exception e) {
            // If parsing fails, stock data is incomplete or invalid
            throw new Exception("Could not retrieve stock information for: " + symbol);
        }
    }
    
    /**
     * Extracts the current price from JSON response
     * 
     * @param jsonResponse The JSON text from API
     * @return The current stock price
     * @throws Exception if price cannot be found
     * 
     * Example JSON: {"Global Quote": {"05. price": "278.12"}}
     * Returns: 278.12
     */
    private double extractPrice(String jsonResponse) throws Exception {
        // Look for the price field in JSON
        // Alpha Vantage uses "05. price" as the key
        String priceKey = "\"05. price\":";
        int priceIndex = jsonResponse.indexOf(priceKey);
        
        // If price field not found, throw exception
        if (priceIndex == -1) {
            throw new Exception("Price not found in response");
        }
        
        // Extract the price value between quotes
        // Example: "05. price": "278.12" → extract "278.12"
        int startQuote = jsonResponse.indexOf("\"", priceIndex + priceKey.length());
        int endQuote = jsonResponse.indexOf("\"", startQuote + 1);
        String priceStr = jsonResponse.substring(startQuote + 1, endQuote);
        
        // Convert string to number and return
        // Example: "278.12" → 278.12
        return Double.parseDouble(priceStr);
    }
    
    /**
     * Extracts company name from JSON response (if available)
     * 
     * @param jsonResponse The JSON text from API
     * @param symbol The stock symbol (used as fallback)
     * @return Company name or symbol if name not found
     * 
     * Example: For AAPL, might return "Apple Inc." or just "AAPL"
     */
    private String extractName(String jsonResponse, String symbol) {
        // Try to extract company name from "01. symbol" field
        String symbolKey = "\"01. symbol\":";
        int symbolIndex = jsonResponse.indexOf(symbolKey);
        
        if (symbolIndex != -1) {
            try {
                int startQuote = jsonResponse.indexOf("\"", symbolIndex + symbolKey.length());
                int endQuote = jsonResponse.indexOf("\"", startQuote + 1);
                return jsonResponse.substring(startQuote + 1, endQuote);
            } catch (Exception e) {
                // If extraction fails, just use symbol
            }
        }
        
        // If name not found, return symbol as name
        return symbol.toUpperCase();
    }
    
    /**
     * Inner class to hold stock information
     * This is a simple data container (like a struct in C)
     */
    public static class StockInfo {
        private String symbol;       // Stock symbol (ex: "AAPL")
        private String name;         // Company name (ex: "Apple Inc.")
        private double currentPrice; // Current price (ex: 278.12)
        
        // Constructor - creates new StockInfo object
        // Example: new StockInfo("AAPL", "Apple Inc.", 278.12)
        public StockInfo(String symbol, String name, double currentPrice) {
            this.symbol = symbol;           // Store symbol
            this.name = name;               // Store name
            this.currentPrice = currentPrice; // Store price
        }
        
        // Getter methods - return the stored values
        public String getSymbol() { return symbol; }
        public String getName() { return name; }
        public double getCurrentPrice() { return currentPrice; }
        
        // toString - formats the info for display
        // Example: "AAPL (Apple Inc.) - $278.12"
        @Override
        public String toString() {
            return symbol + " (" + name + ") - $" + String.format("%.2f", currentPrice);
        }
    }
}
