package Forms;

import Core.Service.AttendanceCalculator;
import Core.Service.DtrPdfRenderer;
import Interface.IEmpMgmtProcess;
import Interface.ITimeKeepingProcess;
import Objects.models.DailyAttendanceRecord;
import Objects.models.EmpDetail;

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
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
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
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerDateModel;
import javax.swing.table.AbstractTableModel;

/**
 * Daily Time Record (Phase 7b) — read-only per-employee attendance report.
 *
 * Pick one employee + a date range, view that employee's computed daily records
 * (same numbers as the Timekeeping grid and Payroll, via AttendanceCalculator),
 * and export an official DTR to PDF. Records come from the EXACT single-employee
 * read (ITimeKeepingProcess.GetTimeRecordsForEmployee), never a fuzzy search, so
 * the report can only ever contain the selected employee's rows.
 *
 * Pure UI: all data goes through ITimeKeepingProcess + IEmpMgmtProcess; the PDF
 * bytes come from DtrPdfRenderer (no recompute — it renders the records as-is).
 */
public class DtrReportPanel extends JPanel {

  private static final Color BRAND_DARK = new Color(0x0D1B2A);
  private static final Color MUTED = new Color(0x6B7682);
  private static final String FONT = "Segoe UI";

  private final ITimeKeepingProcess timeProcess;
  private final IEmpMgmtProcess empProcess;
  private final DtrPdfRenderer renderer;

  private final JComboBox<EmpDetail> employeePicker = new JComboBox<>();
  private final JSpinner fromSpinner = new JSpinner(new SpinnerDateModel());
  private final JSpinner toSpinner = new JSpinner(new SpinnerDateModel());

  private final DtrTableModel tableModel = new DtrTableModel();
  private final JTable table = new JTable(tableModel);
  private final JLabel summaryLabel = new JLabel("Select an employee and click Generate.");

  private JButton generateBtn;
  private JButton exportBtn;

  // Last-generated state (what Export turns into a PDF)
  private EmpDetail currentEmployee;
  private LocalDate currentFrom;
  private LocalDate currentTo;
  private List<DailyAttendanceRecord> currentRecords = new ArrayList<>();

  public DtrReportPanel(
    ITimeKeepingProcess timeProcess,
    IEmpMgmtProcess empProcess,
    DtrPdfRenderer renderer
  ) {
    this.timeProcess = timeProcess;
    this.empProcess = empProcess;
    this.renderer = renderer;

    setLayout(new BorderLayout());
    setBackground(Color.WHITE);

    add(buildTop(), BorderLayout.NORTH);
    add(buildCenter(), BorderLayout.CENTER);
    add(buildBottom(), BorderLayout.SOUTH);

    initDefaults();
    wireListeners();
    loadEmployees();
  }

  // -------------------------------------------------------------------------
  // Layout
  // -------------------------------------------------------------------------

  private JComponent buildTop() {
    JPanel top = new JPanel(new BorderLayout());
    top.setBackground(Color.WHITE);
    top.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

    JLabel title = new JLabel("Daily Time Record");
    title.setFont(new Font(FONT, Font.BOLD, 20));
    title.setForeground(BRAND_DARK);

    JLabel sub = new JLabel(
      "Per-employee attendance for a date range. Export an official DTR to PDF."
    );
    sub.setFont(new Font(FONT, Font.PLAIN, 12));
    sub.setForeground(MUTED);

    JPanel heads = new JPanel(new BorderLayout());
    heads.setBackground(Color.WHITE);
    heads.add(title, BorderLayout.NORTH);
    heads.add(sub, BorderLayout.SOUTH);

    JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    filters.setBackground(Color.WHITE);

    employeePicker.setFont(new Font(FONT, Font.PLAIN, 13));
    employeePicker.setRenderer(new EmployeeCellRenderer());
    employeePicker.setPrototypeDisplayValue(null);

    fromSpinner.setEditor(new JSpinner.DateEditor(fromSpinner, "yyyy-MM-dd"));
    toSpinner.setEditor(new JSpinner.DateEditor(toSpinner, "yyyy-MM-dd"));

    generateBtn = brandButton("Generate");
    exportBtn = plainButton("Export DTR (PDF)");
    exportBtn.setEnabled(false);

    filters.add(new JLabel("Employee:"));
    filters.add(employeePicker);
    filters.add(new JLabel("   From:"));
    filters.add(fromSpinner);
    filters.add(new JLabel("To:"));
    filters.add(toSpinner);
    filters.add(generateBtn);
    filters.add(exportBtn);

    JPanel wrap = new JPanel(new BorderLayout());
    wrap.setBackground(Color.WHITE);
    wrap.add(heads, BorderLayout.NORTH);
    wrap.add(filters, BorderLayout.SOUTH);

    top.add(wrap, BorderLayout.CENTER);
    return top;
  }

