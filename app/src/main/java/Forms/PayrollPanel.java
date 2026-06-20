package Forms;

import Helper.AmountUtil;
import Interface.IEmpMgmtProcess;
import Interface.IPayrollProcess;
import Objects.enums.Constants.Months;
import Objects.enums.Status.PayslipStatus;
import Objects.models.IAM.Session;
import Objects.models.PayrollPeriod;
import Objects.models.Payslip;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

/**
 * Payroll Run — the period-based payroll screen (replaces the old per-employee,
 * in-memory "Generate Slip" flow).
 *
 * Flow (generate-all -> review -> finalize -> pay), all delegated to
 * IPayrollProcess — this panel is pure UI and holds no payroll logic:
 *   1. Pick month / year / cut-off and "Generate Payroll" -> RunPeriod(...)
 *      creates (or reuses) the period and saves Draft payslip snapshots for
 *      every active employee.
 *   2. The grid shows the period's payslips (GetPayslipsForPeriod).
 *   3. "Finalize Period" -> FinalizePeriod(...) locks the slips.
 *   4. "Mark as Paid" -> PayPeriod(...) settles the period.
 *
 * An "Open periods" dropdown lets the user jump back to an existing open period
 * to review / finalize / pay it.
 *
 * NOTE: line-item breakdowns (Payroll_Allowance / Payroll_Deduction) are not
 * persisted yet (3c-2), so the grid shows the header aggregates only.
 */
public class PayrollPanel extends JPanel {

  // Brand tokens (match ShellFrame / TimeKeepingPanel)
  private static final Color BRAND_DARK = new Color(0x0D1B2A);
  private static final Color BRAND_RED = new Color(0xE53935);
  private static final Color MUTED = new Color(0x6B7682);
  private static final String FONT = "Segoe UI";

  private final IPayrollProcess payrollProcess;
  @SuppressWarnings("unused")
  private final IEmpMgmtProcess empMgmtProcess; // kept for ctor compatibility

  // Period controls
  private final JComboBox<String> monthPicker = new JComboBox<>();
  private final JComboBox<String> yearPicker = new JComboBox<>();
  private final JRadioButton firstCutOffRdBtn = new JRadioButton("1st cut-off (1\u201315)");
  private final JRadioButton secondCutOffRdBtn = new JRadioButton("2nd cut-off (16\u2013end)");
  private final JButton generateBtn = brandButton("Generate Payroll");

  private final JComboBox<PayrollPeriod> openPeriodPicker = new JComboBox<>();
  private final JButton loadBtn = plainButton("Load");

  // Grid
  private final PayslipRunTableModel tableModel = new PayslipRunTableModel();
  private final JTable table = new JTable(tableModel);

  // Footer: summary + actions
  private final JLabel statusLabel = new JLabel("No period loaded");
  private final JLabel summaryLabel = new JLabel(" ");
  private final JButton finalizeBtn = plainButton("Finalize Period");
  private final JButton payBtn = brandButton("Mark as Paid");

  // State
  private long currentPeriodId = -1;

  public PayrollPanel(IPayrollProcess payrollProcess, IEmpMgmtProcess empMgmtProcess) {
    this.payrollProcess = payrollProcess;
    this.empMgmtProcess = empMgmtProcess;

    setLayout(new BorderLayout());
    setBackground(Color.WHITE);

    add(buildTop(), BorderLayout.NORTH);
    add(buildCenter(), BorderLayout.CENTER);
    add(buildBottom(), BorderLayout.SOUTH);

    wireListeners();
    loadOpenPeriods();
    refreshActions(null);
  }

  // -------------------------------------------------------------------------
  // Layout
  // -------------------------------------------------------------------------

  private JComponent buildTop() {
    JPanel top = new JPanel();
    top.setBackground(Color.WHITE);
    top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
    top.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

    JLabel title = new JLabel("Payroll Run");
    title.setFont(new Font(FONT, Font.BOLD, 20));
    title.setForeground(BRAND_DARK);
    title.setAlignmentX(Component.LEFT_ALIGNMENT);

    for (Months m : Months.values()) {
      monthPicker.addItem(m.GetName());
    }
    int thisYear = Year.now().getValue();
    int startYear = Math.min(2024, thisYear);
    for (int y = startYear; y <= thisYear; y++) {
      yearPicker.addItem(String.valueOf(y));
    }
    yearPicker.setSelectedItem("2024");

    firstCutOffRdBtn.setSelected(true);
    firstCutOffRdBtn.setBackground(Color.WHITE);
    secondCutOffRdBtn.setBackground(Color.WHITE);
    ButtonGroup grp = new ButtonGroup();
    grp.add(firstCutOffRdBtn);
    grp.add(secondCutOffRdBtn);

    JPanel genRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    genRow.setBackground(Color.WHITE);
    genRow.setAlignmentX(Component.LEFT_ALIGNMENT);
    genRow.add(new JLabel("New run:"));
    genRow.add(monthPicker);
    genRow.add(yearPicker);
    genRow.add(firstCutOffRdBtn);
    genRow.add(secondCutOffRdBtn);
    genRow.add(generateBtn);

    JPanel openRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    openRow.setBackground(Color.WHITE);
    openRow.setAlignmentX(Component.LEFT_ALIGNMENT);
    openRow.add(new JLabel("Open periods:"));
    openRow.add(openPeriodPicker);
    openRow.add(loadBtn);

    top.add(title);
    top.add(Box.createVerticalStrut(8));
    top.add(genRow);
    top.add(openRow);
    return top;
  }

