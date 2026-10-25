package com.portfolio.service;

import java.net.URI;
import java.net.http.*;
import java.util.*;
import com.portfolio.model.PortfolioItem;

/**
 * Groq AI Service for Natural Language Conversations
 * Library-independent JSON handling
 */
public class GroqAIService {

    private static final String API_KEY = System.getenv("GROQ_API_KEY") != null ? System.getenv("GROQ_API_KEY")
            : ApiKeyManager.getGroqKey();
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";

    private final HttpClient httpClient;
    private final PortfolioService portfolioService;
    private final List<Message> conversationHistory;

    public GroqAIService(PortfolioService portfolioService) {
        this.httpClient = Net.client();
        this.portfolioService = portfolioService;
        this.conversationHistory = new ArrayList<>();
    }

    public void clearHistory() {
        conversationHistory.clear();
    }

    public String chat(String userMessage) throws Exception {
        String portfolioContext = buildPortfolioContext();
        if (conversationHistory.isEmpty()) {
            conversationHistory.add(new Message("system",
                    "You are 'StockVault AI', a warm and concise portfolio assistant. Indian Rupees (₹) focus. " +
                            "IMPORTANT: Do NOT use the words 'Buy' or 'Sell' in market condition analysis or recommendations. "
                            +
                            "Use terms like 'Add potential', 'Reduce exposure', 'Strong position', 'Underperforming', etc. "
                            +
                            "Respond with professional insights. Portfolio:\n"
                            + portfolioContext));
        }
        conversationHistory.add(new Message("user", userMessage));

        // Build JSON manually to avoid library issues
        StringBuilder json = new StringBuilder();
        json.append("{ \"model\": \"llama-3.3-70b-versatile\", \"messages\": [");
        for (int i = 0; i < conversationHistory.size(); i++) {
            Message m = conversationHistory.get(i);
            json.append(String.format("{\"role\":\"%s\",\"content\":\"%s\"}", m.role, escapeJson(m.content)));
            if (i < conversationHistory.size() - 1)
                json.append(",");
        }
        json.append("], \"temperature\": 0.7, \"max_tokens\": 150 }");

        HttpRequest request = HttpRequest.newBuilder().timeout(Net.REQUEST_TIMEOUT)
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200)
            throw new Exception("AI error: " + response.body());

        String aiResponse = parseAIResponse(response.body());
        conversationHistory.add(new Message("assistant", aiResponse));

        // Keep history manageable
        if (conversationHistory.size() > 11) {
            conversationHistory.remove(1);
            conversationHistory.remove(1);
        }

