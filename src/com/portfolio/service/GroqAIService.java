package com.portfolio.service;

import java.net.URI;
import java.net.http.*;
import org.json.*;
import com.portfolio.model.PortfolioItem;

/**
 * Groq AI Service for Natural Language Conversations
 * FREE, Fast, and Powerful
 */
public class GroqAIService {
    
    private static final String API_KEY = System.getenv("GROQ_API_KEY") != null ? 
        System.getenv("GROQ_API_KEY") : "YOUR_GROQ_API_KEY_HERE";  // Set via environment variable
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    
    private final HttpClient httpClient;
    private final PortfolioService portfolioService;
    private final java.util.List<JSONObject> conversationHistory;
    
    public GroqAIService(PortfolioService portfolioService) {
        this.httpClient = HttpClient.newHttpClient();
        this.portfolioService = portfolioService;
        this.conversationHistory = new java.util.ArrayList<>();
    }
    
    /**
     * Clear conversation history
     */
    public void clearHistory() {
        conversationHistory.clear();
    }
    
    /**
     * Have a conversation with Groq AI about portfolio
     * Maintains conversation history for context
     * @param userMessage What the user said
     * @return AI's response
     */
    public String chat(String userMessage) throws Exception {
        // Build context about user's portfolio
        String portfolioContext = buildPortfolioContext();
        
        // Create system prompt (only on first message)
        if (conversationHistory.isEmpty()) {
            String systemPrompt = "You are a helpful and friendly stock portfolio assistant named 'StockVault AI'. " +
                "You help users manage their stock portfolio through natural conversation. " +
                "Be conversational, warm, and concise. Keep responses under 40 words unless asked for details. " +
                "You can ask clarifying questions if needed. " +
                "Use emojis occasionally to be friendly. " +
                "Always use Indian Rupees (₹) for currency.\n\n" +
                "User's Current Portfolio:\n" + portfolioContext;
            
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            conversationHistory.add(systemMsg);
        }
        
        // Add user message to history
        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", userMessage);
        conversationHistory.add(userMsg);
        
        // Build request
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", "llama-3.3-70b-versatile");  // Fast and smart
        
        JSONArray messages = new JSONArray();
        for (JSONObject msg : conversationHistory) {
            messages.put(msg);
        }
        
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 150);  // Shorter responses
        
