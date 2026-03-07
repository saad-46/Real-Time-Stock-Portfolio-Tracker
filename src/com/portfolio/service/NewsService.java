package com.portfolio.service;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

public class NewsService {
    private final HttpClient httpClient;
    private List<NewsItem> cachedNews;
    private long lastFetchTime = 0;

    public NewsService() {
        this.httpClient = HttpClient.newHttpClient();
        this.cachedNews = new ArrayList<>();
    }

    public List<NewsItem> fetchMarketNews() {
        try {
            List<NewsItem> news = fetchFromGNews();
            if (!news.isEmpty()) {
                cachedNews = news;
                lastFetchTime = System.currentTimeMillis();
                return cachedNews;
            }
        } catch (Exception ignored) {
        }

        try {
            List<NewsItem> news = fetchFromNewsData();
            if (!news.isEmpty()) {
                cachedNews = news;
                lastFetchTime = System.currentTimeMillis();
                return cachedNews;
            }
        } catch (Exception ignored) {
        }

        if (cachedNews.isEmpty())
            cachedNews = getDefaultNews();
        return cachedNews;
    }

    public List<NewsItem> getCachedNews() {
        return cachedNews.isEmpty() ? getDefaultNews() : cachedNews;
    }

    public long getLastFetchTime() {
        return lastFetchTime;
    }

