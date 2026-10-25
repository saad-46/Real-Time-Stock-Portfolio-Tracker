package com.portfolio.ui;

import com.portfolio.service.PortfolioService;
import com.portfolio.model.PortfolioItem;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.general.DefaultPieDataset;
import org.jfree.data.category.DefaultCategoryDataset;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * ModernChartWindow displays portfolio visualization with charts.
 * Features: 4 different chart types
 */
public class ModernChartWindow extends JFrame {
    private PortfolioService portfolioService;

    /**
     * Constructor creates chart window
     */
    public ModernChartWindow(PortfolioService portfolioService) {
        this.portfolioService = portfolioService;

        // Configure frame
        setTitle("Portfolio Charts");
        setSize(1200, 800);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        // Create tabbed pane with 4 charts
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setBackground(new Color(30, 30, 30));
        tabbedPane.setForeground(Color.WHITE);

        // Add charts to tabs
        tabbedPane.addTab("Portfolio Distribution", createPortfolioDistributionChart());
        tabbedPane.addTab("Profit vs Loss", createProfitLossChart());
        tabbedPane.addTab("Stock Values", createStockValuesChart());
        tabbedPane.addTab("Gain/Loss", createGainLossChart());

        add(tabbedPane);
        setVisible(true);
    }

    /**
     * Create portfolio distribution pie chart
     */
    private JPanel createPortfolioDistributionChart() {
        DefaultPieDataset dataset = new DefaultPieDataset();

        // Add data points
        List<PortfolioItem> items = portfolioService.getAllPortfolioItems();
        for (PortfolioItem item : items) {
            dataset.setValue(item.getSymbol(), item.getCurrentValue());
        }

        // Create chart
        JFreeChart chart = ChartFactory.createPieChart(
                "Portfolio Distribution",
                dataset,
                true,
                true,
                false
        );

        chart.setBackgroundPaint(new Color(30, 30, 30));
        chart.getTitle().setPaint(Color.WHITE);

        // Create panel
        ChartPanel panel = new ChartPanel(chart);
        panel.setBackground(new Color(30, 30, 30));
        return panel;
    }

    /**
     * Create profit vs loss pie chart
     */
    private JPanel createProfitLossChart() {
        DefaultPieDataset dataset = new DefaultPieDataset();

        double totalProfit = 0;
        double totalLoss = 0;

        List<PortfolioItem> items = portfolioService.getAllPortfolioItems();
        for (PortfolioItem item : items) {
            if (item.getProfitLoss() >= 0) {
                totalProfit += item.getProfitLoss();
            } else {
                totalLoss += Math.abs(item.getProfitLoss());
            }
        }

        dataset.setValue("Profit", totalProfit);
        dataset.setValue("Loss", totalLoss);

        JFreeChart chart = ChartFactory.createPieChart(
                "Profit vs Loss",
                dataset,
                true,
                true,
                false
        );

        chart.setBackgroundPaint(new Color(30, 30, 30));
        chart.getTitle().setPaint(Color.WHITE);

        ChartPanel panel = new ChartPanel(chart);
        panel.setBackground(new Color(30, 30, 30));
        return panel;
    }

    /**
     * Create stock values bar chart
     */
    private JPanel createStockValuesChart() {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        List<PortfolioItem> items = portfolioService.getAllPortfolioItems();
        for (PortfolioItem item : items) {
            dataset.addValue(item.getCurrentValue(), "Current Value", item.getSymbol());
        }

        JFreeChart chart = ChartFactory.createBarChart(
                "Stock Values",
                "Stock",
                "Value ($)",
                dataset,
                org.jfree.chart.plot.PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        chart.setBackgroundPaint(new Color(30, 30, 30));
        chart.getTitle().setPaint(Color.WHITE);

        ChartPanel panel = new ChartPanel(chart);
        panel.setBackground(new Color(30, 30, 30));
        return panel;
    }

    /**
     * Create gain/loss bar chart
     */
    private JPanel createGainLossChart() {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();

        List<PortfolioItem> items = portfolioService.getAllPortfolioItems();
        for (PortfolioItem item : items) {
            dataset.addValue(item.getProfitLoss(), "Profit/Loss", item.getSymbol());
        }

        JFreeChart chart = ChartFactory.createBarChart(
                "Gain/Loss per Stock",
                "Stock",
                "Gain/Loss ($)",
                dataset,
                org.jfree.chart.plot.PlotOrientation.VERTICAL,
                true,
                true,
                false
        );

        chart.setBackgroundPaint(new Color(30, 30, 30));
        chart.getTitle().setPaint(Color.WHITE);

        ChartPanel panel = new ChartPanel(chart);
        panel.setBackground(new Color(30, 30, 30));
        return panel;
    }
}
