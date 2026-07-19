package Forms;

import Core.Service.LoanLedgerReportPdfRenderer;
import Interface.ILoanLedgerReportProcess;
import Objects.models.IAM.Session;
import Objects.models.LoanLedgerRow;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.io.FileOutputStream;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Loan Ledger Report screen (Reporting layer) — read-only view over
 * vw_LoanLedgerReport (script 17). Filter by loan status (All / Active / Fully
 * Paid / Cancelled); the grid shows each loan's principal, total payable, paid,
 * and outstanding balance. Export renders the same rows via
 * LoanLedgerReportPdfRenderer and logs the print (reprint-aware, per filter).
 * Granted VIEW.
 */
public class LoanLedgerReportPanel extends JPanel {

  private static final Color BRAND_DARK = new Color(0x0D1B2A);
  private static final Color MUTED = new Color(0x6B7682);
  private static final String FONT = "Segoe UI";

  // filter labels and their status codes (null = all)
  private static final String[] FILTER_LABELS = { "All", "Active", "Fully Paid", "Cancelled" };
  private static final Integer[] FILTER_CODES = { null, 0, 1, 2 };

  private final ILoanLedgerReportProcess process;
  private final LoanLedgerReportPdfRenderer renderer;

  private final LedgerTableModel tableModel = new LedgerTableModel();
  private final JTable table = new JTable(tableModel);
  private final JComboBox<String> statusPicker = new JComboBox<>(FILTER_LABELS);
  private final JLabel summaryLabel = new JLabel(" ");
  private JButton refreshBtn;
  private JButton exportBtn;

  private List<LoanLedgerRow> currentRows = new ArrayList<>();

  public LoanLedgerReportPanel(
    ILoanLedgerReportProcess process,
    LoanLedgerReportPdfRenderer renderer
  ) {
    this.process = process;
    this.renderer = renderer;
    setLayout(new BorderLayout());
    setBackground(Color.WHITE);
    add(buildTop(), BorderLayout.NORTH);
    add(buildCenter(), BorderLayout.CENTER);
    add(buildBottom(), BorderLayout.SOUTH);
    wireListeners();
    reload();
  }

  private JComponent buildTop() {
    JPanel top = new JPanel(new BorderLayout());
    top.setBackground(Color.WHITE);
    top.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
    JLabel title = new JLabel("Loan Ledger Report");
    title.setFont(new Font(FONT, Font.BOLD, 20));
    title.setForeground(BRAND_DARK);
    JLabel sub = new JLabel("Employee loans with current outstanding balances.");
    sub.setFont(new Font(FONT, Font.PLAIN, 12));
    sub.setForeground(MUTED);
    JPanel titleBox = new JPanel(new BorderLayout());
    titleBox.setBackground(Color.WHITE);
    titleBox.add(title, BorderLayout.NORTH);
    titleBox.add(sub, BorderLayout.SOUTH);
    JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    controls.setBackground(Color.WHITE);
    controls.add(new JLabel("Status:"));
    controls.add(statusPicker);
    refreshBtn = new JButton("Refresh");
    exportBtn = new JButton("Export PDF");
    controls.add(refreshBtn);
    controls.add(exportBtn);
    top.add(titleBox, BorderLayout.WEST);
    top.add(controls, BorderLayout.EAST);
    return top;
  }

  private JComponent buildCenter() {
    table.setRowHeight(24);
    table.setFont(new Font(FONT, Font.PLAIN, 12));
    table.getTableHeader().setFont(new Font(FONT, Font.BOLD, 12));
    table.setGridColor(new Color(0xE0E0E0));
    table.setFillsViewportHeight(true);
    DefaultTableCellRenderer right = new DefaultTableCellRenderer();
    right.setHorizontalAlignment(SwingConstants.RIGHT);
    for (int col : new int[] { 4, 5, 6, 7 }) {
      table.getColumnModel().getColumn(col).setCellRenderer(right);
    }
    JScrollPane scroll = new JScrollPane(table);
    scroll.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
    return scroll;
  }

