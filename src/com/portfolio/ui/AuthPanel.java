package com.portfolio.ui;

import com.portfolio.service.AccountService;
import com.portfolio.service.AccountService.Result;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.util.concurrent.ExecutionException;

/**
 * Sign-in and account-creation form on the landing frame. It collects input, hands it to {@link AccountService} off the event
 * thread, and reports the outcome. Passwords stay in {@code char[]} and are cleared after use; nothing is stored or logged here.
 */
public class AuthPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    public enum Mode { SIGN_IN, REGISTER }

    /** What the host does with the outcome. */
    public interface Listener {
        void onAuthenticated(int userId, String username);

        void onBack();
    }

    private final AccountService accounts;
    private final Listener listener;
    private final JLabel title = Theme.label("", Theme.font(Font.BOLD, 28f), LandingPalette.TEXT);
    private final JLabel intro = Theme.label("", Theme.font(Font.PLAIN, 14f), LandingPalette.TEXT_DIM);
    private final JTextField name = field(new JTextField(), "auth.name", "Name");
    private final JTextField email = field(new JTextField(), "auth.email", "Email");
    private final JPasswordField password = (JPasswordField) field(new JPasswordField(), "auth.password", "Password");
    private final JLabel nameLabel = fieldLabel("Name");
    private final JLabel message = Theme.label(" ", Theme.font(Font.PLAIN, 13f), LandingPalette.DOWN);
    private final GradientButton submit = new GradientButton("Sign In", GradientButton.Style.PRIMARY);
    private final GradientButton back = new GradientButton("Back", GradientButton.Style.SECONDARY);
    private final JButton switchMode = new JButton();
    private Mode mode = Mode.SIGN_IN;
    private boolean busy;

    public AuthPanel(AccountService accounts, Listener listener) {
        super(new GridBagLayout());
        this.accounts = accounts;
        this.listener = listener;
        setName("auth");
        setOpaque(true);
        setBackground(LandingPalette.NAVY_TOP);

        JPanel card = new JPanel() {
            private static final long serialVersionUID = 1L;

            @Override public Dimension getPreferredSize() { // fixed width, height follows the fields shown
                return new Dimension(440, super.getPreferredSize().height);
            }

            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                LandingPalette.hints(g2);
                LandingPalette.card(g2, getWidth(), getHeight(), 22);
                g2.dispose();
            }
        };
        card.setOpaque(false);
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(new EmptyBorder(30, 36, 28, 36));

        title.setName("auth.title");
        title.setAlignmentX(LEFT_ALIGNMENT);
        intro.setAlignmentX(LEFT_ALIGNMENT);
        card.add(title);
        card.add(Box.createVerticalStrut(4));
        card.add(intro);
        card.add(Box.createVerticalStrut(20));
        addRow(card, nameLabel, name);
        addRow(card, fieldLabel("Email"), email);
        addRow(card, fieldLabel("Password"), password);
        message.setName("auth.message");
        message.setAlignmentX(LEFT_ALIGNMENT);
        message.getAccessibleContext().setAccessibleName("Form message");
        card.add(Box.createVerticalStrut(2));
        card.add(message);
        card.add(Box.createVerticalStrut(14));

        JPanel buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));
        buttons.setOpaque(false);
        buttons.setAlignmentX(LEFT_ALIGNMENT);
        submit.setName("auth.submit");
        back.setName("auth.back");
        back.setMnemonic(KeyEvent.VK_B);
        back.getAccessibleContext().setAccessibleDescription("Return to the StockVault start page");
        buttons.add(submit);
        buttons.add(Box.createHorizontalStrut(12));
        buttons.add(back);
        card.add(buttons);
        card.add(Box.createVerticalStrut(14));

        switchMode.setName("auth.switch");
        switchMode.setContentAreaFilled(false);
        switchMode.setBorderPainted(false);
        switchMode.setFocusPainted(true);
        switchMode.setOpaque(false);
        switchMode.setForeground(LandingPalette.link());
        switchMode.setFont(Theme.font(Font.BOLD, 13f));
        switchMode.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        switchMode.setAlignmentX(LEFT_ALIGNMENT);
        switchMode.setHorizontalAlignment(JButton.LEFT);
        switchMode.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
        card.add(switchMode);
        javax.swing.JTextArea limits = new javax.swing.JTextArea("Password recovery is not available. Your account and data stay on this computer.");
        limits.setEditable(false);
        limits.setFocusable(false);
        limits.setOpaque(false);
        limits.setLineWrap(true);
        limits.setWrapStyleWord(true);
        limits.setBorder(null);
        limits.setFont(Theme.font(Font.PLAIN, 11f));
        limits.setForeground(LandingPalette.TEXT_DIM);
        limits.setMaximumSize(new Dimension(368, 60));
        limits.setAlignmentX(LEFT_ALIGNMENT);
        card.add(Box.createVerticalStrut(8));
        card.add(limits);

        add(card, new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, new Insets(20, 20, 20, 20), 0, 0));

        submit.addActionListener(e -> submit());
        back.addActionListener(e -> {
            if (!busy) {
                clear();
                listener.onBack();
            }
        });
        switchMode.addActionListener(e -> {
            if (!busy) {
                show(mode == Mode.SIGN_IN ? Mode.REGISTER : Mode.SIGN_IN, null);
            }
        });
        // Enter in any field submits
        for (JTextField f : new JTextField[] { name, email, password }) {
            f.addActionListener(e -> submit());
        }
        show(Mode.SIGN_IN, null);
    }

    /** Switch to a mode and optionally show a note (for example, "Sign in to open Portfolio"). */
    public void show(Mode m, String note) {
        this.mode = m;
        boolean reg = m == Mode.REGISTER;
        title.setText(reg ? "Create your account" : "Sign in");
        intro.setText(reg ? "Create a local account on this computer." : "Sign in with your email and password.");
        name.setVisible(reg);
        nameLabel.setVisible(reg);
        submit.setText(reg ? "Create Account" : "Sign In");
        submit.getAccessibleContext().setAccessibleName(submit.getText());
        switchMode.setText(reg ? "I already have an account" : "Create an account");
        setMessage(note == null ? " " : note, false);
        clear();
        revalidate();
        repaint();
        (reg ? name : email).requestFocusInWindow();
    }

    public Mode mode() {
        return mode;
    }

    public JButton submitButton() {
        return submit;
    }

    private void submit() {
        if (busy) {
            return;
        }
        final Mode m = mode;
        final String user = email.getText().strip();
        final String fullName = name.getText();
        final char[] pw = password.getPassword();
        setBusy(true);
        setMessage(m == Mode.SIGN_IN ? "Signing in…" : "Creating your account…", false);
        new SwingWorker<Result, Void>() {
            @Override protected Result doInBackground() {
                return m == Mode.SIGN_IN ? accounts.signIn(user, pw) : accounts.createAccount(fullName, user, pw);
            }

            @Override protected void done() {
                setBusy(false);
                try {
                    Result r = get();
                    if (r.ok()) {
                        clear();
                        listener.onAuthenticated(r.userId(), user.strip());
                    } else {
                        password.setText("");
                        setMessage(r.message(), true);
                        (m == Mode.SIGN_IN && r.status() == AccountService.Status.BAD_CREDENTIALS ? password : email).requestFocusInWindow();
                    }
                } catch (ExecutionException e) {
                    setMessage("Something went wrong (" + (e.getCause() == null ? e : e.getCause()).getClass().getSimpleName() + ").", true);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    private void setBusy(boolean b) {
        busy = b;
        submit.setEnabled(!b);
        switchMode.setEnabled(!b);
        back.setEnabled(!b);
        setCursor(b ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    }

    private void setMessage(String text, boolean error) {
        message.setText(text == null || text.isBlank() ? " " : text);
        message.setForeground(error ? LandingPalette.DOWN : LandingPalette.TEXT_DIM);
    }

    private void clear() {
        password.setText("");
        if (mode == Mode.SIGN_IN) {
            name.setText("");
        }
    }

    private static JLabel fieldLabel(String text) {
        JLabel l = Theme.label(text, Theme.font(Font.BOLD, 12f), LandingPalette.TEXT_DIM);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private static JTextField field(JTextField f, String name, String accessible) {
        f.setName(name);
        f.setColumns(24);
        f.setFont(Theme.font(Font.PLAIN, 14f));
        f.setForeground(LandingPalette.TEXT);
        f.setCaretColor(LandingPalette.TEXT);
        f.setBackground(LandingPalette.FIELD);
        f.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(LandingPalette.CARD_EDGE),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        f.setAlignmentX(LEFT_ALIGNMENT);
        f.getAccessibleContext().setAccessibleName(accessible);
        return f;
    }

    private static void addRow(JPanel card, JLabel label, JComponent field) {
        card.add(label);
        card.add(Box.createVerticalStrut(4));
        card.add(field);
        card.add(Box.createVerticalStrut(12));
    }

    @Override
    protected void paintComponent(Graphics g) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setPaint(new GradientPaint(0, 0, LandingPalette.NAVY_TOP, 0, getHeight(), LandingPalette.NAVY_BOTTOM));
        g2.fillRect(0, 0, getWidth(), getHeight());
        g2.dispose();
    }
}