  private JComponent buildCenter() {
    table.setFont(new Font(FONT, Font.PLAIN, 13));
    table.setRowHeight(24);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setFillsViewportHeight(true);

    JScrollPane pane = new JScrollPane(table);
    pane.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
    return pane;
  }

  private JComponent buildBottom() {
    JPanel bottom = new JPanel(new BorderLayout());
    bottom.setBackground(Color.WHITE);
    bottom.setBorder(BorderFactory.createEmptyBorder(8, 16, 16, 16));

    JPanel info = new JPanel();
    info.setBackground(Color.WHITE);
    info.setLayout(new BoxLayout(info, BoxLayout.Y_AXIS));
    statusLabel.setFont(new Font(FONT, Font.BOLD, 13));
    statusLabel.setForeground(BRAND_DARK);
    summaryLabel.setFont(new Font(FONT, Font.PLAIN, 12));
    summaryLabel.setForeground(MUTED);
    info.add(statusLabel);
    info.add(summaryLabel);

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    actions.setBackground(Color.WHITE);
    actions.add(finalizeBtn);
    actions.add(payBtn);

    bottom.add(info, BorderLayout.WEST);
    bottom.add(actions, BorderLayout.EAST);
    return bottom;
  }

  // -------------------------------------------------------------------------
  // Listeners + actions
  // -------------------------------------------------------------------------

  private void wireListeners() {
    generateBtn.addActionListener(e -> onGenerate());
    loadBtn.addActionListener(e -> onLoad());
    finalizeBtn.addActionListener(e -> onFinalize());
    payBtn.addActionListener(e -> onPay());
  }

  private void onGenerate() {
    PayrollPeriod period = buildPeriodFromControls();
    runBusy(() -> {
      long periodId = payrollProcess.RunPeriod(
        period, period.GetEndDate(), Session.GetUserId()
      );
      currentPeriodId = periodId;
      loadGrid(periodId);
      loadOpenPeriods();
    }, "Could not generate payroll for this period.");
  }

  private void onLoad() {
    Object sel = openPeriodPicker.getSelectedItem();
    if (!(sel instanceof PayrollPeriod)) {
      return;
    }
    long periodId = ((PayrollPeriod) sel).GetPayrollPeriodId();
    currentPeriodId = periodId;
    runBusy(() -> loadGrid(periodId), "Could not load the selected period.");
  }

  private void onFinalize() {
    if (currentPeriodId <= 0) {
      return;
    }
    int ok = JOptionPane.showConfirmDialog(
      this,
      "Finalize all draft payslips for this period?\nThey become read-only.",
      "Finalize Period",
      JOptionPane.OK_CANCEL_OPTION,
      JOptionPane.WARNING_MESSAGE
    );
    if (ok != JOptionPane.OK_OPTION) {
      return;
    }
    runBusy(() -> {
      payrollProcess.FinalizePeriod(currentPeriodId);
      loadGrid(currentPeriodId);
      loadOpenPeriods();
    }, "Could not finalize the period.");
  }

  private void onPay() {
    if (currentPeriodId <= 0) {
      return;
    }
    int ok = JOptionPane.showConfirmDialog(
      this,
      "Mark this period as paid?\nPay date will be recorded as today.",
      "Mark as Paid",
      JOptionPane.OK_CANCEL_OPTION,
      JOptionPane.QUESTION_MESSAGE
    );
    if (ok != JOptionPane.OK_OPTION) {
      return;
    }
    runBusy(() -> {
      payrollProcess.PayPeriod(currentPeriodId, LocalDate.now());
      loadGrid(currentPeriodId);
      loadOpenPeriods();
    }, "Could not mark the period as paid.");
  }

  // -------------------------------------------------------------------------
  // Data loading
  // -------------------------------------------------------------------------

  private void loadOpenPeriods() {
    try {
      List<PayrollPeriod> open = payrollProcess.GetOpenPeriods();
      openPeriodPicker.removeAllItems();
      for (PayrollPeriod p : open) {
        openPeriodPicker.addItem(p);
      }
      loadBtn.setEnabled(!open.isEmpty());
    } catch (SQLException e) {
      e.printStackTrace();
      loadBtn.setEnabled(false);
    }
  }

  private void loadGrid(long periodId) throws SQLException {
    List<Payslip> slips = payrollProcess.GetPayslipsForPeriod(periodId);
    tableModel.setRows(slips);
    updateSummary(slips);
    refreshActions(slips);
  }

