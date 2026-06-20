package Forms;

import Core.Service.PayslipPdfRenderer;
import Helper.AmountUtil;
import Interface.IEmpMgmtProcess;
import Interface.IPayslipPrintProcess;
import Objects.models.EmpDetail;
import Objects.models.IAM.Session;
import Objects.models.Payslip;
import Objects.models.PayslipDetail;

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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

/**
 * Payslip Register — read-only distribution screen. Pick an employee, pick a
 * year (derived from that employee's own locked slips), select a period, and
 * print the payslip to PDF. No self-service: an admin/HR user prints on request.
 *
 * Pure UI — all data + auditing go through IPayslipPrintProcess; the PDF bytes
 * come from PayslipPdfRenderer (frozen snapshot, no recompute). Every print is
 * logged via RecordPrint, with no cap on reprints.
 */
public class PayslipRegisterPanel extends JPanel {

  private static final Color BRAND_DARK = new Color(0x0D1B2A);
  private static final Color BRAND_RED = new Color(0xE53935);
  private static final Color MUTED = new Color(0x6B7682);
  private static final String FONT = "Segoe UI";

  private final IPayslipPrintProcess printProcess;
  private final IEmpMgmtProcess empMgmtProcess;
  private final PayslipPdfRenderer renderer;

  private final JComboBox<EmpDetail> employeePicker = new JComboBox<>();
  private final JComboBox<Integer> yearPicker = new JComboBox<>();
  private final JButton printBtn = brandButton("Print Payslip (PDF)");
  private final JLabel statusLabel = new JLabel("Select an employee to begin");

  private final PayslipTableModel tableModel = new PayslipTableModel();
  private final JTable table = new JTable(tableModel);

  private List<Payslip> lockedHistory = new ArrayList<>();
  private boolean suppressEvents = false;

  public PayslipRegisterPanel(
    IPayslipPrintProcess printProcess,
    IEmpMgmtProcess empMgmtProcess,
    PayslipPdfRenderer renderer
  ) {
    this.printProcess = printProcess;
    this.empMgmtProcess = empMgmtProcess;
    this.renderer = renderer;

    setLayout(new BorderLayout());
    setBackground(Color.WHITE);
    add(buildTop(), BorderLayout.NORTH);
    add(buildCenter(), BorderLayout.CENTER);
    add(buildBottom(), BorderLayout.SOUTH);

    wireListeners();
    loadEmployees();
  }

  // -------------------------------------------------------------------------
  // Layout
  // -------------------------------------------------------------------------

