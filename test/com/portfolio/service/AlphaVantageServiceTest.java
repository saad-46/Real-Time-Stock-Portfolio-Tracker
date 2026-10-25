package com.portfolio.service;

import com.portfolio.model.Stock;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AlphaVantageServiceTest {

    @Test
    void parsesValidQuote() {
        Stock s = AlphaVantageService.parseGlobalQuote("AAPL", "{\"Global Quote\":{\"01. symbol\":\"AAPL\",\"05. price\":\"189.4300\"}}");
        assertEquals("AAPL", s.getSymbol());
        assertEquals(189.43, s.getCurrentPrice(), 1e-9);
    }

    @Test
    void rateLimitNoteIsUnavailableNotMockPrice() {
        assertThrows(PriceUnavailableException.class, () ->
                AlphaVantageService.parseGlobalQuote("AAPL", "{\"Note\":\"Thank you for using Alpha Vantage! call frequency is 5 per minute\"}"));
    }

    @Test
    void informationMessageIsUnavailable() {
        assertThrows(PriceUnavailableException.class, () ->
                AlphaVantageService.parseGlobalQuote("AAPL", "{\"Information\":\"API rate limit reached\"}"));
    }

    @Test
    void emptyQuoteMeansSymbolNotFound() {
        assertNull(AlphaVantageService.parseGlobalQuote("ZZZZ", "{\"Global Quote\":{}}"));
    }

    @Test
    void errorMessageMeansInvalidSymbol() {
        assertNull(AlphaVantageService.parseGlobalQuote("ZZZZ", "{\"Error Message\":\"Invalid API call\"}"));
    }

    @Test
    void malformedAndUnexpectedBodiesAreUnavailable() {
        assertThrows(PriceUnavailableException.class, () -> AlphaVantageService.parseGlobalQuote("A", "<html>oops"));
        assertThrows(PriceUnavailableException.class, () -> AlphaVantageService.parseGlobalQuote("A", "{\"foo\":1}"));
    }

    @Test
    void invalidPricesAreRejected() {
        for (String p : new String[]{"0", "-5", "abc", "", "NaN", "Infinity"}) {
            assertThrows(PriceUnavailableException.class, () ->
                    AlphaVantageService.parseGlobalQuote("A", "{\"Global Quote\":{\"05. price\":\"" + p + "\"}}"), "price=" + p);
        }
    }

    @Test
    void missingApiKeyFailsLoudlyWithoutNetwork() {
        assertThrows(PriceUnavailableException.class, () -> new AlphaVantageService(null).getStock("AAPL"));
        assertThrows(PriceUnavailableException.class, () -> new AlphaVantageService("  ").getStock("AAPL"));
    }

    /** Body captured from the live service on 2026-10-24 (pretty-printed by the provider). */
    @Test
    void parsesQuoteExactlyAsTheLiveProviderFormatsIt() {
        String live = "{\n    \"Global Quote\": {\n        \"01. symbol\": \"AAPL\",\n        \"02. open\": \"349.1150\",\n"
                + "        \"03. high\": \"351.7300\",\n        \"04. low\": \"345.7500\",\n        \"05. price\": \"347.7250\",\n"
                + "        \"06. volume\": \"39539690\",\n        \"07. latest trading day\": \"2026-10-23\",\n"
                + "        \"08. previous close\": \"348.2200\",\n        \"09. change\": \"-0.4950\",\n"
                + "        \"10. change percent\": \"-0.1422%\"\n    }\n}\n";
        Stock s = AlphaVantageService.parseGlobalQuote("AAPL", live);
        assertEquals(347.725, s.getCurrentPrice(), 1e-9);
    }
}