  private JComponent buildBottom() {
    JPanel bottom = new JPanel(new BorderLayout());
    bottom.setBackground(Color.WHITE);
    bottom.setBorder(BorderFactory.createEmptyBorder(8, 16, 12, 16));
    summaryLabel.setFont(new Font(FONT, Font.BOLD, 12));
    summaryLabel.setForeground(BRAND_DARK);
    bottom.add(summaryLabel, BorderLayout.WEST);
    return bottom;
  }

  private void wireListeners() {
    refreshBtn.addActionListener(e -> reload());
    statusPicker.addActionListener(e -> reload());
    exportBtn.addActionListener(e -> exportPdf());
  }

  private String selectedLabel() {
    int i = statusPicker.getSelectedIndex();
    return (i < 0) ? "All" : FILTER_LABELS[i];
  }

  private Integer selectedCode() {
    int i = statusPicker.getSelectedIndex();
    return (i < 0) ? null : FILTER_CODES[i];
  }

  private void reload() {
    currentRows = process.GetLoans(selectedCode());
    tableModel.setRows(currentRows);
    double out = 0;
    for (LoanLedgerRow r : currentRows) {
      out += r.GetOutstandingBalance();
    }
    DecimalFormat m = new DecimalFormat("#,##0.00");
    summaryLabel.setText(
      currentRows.size() + " loan(s)   -   Total outstanding: PHP " + m.format(out)
    );
    exportBtn.setEnabled(!currentRows.isEmpty());
  }

  private void exportPdf() {
    if (currentRows.isEmpty()) {
      return;
    }
    String label = selectedLabel();
    try {
      byte[] pdf = renderer.Render(label, currentRows);
      JFileChooser chooser = new JFileChooser();
      chooser.setSelectedFile(new File(LoanLedgerReportPdfRenderer.SuggestFileName(label)));
      if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
        return;
      }
      File target = chooser.getSelectedFile();
      try (FileOutputStream fos = new FileOutputStream(target)) {
        fos.write(pdf);
      }
      boolean isReprint = process.RecordPrint(label, Session.GetUsername(), "Loan Ledger report export (" + label + ")");
      JOptionPane.showMessageDialog(this,
        (isReprint ? "Re-exported" : "Exported") + " Loan Ledger report (" + label + ").",
        "Export complete", JOptionPane.INFORMATION_MESSAGE);
    } catch (SQLException ex) {
      JOptionPane.showMessageDialog(this,
        "The PDF was saved, but the print could not be audited: " + ex.getMessage(),
        "Audit warning", JOptionPane.WARNING_MESSAGE);
    } catch (Exception ex) {
      JOptionPane.showMessageDialog(this,
        "Could not export the report: " + ex.getMessage(),
        "Export failed", JOptionPane.ERROR_MESSAGE);
    }
  }

  private static final class LedgerTableModel extends AbstractTableModel {
    private final String[] cols = {
      "Emp No", "Employee Full Name", "Department", "Loan Type",
      "Principal", "Total Payable", "Paid", "Outstanding", "Terms", "Status",
    };
    private final DecimalFormat money = new DecimalFormat("#,##0.00");
    private List<LoanLedgerRow> rows = new ArrayList<>();

    void setRows(List<LoanLedgerRow> rows) {
      this.rows = (rows != null) ? rows : new ArrayList<>();
      fireTableDataChanged();
    }

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return cols.length; }
    @Override public String getColumnName(int c) { return cols[c]; }

    @Override
    public Object getValueAt(int r, int c) {
      LoanLedgerRow row = rows.get(r);
      switch (c) {
        case 0: return row.GetEmployeeNo();
        case 1: return row.GetEmployeeFullName();
        case 2: return row.GetDepartment();
        case 3: return row.GetLoanType();
        case 4: return money.format(row.GetPrincipal());
        case 5: return money.format(row.GetTotalPayable());
        case 6: return money.format(row.GetAmountPaid());
        case 7: return money.format(row.GetOutstandingBalance());
        case 8: return row.GetTerms();
        case 9: return row.GetStatusLabel();
        default: return "";
      }
    }

    @Override public boolean isCellEditable(int r, int c) { return false; }
  }
}