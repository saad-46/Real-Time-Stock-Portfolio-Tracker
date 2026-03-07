package com.portfolio.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * CurrencyService - Fetches live exchange rates.
 * Used to convert portfolio values to the user's preferred currency (INR, USD,
 * EUR, SAR).
 */
public class CurrencyService {

    private static final String API_KEY = ApiKeyManager.getExchangeRateApiKey();
    private static final String API_URL = "https://v6.exchangerate-api.com/v6/" + API_KEY + "/latest/";
    private final HttpClient client;

    public CurrencyService() {
        this.client = HttpClient.newHttpClient();
    }

    /**
     * Converts an amount from one currency to another using live rates.
     */
    public double convert(double amount, String fromCurrency, String toCurrency) {
        if (fromCurrency.equals(toCurrency)) {
            return amount;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + fromCurrency))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String body = response.body();
                // Simple parsing without external json library dependencies considering the
                // simple response format
                String searchString = "\"" + toCurrency + "\":";
                int index = body.indexOf(searchString);
                if (index != -1) {
                    int startIndex = index + searchString.length();
                    int endIndex = body.indexOf(",", startIndex);
                    if (endIndex == -1)
                        endIndex = body.indexOf("}", startIndex); // If it's the last item

                    if (endIndex != -1) {
                        String rateStr = body.substring(startIndex, endIndex).trim();
                        double rate = Double.parseDouble(rateStr);
                        return amount * rate;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Failed to fetch exchange rate: " + e.getMessage());
        }

        // Fallback static rates if API fails
        return fallbackConvert(amount, fromCurrency, toCurrency);
    }

    // Hardcoded fallback rates
    private double fallbackConvert(double amount, String from, String to) {
        // Base USD
        double inUsd = amount;
        if (from.equals("INR"))
            inUsd = amount / 83.0;
        else if (from.equals("EUR"))
            inUsd = amount / 0.92;
        else if (from.equals("SAR"))
            inUsd = amount / 3.75;

        if (to.equals("USD"))
            return inUsd;
        if (to.equals("INR"))
            return inUsd * 83.0;
        if (to.equals("EUR"))
            return inUsd * 0.92;
        if (to.equals("SAR"))
            return inUsd * 3.75;

        return amount; // Unsupported
    }
}
