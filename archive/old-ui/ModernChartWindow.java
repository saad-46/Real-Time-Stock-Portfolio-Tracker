package com.portfolio.ui;

import com.portfolio.model.*;
import com.portfolio.service.*;
import javax.swing.*;
import java.awt.*;
import org.jfree.chart.*;
import org.jfree.chart.plot.*;
import org.jfree.data.general.*;
import org.jfree.data.category.*;
import java.util.List;

// Modern chart window with dark theme
public class ModernChartWindow extends JFrame {
    
    private static final Color DARK_BG = new Color(18, 18, 18);
    private static final Color CARD_BG = new Color(30, 30, 30);
    private static final Color ACCENT_GREEN = new Color(0, 200, 83);
    private static final Color ACCENT_RED = new Color(255, 59, 48);
    private static final Color TEXT_PRIMARY = new Color(255, 255, 255);
    
    private PortfolioService portfolioService;
    
    public ModernChartWindow(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;
        
        setTitle("Portfolio Charts");
        setSize(1400, 900);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        getContentPane().setBackground(DARK_BG);
        
        initializeCharts();
    }
    
    private void initializeCharts() {
        JPanel mainPanel = new JPanel(new GridLayout(2, 2, 20, 20));
        mainPanel.setBackground(DARK_BG);
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        
        mainPanel.add(createPortfolioDistributionChart());
        mainPanel.add(createProfitLossChart());
        mainPanel.add(createStockValueChart());
        mainPanel.add(createGainLossChart());
        
        add(mainPanel);
    }
    
    private ChartPanel createPortfolioDistributionChart() {
        DefaultPieDataset dataset = new DefaultPieDataset();
        
        List<PortfolioItem> items = portfolioService.getPortfolioItems();
        for (PortfolioItem item : items) {
            dataset.setValue(item.getStock().getSymbol(), item.getTotalValue());
        }
        
        JFreeChart chart = ChartFactory.createPieChart(
            "Portfolio Distribution",
            dataset,
            true,
            true,
            false
        );
        
        styleChart(chart);
        return new ChartPanel(chart);
    }
    
    private ChartPanel createProfitLossChart() {
        DefaultPieDataset dataset = new DefaultPieDataset();
        
        double totalProfit = 0;
        double totalLoss = 0;
        
        List<PortfolioItem> items = portfolioService.getPortfolioItems();
        for (PortfolioItem item : items) {
            double gainLoss = item.getGainLoss();
            if (gainLoss > 0) {
                totalProfit += gainLoss;
            } else {
                totalLoss += Math.abs(gainLoss);
            }
        }
        
        if (totalProfit > 0) dataset.setValue("Profit", totalProfit);
        if (totalLoss > 0) dataset.setValue("Loss", totalLoss);
        if (totalProfit == 0 && totalLoss == 0) dataset.setValue("No Change", 1);
        
        JFreeChart chart = ChartFactory.createPieChart(
            "Profit vs Loss",
            dataset,
            true,
            true,
            false
        );
        
        styleChart(chart);
        PiePlot plot = (PiePlot) chart.getPlot();
        plot.setSectionPaint("Profit", ACCENT_GREEN);
        plot.setSectionPaint("Loss", ACCENT_RED);
        
        return new ChartPanel(chart);
    }
    
    private ChartPanel createStockValueChart() {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        
        List<PortfolioItem> items = portfolioService.getPortfolioItems();
        for (PortfolioItem item : items) {
            dataset.addValue(item.getTotalValue(), "Value", item.getStock().getSymbol());
        }
        
        JFreeChart chart = ChartFactory.createBarChart(
            "Stock Values",
            "Stock",
            "Value ($)",
            dataset,
            PlotOrientation.VERTICAL,
            false,
            true,
            false
        );
        
        styleChart(chart);
        return new ChartPanel(chart);
    }
    
    private ChartPanel createGainLossChart() {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        
        List<PortfolioItem> items = portfolioService.getPortfolioItems();
        for (PortfolioItem item : items) {
            dataset.addValue(item.getGainLoss(), "Gain/Loss", item.getStock().getSymbol());
        }
        
        JFreeChart chart = ChartFactory.createBarChart(
            "Gain/Loss by Stock",
            "Stock",
            "Gain/Loss ($)",
            dataset,
            PlotOrientation.VERTICAL,
            false,
            true,
            false
        );
        
        styleChart(chart);
        return new ChartPanel(chart);
    }
    
    private void styleChart(JFreeChart chart) {
        chart.setBackgroundPaint(CARD_BG);
        chart.getTitle().setPaint(TEXT_PRIMARY);
        chart.getTitle().setFont(new Font("Segoe UI", Font.BOLD, 18));
        
        Plot plot = chart.getPlot();
        plot.setBackgroundPaint(DARK_BG);
        plot.setOutlineVisible(false);
        
        if (plot instanceof PiePlot) {
            PiePlot piePlot = (PiePlot) plot;
            piePlot.setLabelFont(new Font("Segoe UI", Font.PLAIN, 12));
            piePlot.setLabelPaint(TEXT_PRIMARY);
        } else if (plot instanceof CategoryPlot) {
            CategoryPlot catPlot = (CategoryPlot) plot;
            catPlot.setRangeGridlinePaint(new Color(60, 60, 60));
            catPlot.getDomainAxis().setLabelPaint(TEXT_PRIMARY);
            catPlot.getDomainAxis().setTickLabelPaint(TEXT_PRIMARY);
            catPlot.getRangeAxis().setLabelPaint(TEXT_PRIMARY);
            catPlot.getRangeAxis().setTickLabelPaint(TEXT_PRIMARY);
        }
    }
}