        // Send request
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Authorization", "Bearer " + API_KEY)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
            .build();
        
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() != 200) {
            throw new Exception("Groq API error: " + response.body());
        }
        
        // Parse response
        JSONObject json = new JSONObject(response.body());
        JSONArray choices = json.getJSONArray("choices");
        JSONObject firstChoice = choices.getJSONObject(0);
        JSONObject message = firstChoice.getJSONObject("message");
        String aiResponse = message.getString("content");
        
        // Add AI response to history
        JSONObject assistantMsg = new JSONObject();
        assistantMsg.put("role", "assistant");
        assistantMsg.put("content", aiResponse);
        conversationHistory.add(assistantMsg);
        
        // Keep history manageable (last 10 messages)
        if (conversationHistory.size() > 11) {  // 1 system + 10 messages
            // Keep system message, remove oldest user/assistant pair
            conversationHistory.remove(1);
            conversationHistory.remove(1);
        }
        
        return aiResponse.trim();
    }
    
    /**
     * Build context about user's portfolio for AI
     */
    private String buildPortfolioContext() {
        try {
            double totalValue = portfolioService.calculateCurrentValue();
            double investment = portfolioService.calculateTotalInvestment();
            double profitLoss = portfolioService.calculateProfitLoss();
            int stockCount = portfolioService.getPortfolioItems().size();
            
            StringBuilder context = new StringBuilder();
            context.append(String.format("Total Value: ₹%.2f\n", totalValue));
            context.append(String.format("Total Investment: ₹%.2f\n", investment));
            context.append(String.format("Profit/Loss: ₹%.2f\n", profitLoss));
            context.append(String.format("Number of Stocks: %d\n", stockCount));
            
            if (stockCount > 0) {
                context.append("\nStocks:\n");
                for (var item : portfolioService.getPortfolioItems()) {
                    context.append(String.format("- %s: %d shares @ ₹%.2f (Total: ₹%.2f)\n",
                        item.getStock().getSymbol(),
                        item.getQuantity(),
                        item.getStock().getCurrentPrice(),
                        item.getTotalValue()));
                }
            }
            
            return context.toString();
        } catch (Exception e) {
            return "Portfolio data unavailable.";
        }
    }
    
    /**
     * Execute portfolio actions based on AI understanding
     * @param userIntent What the user wants to do
     * @return Result message with action taken
     */
    public String executeAction(String userIntent) {
        try {
            // Check for "remove all stocks" or "sell everything" command
            String lowerIntent = userIntent.toLowerCase();
            if ((lowerIntent.contains("remove all") || lowerIntent.contains("sell all") || 
                 lowerIntent.contains("sell everything") || lowerIntent.contains("clear portfolio")) &&
                (lowerIntent.contains("stock") || lowerIntent.contains("share") || lowerIntent.contains("portfolio"))) {
                
                // Sell all stocks
                StringBuilder result = new StringBuilder("✅ Selling all stocks:\n");
                java.util.List<PortfolioItem> items = new java.util.ArrayList<>(portfolioService.getPortfolioItems());
                
                if (items.isEmpty()) {
                    return "❌ Your portfolio is already empty.";
                }
                
                for (PortfolioItem item : items) {
                    String symbol = item.getStock().getSymbol();
                    int quantity = item.getQuantity();
                    boolean success = portfolioService.sellAllStock(symbol);
                    if (success) {
                        result.append(String.format("• Sold all %d shares of %s\n", quantity, symbol));
                    }
                }
                
                return result.toString();
            }
            
            // Ask AI to extract action and parameters
            String prompt = "Extract the stock trading action from this request: \"" + userIntent + "\"\n\n" +
                "Respond with ONLY a JSON object. Examples:\n" +
                "{\"action\": \"buy\", \"symbol\": \"AAPL\", \"quantity\": 10, \"price\": 150}\n" +
                "{\"action\": \"sell\", \"symbol\": \"GOOGL\", \"quantity\": 5}\n" +
                "{\"action\": \"sell_all\", \"symbol\": \"TSLA\"}\n" +
                "{\"action\": \"show_portfolio\"}\n" +
                "{\"action\": \"show_profit\"}\n" +
                "{\"action\": \"refresh_prices\"}\n" +
                "{\"action\": \"unknown\"}\n\n" +
                "For sell actions, if user says 'all' or 'all shares', use sell_all action.";
            
            // Create a simple request without conversation history
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", "llama-3.3-70b-versatile");
            
            JSONArray messages = new JSONArray();
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", prompt);
            messages.put(userMsg);
            
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.3);  // Lower temperature for more precise extraction
            requestBody.put("max_tokens", 100);
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                return "❌ Error: Could not process action";
            }
            
            JSONObject json = new JSONObject(response.body());
            String aiResponse = json.getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content");
            
            // Extract JSON from response (might have extra text)
            int jsonStart = aiResponse.indexOf("{");
            int jsonEnd = aiResponse.lastIndexOf("}") + 1;
            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                aiResponse = aiResponse.substring(jsonStart, jsonEnd);
            }
            
            // Parse JSON response
            JSONObject action = new JSONObject(aiResponse);
            String actionType = action.getString("action");
            
            switch (actionType) {
                case "buy":
                    String buySymbol = action.getString("symbol").toUpperCase();
                    int buyQuantity = action.getInt("quantity");
                    double buyPrice = action.getDouble("price");
                    portfolioService.buyStock(buySymbol, buySymbol, buyQuantity, buyPrice);
                    return String.format("✅ Bought %d shares of %s at ₹%.2f\nTotal cost: ₹%.2f", 
                        buyQuantity, buySymbol, buyPrice, buyQuantity * buyPrice);
                    
                case "sell":
                    String sellSymbol = action.getString("symbol").toUpperCase();
                    int sellQuantity = action.getInt("quantity");
                    boolean sellSuccess = portfolioService.sellStock(sellSymbol, sellQuantity);
                    if (sellSuccess) {
                        return String.format("✅ Sold %d shares of %s", sellQuantity, sellSymbol);
                    } else {
                        return "❌ Could not sell stock. Check if you own it.";
                    }
                    
                case "sell_all":
                    String sellAllSymbol = action.getString("symbol").toUpperCase();
                    boolean sellAllSuccess = portfolioService.sellAllStock(sellAllSymbol);
                    if (sellAllSuccess) {
                        return String.format("✅ Sold all shares of %s", sellAllSymbol);
                    } else {
                        return "❌ Could not sell stock. Check if you own it.";
                    }
                    
                case "show_portfolio":
                    return buildPortfolioContext();
                    
                case "show_profit":
                    double profit = portfolioService.calculateProfitLoss();
                    return String.format("Your profit/loss is ₹%.2f", profit);
                    
                case "refresh_prices":
                    portfolioService.updateAllPrices();
                    return "✅ Prices updated!";
                    
                default:
                    return "I'm not sure how to do that. Try asking me to buy, sell, or show your portfolio.";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Error: " + e.getMessage();
        }
    }
}