    private List<NewsItem> fetchFromGNews() throws Exception {
        String apiKey = ApiKeyManager.getGNewsKey();
        String url = "https://gnews.io/api/v4/top-headlines?category=business&lang=en&max=10&token=" + apiKey;
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().timeout(Duration.ofSeconds(15))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200)
            throw new Exception("GNews API returned status: " + response.statusCode());
        return parseGNewsResponse(response.body());
    }

    private List<NewsItem> fetchFromNewsData() throws Exception {
        String apiKey = ApiKeyManager.getNewsDataKey();
        String url = "https://newsdata.io/api/1/news?apikey=" + apiKey + "&q=stock%20market&language=en&size=10";
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().timeout(Duration.ofSeconds(15))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200)
            throw new Exception("NewsData.io API returned status: " + response.statusCode());
        return parseNewsDataResponse(response.body());
    }

    private List<NewsItem> parseGNewsResponse(String json) {
        List<NewsItem> items = new ArrayList<>();
        try {
            int articlesIdx = json.indexOf("\"articles\":");
            if (articlesIdx == -1)
                return items;

            int searchFrom = articlesIdx;
            while (items.size() < 10) {
                int titleIdx = json.indexOf("\"title\":", searchFrom);
                if (titleIdx == -1)
                    break;

                String title = extractJsonString(json, titleIdx);
                int descIdx = json.indexOf("\"description\":", titleIdx);
                String description = descIdx != -1 ? extractJsonString(json, descIdx) : "";
                int urlIdx = json.indexOf("\"url\":", titleIdx);
                String articleUrl = urlIdx != -1 ? extractJsonString(json, urlIdx) : "";
                int imageIdx = json.indexOf("\"image\":", titleIdx);
                String imageUrl = imageIdx != -1 ? extractJsonString(json, imageIdx) : "";

                int sourceIdx = json.indexOf("\"source\":", titleIdx);
                String sourceName = "News";
                if (sourceIdx != -1) {
                    int nameIdx = json.indexOf("\"name\":", sourceIdx);
                    if (nameIdx != -1 && nameIdx < sourceIdx + 200)
                        sourceName = extractJsonString(json, nameIdx);
                }

                if (title != null && !title.isEmpty()) {
                    NewsItem item = new NewsItem(title, categorizeNews(title + " " + description),
                            analyzeSentiment(title + " " + description), articleUrl);
                    item.setSource(sourceName);
                    item.setDescription(description);
                    item.setImageUrl(imageUrl);
                    items.add(item);
                }
                searchFrom = titleIdx + title.length() + 20;
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    private List<NewsItem> parseNewsDataResponse(String json) {
        List<NewsItem> items = new ArrayList<>();
        try {
            int resultsIdx = json.indexOf("\"results\":");
            if (resultsIdx == -1)
                return items;

            int searchFrom = resultsIdx;
            while (items.size() < 10) {
                int titleIdx = json.indexOf("\"title\":", searchFrom);
                if (titleIdx == -1)
                    break;

                String title = extractJsonString(json, titleIdx);
                int linkIdx = json.indexOf("\"link\":", titleIdx);
                String articleUrl = linkIdx != -1 ? extractJsonString(json, linkIdx) : "";
                int descIdx = json.indexOf("\"description\":", titleIdx);
                String description = descIdx != -1 ? extractJsonString(json, descIdx) : "";
                int imageIdx = json.indexOf("\"image_url\":", titleIdx);
                String imageUrl = imageIdx != -1 ? extractJsonString(json, imageIdx) : "";
                int sourceIdx = json.indexOf("\"source_id\":", titleIdx);
                String sourceName = sourceIdx != -1 ? extractJsonString(json, sourceIdx) : "News";

                if (title != null && !title.isEmpty()) {
                    NewsItem item = new NewsItem(title, categorizeNews(title + " " + description),
                            analyzeSentiment(title + " " + description), articleUrl);
                    item.setSource(sourceName);
                    item.setDescription(description);
                    item.setImageUrl(imageUrl);
                    items.add(item);
                }
                searchFrom = titleIdx + title.length() + 20;
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    private String extractJsonString(String json, int keyIndex) {
        try {
            int colonIdx = json.indexOf(":", keyIndex);
            if (colonIdx == -1)
                return "";
            int startQuote = json.indexOf("\"", colonIdx + 1);
            if (startQuote == -1)
                return "";
            int endQuote = startQuote + 1;
            while (endQuote < json.length()) {
                if (json.charAt(endQuote) == '"' && json.charAt(endQuote - 1) != '\\')
                    break;
                endQuote++;
            }
            return json.substring(startQuote + 1, endQuote).replace("\\\"", "\"").replace("\\n", " ").replace("\\\\",
                    "\\");
        } catch (Exception e) {
            return "";
        }
    }

    private String categorizeNews(String text) {
        String lower = text.toLowerCase();
        if (lower.contains("tech") || lower.contains("ai") || lower.contains("software") || lower.contains("chip"))
            return "Technology";
        if (lower.contains("bank") || lower.contains("fed") || lower.contains("rate") || lower.contains("financial"))
            return "Finance";
        if (lower.contains("oil") || lower.contains("energy") || lower.contains("gas") || lower.contains("solar"))
            return "Energy";
        if (lower.contains("pharma") || lower.contains("health") || lower.contains("drug") || lower.contains("medical"))
            return "Healthcare";
        if (lower.contains("auto") || lower.contains("ev") || lower.contains("car") || lower.contains("vehicle"))
            return "Automobile";
        if (lower.contains("crypto") || lower.contains("bitcoin") || lower.contains("blockchain"))
            return "Crypto";
        return "Market";
    }

    private String analyzeSentiment(String text) {
        String lower = text.toLowerCase();
        int bullish = 0, bearish = 0;
        String[] bullWords = { "surge", "gain", "rise", "rally", "high", "record", "growth", "profit", "boom", "up",
                "positive", "strong", "beat", "upgrade" };
        String[] bearWords = { "drop", "fall", "crash", "dip", "low", "loss", "fear", "concern", "decline", "down",
                "negative", "weak", "miss", "downgrade", "sell" };
        for (String word : bullWords)
            if (lower.contains(word))
                bullish++;
        for (String word : bearWords)
            if (lower.contains(word))
                bearish++;
        return bullish > bearish ? "Bullish" : (bearish > bullish ? "Bearish" : "Neutral");
    }

    private List<NewsItem> getDefaultNews() {
        List<NewsItem> items = new ArrayList<>();
        items.add(new NewsItem("Click 'Refresh News' to fetch latest market news", "Info", "Neutral", ""));
        items.add(new NewsItem("News is fetched on-demand to conserve API quota", "Info", "Neutral", ""));
        return items;
    }

    public static class NewsItem {
        private String title, category, sentiment, url, source, description, imageUrl, publishedAt;

        public NewsItem(String title, String category, String sentiment, String url) {
            this.title = title;
            this.category = category;
            this.sentiment = sentiment;
            this.url = url;
            this.source = "";
            this.description = "";
            this.imageUrl = "";
            this.publishedAt = "";
        }

        public String getTitle() {
            return title;
        }

        public String getCategory() {
            return category;
        }

        public String getSentiment() {
            return sentiment;
        }

        public String getUrl() {
            return url;
        }

        public String getSource() {
            return source;
        }

        public void setSource(String source) {
            this.source = source;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getImageUrl() {
            return imageUrl;
        }

        public void setImageUrl(String imageUrl) {
            this.imageUrl = imageUrl;
        }

        public String getPublishedAt() {
            return publishedAt;
        }

        public void setPublishedAt(String publishedAt) {
            this.publishedAt = publishedAt;
        }
    }
}
