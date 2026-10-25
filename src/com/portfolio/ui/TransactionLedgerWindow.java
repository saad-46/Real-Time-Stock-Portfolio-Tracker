package com.portfolio.ui;

import com.portfolio.model.Transaction;
import com.portfolio.service.CsvExporter;
import com.portfolio.service.PortfolioService;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/**
 * Searchable buy/sell history with CSV export of the filtered rows.
 */
public class TransactionLedgerWindow extends JFrame {
    private static final long serialVersionUID = 1L;

    private final transient PortfolioService service;
    private final DefaultTableModel model;
    private final JTextField symbolField = new JTextField(8);
    private final JComboBox<String> typeBox = new JComboBox<>(new String[]{"All", "BUY", "SELL"});
    private final JTextField fromField = new JTextField(9);
    private final JTextField toField = new JTextField(9);
    private final JLabel status = new JLabel(" ");
    private transient List<Transaction> shown = List.of();

    public TransactionLedgerWindow(PortfolioService service) {
        super("Transaction Ledger");
        this.service = service;
        this.model = new DefaultTableModel(new String[]{"Date/Time", "Symbol", "Type", "Quantity", "Price", "Realized P/L"}, 0) {
            private static final long serialVersionUID = 1L;

            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 5));
        filters.add(new JLabel("Symbol:"));
        filters.add(symbolField);
        filters.add(new JLabel("Type:"));
        filters.add(typeBox);
        filters.add(new JLabel("From (yyyy-mm-dd):"));
        filters.add(fromField);
        filters.add(new JLabel("To:"));
        filters.add(toField);
        JButton apply = new JButton("Apply");
        apply.addActionListener(e -> reload());
        JButton export = new JButton("Export CSV...");
        export.addActionListener(e -> exportCsv());
        filters.add(apply);
        filters.add(export);

        JTable table = new JTable(model);
        table.setAutoCreateRowSorter(true);
        getContentPane().add(filters, BorderLayout.NORTH);
        getContentPane().add(new JScrollPane(table), BorderLayout.CENTER);
        getContentPane().add(status, BorderLayout.SOUTH);

        setSize(820, 480);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        reload();
        setVisible(true);
    }

    private LocalDate parseDate(JTextField field, String label) {
        String t = field.getText().trim();
        if (t.isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(t);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(label + " must be a date like 2025-01-31");
        }
    }

    private void reload() {
        LocalDate from;
        LocalDate to;
        try {
            from = parseDate(fromField, "From");
            to = parseDate(toField, "To");
        } catch (IllegalArgumentException e) {
            status.setText(e.getMessage());
            return;
        }
        if (from != null && to != null && from.isAfter(to)) {
            status.setText("From date is after To date");
            return;
        }
        String type = "All".equals(typeBox.getSelectedItem()) ? null : (String) typeBox.getSelectedItem();
        shown = service.searchTransactions(symbolField.getText(), type, from, to);
        model.setRowCount(0);
        for (Transaction t : shown) {
            model.addRow(new Object[]{
                    t.getFormattedTimestamp(),
                    t.getSymbol(), t.getType(), t.getQuantity(),
                    String.format("$%.2f", t.getPrice()),
                    t.getRealizedPl() == null ? "" : String.format("$%.2f", t.getRealizedPl())});
        }
        status.setText(shown.size() + " transaction(s). Realized P/L (all sales): $"
                + service.getTotalRealizedProfitLoss().toPlainString());
    }

    private void exportCsv() {
        JFileChooser chooser = new JFileChooser();
        chooser.setSelectedFile(new java.io.File("transactions.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path target = chooser.getSelectedFile().toPath();
        if (Files.exists(target) && JOptionPane.showConfirmDialog(this, target.getFileName() + " exists. Overwrite?",
                "Confirm overwrite", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            CsvExporter.write(target, CsvExporter.transactionsCsv(shown));
            status.setText("Exported " + shown.size() + " row(s) to " + target);
        } catch (IOException e) {
            JOptionPane.showMessageDialog(this, "Export failed: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
