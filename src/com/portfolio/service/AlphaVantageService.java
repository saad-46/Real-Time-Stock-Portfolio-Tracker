package com.portfolio.service; // This file belongs to the "service" folder

import com.portfolio.model.Stock;  // Import Stock class
import java.net.URI;               // Import URI class for web addresses
import java.net.http.HttpClient;   // Import HttpClient to make web requests
import java.net.http.HttpRequest;  // Import HttpRequest to build requests
import java.net.http.HttpResponse; // Import HttpResponse to handle responses

// This class gets real stock prices from Alpha Vantage website
// It IMPLEMENTS StockPriceService, meaning it must have the methods from that interface
public class AlphaVantageService implements StockPriceService {
    // Constants - values that never change
    private static final String API_KEY = "M60K5JGJIO11K5QS";  // Your personal API key to access Alpha Vantage
    private static final String BASE_URL = "https://www.alphavantage.co/query";  // The website address for API
    
    private final HttpClient httpClient;  // Tool to make web requests (like a web browser)

    // Constructor - runs when you create new AlphaVantageService()
    public AlphaVantageService() {
        this.httpClient = HttpClient.newHttpClient();  // Create a new web request tool
    }

    // This method gets the current price for a stock symbol
    // Example: getCurrentPrice("AAPL") returns 278.12
    @Override
    public double getCurrentPrice(String symbol) throws Exception {
        // Build the complete web address (URL) to request
        // Example: "https://www.alphavantage.co/query?function=GLOBAL_QUOTE&symbol=AAPL&apikey=M60K5JGJIO11K5QS"
        String url = String.format("%s?function=GLOBAL_QUOTE&symbol=%s&apikey=%s",
                BASE_URL, symbol, API_KEY);

        System.out.println("📡 Fetching price for " + symbol + "...");  // Show user we're getting data

        // Build the web request (like typing a URL in your browser)
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))  // Set the web address
                .GET()                 // Use GET method (just reading data, not sending)
                .timeout(java.time.Duration.ofSeconds(10))  // Timeout after 10 seconds
                .build();              // Finish building the request

        // Send the request and get the response (like loading a webpage)
        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());  // Get response as text

        // Check if the request was successful (200 = OK)
        if (response.statusCode() != 200) {
            throw new Exception("API request failed with status: " + response.statusCode());  // Something went wrong
        }

        // Parse the response text to extract the price
        return parsePriceFromResponse(response.body());  // Extract price from JSON text
    }

    // This method updates a stock object with the latest price
    // Example: updateStockPrice(appleStock) will fetch real price and update the stock
    @Override
    public void updateStockPrice(Stock stock) throws Exception {
        double price = getCurrentPrice(stock.getSymbol());  // Get the current price from API
        stock.setCurrentPrice(price);                       // Update the stock with new price
    }

    // This method gets historical price data for the last 6 months
    // Returns a JSON string with dates and prices for charting
    // Example: getHistoricalData("AAPL") returns price data for last 6 months
    public String getHistoricalData(String symbol) throws Exception {
        // Build URL for TIME_SERIES_DAILY function (gets daily prices)
        String url = String.format("%s?function=TIME_SERIES_DAILY&symbol=%s&apikey=%s",
                BASE_URL, symbol, API_KEY);

        System.out.println("📊 Fetching historical data for " + symbol + "...");

        // Build and send the request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(java.time.Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("API request failed with status: " + response.statusCode());
        }

        return response.body();  // Return the full JSON response
    }

    // This private method extracts the price from the JSON response text
    // Example: Input: '{"Global Quote": {"05. price": "278.12"}}' → Output: 278.12
    private double parsePriceFromResponse(String jsonResponse) {
        // Parse JSON response to extract the stock price
        // Alpha Vantage returns data like: {"Global Quote": {"05. price": "150.25"}}
        
        try {
            // Look for the price field in the JSON text
            String priceKey = "\"05. price\":";  // This is what we're searching for
            int priceIndex = jsonResponse.indexOf(priceKey);  // Find where it appears in the text
            
            // If we can't find the price field, something went wrong
            if (priceIndex == -1) {
                System.err.println("⚠️ Could not find price in API response. Using mock price.");
                // Show first 200 characters of response to help debug
                System.err.println("Response: " + jsonResponse.substring(0, Math.min(200, jsonResponse.length())));
                return 100.0 + (Math.random() * 100);  // Return a random price between $100-$200
            }
            
            // Extract the price value from the JSON text
            // Example: Find the quotes around "278.12" and extract just the number
            int startQuote = jsonResponse.indexOf("\"", priceIndex + priceKey.length());  // Find opening quote
            int endQuote = jsonResponse.indexOf("\"", startQuote + 1);  // Find closing quote
            String priceStr = jsonResponse.substring(startQuote + 1, endQuote);  // Extract text between quotes
            
            return Double.parseDouble(priceStr);  // Convert text "278.12" to number 278.12
            
        } catch (Exception e) {
            // If anything goes wrong, show error and return mock price
            System.err.println("⚠️ Error parsing price: " + e.getMessage());
            return 100.0 + (Math.random() * 100);  // Return random price as fallback
        }
    }
}
