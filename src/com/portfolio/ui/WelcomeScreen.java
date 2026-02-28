package com.portfolio.ui;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;

/**
 * Welcome/Login Screen - Shows before main dashboard
 * Modern design with rounded corners and smooth animations
 */
public class WelcomeScreen extends JFrame {
    
    // Colors
    static final Color BG = new Color(15, 15, 25);
    static final Color CARD_BG = new Color(30, 30, 50);
    static final Color ACCENT = new Color(102, 126, 234);
    static final Color TEXT = new Color(255, 255, 255);
    static final Color TEXT_DIM = new Color(180, 180, 200);
    
    private Runnable onLoginSuccess;
    
    public WelcomeScreen(Runnable onLoginSuccess) {
        super("StockVault - Welcome");
        this.onLoginSuccess = onLoginSuccess;
        
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(900, 700);
        setLocationRelativeTo(null);
        setUndecorated(false);
        getContentPane().setBackground(BG);
        
        initUI();
    }
    
    private void initUI() {
        JPanel mainPanel = new JPanel(new GridBagLayout());
        mainPanel.setBackground(BG);
        mainPanel.setBorder(new EmptyBorder(40, 40, 40, 40));
        
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.insets = new Insets(10, 10, 10, 10);
        
        // Logo/Icon
        JLabel logoLabel = new JLabel("💎");
        logoLabel.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 80));
        logoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        mainPanel.add(logoLabel, gbc);
        
        // Title
        gbc.gridy++;
        JLabel titleLabel = new JLabel("StockVault");
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 48));
        titleLabel.setForeground(TEXT);
        titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        mainPanel.add(titleLabel, gbc);
        
        // Subtitle
        gbc.gridy++;
        JLabel subtitleLabel = new JLabel("Your Premium Portfolio Tracker");
        subtitleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 20));
        subtitleLabel.setForeground(TEXT_DIM);
        subtitleLabel.setHorizontalAlignment(SwingConstants.CENTER);
        mainPanel.add(subtitleLabel, gbc);
        
        // Spacer
        gbc.gridy++;
        mainPanel.add(Box.createVerticalStrut(40), gbc);
        
        // Login Card
        gbc.gridy++;
        JPanel loginCard = createLoginCard();
        mainPanel.add(loginCard, gbc);
        
        // Features
        gbc.gridy++;
        gbc.insets = new Insets(30, 10, 10, 10);
        JPanel featuresPanel = createFeaturesPanel();
        mainPanel.add(featuresPanel, gbc);
        
        add(mainPanel);
    }
    
    private JPanel createLoginCard() {
        RoundedPanel card = new RoundedPanel(25);
        card.setBackground(CARD_BG);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(40, 50, 40, 50));
        card.setPreferredSize(new Dimension(450, 350));
        
        // Welcome text
        JLabel welcomeLabel = new JLabel("Welcome Back!");
        welcomeLabel.setFont(new Font("Segoe UI", Font.BOLD, 28));
        welcomeLabel.setForeground(TEXT);
        welcomeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(welcomeLabel);
        
        card.add(Box.createVerticalStrut(10));
        
        JLabel infoLabel = new JLabel("Sign in to access your portfolio");
        infoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        infoLabel.setForeground(TEXT_DIM);
        infoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(infoLabel);
        
        card.add(Box.createVerticalStrut(30));
        
        // Email field
        JLabel emailLabel = new JLabel("Email");
        emailLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        emailLabel.setForeground(TEXT);
        emailLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(emailLabel);
        
        card.add(Box.createVerticalStrut(8));
        
        RoundedTextField emailField = new RoundedTextField(20, 12);
        emailField.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        emailField.setForeground(TEXT);
        emailField.setBackground(new Color(40, 40, 60));
        emailField.setCaretColor(TEXT);
        emailField.setBorder(new EmptyBorder(12, 15, 12, 15));
        emailField.setMaximumSize(new Dimension(350, 45));
        emailField.setAlignmentX(Component.CENTER_ALIGNMENT);
        emailField.setText("demo@stockvault.com");
        card.add(emailField);
        
        card.add(Box.createVerticalStrut(20));
        
        // Password field
        JLabel passwordLabel = new JLabel("Password");
        passwordLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        passwordLabel.setForeground(TEXT);
        passwordLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(passwordLabel);
        
        card.add(Box.createVerticalStrut(8));
        
        RoundedPasswordField passwordField = new RoundedPasswordField(20, 12);
        passwordField.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        passwordField.setForeground(TEXT);
        passwordField.setBackground(new Color(40, 40, 60));
        passwordField.setCaretColor(TEXT);
        passwordField.setBorder(new EmptyBorder(12, 15, 12, 15));
        passwordField.setMaximumSize(new Dimension(350, 45));
        passwordField.setAlignmentX(Component.CENTER_ALIGNMENT);
        passwordField.setText("demo123");
        card.add(passwordField);
        
        card.add(Box.createVerticalStrut(30));
        
        // Login button
        RoundedButton loginButton = new RoundedButton("Sign In to Dashboard", 12);
        loginButton.setFont(new Font("Segoe UI", Font.BOLD, 16));
        loginButton.setForeground(Color.WHITE);
        loginButton.setBackground(ACCENT);
        loginButton.setMaximumSize(new Dimension(350, 50));
        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        loginButton.setCursor(new Cursor(Cursor.HAND_CURSOR));
        loginButton.addActionListener(e -> handleLogin());
        card.add(loginButton);
        
        card.add(Box.createVerticalStrut(15));
        
        // Skip login link
        JLabel skipLabel = new JLabel("<html><u>Skip and explore as guest</u></html>");
        skipLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        skipLabel.setForeground(ACCENT);
        skipLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        skipLabel.setCursor(new Cursor(Cursor.HAND_CURSOR));
        skipLabel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                handleLogin();
            }
        });
        card.add(skipLabel);
        
        return card;
    }
    
    private JPanel createFeaturesPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 30, 0));
        panel.setOpaque(false);
        
        String[] features = {
            "📊 Real-time Tracking",
            "📈 Advanced Analytics",
            "🎤 Voice Assistant",
            "💎 Premium Features"
        };
        
        for (String feature : features) {
            JLabel label = new JLabel(feature);
            label.setFont(new Font("Segoe UI Emoji", Font.PLAIN, 15));
            label.setForeground(TEXT_DIM);
            panel.add(label);
        }
        
        return panel;
    }
    
    private void handleLogin() {
        // Show loading
        JDialog loadingDialog = new JDialog(this, "Signing In...", true);
        loadingDialog.setSize(300, 150);
        loadingDialog.setLocationRelativeTo(this);
        loadingDialog.setUndecorated(true);
        loadingDialog.getContentPane().setBackground(CARD_BG);
        
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(CARD_BG);
        panel.setBorder(new EmptyBorder(30, 30, 30, 30));
        
        JLabel label = new JLabel("Loading your portfolio...");
        label.setFont(new Font("Segoe UI", Font.PLAIN, 16));
        label.setForeground(TEXT);
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        progress.setAlignmentX(Component.CENTER_ALIGNMENT);
        
        panel.add(label);
        panel.add(Box.createVerticalStrut(20));
        panel.add(progress);
        
        loadingDialog.add(panel);
        
        // Simulate loading in background
        new Thread(() -> {
            try {
                Thread.sleep(1500);  // Simulate loading
                SwingUtilities.invokeLater(() -> {
                    loadingDialog.dispose();
                    dispose();  // Close welcome screen
                    if (onLoginSuccess != null) {
                        onLoginSuccess.run();  // Open main dashboard
                    }
                });
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
        }).start();
        
        loadingDialog.setVisible(true);
    }
    
    // Custom components
    class RoundedPanel extends JPanel {
        private int radius;
        
        RoundedPanel(int radius) {
            super();
            this.radius = radius;
            setOpaque(false);
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            super.paintComponent(g);
        }
    }
    
    class RoundedTextField extends JTextField {
        private int radius;
        
        RoundedTextField(int columns, int radius) {
            super(columns);
            this.radius = radius;
            setOpaque(false);
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            super.paintComponent(g);
        }
        
        @Override
        protected void paintBorder(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(60, 60, 90));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
        }
    }
    
    class RoundedPasswordField extends JPasswordField {
        private int radius;
        
        RoundedPasswordField(int columns, int radius) {
            super(columns);
            this.radius = radius;
            setOpaque(false);
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(getBackground());
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            super.paintComponent(g);
        }
        
        @Override
        protected void paintBorder(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(new Color(60, 60, 90));
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
        }
    }
    
    class RoundedButton extends JButton {
        private int radius;
        
        RoundedButton(String text, int radius) {
            super(text);
            this.radius = radius;
            setOpaque(false);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
        }
        
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            
            if (getModel().isPressed()) {
                g2.setColor(getBackground().darker());
            } else if (getModel().isRollover()) {
                g2.setColor(getBackground().brighter());
            } else {
                g2.setColor(getBackground());
            }
            
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), radius, radius);
            super.paintComponent(g);
        }
    }
}
