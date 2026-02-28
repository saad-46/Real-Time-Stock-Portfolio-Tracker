package com.portfolio.service; // Service package for business logic

import org.json.JSONArray;   // Import JSON array handling
import org.json.JSONObject;  // Import JSON object handling
import java.net.URI;         // Import URI for web requests
import java.net.http.*;      // Import HTTP client classes
import java.util.ArrayList;  // Import ArrayList
import java.util.List;       // Import List interface

/**
 * StockSearchService - Searches for stocks by name or symbol
 * Uses Alpha Vantage SYMBOL_SEARCH API to find matching stocks
 * 
 * Example: Search "apple" returns ["AAPL - Apple Inc.", "APLE - Apple Hospitality REIT", ...]
 */
public class StockSearchService {
    
    // Alpha Vantage API key - get free key from https://www.alphavantage.co/support/#api-key
    private static final String API_KEY = "demo";  // Replace with your API key
    
    // Base URL for Alpha Vantage API
    private static final String BASE_URL = "https://www.alphavantage.co/query";
    
    /**
     * SearchResult - Represents a single stock search result
     * Contains symbol, name, and other details
     */
    public static class SearchResult {
        private String symbol;      // Stock symbol (ex: "AAPL")
        private String name;        // Company name (ex: "Apple Inc.")
        private String type;        // Security type (ex: "Equity")
        private String region;      // Region (ex: "United States")
        private String currency;    // Currency (ex: "USD")
        
        // Constructor - creates a search result
        public SearchResult(String symbol, String name, String type, String region, String currency) {
            this.symbol = symbol;
            this.name = name;
            this.type = type;
            this.region = region;
            this.currency = currency;
        }
        
        // Getters - return the values
        public String getSymbol() { return symbol; }
        public String getName() { return name; }
        public String getType() { return type; }
        public String getRegion() { return region; }
        public String getCurrency() { return currency; }
        
        // toString - formats the result for display
        // Example: "AAPL - Apple Inc. (Equity)"
        @Override
        public String toString() {
            return symbol + " - " + name + " (" + type + ")";
        }
        
        // Display format for dropdown
        // Example: "AAPL - Apple Inc."
        public String getDisplayText() {
            return symbol + " - " + name;
        }
    }
    
    /**
     * Searches for stocks matching the query
     * 
     * @param query The search term (ex: "apple", "goog", "tesla")
     * @return List of matching stocks
     * @throws Exception if search fails
     * 
     * Example: searchStocks("apple") returns list of Apple-related stocks
     */
    public List<SearchResult> searchStocks(String query) throws Exception {
        // If query is empty, return empty list
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        // Build API URL with parameters
        // Example: https://www.alphavantage.co/query?function=SYMBOL_SEARCH&keywords=apple&apikey=demo
        String url = BASE_URL + 
                    "?function=SYMBOL_SEARCH" +      // Use SYMBOL_SEARCH function
                    "&keywords=" + query +            // Search term
                    "&apikey=" + API_KEY;             // API key
        
        // Create HTTP client to make web request
        HttpClient client = HttpClient.newHttpClient();
        
        // Build HTTP request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))                // Set URL
                .GET()                               // Use GET method
                .build();
        
        // Send request and get response
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        
        // Parse JSON response
        JSONObject json = new JSONObject(response.body());
        
        // Check for error messages
        if (json.has("Error Message")) {
            throw new Exception("API Error: " + json.getString("Error Message"));
        }
        
        if (json.has("Note")) {
            throw new Exception("API Limit: Please wait a moment and try again");
        }
        
        // Create list to store results
        List<SearchResult> results = new ArrayList<>();
        
        // Get "bestMatches" array from JSON
        // This contains all matching stocks
        if (json.has("bestMatches")) {
            JSONArray matches = json.getJSONArray("bestMatches");
            
            // Loop through each match
            for (int i = 0; i < matches.length(); i++) {
                JSONObject match = matches.getJSONObject(i);
                
                // Extract data from JSON
                String symbol = match.getString("1. symbol");           // Stock symbol
                String name = match.getString("2. name");               // Company name
                String type = match.getString("3. type");               // Security type
                String region = match.getString("4. region");           // Region
                String currency = match.getString("8. currency");       // Currency
                
                // Create SearchResult object and add to list
                SearchResult result = new SearchResult(symbol, name, type, region, currency);
                results.add(result);
            }
        }
        
        return results;  // Return the list of results
    }
    
    /**
     * Quick search that returns only US stocks
     * Filters out non-US stocks for cleaner results
     * 
     * @param query The search term
     * @return List of US stocks matching the query
     * @throws Exception if search fails
     */
    public List<SearchResult> searchUSStocks(String query) throws Exception {
        List<SearchResult> allResults = searchStocks(query);
        List<SearchResult> usResults = new ArrayList<>();
        
        // Filter to only include US stocks
        for (SearchResult result : allResults) {
            if ("United States".equals(result.getRegion())) {
                usResults.add(result);
            }
        }
        
        return usResults;
    }
}
