package Forms;

import Core.Service.PayrollSummaryPdfRenderer;
import Interface.IPayrollSummaryProcess;
import Objects.models.IAM.Session;
import Objects.models.PayPeriodOption;
import Objects.models.PayrollSummaryRow;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
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
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Payroll Summary Report — read-only, cross-employee monthly summary over
 * vw_MonthlyPayrollSummary (script 17). One row per employee for the selected
 * month, with statutory IDs, per-statutory contributions, gross and net pay,
 * plus a bold TOTAL row mirroring the MotorPH sample report.
 *
 * Export PDF renders the same rows via PayrollSummaryPdfRenderer (pure, no
 * recompute) and logs each export through RecordPrint (reprint-aware), exactly
 * like the Payslip Register. Granted VIEW; the export is a read + an audit row.
 */
public class PayrollSummaryPanel extends JPanel {

  private static final Color BRAND_DARK = new Color(0x0D1B2A);
  private static final Color MUTED = new Color(0x6B7682);
  private static final Color TOTAL_BG = new Color(0xF0F2F5);
  private static final String FONT = "Segoe UI";

  private final IPayrollSummaryProcess process;
  private final PayrollSummaryPdfRenderer renderer;

  private final SummaryTableModel tableModel = new SummaryTableModel();
  private final JTable table = new JTable(tableModel);
  private final JComboBox<PayPeriodOption> periodPicker = new JComboBox<>();
  private final JLabel summaryLabel = new JLabel(" ");
  private JButton refreshBtn;
  private JButton exportBtn;

  private List<PayrollSummaryRow> currentRows = new ArrayList<>();

  public PayrollSummaryPanel(
    IPayrollSummaryProcess process,
    PayrollSummaryPdfRenderer renderer
  ) {
    this.process = process;
    this.renderer = renderer;

    setLayout(new BorderLayout());
    setBackground(Color.WHITE);

    add(buildTop(), BorderLayout.NORTH);
    add(buildCenter(), BorderLayout.CENTER);
    add(buildBottom(), BorderLayout.SOUTH);

    loadPeriods();
    wireListeners();
    reload();
  }

  // ---- Layout ------------------------------------------------------------

  private JComponent buildTop() {
    JPanel top = new JPanel(new BorderLayout());
    top.setBackground(Color.WHITE);
    top.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

    JLabel title = new JLabel("Payroll Summary Report");
    title.setFont(new Font(FONT, Font.BOLD, 20));
    title.setForeground(BRAND_DARK);

    JLabel sub = new JLabel(
      "Monthly summary of gross income, statutory contributions, and net pay per employee."
    );
    sub.setFont(new Font(FONT, Font.PLAIN, 12));
    sub.setForeground(MUTED);

    JPanel titleBox = new JPanel();
    titleBox.setBackground(Color.WHITE);
    titleBox.setLayout(new javax.swing.BoxLayout(titleBox, javax.swing.BoxLayout.Y_AXIS));
    title.setAlignmentX(Component.LEFT_ALIGNMENT);
    sub.setAlignmentX(Component.LEFT_ALIGNMENT);
    titleBox.add(title);
    titleBox.add(sub);

    JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    controls.setBackground(Color.WHITE);
    JLabel pick = new JLabel("Period:");
    pick.setFont(new Font(FONT, Font.PLAIN, 12));
    pick.setForeground(BRAND_DARK);
    refreshBtn = brandButton("Refresh");
    exportBtn = brandButton("Export PDF");
    controls.add(pick);
    controls.add(periodPicker);
    controls.add(refreshBtn);
    controls.add(exportBtn);

    top.add(titleBox, BorderLayout.WEST);
    top.add(controls, BorderLayout.EAST);
    return top;
  }

  private JComponent buildCenter() {
    table.setFont(new Font(FONT, Font.PLAIN, 12));
    table.setRowHeight(24);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF); // wide report -> horizontal scroll
    table.getTableHeader().setReorderingAllowed(false);

