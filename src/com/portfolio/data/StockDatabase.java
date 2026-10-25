package com.portfolio.data;

import java.util.*;

/**
 * StockDatabase - Comprehensive list of 100+ stocks across all sectors
 * Real stock symbols from major exchanges (US, India, Global)
 */
public class StockDatabase {
    
    /**
     * Stock data format: {Symbol, Name, BasePrice, ChangePercent, Sector, MarketCap}
     * NOTE: BasePrice and ChangePercent are SAMPLE numbers, not market data. They are never displayed as quotes;
     * the UI shows a real price only when the portfolio/watchlist holds a provider-reported one.
     */
    private static final Object[][] ALL_STOCKS = {
        // === TECHNOLOGY (IT) ===
        { "AAPL", "Apple Inc", 198.45, "+1.23%", "IT", "Large Cap" },
        { "GOOGL", "Alphabet Inc", 142.50, "+0.87%", "IT", "Large Cap" },
        { "MSFT", "Microsoft Corp", 415.32, "+2.14%", "IT", "Large Cap" },
        { "NVDA", "NVIDIA Corp", 875.50, "+5.67%", "IT", "Large Cap" },
        { "META", "Meta Platforms", 505.00, "+3.44%", "IT", "Large Cap" },
        { "AMD", "Advanced Micro Devices", 178.40, "+4.12%", "IT", "Large Cap" },
        { "INTC", "Intel Corporation", 42.15, "-1.23%", "IT", "Large Cap" },
        { "CRM", "Salesforce", 285.60, "+2.34%", "IT", "Large Cap" },
        { "ORCL", "Oracle Corporation", 125.80, "+1.45%", "IT", "Large Cap" },
        { "ADBE", "Adobe Inc", 565.20, "+0.98%", "IT", "Large Cap" },
        { "CSCO", "Cisco Systems", 52.30, "+0.67%", "IT", "Large Cap" },
        { "AVGO", "Broadcom Inc", 1245.00, "+3.21%", "IT", "Large Cap" },
        { "TXN", "Texas Instruments", 185.40, "+1.12%", "IT", "Large Cap" },
        { "QCOM", "Qualcomm Inc", 165.80, "+2.45%", "IT", "Large Cap" },
        { "SNOW", "Snowflake Inc", 185.30, "+4.56%", "IT", "Mid Cap" },
        { "PLTR", "Palantir Technologies", 28.45, "+6.78%", "IT", "Mid Cap" },
        { "CRWD", "CrowdStrike Holdings", 285.60, "+3.89%", "IT", "Mid Cap" },
        { "ZS", "Zscaler Inc", 195.40, "+2.34%", "IT", "Mid Cap" },
        { "DDOG", "Datadog Inc", 125.80, "+1.67%", "IT", "Mid Cap" },
        { "NET", "Cloudflare Inc", 85.20, "+3.45%", "IT", "Mid Cap" },
        
        // === E-COMMERCE & RETAIL ===
        { "AMZN", "Amazon.com Inc", 178.50, "+1.55%", "FMCG", "Large Cap" },
        { "SHOP", "Shopify Inc", 78.90, "+2.34%", "FMCG", "Mid Cap" },
        { "WMT", "Walmart Inc", 165.40, "+0.89%", "FMCG", "Large Cap" },
        { "TGT", "Target Corporation", 145.20, "+1.23%", "FMCG", "Large Cap" },
        { "COST", "Costco Wholesale", 785.60, "+1.45%", "FMCG", "Large Cap" },
        { "HD", "Home Depot", 385.20, "+0.98%", "FMCG", "Large Cap" },
        { "LOW", "Lowe's Companies", 245.80, "+1.12%", "FMCG", "Large Cap" },
        
        // === AUTOMOTIVE ===
        { "TSLA", "Tesla Inc", 185.20, "-3.21%", "Automobile", "Large Cap" },
        { "F", "Ford Motor Company", 12.45, "+1.89%", "Automobile", "Large Cap" },
        { "GM", "General Motors", 38.60, "+2.34%", "Automobile", "Large Cap" },
        { "RIVN", "Rivian Automotive", 18.75, "+5.67%", "Automobile", "Mid Cap" },
        { "LCID", "Lucid Group Inc", 3.45, "+8.90%", "Automobile", "Small Cap" },
        { "NIO", "NIO Inc", 8.90, "+4.56%", "Automobile", "Mid Cap" },
        { "XPEV", "XPeng Inc", 12.30, "+3.45%", "Automobile", "Mid Cap" },
        
        // === MEDIA & ENTERTAINMENT ===
        { "NFLX", "Netflix Inc", 615.30, "-0.92%", "Telecom", "Large Cap" },
        { "DIS", "Walt Disney Company", 95.40, "+1.23%", "Telecom", "Large Cap" },
        { "PARA", "Paramount Global", 15.60, "-2.34%", "Telecom", "Mid Cap" },
        { "WBD", "Warner Bros Discovery", 10.80, "-1.45%", "Telecom", "Mid Cap" },
        { "SPOT", "Spotify Technology", 285.40, "+3.67%", "Telecom", "Mid Cap" },
        { "RBLX", "Roblox Corporation", 42.50, "+4.89%", "Telecom", "Mid Cap" },
        
        // === FINANCIAL SERVICES (BANKING) ===
        { "JPM", "JPMorgan Chase", 185.60, "+1.45%", "Banking", "Large Cap" },
        { "BAC", "Bank of America", 35.80, "+0.98%", "Banking", "Large Cap" },
        { "WFC", "Wells Fargo", 52.40, "+1.23%", "Banking", "Large Cap" },
        { "C", "Citigroup Inc", 58.90, "+1.67%", "Banking", "Large Cap" },
        { "GS", "Goldman Sachs", 425.30, "+2.34%", "Banking", "Large Cap" },
        { "MS", "Morgan Stanley", 95.60, "+1.89%", "Banking", "Large Cap" },
        { "BLK", "BlackRock Inc", 825.40, "+1.12%", "Banking", "Large Cap" },
        { "SCHW", "Charles Schwab", 72.50, "+0.87%", "Banking", "Large Cap" },
        { "AXP", "American Express", 215.80, "+1.45%", "Banking", "Large Cap" },
        { "V", "Visa Inc", 275.60, "+1.23%", "Banking", "Large Cap" },
        { "MA", "Mastercard Inc", 445.20, "+1.56%", "Banking", "Large Cap" },
        { "PYPL", "PayPal Holdings", 68.40, "+2.89%", "Banking", "Mid Cap" },
        { "SQ", "Block Inc", 78.90, "+3.45%", "Banking", "Mid Cap" },
        { "COIN", "Coinbase Global", 185.60, "+8.90%", "Banking", "Mid Cap" },
        
        // === HEALTHCARE & PHARMA ===
        { "JNJ", "Johnson & Johnson", 158.40, "+0.67%", "Healthcare", "Large Cap" },
        { "UNH", "UnitedHealth Group", 525.80, "+1.23%", "Healthcare", "Large Cap" },
        { "PFE", "Pfizer Inc", 28.90, "-1.45%", "Healthcare", "Large Cap" },
        { "ABBV", "AbbVie Inc", 165.40, "+1.89%", "Healthcare", "Large Cap" },
        { "TMO", "Thermo Fisher Scientific", 545.60, "+1.12%", "Healthcare", "Large Cap" },
        { "ABT", "Abbott Laboratories", 108.50, "+0.98%", "Healthcare", "Large Cap" },
        { "DHR", "Danaher Corporation", 245.80, "+1.45%", "Healthcare", "Large Cap" },
        { "BMY", "Bristol-Myers Squibb", 52.30, "+0.87%", "Healthcare", "Large Cap" },
        { "LLY", "Eli Lilly and Company", 785.60, "+2.34%", "Healthcare", "Large Cap" },
        { "AMGN", "Amgen Inc", 285.40, "+1.23%", "Healthcare", "Large Cap" },
        { "GILD", "Gilead Sciences", 78.90, "+1.67%", "Healthcare", "Large Cap" },
        { "MRNA", "Moderna Inc", 95.60, "+5.67%", "Healthcare", "Mid Cap" },
        { "BNTX", "BioNTech SE", 105.40, "+4.89%", "Healthcare", "Mid Cap" },
        
        // === ENERGY & OIL ===
        { "XOM", "Exxon Mobil", 108.50, "+1.23%", "Energy", "Large Cap" },
        { "CVX", "Chevron Corporation", 158.40, "+1.45%", "Energy", "Large Cap" },
        { "COP", "ConocoPhillips", 125.60, "+1.89%", "Energy", "Large Cap" },
        { "SLB", "Schlumberger", 52.80, "+2.34%", "Energy", "Large Cap" },
        { "EOG", "EOG Resources", 128.40, "+1.67%", "Energy", "Large Cap" },
        { "PXD", "Pioneer Natural Resources", 245.60, "+1.12%", "Energy", "Large Cap" },
        { "MPC", "Marathon Petroleum", 165.80, "+0.98%", "Energy", "Large Cap" },
        { "VLO", "Valero Energy", 145.20, "+1.45%", "Energy", "Large Cap" },
        
        // === CONSUMER GOODS (FMCG) ===
        { "PG", "Procter & Gamble", 158.40, "+0.67%", "FMCG", "Large Cap" },
        { "KO", "Coca-Cola Company", 58.90, "+0.89%", "FMCG", "Large Cap" },
        { "PEP", "PepsiCo Inc", 175.60, "+1.12%", "FMCG", "Large Cap" },
        { "MDLZ", "Mondelez International", 72.50, "+0.98%", "FMCG", "Large Cap" },
        { "NKE", "Nike Inc", 108.40, "+1.45%", "FMCG", "Large Cap" },
        { "SBUX", "Starbucks Corporation", 95.60, "+1.23%", "FMCG", "Large Cap" },
        { "MCD", "McDonald's Corporation", 285.40, "+0.87%", "FMCG", "Large Cap" },
        { "CL", "Colgate-Palmolive", 78.90, "+0.67%", "FMCG", "Large Cap" },
        
        // === TELECOMMUNICATIONS ===
        { "T", "AT&T Inc", 18.45, "+0.89%", "Telecom", "Large Cap" },
        { "VZ", "Verizon Communications", 38.60, "+1.12%", "Telecom", "Large Cap" },
        { "TMUS", "T-Mobile US", 165.80, "+1.89%", "Telecom", "Large Cap" },
        { "CMCSA", "Comcast Corporation", 42.50, "+0.98%", "Telecom", "Large Cap" },
        
        // === AEROSPACE & DEFENSE ===
        { "BA", "Boeing Company", 185.60, "+2.34%", "Automobile", "Large Cap" },
        { "LMT", "Lockheed Martin", 445.80, "+1.23%", "Automobile", "Large Cap" },
        { "RTX", "Raytheon Technologies", 95.40, "+1.45%", "Automobile", "Large Cap" },
        { "NOC", "Northrop Grumman", 485.60, "+1.12%", "Automobile", "Large Cap" },
        
        // === SEMICONDUCTORS ===
        { "TSM", "Taiwan Semiconductor", 145.80, "+2.89%", "IT", "Large Cap" },
        { "ASML", "ASML Holding", 885.60, "+1.67%", "IT", "Large Cap" },
        { "MU", "Micron Technology", 95.40, "+3.45%", "IT", "Large Cap" },
        { "AMAT", "Applied Materials", 185.60, "+2.12%", "IT", "Large Cap" },
        { "LRCX", "Lam Research", 885.40, "+1.89%", "IT", "Large Cap" },
        { "KLAC", "KLA Corporation", 625.80, "+1.45%", "IT", "Large Cap" },
        
        // === INDIAN STOCKS (NSE) ===
        { "RELIANCE.NS", "Reliance Industries", 2850.50, "+1.45%", "Energy", "Large Cap" },
        { "TCS.NS", "Tata Consultancy Services", 3650.80, "+0.98%", "IT", "Large Cap" },
        { "INFY.NS", "Infosys Limited", 1485.60, "+1.23%", "IT", "Large Cap" },
        { "HDFCBANK.NS", "HDFC Bank", 1650.40, "+0.87%", "Banking", "Large Cap" },
        { "ICICIBANK.NS", "ICICI Bank", 985.60, "+1.12%", "Banking", "Large Cap" },
        { "BHARTIARTL.NS", "Bharti Airtel", 1285.40, "+1.67%", "Telecom", "Large Cap" },
        { "ITC.NS", "ITC Limited", 445.80, "+0.98%", "FMCG", "Large Cap" },
        { "SBIN.NS", "State Bank of India", 625.40, "+1.45%", "Banking", "Large Cap" },
        { "WIPRO.NS", "Wipro Limited", 485.60, "+0.89%", "IT", "Large Cap" },
        { "TATAMOTORS.NS", "Tata Motors", 785.40, "+2.34%", "Automobile", "Large Cap" },
        { "MARUTI.NS", "Maruti Suzuki", 11850.60, "+1.12%", "Automobile", "Large Cap" },
        { "SUNPHARMA.NS", "Sun Pharmaceutical", 1485.80, "+0.98%", "Healthcare", "Large Cap" },
        { "ONGC.NS", "Oil and Natural Gas Corp", 245.60, "+1.23%", "Energy", "Large Cap" },
        { "NTPC.NS", "NTPC Limited", 285.40, "+0.87%", "Energy", "Large Cap" },
        { "POWERGRID.NS", "Power Grid Corporation", 245.80, "+0.67%", "Energy", "Large Cap" }
    };
    
