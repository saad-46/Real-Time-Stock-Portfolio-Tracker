package com.portfolio.service;

import com.portfolio.model.Stock;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class TwelveDataService implements StockPriceService {
    private static final String BASE_URL = "https://api.twelvedata.com";
    private final HttpClient httpClient;

    public TwelveDataService() {
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public double getCurrentPrice(String symbol) throws Exception {
        String apiKey = ApiKeyManager.getTwelveDataKey();
        String url = String.format("%s/price?symbol=%s&apikey=%s", BASE_URL, symbol.toUpperCase(), apiKey);
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().timeout(Duration.ofSeconds(10))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200)
            throw new Exception("TwelveData API request failed with status: " + response.statusCode());

        String body = response.body();
        if (body.contains("\"code\"") && body.contains("\"status\":\"error\""))
            throw new Exception("TwelveData error: " + body);

        double price = parsePrice(body);
        if (price <= 0)
            throw new Exception("TwelveData returned invalid price for " + symbol);
        return price;
    }

    @Override
    public void updateStockPrice(Stock stock) throws Exception {
        String apiKey = ApiKeyManager.getTwelveDataKey();
        String url = String.format("%s/quote?symbol=%s&apikey=%s", BASE_URL, stock.getSymbol().toUpperCase(), apiKey);
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().timeout(Duration.ofSeconds(10))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200)
            throw new Exception("TwelveData API failed: " + response.statusCode());
        String body = response.body();
        if (body.contains("\"code\"") && body.contains("\"status\":\"error\""))
            throw new Exception("TwelveData error for " + stock.getSymbol());

        double price = parseJsonStringValue(body, "\"close\":");
        double changePercent = parseJsonStringValue(body, "\"percent_change\":");
        double high = parseJsonStringValue(body, "\"high\":");
        double low = parseJsonStringValue(body, "\"low\":");

        if (price <= 0)
            price = parseJsonStringValue(body, "\"price\":");
        if (price <= 0)
            throw new Exception("TwelveData returned invalid price for " + stock.getSymbol());

        stock.setCurrentPrice(price);
        stock.setChangePercent(changePercent);
        stock.setHigh24h(high);
        stock.setLow24h(low);
    }

    @Override
    public String getHistoricalData(String symbol) throws Exception {
        return getHistoricalData(symbol, 30);
    }

    public String getHistoricalData(String symbol, int days) throws Exception {
        String apiKey = ApiKeyManager.getTwelveDataKey();
        String url = String.format("%s/time_series?symbol=%s&interval=1day&outputsize=%d&apikey=%s", BASE_URL,
                symbol.toUpperCase(), days, apiKey);
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().timeout(Duration.ofSeconds(15))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200)
            throw new Exception("TwelveData historical data failed: " + response.statusCode());
        return response.body();
    }

    private double parsePrice(String json) {
        try {
            String key = "\"price\":";
            int index = json.indexOf(key);
            if (index == -1)
                return 0.0;
            int start = index + key.length();
            while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"'))
                start++;
            int end = start;
            while (end < json.length() && json.charAt(end) != '"' && json.charAt(end) != ',' && json.charAt(end) != '}')
                end++;
            return Double.parseDouble(json.substring(start, end).trim());
        } catch (Exception e) {
            return 0.0;
        }
    }

    private double parseJsonStringValue(String json, String key) {
        try {
            int index = json.indexOf(key);
            if (index == -1)
                return 0.0;
            int start = index + key.length();
            while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"'))
                start++;
            int end = start;
            while (end < json.length() && json.charAt(end) != '"' && json.charAt(end) != ',' && json.charAt(end) != '}')
                end++;
            String valueStr = json.substring(start, end).trim();
            if (valueStr.isEmpty() || valueStr.equals("null"))
                return 0.0;
            return Double.parseDouble(valueStr);
        } catch (Exception e) {
            return 0.0;
        }
    }
}