    DefaultTableCellRenderer body = new DefaultTableCellRenderer() {
      @Override
      public Component getTableCellRendererComponent(
        JTable t, Object value, boolean sel, boolean focus, int row, int col) {
        Component c = super.getTableCellRendererComponent(t, value, sel, focus, row, col);
        boolean isTotal = tableModel.IsTotalRow(row);
        c.setFont(new Font(FONT, isTotal ? Font.BOLD : Font.PLAIN, 12));
        if (!sel) {
          c.setBackground(isTotal ? TOTAL_BG : Color.WHITE);
        }
        ((JLabel) c).setHorizontalAlignment(
          tableModel.IsMoneyColumn(col) ? SwingConstants.RIGHT : SwingConstants.LEFT
        );
        return c;
      }
    };
    for (int i = 0; i < tableModel.getColumnCount(); i++) {
      table.getColumnModel().getColumn(i).setCellRenderer(body);
      table.getColumnModel().getColumn(i).setPreferredWidth(tableModel.PreferredWidth(i));
    }

    JScrollPane scroll = new JScrollPane(table);
    scroll.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
    return scroll;
  }

  private JComponent buildBottom() {
    JPanel bottom = new JPanel(new BorderLayout());
    bottom.setBackground(Color.WHITE);
    bottom.setBorder(BorderFactory.createEmptyBorder(8, 16, 16, 16));
    summaryLabel.setFont(new Font(FONT, Font.PLAIN, 12));
    summaryLabel.setForeground(MUTED);
    bottom.add(summaryLabel, BorderLayout.WEST);
    return bottom;
  }

  // ---- Behaviour ---------------------------------------------------------

  private void wireListeners() {
    refreshBtn.addActionListener(e -> reload());
    exportBtn.addActionListener(e -> onExport());
    periodPicker.addActionListener(e -> reload());
  }

  private void loadPeriods() {
    periodPicker.removeAllItems();
    List<PayPeriodOption> periods = process.GetAvailablePeriods();
    for (PayPeriodOption p : periods) {
      periodPicker.addItem(p);
    }
    if (periodPicker.getItemCount() > 0) {
      periodPicker.setSelectedIndex(0); // newest period
    }
  }

  private void reload() {
    PayPeriodOption sel = (PayPeriodOption) periodPicker.getSelectedItem();
    if (sel == null) {
      currentRows = new ArrayList<>();
      tableModel.setData(currentRows);
      summaryLabel.setText("No finalized payroll periods available.");
      exportBtn.setEnabled(false);
      return;
    }
    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    try {
      currentRows = process.GetSummaryForPeriod(sel.GetYear(), sel.GetMonth());
      tableModel.setData(currentRows);
      exportBtn.setEnabled(!currentRows.isEmpty());
      summaryLabel.setText(
        currentRows.isEmpty()
          ? "No payslips found for " + sel + "."
          : currentRows.size() + (currentRows.size() == 1 ? " employee" : " employees")
            + " for " + sel + ". TOTAL row shown at the bottom."
      );
    } finally {
      setCursor(Cursor.getDefaultCursor());
    }
  }

  private void onExport() {
    PayPeriodOption sel = (PayPeriodOption) periodPicker.getSelectedItem();
    if (sel == null || currentRows.isEmpty()) {
      JOptionPane.showMessageDialog(
        this, "There is nothing to export for this period.",
        "Export PDF", JOptionPane.INFORMATION_MESSAGE
      );
      return;
    }

    String reason = JOptionPane.showInputDialog(
      this, "Reason for export / re-export (optional):", "Export Payroll Summary",
      JOptionPane.QUESTION_MESSAGE
    );
    if (reason == null) return; // cancelled
    String reasonValue = reason.isBlank() ? null : reason.trim();

    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    try {
      String monthName = currentRows.get(0).GetPayMonthName();
      byte[] pdf = renderer.Render(sel.GetYear(), sel.GetMonth(), monthName, currentRows);

      JFileChooser chooser = new JFileChooser();
      chooser.setDialogTitle("Save Payroll Summary PDF");
      chooser.setSelectedFile(
        new File(PayrollSummaryPdfRenderer.SuggestFileName(sel.GetYear(), sel.GetMonth()))
      );
      if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;

      File target = chooser.getSelectedFile();
      Files.write(target.toPath(), pdf);

      boolean reprint =
        process.RecordPrint(sel.GetYear(), sel.GetMonth(), Session.GetUsername(), reasonValue);
      openFile(target);
      summaryLabel.setText(
        (reprint ? "Re-exported" : "Exported") + " \u2014 saved to "
          + target.getName() + " (logged)"
      );
    } catch (Exception ex) {
      JOptionPane.showMessageDialog(
        this, "Could not generate the payroll summary PDF:\n" + ex.getMessage(),
        "Export PDF", JOptionPane.ERROR_MESSAGE
      );
    } finally {
      setCursor(Cursor.getDefaultCursor());
    }
  }

  private void openFile(File f) {
    try {
      if (Desktop.isDesktopSupported()
          && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
        Desktop.getDesktop().open(f);
      }
    } catch (IOException ex) {
      // Non-fatal: the PDF is already saved even if the default viewer can't open it.
      System.err.println("PayrollSummaryPanel.openFile: " + ex.getMessage());
    }
  }

  private JButton brandButton(String text) {
    JButton b = new JButton(text);
    b.setFont(new Font(FONT, Font.BOLD, 12));
    b.setFocusPainted(false);
    b.setBackground(BRAND_DARK);
    b.setForeground(Color.WHITE);
    b.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return b;
  }

  // ---- Table model -------------------------------------------------------

  private static final class SummaryTableModel extends AbstractTableModel {

    private static final DecimalFormat MONEY = new DecimalFormat("\u20B1#,##0.00");

    private final String[] cols = {
      "Employee No", "Employee Full Name", "Position", "Department",
      "Gross Income",
      "SSS No.", "SSS Contribution",
      "PhilHealth No.", "PhilHealth Contribution",
      "Pag-IBIG No.", "Pag-IBIG Contribution",
      "TIN", "Withholding Tax",
      "Net Pay"
    };
    private final int[] widths = {
      90, 200, 200, 130, 110,
      120, 130, 130, 150, 120, 140, 130, 120, 110
    };
    private final boolean[] money = {
      false, false, false, false, true,
      false, true, false, true, false, true, false, true, true
    };

    private List<PayrollSummaryRow> rows = new ArrayList<>();
    private boolean hasTotal = false;
    private double tGross, tSss, tPhil, tPag, tTax, tNet;

    void setData(List<PayrollSummaryRow> r) {
      this.rows = (r != null) ? r : new ArrayList<>();
      tGross = tSss = tPhil = tPag = tTax = tNet = 0;
      for (PayrollSummaryRow row : rows) {
        tGross += row.GetGrossIncome();
        tSss += row.GetSocialSecurityContribution();
        tPhil += row.GetPhilHealthContribution();
        tPag += row.GetPagIbigContribution();
        tTax += row.GetWithholdingTax();
        tNet += row.GetNetPay();
      }
      this.hasTotal = !rows.isEmpty();
      fireTableDataChanged();
    }

    boolean IsTotalRow(int row) {
      return hasTotal && row == rows.size();
    }

    boolean IsMoneyColumn(int col) {
      return money[col];
    }

    int PreferredWidth(int col) {
      return widths[col];
    }

    @Override
    public int getRowCount() {
      return rows.size() + (hasTotal ? 1 : 0);
    }

    @Override
    public int getColumnCount() {
      return cols.length;
    }

    @Override
    public String getColumnName(int c) {
      return cols[c];
    }

    @Override
    public boolean isCellEditable(int r, int c) {
      return false;
    }

    @Override
    public Object getValueAt(int r, int c) {
      if (IsTotalRow(r)) {
        return switch (c) {
          case 1 -> "TOTAL";
          case 4 -> MONEY.format(tGross);
          case 6 -> MONEY.format(tSss);
          case 8 -> MONEY.format(tPhil);
          case 10 -> MONEY.format(tPag);
          case 12 -> MONEY.format(tTax);
          case 13 -> MONEY.format(tNet);
          default -> "";
        };
      }
      PayrollSummaryRow row = rows.get(r);
      return switch (c) {
        case 0 -> row.GetEmployeeNo();
        case 1 -> row.GetEmployeeFullName();
        case 2 -> row.GetPosition();
        case 3 -> row.GetDepartment();
        case 4 -> MONEY.format(row.GetGrossIncome());
        case 5 -> row.GetSocialSecurityNo();
        case 6 -> MONEY.format(row.GetSocialSecurityContribution());
        case 7 -> row.GetPhilHealthNo();
        case 8 -> MONEY.format(row.GetPhilHealthContribution());
        case 9 -> row.GetPagIbigNo();
        case 10 -> MONEY.format(row.GetPagIbigContribution());
        case 11 -> row.GetTin();
        case 12 -> MONEY.format(row.GetWithholdingTax());
        case 13 -> MONEY.format(row.GetNetPay());
        default -> null;
      };
    }
  }
}