    /**
     * Get all stocks as a list
     */
    public static List<Object[]> getAllStocks() {
        return Arrays.asList(ALL_STOCKS);
    }
    
    /**
     * Get stocks filtered by sector
     */
    public static List<Object[]> getStocksBySector(String sector) {
        List<Object[]> filtered = new ArrayList<>();
        for (Object[] stock : ALL_STOCKS) {
            if (sector.equals("All Sectors") || stock[4].equals(sector)) {
                filtered.add(stock);
            }
        }
        return filtered;
    }
    
    /**
     * Get stocks filtered by market cap
     */
    public static List<Object[]> getStocksByMarketCap(String marketCap) {
        List<Object[]> filtered = new ArrayList<>();
        for (Object[] stock : ALL_STOCKS) {
            if (marketCap.equals("All Caps") || stock[5].equals(marketCap)) {
                filtered.add(stock);
            }
        }
        return filtered;
    }
    
    /**
     * Search stocks by symbol or name
     */
    public static List<Object[]> searchStocks(String query) {
        List<Object[]> results = new ArrayList<>();
        String lowerQuery = query.toLowerCase();
        for (Object[] stock : ALL_STOCKS) {
            String symbol = ((String) stock[0]).toLowerCase();
            String name = ((String) stock[1]).toLowerCase();
            if (symbol.contains(lowerQuery) || name.contains(lowerQuery)) {
                results.add(stock);
            }
        }
        return results;
    }
}