        return aiResponse.trim();
    }

    public String getRecommendations() throws Exception {
        String portfolioContext = buildPortfolioContext();
        String prompt = "As a professional financial advisor, analyze this portfolio and provide 3-5 high-quality recommendations in Markdown. "
                +
                "IMPORTANT: Do NOT use the words 'Buy' or 'Sell'. Use modern financial terminology instead. Portfolio:\n"
                + portfolioContext;

        String json = String.format(
                "{ \"model\": \"llama-3.3-70b-versatile\", \"messages\": [{\"role\":\"user\",\"content\":\"%s\"}], \"temperature\": 0.6, \"max_tokens\": 800 }",
                escapeJson(prompt));

        HttpRequest request = HttpRequest.newBuilder().timeout(Net.REQUEST_TIMEOUT)
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200)
            throw new Exception("AI Recommendation error");

        return parseAIResponse(response.body());
    }

    /** What the assistant understood the user to want. Nothing has happened yet; see {@link #perform}. */
    public static final class AIAction {
        public enum Kind { SELL_ALL, BUY, SELL, SHOW_PORTFOLIO, UNCLEAR }

        public final Kind kind;
        public final String symbol;
        public final int quantity;
        public final double price;
        public final String message;

        AIAction(Kind kind, String symbol, int quantity, double price, String message) {
            this.kind = kind;
            this.symbol = symbol;
            this.quantity = quantity;
            this.price = price;
            this.message = message;
        }

        /** True for actions that change money or holdings; the caller must show {@link #describe} and get a yes first. */
        public boolean requiresConfirmation() {
            return kind == Kind.SELL_ALL || kind == Kind.BUY || kind == Kind.SELL;
        }

        /** Plain-language description of exactly what will be done, for the confirmation dialog. */
        public String describe(PortfolioService portfolio) {
            switch (kind) {
                case SELL_ALL:
                    return "SELL EVERYTHING: all shares of all " + portfolio.getAllPortfolioItems().size()
                            + " holding(s), each at a live market quote obtained at execution time.";
                case BUY:
                    return String.format("BUY %d shares of %s at %.2f per share (the price from your request, in %s), paid from your wallet.",
                            quantity, symbol, price, portfolio.getBaseCurrency());
                case SELL:
                    return String.format("SELL %d shares of %s at a live market quote obtained at execution time.", quantity, symbol);
                default:
                    return message == null ? "" : message;
            }
        }
    }

    static final int MAX_AI_QUANTITY = 1_000_000;

    static boolean isSellAllIntent(String userIntent) {
        String t = userIntent == null ? "" : userIntent.toLowerCase();
        return t.contains("sell all") || t.contains("clear portfolio") || t.contains("remove all");
    }

    /**
     * Strictly interpret the model's reply (a JSON object). Only an explicit "action" of buy / sell / show_portfolio is
     * accepted; the symbol must look like a ticker, the quantity a positive whole number (capped) and a buy needs a
     * positive finite price. Anything else is UNCLEAR, never a guess.
     */
    static AIAction interpret(String aiReply) {
        try {
            String reply = aiReply == null ? "" : aiReply;
            int open = reply.indexOf('{');
            int close = reply.lastIndexOf('}');
            if (open < 0 || close <= open) {
                return unclear("I couldn't understand that command.");
            }
            org.json.JSONObject o = new org.json.JSONObject(reply.substring(open, close + 1));
            String action = o.optString("action", "").trim().toLowerCase();
            if (action.equals("show_portfolio")) {
                return new AIAction(AIAction.Kind.SHOW_PORTFOLIO, null, 0, 0, null);
            }
            if (!action.equals("buy") && !action.equals("sell")) {
                return unclear("I couldn't understand that command.");
            }
            String symbol = o.optString("symbol", "").trim().toUpperCase();
            if (!symbol.matches("[A-Z0-9][A-Z0-9.\\-]{0,11}")) {
                return unclear("❌ Could not understand the " + action + " command. Please specify: '"
                        + (action.equals("buy") ? "Buy [quantity] [symbol] at [price]" : "Sell [quantity] [symbol]") + "'");
            }
            int quantity = o.optInt("quantity", 0);
            if (quantity <= 0 || quantity > MAX_AI_QUANTITY) {
                return unclear("❌ The quantity must be a whole number from 1 to " + MAX_AI_QUANTITY + ".");
            }
            if (action.equals("sell")) {
                return new AIAction(AIAction.Kind.SELL, symbol, quantity, 0, null);
            }
            double price = o.optDouble("price", 0);
            if (!(price > 0) || Double.isInfinite(price)) {
                return unclear("❌ No price was given. Please say: 'Buy [quantity] [symbol] at [price]'");
            }
            return new AIAction(AIAction.Kind.BUY, symbol, quantity, price, null);
        } catch (org.json.JSONException e) {
            return unclear("I couldn't understand that command.");
        }
    }

    private static AIAction unclear(String message) {
        return new AIAction(AIAction.Kind.UNCLEAR, null, 0, 0, message);
    }

    /**
     * Work out what the user wants WITHOUT doing it (one model call for anything but "sell all"). The caller shows
     * {@link AIAction#describe} and, for actions that {@link AIAction#requiresConfirmation}, asks first.
     */
    public AIAction planAction(String userIntent) throws Exception {
        if (isSellAllIntent(userIntent)) {
            return new AIAction(AIAction.Kind.SELL_ALL, null, 0, 0, null);
        }
        String prompt = "Extract trading action from: \"" + userIntent
                + "\". Respond ONLY with JSON: {\"action\":\"buy|sell|show_portfolio\",\"symbol\":\"TICKER\",\"quantity\":10,\"price\":150}";
        String json = String.format(
                "{ \"model\": \"llama-3.3-70b-versatile\", \"messages\": [{\"role\":\"user\",\"content\":\"%s\"}], \"temperature\": 0.1, \"max_tokens\": 100 }",
                escapeJson(prompt));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL))
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(java.time.Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            // the status only: the body can echo request details
            throw new Exception("The AI service returned HTTP " + response.statusCode());
        }
        return interpret(parseAIResponse(response.body()));
    }

    /** Carry out a plan through the normal validated trading rules. Reports what actually happened. */
    public String perform(AIAction action) {
        switch (action.kind) {
            case SELL_ALL: {
                List<PortfolioItem> items = new ArrayList<>(portfolioService.getPortfolioItems());
                if (items.isEmpty())
                    return "❌ Portfolio is already empty.";
                StringBuilder result = new StringBuilder("✅ Selling all stocks:\n");
                for (PortfolioItem item : items) {
                    String sym = item.getStock().getSymbol();
                    // Each sale needs a validated live quote; report what actually happened, not what was asked.
                    if (portfolioService.sellAllStock(sym)) {
                        result.append("• Sold all of ").append(sym).append("\n");
                    } else {
                        result.append("• NOT sold: ").append(sym).append(" (")
                                .append(portfolioService.getLastTradeProblem()).append(")\n");
                    }
                }
                return result.toString();
            }
            case BUY:
                try {
                    portfolioService.buyStock(action.symbol, action.symbol, action.quantity, action.price);
                } catch (IllegalArgumentException | IllegalStateException e) {
                    // includes InsufficientFundsException: nothing was bought and no money moved
                    return "❌ Not bought: " + e.getMessage();
                }
                return String.format("✅ Bought %d %s @ %.2f", action.quantity, action.symbol, action.price);
            case SELL:
                if (!portfolioService.sellStock(action.symbol, action.quantity)) {
                    return "❌ Not sold: " + portfolioService.getLastTradeProblem();
                }
                return String.format("✅ Sold %d %s", action.quantity, action.symbol);
            case SHOW_PORTFOLIO:
                return buildPortfolioContext();
            default:
                return action.message != null ? action.message
                        : "❌ I couldn't understand that command. Try: 'Buy 10 AAPL at 150' or 'Sell 5 GOOGL'";
        }
    }

    /**
     * Plan and perform in one step WITHOUT any confirmation.
     *
     * @deprecated unsafe for interactive use: call {@link #planAction}, show {@link AIAction#describe}, and only then
     *             {@link #perform}. The dashboard does.
     */
    @Deprecated
    public String executeAction(String userIntent) {
        try {
            return perform(planAction(userIntent));
        } catch (Exception e) {
            return "❌ Error: " + e.getMessage();
        }
    }

    private String buildPortfolioContext() {
        try {
            double totalValue = portfolioService.calculateCurrentValue();
            double profitLoss = portfolioService.calculateProfitLoss();
            StringBuilder context = new StringBuilder();
            context.append(String.format("Value: ₹%.2f, P/L: ₹%.2f%n", totalValue, profitLoss));
            if (portfolioService.profitLossChecked().approximate()) {
                context.append("(These totals are approximate: fixed exchange rates are in use because live rates are unavailable.)")
                        .append(System.lineSeparator());
            }
            for (PortfolioItem item : portfolioService.getPortfolioItems()) {
                context.append(String.format("- %s: %d shares @ ₹%.2f%n",
                        item.getStock().getSymbol(), item.getQuantity(), item.getStock().getCurrentPrice()));
            }
            return context.toString();
        } catch (Exception e) {
            return "Portfolio data unavailable.";
        }
    }

    private String parseAIResponse(String body) {
        try {
            // Find the content field in the JSON response
            int contentIdx = body.indexOf("\"content\":");
            if (contentIdx == -1) {
                return "AI response error: No content found.";
            }
            
            // Find the start of the content string
            int start = body.indexOf("\"", contentIdx + 10) + 1;
            if (start == 0) {
                return "AI response error: Invalid format.";
            }
            
            // Find the end of the content string (before the next field)
            int end = start;
            boolean escaped = false;
            while (end < body.length()) {
                char c = body.charAt(end);
                if (c == '\\' && !escaped) {
                    escaped = true;
                } else if (c == '"' && !escaped) {
                    break;
                } else {
                    escaped = false;
                }
                end++;
            }
            
            if (end >= body.length()) {
                return "AI response error: Incomplete response.";
            }
            
            String content = body.substring(start, end);
            
            // Unescape the JSON string
            content = content.replace("\\n", "\n")
                           .replace("\\\"", "\"")
                           .replace("\\\\", "\\")
                           .replace("\\t", "\t");
            
            return content.trim();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error parsing AI response: " + e.getMessage();
        }
    }

    private String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                 .replace("\"", "\\\"")
                 .replace("\n", "\\n")
                 .replace("\r", "\\r")
                 .replace("\t", "\\t");
    }

    private String extractField(String json, String field) {
        int idx = json.indexOf("\"" + field + "\"");
        if (idx == -1)
            return "0";
        int start = json.indexOf(":", idx) + 1;
        int end = json.indexOf(",", start);
        if (end == -1)
            end = json.indexOf("}", start);
        String val = json.substring(start, end).trim().replace("\"", "");
        return val;
    }

    /**
     * Analyze a specific stock using Groq AI with structured bullet format
     * @param symbol Stock symbol (e.g., "AAPL")
     * @param currentPrice Current stock price
     * @return AI-generated analysis in structured bullet format
     */
    public String analyzeStock(String symbol, double currentPrice) throws Exception {
        String prompt = String.format(
            "Analyze %s stock currently trading at ₹%.2f.%n%n" +
            "Provide analysis in this EXACT format with bullet points:%n%n" +
            "📊 CURRENT POSITION:%n" +
            "• [Brief market position statement]%n%n" +
            "💪 KEY STRENGTHS:%n" +
            "• [Strength 1]%n" +
            "• [Strength 2]%n%n" +
            "⚠️ RISKS:%n" +
            "• [Risk 1]%n" +
            "• [Risk 2]%n%n" +
            "🎯 OUTLOOK:%n" +
            "• Short-term: [Brief outlook]%n" +
            "• Long-term: [Brief outlook]%n%n" +
            "💡 RECOMMENDATION:%n" +
            "• [Clear recommendation]%n%n" +
            "IMPORTANT: Use professional financial terminology. Do NOT use 'Buy' or 'Sell'. " +
            "Use terms like 'Strong position', 'Consider adding', 'Reduce exposure', etc. Keep it concise.",
            symbol, currentPrice
        );

        String json = String.format(
            "{ \"model\": \"llama-3.3-70b-versatile\", \"messages\": [{\"role\":\"user\",\"content\":\"%s\"}], \"temperature\": 0.7, \"max_tokens\": 500 }",
            escapeJson(prompt)
        );

        HttpRequest request = HttpRequest.newBuilder().timeout(Net.REQUEST_TIMEOUT)
            .uri(URI.create(API_URL))
            .header("Authorization", "Bearer " + API_KEY)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new Exception("AI Stock Analysis error: " + response.statusCode());
        }

        String aiResponse = parseAIResponse(response.body());
        
        // If parsing failed and we got JSON, return error
        if (aiResponse.contains("\"content\"") || aiResponse.contains("choices")) {
            return "Error: Unable to parse AI response. Please try again.";
        }
        
        return aiResponse;
    }
    private static class Message {
        String role, content;

        Message(String r, String c) {
            this.role = r;
            this.content = c;
        }
    }
}
