package com.portfolio;

import com.portfolio.service.NewsService;
import java.util.List;

public class VerifyNewsImages {
    public static void main(String[] args) {
        System.out.println("🔍 Verifying News Image Extraction...");
        NewsService newsService = new NewsService();

        // Test Mock GNews Response
        System.out.println("\n[TEST] Mock GNews Response Parsing");
        String mockGNews = "{\"articles\":[{\"title\":\"Test GNews Article\",\"description\":\"Desc\",\"url\":\"http://test.com\",\"image\":\"http://test.com/img.jpg\",\"source\":{\"name\":\"Test Source\"}}]}";
        try {
            java.lang.reflect.Method method = NewsService.class.getDeclaredMethod("parseGNewsResponse", String.class);
            method.setAccessible(true);
            List<NewsService.NewsItem> items = (List<NewsService.NewsItem>) method.invoke(newsService, mockGNews);
            if (!items.isEmpty() && "http://test.com/img.jpg".equals(items.get(0).getImageUrl())) {
                System.out.println("✅ GNews Image URL parsed correctly: " + items.get(0).getImageUrl());
            } else {
                System.out.println("❌ GNews Image URL parsing FAILED. Found: "
                        + (items.isEmpty() ? "None" : items.get(0).getImageUrl()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Test Mock NewsData.io Response
        System.out.println("\n[TEST] Mock NewsData.io Response Parsing");
        String mockNewsData = "{\"results\":[{\"title\":\"Test NewsData Article\",\"link\":\"http://test.com\",\"description\":\"Desc\",\"image_url\":\"http://test.com/img2.jpg\",\"source_id\":\"Test Source\"}]}";
        try {
            java.lang.reflect.Method method = NewsService.class.getDeclaredMethod("parseNewsDataResponse",
                    String.class);
            method.setAccessible(true);
            List<NewsService.NewsItem> items = (List<NewsService.NewsItem>) method.invoke(newsService, mockNewsData);
            if (!items.isEmpty() && "http://test.com/img2.jpg".equals(items.get(0).getImageUrl())) {
                System.out.println("✅ NewsData.io Image URL parsed correctly: " + items.get(0).getImageUrl());
            } else {
                System.out.println("❌ NewsData.io Image URL parsing FAILED. Found: "
                        + (items.isEmpty() ? "None" : items.get(0).getImageUrl()));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        System.out.println("\n🔍 Running Live Fetch (might fail due to quota)...");
        List<NewsService.NewsItem> articles = newsService.fetchMarketNews();

        if (articles == null || articles.isEmpty()) {
            System.out.println("❌ No articles fetched.");
            return;
        }

        System.out.println("✅ Articles fetched: " + articles.size());
        int articlesWithImages = 0;
        for (NewsService.NewsItem item : articles) {
            if (item.getImageUrl() != null && !item.getImageUrl().isEmpty()) {
                System.out.println("--- Found Image for: " + item.getTitle());
                System.out.println("    URL: " + item.getImageUrl());
                articlesWithImages++;
            }
        }

        System.out.println("\n📊 SUMMARY:");
        System.out.println("Total Articles: " + articles.size());
        System.out.println("Articles with Images: " + articlesWithImages);

        if (articlesWithImages > 0) {
            System.out.println("🚀 SUCCESS: News images are Being extracted correctly from live API!");
        } else {
            System.out
                    .println("ℹ️ NOTE: No live images found (API quota likely empty or no images in current results).");
        }
    }
}