  private JComponent buildTop() {
    JPanel top = new JPanel();
    top.setBackground(Color.WHITE);
    top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
    top.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

    JLabel title = new JLabel("Payslip Register");
    title.setFont(new Font(FONT, Font.BOLD, 20));
    title.setForeground(BRAND_DARK);
    title.setAlignmentX(Component.LEFT_ALIGNMENT);

    JLabel sub = new JLabel("Print finalized payslips on request. Reprints are unlimited and logged.");
    sub.setFont(new Font(FONT, Font.PLAIN, 12));
    sub.setForeground(MUTED);
    sub.setAlignmentX(Component.LEFT_ALIGNMENT);

    employeePicker.setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(
        JList<?> list, Object value, int index, boolean sel, boolean foc
      ) {
        super.getListCellRendererComponent(list, value, index, sel, foc);
        if (value instanceof EmpDetail e) {
          setText(e.GetEmployeeId() + "  \u2014  " + e.GetLastName() + ", " + e.GetFirstName());
        } else {
          setText("Select employee\u2026");
        }
        return this;
      }
    });

    JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    row.setBackground(Color.WHITE);
    row.setAlignmentX(Component.LEFT_ALIGNMENT);
    row.add(new JLabel("Employee:"));
    row.add(employeePicker);
    row.add(Box.createHorizontalStrut(12));
    row.add(new JLabel("Year:"));
    row.add(yearPicker);

    top.add(title);
    top.add(Box.createVerticalStrut(2));
    top.add(sub);
    top.add(Box.createVerticalStrut(8));
    top.add(row);
    return top;
  }

  private JComponent buildCenter() {
    table.setFont(new Font(FONT, Font.PLAIN, 13));
    table.setRowHeight(24);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setFillsViewportHeight(true);
    table.getSelectionModel().addListSelectionListener(e -> {
      if (!e.getValueIsAdjusting()) {
        printBtn.setEnabled(table.getSelectedRow() >= 0);
      }
    });

    JScrollPane pane = new JScrollPane(table);
    pane.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
    return pane;
  }

  private JComponent buildBottom() {
    JPanel bottom = new JPanel(new BorderLayout());
    bottom.setBackground(Color.WHITE);
    bottom.setBorder(BorderFactory.createEmptyBorder(8, 16, 16, 16));

    statusLabel.setFont(new Font(FONT, Font.PLAIN, 12));
    statusLabel.setForeground(MUTED);

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    actions.setBackground(Color.WHITE);
    printBtn.setEnabled(false);
    actions.add(printBtn);

    bottom.add(statusLabel, BorderLayout.WEST);
    bottom.add(actions, BorderLayout.EAST);
    return bottom;
  }

  // -------------------------------------------------------------------------
  // Listeners
  // -------------------------------------------------------------------------

  private void wireListeners() {
    employeePicker.addActionListener(e -> onEmployeeChanged());
    yearPicker.addActionListener(e -> onYearChanged());
    printBtn.addActionListener(e -> onPrint());
  }

  private void loadEmployees() {
    suppressEvents = true;
    employeePicker.removeAllItems();
    for (EmpDetail e : empMgmtProcess.GetEmpDetails()) {
      employeePicker.addItem(e);
    }
    employeePicker.setSelectedIndex(-1);
    suppressEvents = false;
  }

  private void onEmployeeChanged() {
    if (suppressEvents) return;
    EmpDetail emp = (EmpDetail) employeePicker.getSelectedItem();
    if (emp == null) {
      lockedHistory = new ArrayList<>();
      yearPicker.removeAllItems();
      tableModel.setRows(new ArrayList<>());
      printBtn.setEnabled(false);
      statusLabel.setText("Select an employee to begin");
      return;
    }
    runBusy(() -> {
      lockedHistory = printProcess.GetPrintableHistory(emp.GetEmployeeId());
      populateYears();
      applyYearFilter();
    }, "Could not load payslips for this employee.");
  }

  private void onYearChanged() {
    if (suppressEvents) return;
    applyYearFilter();
  }

  private void populateYears() {
    suppressEvents = true;
    yearPicker.removeAllItems();
    TreeSet<Integer> years = new TreeSet<>(Collections.reverseOrder());
    for (Payslip s : lockedHistory) {
      if (s.GetPeriodStart() != null) years.add(s.GetPeriodStart().getYear());
    }
    for (Integer y : years) yearPicker.addItem(y);
    if (yearPicker.getItemCount() > 0) yearPicker.setSelectedIndex(0);
    suppressEvents = false;
  }

  private void applyYearFilter() {
    Integer year = (Integer) yearPicker.getSelectedItem();
    List<Payslip> rows = new ArrayList<>();
    if (year != null) {
      for (Payslip s : lockedHistory) {
        if (s.GetPeriodStart() != null && s.GetPeriodStart().getYear() == year) rows.add(s);
      }
    }
    tableModel.setRows(rows);
    printBtn.setEnabled(false);
    if (lockedHistory.isEmpty()) {
      statusLabel.setText("No finalized payslips for this employee.");
    } else if (rows.isEmpty()) {
      statusLabel.setText("No payslips in " + year + ".");
    } else {
      statusLabel.setText(rows.size() + " payslip(s) in " + year + ". Select one to print.");
    }
  }

  // -------------------------------------------------------------------------
  // Print
  // -------------------------------------------------------------------------

  private void onPrint() {
    int viewRow = table.getSelectedRow();
    if (viewRow < 0) return;
    Payslip slip = tableModel.getRow(viewRow);

    String reason = JOptionPane.showInputDialog(
      this, "Reason for printing / reprint (optional):", "Print Payslip",
      JOptionPane.QUESTION_MESSAGE
    );
    if (reason == null) return; // cancelled
    String reasonValue = reason.isBlank() ? null : reason.trim();

    runBusy(() -> {
      PayslipDetail detail = printProcess.GetPayslipDetail(slip.GetPayslipId());
      if (detail == null) {
        JOptionPane.showMessageDialog(this, "Payslip could not be found.", "Print", JOptionPane.ERROR_MESSAGE);
        return;
      }
      byte[] pdf = renderer.Render(detail);

      JFileChooser chooser = new JFileChooser();
      chooser.setDialogTitle("Save Payslip PDF");
      chooser.setSelectedFile(new File(PayslipPdfRenderer.SuggestFileName(slip)));
      if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return; // save cancelled

      File target = chooser.getSelectedFile();
      Files.write(target.toPath(), pdf);

      boolean reprint = printProcess.RecordPrint(slip.GetPayslipId(), Session.GetUsername(), reasonValue);
      openFile(target);
      statusLabel.setText(
        (reprint ? "Reprinted" : "Printed") + " \u2014 saved to " + target.getName() + " (logged)"
      );
    }, "Could not generate the payslip PDF.");
  }

  private void openFile(File f) {
    try {
      if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
        Desktop.getDesktop().open(f);
      }
    } catch (IOException ex) {
      // Non-fatal: the PDF is already saved even if the default viewer can't open it.
      System.err.println("PayslipRegisterPanel.openFile: " + ex.getMessage());
    }
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  @FunctionalInterface
  private interface PanelAction {
    void run() throws Exception;
  }

  /** Runs a DB/IO action with a wait cursor; reports any failure in one dialog. */
  private void runBusy(PanelAction action, String failMessage) {
    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    try {
      action.run();
    } catch (Exception ex) {
      ex.printStackTrace();
      JOptionPane.showMessageDialog(
        this, failMessage + "\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE
      );
    } finally {
      setCursor(Cursor.getDefaultCursor());
    }
  }

  private JButton brandButton(String text) {
    JButton b = new JButton(text);
    b.setFont(new Font(FONT, Font.BOLD, 13));
    b.setBackground(BRAND_RED);
    b.setForeground(Color.WHITE);
    b.setFocusPainted(false);
    return b;
  }

  // -------------------------------------------------------------------------
  // Table model
  // -------------------------------------------------------------------------

  private static class PayslipTableModel extends AbstractTableModel {

    private final String[] cols = { "Period", "Status", "Gross", "Deductions", "Net" };
    private List<Payslip> rows = new ArrayList<>();

    void setRows(List<Payslip> r) {
      this.rows = (r != null) ? r : new ArrayList<>();
      fireTableDataChanged();
    }

    Payslip getRow(int i) {
      return rows.get(i);
    }

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return cols.length; }
    @Override public String getColumnName(int c) { return cols[c]; }
    @Override public boolean isCellEditable(int r, int c) { return false; }

    @Override
    public Object getValueAt(int r, int c) {
      Payslip p = rows.get(r);
      return switch (c) {
        case 0 -> p.GetPeriodName() != null
          ? p.GetPeriodName()
          : (p.GetPeriodStart() + " \u2013 " + p.GetPeriodEnd());
        case 1 -> p.GetPayslipStatus() != null ? p.GetPayslipStatus().toString() : "";
        case 2 -> AmountUtil.FormatAmount(p.GetGrossPay());
        case 3 -> AmountUtil.FormatAmount(p.GetTotalDeductions());
        case 4 -> AmountUtil.FormatAmount(p.GetNetPay());
        default -> "";
      };
    }
  }
}