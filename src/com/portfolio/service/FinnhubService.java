package com.portfolio.service;

import com.portfolio.model.Stock;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class FinnhubService implements StockPriceService {
    private static final String BASE_URL = "https://finnhub.io/api/v1";
    private final HttpClient httpClient;

    public FinnhubService() {
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public double getCurrentPrice(String symbol) throws Exception {
        String apiKey = ApiKeyManager.getFinnhubKey();
        String url = String.format("%s/quote?symbol=%s&token=%s", BASE_URL, symbol.toUpperCase(), apiKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(10))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new Exception("Finnhub API request failed with status: " + response.statusCode());
        }

        double price = parseJsonDouble(response.body(), "\"c\":");
        if (price <= 0)
            throw new Exception("Finnhub returned invalid price for " + symbol + ": " + price);
        return price;
    }

    @Override
    public void updateStockPrice(Stock stock) throws Exception {
        String apiKey = ApiKeyManager.getFinnhubKey();
        String quoteUrl = String.format("%s/quote?symbol=%s&token=%s", BASE_URL, stock.getSymbol().toUpperCase(),
                apiKey);
        HttpRequest quoteRequest = HttpRequest.newBuilder().uri(URI.create(quoteUrl)).GET()
                .timeout(Duration.ofSeconds(10)).build();
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

        if (stock.getName() == null || stock.getName().equals(stock.getSymbol()) || stock.getMarketCap() == null) {
            String profileUrl = String.format("%s/stock/profile2?symbol=%s&token=%s", BASE_URL,
                    stock.getSymbol().toUpperCase(), apiKey);
            HttpRequest profileRequest = HttpRequest.newBuilder().uri(URI.create(profileUrl)).GET()
                    .timeout(Duration.ofSeconds(10)).build();
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

    public String searchStocks(String query) throws Exception {
        String apiKey = ApiKeyManager.getFinnhubKey();
        String url = String.format("%s/search?q=%s&token=%s", BASE_URL, java.net.URLEncoder.encode(query, "UTF-8"),
                apiKey);
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().timeout(Duration.ofSeconds(10))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200)
            throw new Exception("Finnhub search failed: " + response.statusCode());
        return response.body();
    }

    private double parseJsonDouble(String json, String key) {
        try {
            int index = json.indexOf(key);
            if (index == -1)
                return 0.0;
            int start = index + key.length();
            while (start < json.length() && json.charAt(start) == ' ')
                start++;
            int end = start;
            while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}')
                end++;
            String valueStr = json.substring(start, end).trim();
            if (valueStr.equals("null"))
                return 0.0;
            return Double.parseDouble(valueStr);
        } catch (Exception e) {
            return 0.0;
        }
    }

    @Override
    public String getHistoricalData(String symbol) throws Exception {
        return "{}";
    }
}