  private void updateSummary(List<Payslip> slips) {
    double gross = 0, net = 0;
    for (Payslip s : slips) {
      gross += s.GetGrossPay();
      net += s.GetNetPay();
    }
    summaryLabel.setText(
      slips.size() + " payslip(s)   \u2022   Gross " +
      AmountUtil.FormatAmount(gross) + "   \u2022   Net " +
      AmountUtil.FormatAmount(net)
    );
  }

  /** Enables actions from the current period's payslip statuses. */
  private void refreshActions(List<Payslip> slips) {
    boolean hasPeriod = currentPeriodId > 0;
    boolean hasSlips = slips != null && !slips.isEmpty();

    boolean anyDraft = false, anyFinalized = false, anyPaid = false;
    if (hasSlips) {
      for (Payslip s : slips) {
        int st = s.GetPayslipStatus() != null ? s.GetPayslipStatus().getValue() : 0;
        if (st == 1) {
          anyFinalized = true;
        } else if (st == 2) {
          anyPaid = true;
        } else {
          anyDraft = true;
        }
      }
    }

    finalizeBtn.setEnabled(hasPeriod && anyDraft);
    payBtn.setEnabled(hasPeriod && anyFinalized && !anyDraft);

    if (!hasPeriod) {
      statusLabel.setText("No period loaded");
    } else if (!hasSlips) {
      statusLabel.setText("Period has no payslips");
    } else if (anyPaid && !anyDraft && !anyFinalized) {
      statusLabel.setText("Paid");
    } else if (anyFinalized && !anyDraft) {
      statusLabel.setText("Finalized \u2014 ready to pay");
    } else {
      statusLabel.setText("Draft \u2014 review, then finalize");
    }
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private PayrollPeriod buildPeriodFromControls() {
    Months month = Months.values()[monthPicker.getSelectedIndex()];
    int year = Integer.parseInt((String) yearPicker.getSelectedItem());
    boolean firstCut = firstCutOffRdBtn.isSelected();

    LocalDate start = firstCut
      ? LocalDate.of(year, month.GetValue(), 1)
      : LocalDate.of(year, month.GetValue(), 16);
    LocalDate end = firstCut
      ? LocalDate.of(year, month.GetValue(), 15)
      : start.withDayOfMonth(start.lengthOfMonth());

    String name =
      month.GetName() + " " + year + (firstCut ? " (1st cut-off)" : " (2nd cut-off)");

    PayrollPeriod period = new PayrollPeriod();
    period.SetPeriodName(name);
    period.SetStartDate(start);
    period.SetEndDate(end);
    return period;
  }

  /** Runs a DB action with a wait cursor; reports SQL and lock errors. */
  private void runBusy(DbAction action, String failMessage) {
    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    try {
      action.run();
    } catch (IllegalStateException locked) {
      JOptionPane.showMessageDialog(
        this, locked.getMessage(), "Period Locked", JOptionPane.WARNING_MESSAGE
      );
    } catch (SQLException ex) {
      ex.printStackTrace();
      JOptionPane.showMessageDialog(
        this, failMessage + "\n" + ex.getMessage(),
        "Database Error", JOptionPane.ERROR_MESSAGE
      );
    } finally {
      setCursor(Cursor.getDefaultCursor());
    }
  }

  @FunctionalInterface
  private interface DbAction {
    void run() throws SQLException;
  }

  private JButton brandButton(String text) {
    JButton b = new JButton(text);
    b.setFont(new Font(FONT, Font.BOLD, 13));
    b.setBackground(BRAND_RED);
    b.setForeground(Color.WHITE);
    b.setFocusPainted(false);
    return b;
  }

  private JButton plainButton(String text) {
    JButton b = new JButton(text);
    b.setFont(new Font(FONT, Font.PLAIN, 13));
    b.setFocusPainted(false);
    return b;
  }

  // -------------------------------------------------------------------------
  // Table model
  // -------------------------------------------------------------------------

  private static class PayslipRunTableModel extends AbstractTableModel {

    private final String[] cols = {
      "Emp #", "Employee", "Basic", "Allowances",
      "Gross", "Deductions", "Net", "Status",
    };
    private List<Payslip> rows = new ArrayList<>();

    void setRows(List<Payslip> r) {
      this.rows = (r != null) ? r : new ArrayList<>();
      fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
      return rows.size();
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
      Payslip p = rows.get(r);
      switch (c) {
        case 0:
          return p.GetEmployeeId();
        case 1:
          return p.GetEmployeeFullName();
        case 2:
          return AmountUtil.FormatAmount(p.GetBasicPay());
        case 3:
          return AmountUtil.FormatAmount(p.GetTotalAllowances());
        case 4:
          return AmountUtil.FormatAmount(p.GetGrossPay());
        case 5:
          return AmountUtil.FormatAmount(p.GetTotalDeductions());
        case 6:
          return AmountUtil.FormatAmount(p.GetNetPay());
        case 7:
          return statusText(p.GetPayslipStatus());
        default:
          return "";
      }
    }

    private static String statusText(PayslipStatus s) {
      if (s == null) {
        return "";
      }
      switch (s.getValue()) {
        case 1:
          return "Finalized";
        case 2:
          return "Paid";
        default:
          return "Draft";
      }
    }
  }
}