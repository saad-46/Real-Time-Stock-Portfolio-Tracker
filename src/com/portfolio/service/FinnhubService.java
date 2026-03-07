package com.portfolio.service;

import com.portfolio.model.Stock;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * FinnhubService - Primary stock price provider using Finnhub.io API.
 * Free tier: 60 API calls/minute — covers US stocks and major exchanges.
 * Round-robins between multiple API keys for higher throughput.
 */
public class FinnhubService implements StockPriceService {

    private static final String BASE_URL = "https://finnhub.io/api/v1";
    private final HttpClient httpClient;

    public FinnhubService() {
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Gets the current price for a stock symbol from Finnhub.
     * Endpoint: /quote?symbol=AAPL&token=KEY
     * Response:
     * {"c":150.0,"d":-1.5,"dp":-0.99,"h":151.0,"l":149.0,"o":150.5,"pc":151.5,"t":...}
     * c = current price, d = change, dp = percent change
     */
    @Override
    public double getCurrentPrice(String symbol) throws Exception {
        String apiKey = ApiKeyManager.getFinnhubKey();
        String url = String.format("%s/quote?symbol=%s&token=%s", BASE_URL, symbol.toUpperCase(), apiKey);

        System.out.println("📡 Finnhub: Fetching price for " + symbol + "...");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(java.time.Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Finnhub API request failed with status: " + response.statusCode());
        }

        String body = response.body();
        double price = parseJsonDouble(body, "\"c\":");

        if (price <= 0) {
            throw new Exception("Finnhub returned invalid price for " + symbol + ": " + price);
        }

        System.out.println("✅ Finnhub: " + symbol + " = $" + String.format("%.2f", price));
        return price;
    }

    @Override
    public void updateStockPrice(Stock stock) throws Exception {
        String apiKey = ApiKeyManager.getFinnhubKey();

        // 1. Fetch Quote (Price, High, Low, Change)
        String quoteUrl = String.format("%s/quote?symbol=%s&token=%s",
                BASE_URL, stock.getSymbol().toUpperCase(), apiKey);
        HttpRequest quoteRequest = HttpRequest.newBuilder().uri(URI.create(quoteUrl)).GET()
                .timeout(java.time.Duration.ofSeconds(10)).build();
        HttpResponse<String> quoteResponse = httpClient.send(quoteRequest, HttpResponse.BodyHandlers.ofString());

        if (quoteResponse.statusCode() == 200) {
            String body = quoteResponse.body();
            double price = parseJsonDouble(body, "\"c\":");
            double changePercent = parseJsonDouble(body, "\"dp\":");
            double high = parseJsonDouble(body, "\"h\":");
            double low = parseJsonDouble(body, "\"l\":");

            if (price > 0) {
                stock.setCurrentPrice(price);
                stock.setChangePercent(changePercent);
                stock.setHigh24h(high);
                stock.setLow24h(low);
            }
        }

        // 2. Fetch Profile (Name, Market Cap, Sector)
        // Only if name is placeholder or market cap is empty
        if (stock.getName() == null || stock.getName().equals(stock.getSymbol()) || stock.getMarketCap() == null) {
            String profileUrl = String.format("%s/stock/profile2?symbol=%s&token=%s",
                    BASE_URL, stock.getSymbol().toUpperCase(), apiKey);
            HttpRequest profileRequest = HttpRequest.newBuilder().uri(URI.create(profileUrl)).GET()
                    .timeout(java.time.Duration.ofSeconds(10)).build();
            HttpResponse<String> profileResponse = httpClient.send(profileRequest,
                    HttpResponse.BodyHandlers.ofString());

            if (profileResponse.statusCode() == 200) {
                String body = profileResponse.body();
                String name = parseJsonString(body, "\"name\":");
                double marketCapInMillions = parseJsonDouble(body, "\"marketCapitalization\":");
                String sector = parseJsonString(body, "\"finnhubIndustry\":");

                if (name != null && !name.isEmpty())
                    stock.setName(name);
                if (sector != null && !sector.isEmpty())
                    stock.setSector(sector);

                if (marketCapInMillions > 0) {
                    if (marketCapInMillions >= 1000000) {
                        stock.setMarketCap(String.format("₹%.2fT", marketCapInMillions / 1000000));
                    } else if (marketCapInMillions >= 1000) {
                        stock.setMarketCap(String.format("₹%.2fB", marketCapInMillions / 1000));
                    } else {
                        stock.setMarketCap(String.format("₹%.2fM", marketCapInMillions));
                    }
                }
            }
        }

        System.out.println("✅ Finnhub updated " + stock.getSymbol() + ": $" + stock.getCurrentPrice());
    }

    private String parseJsonString(String json, String key) {
        try {
            int index = json.indexOf(key);
            if (index == -1)
                return null;
            int start = index + key.length();
            while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"'))
                start++;
            int end = start;
            while (end < json.length() && json.charAt(end) != '"')
                end++;
            return json.substring(start, end).trim();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Search for stocks using Finnhub symbol search.
     * Endpoint: /search?q=apple&token=KEY
     * Returns JSON with "result" array containing matches.
     */
    public String searchStocks(String query) throws Exception {
        String apiKey = ApiKeyManager.getFinnhubKey();
        String url = String.format("%s/search?q=%s&token=%s", BASE_URL,
                java.net.URLEncoder.encode(query, "UTF-8"), apiKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(java.time.Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("Finnhub search failed: " + response.statusCode());
        }

        return response.body();
    }

    /**
     * Parse a double value from JSON by its key.
     * Example: parseJsonDouble('{"c":150.5,"d":-1.5}', '"c":') returns 150.5
     */
    private double parseJsonDouble(String json, String key) {
        try {
            int index = json.indexOf(key);
            if (index == -1)
                return 0.0;

            int start = index + key.length();
            // Skip whitespace
            while (start < json.length() && json.charAt(start) == ' ')
                start++;

            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') {
                end++;
            }

            String valueStr = json.substring(start, end).trim();
            if (valueStr.equals("null"))
                return 0.0;
            return Double.parseDouble(valueStr);
        } catch (Exception e) {
            return 0.0;
        }
    }

    /**
     * Gets historical price data (stub implementation).
     */
    @Override
    public String getHistoricalData(String symbol) throws Exception {
        // Finnhub historical data requires premium subscription
        return "{}";
    }
}
