package com.portfolio.service;

import com.portfolio.model.Stock;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * TwelveDataService - Secondary stock price provider using Twelve Data API.
 * Free tier: 800 API calls/day, 8 per minute.
 * Good for international stocks and historical data.
 */
public class TwelveDataService implements StockPriceService {

    private static final String BASE_URL = "https://api.twelvedata.com";
    private final HttpClient httpClient;

    public TwelveDataService() {
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Gets the current price for a stock symbol from Twelve Data.
     * Endpoint: /price?symbol=AAPL&apikey=KEY
     * Response: {"price":"150.25"}
     */
    @Override
    public double getCurrentPrice(String symbol) throws Exception {
        String apiKey = ApiKeyManager.getTwelveDataKey();
        String url = String.format("%s/price?symbol=%s&apikey=%s",
                BASE_URL, symbol.toUpperCase(), apiKey);

        System.out.println("📡 TwelveData: Fetching price for " + symbol + "...");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(java.time.Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("TwelveData API request failed with status: " + response.statusCode());
        }

        String body = response.body();

        // Check for error response
        if (body.contains("\"code\"") && body.contains("\"status\":\"error\"")) {
            throw new Exception("TwelveData error: " + body);
        }

        double price = parsePrice(body);

        if (price <= 0) {
            throw new Exception("TwelveData returned invalid price for " + symbol);
        }

        System.out.println("✅ TwelveData: " + symbol + " = $" + String.format("%.2f", price));
        return price;
    }

    /**
     * Updates a stock object with the latest price from Twelve Data.
     * Uses /quote endpoint for full details including change percent.
     */
    @Override
    public void updateStockPrice(Stock stock) throws Exception {
        String apiKey = ApiKeyManager.getTwelveDataKey();
        String url = String.format("%s/quote?symbol=%s&apikey=%s",
                BASE_URL, stock.getSymbol().toUpperCase(), apiKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(java.time.Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("TwelveData API failed: " + response.statusCode());
        }

        String body = response.body();

        if (body.contains("\"code\"") && body.contains("\"status\":\"error\"")) {
            throw new Exception("TwelveData error for " + stock.getSymbol());
        }

        double price = parseJsonStringValue(body, "\"close\":");
        double changePercent = parseJsonStringValue(body, "\"percent_change\":");
        double high = parseJsonStringValue(body, "\"high\":");
        double low = parseJsonStringValue(body, "\"low\":");

        if (price <= 0) {
            // Try the simpler price field
            price = parseJsonStringValue(body, "\"price\":");
        }

        if (price <= 0) {
            throw new Exception("TwelveData returned invalid price for " + stock.getSymbol());
        }

        stock.setCurrentPrice(price);
        stock.setChangePercent(changePercent);
        stock.setHigh24h(high);
        stock.setLow24h(low);
        System.out.println("✅ TwelveData updated " + stock.getSymbol() + ": $" +
                String.format("%.2f", price) + " (" + String.format("%.2f", changePercent) + "%)");
    }

    /**
     * Gets historical daily data for a stock.
     * Endpoint: /time_series?symbol=X&interval=1day&outputsize=30&apikey=KEY
     */
    @Override
    public String getHistoricalData(String symbol) throws Exception {
        return getHistoricalData(symbol, 30); // Default to 30 days
    }

    /**
     * Gets historical daily data for a stock with custom days.
     * Endpoint: /time_series?symbol=X&interval=1day&outputsize=30&apikey=KEY
     */
    public String getHistoricalData(String symbol, int days) throws Exception {
        String apiKey = ApiKeyManager.getTwelveDataKey();
        String url = String.format("%s/time_series?symbol=%s&interval=1day&outputsize=%d&apikey=%s",
                BASE_URL, symbol.toUpperCase(), days, apiKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(java.time.Duration.ofSeconds(15))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("TwelveData historical data failed: " + response.statusCode());
        }

        return response.body();
    }

    /**
     * Parse price from simple {"price":"150.25"} response
     */
    private double parsePrice(String json) {
        try {
            String key = "\"price\":";
            int index = json.indexOf(key);
            if (index == -1)
                return 0.0;

            int start = index + key.length();
            // Skip whitespace and opening quote
            while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"'))
                start++;

            int end = start;
            while (end < json.length() && json.charAt(end) != '"' && json.charAt(end) != ','
                    && json.charAt(end) != '}') {
                end++;
            }

            return Double.parseDouble(json.substring(start, end).trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Parse a string value from JSON that may be quoted.
     * Handles both "key":"value" and "key":value formats.
     */
    private double parseJsonStringValue(String json, String key) {
        try {
            int index = json.indexOf(key);
            if (index == -1)
                return 0.0;

            int start = index + key.length();
            // Skip whitespace and quote
            while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"'))
                start++;

            int end = start;
            while (end < json.length() && json.charAt(end) != '"' && json.charAt(end) != ','
                    && json.charAt(end) != '}') {
                end++;
            }

            String valueStr = json.substring(start, end).trim();
            if (valueStr.isEmpty() || valueStr.equals("null"))
                return 0.0;
            return Double.parseDouble(valueStr);
        } catch (Exception e) {
            return 0.0;
        }
    }

}