  private JComponent buildCenter() {
    table.setFont(new Font(FONT, Font.PLAIN, 13));
    table.setRowHeight(24);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setFillsViewportHeight(true);
    table.getTableHeader().setFont(new Font(FONT, Font.BOLD, 12));

    JScrollPane scroll = new JScrollPane(table);
    scroll.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
    return scroll;
  }

  private JComponent buildBottom() {
    JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 8));
    bar.setBackground(new Color(0xECEDEF));
    bar.setBorder(
      BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xD8DBDF)),
        BorderFactory.createEmptyBorder(4, 16, 4, 16)
      )
    );
    summaryLabel.setFont(new Font(FONT, Font.PLAIN, 12));
    summaryLabel.setForeground(BRAND_DARK);
    bar.add(summaryLabel);
    return bar;
  }

  // -------------------------------------------------------------------------
  // Behaviour
  // -------------------------------------------------------------------------

  private void initDefaults() {
    LocalDate today = LocalDate.now();
    fromSpinner.setValue(toDate(today.withDayOfMonth(1)));
    toSpinner.setValue(toDate(today));
  }

  private void wireListeners() {
    generateBtn.addActionListener(e -> onGenerate());
    exportBtn.addActionListener(e -> onExport());
  }

  private void loadEmployees() {
    employeePicker.removeAllItems();
    List<EmpDetail> employees = empProcess.GetEmpDetails();
    if (employees != null) {
      for (EmpDetail e : employees) {
        employeePicker.addItem(e);
      }
    }
    if (employeePicker.getItemCount() > 0) {
      employeePicker.setSelectedIndex(0);
    }
  }

  private void onGenerate() {
    EmpDetail emp = (EmpDetail) employeePicker.getSelectedItem();
    if (emp == null) {
      JOptionPane.showMessageDialog(
        this,
        "Select an employee first.",
        "Daily Time Record",
        JOptionPane.INFORMATION_MESSAGE
      );
      return;
    }

    LocalDate from = toLocalDate((Date) fromSpinner.getValue());
    LocalDate to = toLocalDate((Date) toSpinner.getValue());
    if (from.isAfter(to)) {
      JOptionPane.showMessageDialog(
        this,
        "The From date must be on or before the To date.",
        "Daily Time Record",
        JOptionPane.WARNING_MESSAGE
      );
      return;
    }

    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    try {
      List<DailyAttendanceRecord> records =
        timeProcess.GetTimeRecordsForEmployee(emp.GetEmployeeId(), from, to);
      AttendanceCalculator.Summary summary = timeProcess.Summarize(records);

      tableModel.setData(records);
      currentEmployee = emp;
      currentFrom = from;
      currentTo = to;
      currentRecords = records;

      exportBtn.setEnabled(!records.isEmpty());
      summaryLabel.setText(buildSummaryText(emp, records.size(), summary));
    } finally {
      setCursor(Cursor.getDefaultCursor());
    }
  }

  private void onExport() {
    if (currentEmployee == null || currentRecords.isEmpty()) {
      return;
    }
    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    try {
      AttendanceCalculator.Summary summary = timeProcess.Summarize(currentRecords);
      byte[] pdf = renderer.Render(
        currentEmployee,
        currentRecords,
        currentFrom,
        currentTo,
        summary
      );

      JFileChooser chooser = new JFileChooser();
      chooser.setDialogTitle("Save Daily Time Record PDF");
      chooser.setSelectedFile(
        new File(
          DtrPdfRenderer.SuggestFileName(currentEmployee, currentFrom, currentTo)
        )
      );
      if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
        return;
      }

      File target = chooser.getSelectedFile();
      Files.write(target.toPath(), pdf);
      openFile(target);
      summaryLabel.setText("DTR saved to " + target.getName());
    } catch (IOException ex) {
      ex.printStackTrace();
      JOptionPane.showMessageDialog(
        this,
        "Could not generate the DTR PDF.\n" + ex.getMessage(),
        "Error",
        JOptionPane.ERROR_MESSAGE
      );
    } finally {
      setCursor(Cursor.getDefaultCursor());
    }
  }

  private String buildSummaryText(
    EmpDetail emp,
    int rowCount,
    AttendanceCalculator.Summary s
  ) {
    StringBuilder sb = new StringBuilder();
    sb.append(emp.GetFullName()).append("  \u2014  ").append(rowCount).append(
      rowCount == 1 ? " day" : " days"
    );
    if (s != null) {
      sb
        .append("    Worked: ")
        .append(s.GetDaysWorked())
        .append("   Late: ")
        .append(s.GetLateDays())
        .append("   Absent: ")
        .append(s.GetAbsentDays())
        .append("   OT hrs: ")
        .append(s.GetOvertimeHours());
    }
    return sb.toString();
  }

  private void openFile(File f) {
    try {
      if (
        Desktop.isDesktopSupported() &&
        Desktop.getDesktop().isSupported(Desktop.Action.OPEN)
      ) {
        Desktop.getDesktop().open(f);
      }
    } catch (IOException ex) {
      System.err.println("DtrReportPanel.openFile: " + ex.getMessage());
    }
  }

  // -------------------------------------------------------------------------
  // Buttons / date helpers
  // -------------------------------------------------------------------------

  private JButton brandButton(String text) {
    JButton b = baseButton(text);
    b.setBackground(BRAND_DARK);
    b.setForeground(Color.WHITE);
    return b;
  }

  private JButton plainButton(String text) {
    return baseButton(text);
  }

  private JButton baseButton(String text) {
    JButton b = new JButton(text);
    b.setFont(new Font(FONT, Font.BOLD, 12));
    b.setFocusPainted(false);
    b.setBackground(new Color(0xE3E6EA));
    b.setForeground(BRAND_DARK);
    b.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return b;
  }

  private static Date toDate(LocalDate d) {
    return Date.from(d.atStartOfDay(ZoneId.systemDefault()).toInstant());
  }

  private static LocalDate toLocalDate(Date d) {
    return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
  }

  // -------------------------------------------------------------------------
  // Employee combo renderer
  // -------------------------------------------------------------------------

  private static final class EmployeeCellRenderer extends DefaultListCellRenderer {

    @Override
    public Component getListCellRendererComponent(
      JList<?> list,
      Object value,
      int index,
      boolean isSelected,
      boolean cellHasFocus
    ) {
      super.getListCellRendererComponent(
        list,
        value,
        index,
        isSelected,
        cellHasFocus
      );
      if (value instanceof EmpDetail e) {
        setText(e.GetEmployeeId() + "  \u2014  " + e.GetFullName());
      }
      return this;
    }
  }

  // -------------------------------------------------------------------------
  // Table model
  // -------------------------------------------------------------------------

  private static final class DtrTableModel extends AbstractTableModel {

    private static final DateTimeFormatter DATE_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT =
      DateTimeFormatter.ofPattern("HH:mm");

    private final String[] cols = {
      "Date",
      "Day",
      "Time In",
      "Time Out",
      "Status",
      "Late (min)",
      "Worked",
      "OT",
      "Day Type",
    };

    private List<DailyAttendanceRecord> rows = new ArrayList<>();

    void setData(List<DailyAttendanceRecord> r) {
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
      DailyAttendanceRecord rec = rows.get(r);
      return switch (c) {
        case 0 -> rec.GetDate() != null ? rec.GetDate().format(DATE_FMT) : "";
        case 1 -> rec.GetDate() != null
          ? rec.GetDate().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.US)
          : "";
        case 2 -> rec.GetTimeIn() != null
          ? rec.GetTimeIn().format(TIME_FMT)
          : "\u2014";
        case 3 -> rec.GetTimeOut() != null
          ? rec.GetTimeOut().format(TIME_FMT)
          : "\u2014";
        case 4 -> rec.GetStatus() != null ? rec.GetStatus().GetLabel() : "";
        case 5 -> rec.GetLateMinutes() > 0 ? rec.GetLateMinutes() : "";
        case 6 -> hm(rec.GetRegularMinutes());
        case 7 -> rec.GetOvertimeMinutes() > 0 ? hm(rec.GetOvertimeMinutes()) : "";
        case 8 -> switch (rec.GetDayType()) {
          case HOLIDAY -> "Holiday";
          case HOLIDAY_SPECIAL -> "Special Holiday";
          case WEEKEND -> "Weekend";
          case REGULAR -> "Regular";
        };
        default -> null;
      };
    }

    private static String hm(long minutes) {
      if (minutes <= 0) return "0:00";
      return (minutes / 60) + ":" + String.format("%02d", minutes % 60);
    }
  }
}