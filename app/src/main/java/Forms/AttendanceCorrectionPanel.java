package Forms;

import Interface.IAttendanceCorrectionProcess;
import Interface.IEmpMgmtProcess;
import Objects.models.Attendance;
import Objects.models.EmpDetail;
import Objects.models.IAM.Session;
import Objects.results.SaveResult;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.time.LocalTime;
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
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerDateModel;
import javax.swing.table.AbstractTableModel;

/**
 * Punch Correction (Phase 7c) — admin-side fix for dirty clock data.
 *
 * Pick an employee + range, Load their raw attendance, then Edit a row's times
 * or Add a punch for a date with no row (a worked day captured as an absence).
 * Both actions are audited via IAttendanceCorrectionProcess (old -> new + reason,
 * stamped with the acting username). Buttons are gated on the EDIT permission
 * for the PUNCHFIX module.
 *
 * Corrections only flow into pay for periods not yet finalized (re-generation is
 * blocked post-finalize); the header states this so nobody "corrects" a locked
 * payslip expecting pay to move.
 */
public class AttendanceCorrectionPanel extends JPanel {

  private static final Color BRAND_DARK = new Color(0x0D1B2A);
  private static final Color MUTED = new Color(0x6B7682);
  private static final String FONT = "Segoe UI";

  private final IAttendanceCorrectionProcess process;
  private final IEmpMgmtProcess empProcess;
  private final boolean canEdit;

  private final JComboBox<EmpDetail> employeePicker = new JComboBox<>();
  private final JSpinner fromSpinner = new JSpinner(new SpinnerDateModel());
  private final JSpinner toSpinner = new JSpinner(new SpinnerDateModel());

  private final PunchTableModel tableModel = new PunchTableModel();
  private final JTable table = new JTable(tableModel);
  private final JLabel summaryLabel = new JLabel(
    "Pick an employee and click Load."
  );

  private JButton loadBtn;
  private JButton editBtn;
  private JButton addBtn;

  public AttendanceCorrectionPanel(
    IAttendanceCorrectionProcess process,
    IEmpMgmtProcess empProcess,
    List<String> permissions
  ) {
    this.process = process;
    this.empProcess = empProcess;
    this.canEdit = permissions != null && permissions.contains("EDIT");

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

    JLabel title = new JLabel("Punch Correction");
    title.setFont(new Font(FONT, Font.BOLD, 20));
    title.setForeground(BRAND_DARK);

    JLabel sub = new JLabel(
      "Fix clock data. Changes are audited. Corrections affect pay only for periods not yet finalized."
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

    fromSpinner.setEditor(new JSpinner.DateEditor(fromSpinner, "yyyy-MM-dd"));
    toSpinner.setEditor(new JSpinner.DateEditor(toSpinner, "yyyy-MM-dd"));

    loadBtn = brandButton("Load");

    filters.add(new JLabel("Employee:"));
    filters.add(employeePicker);
    filters.add(new JLabel("   From:"));
    filters.add(fromSpinner);
    filters.add(new JLabel("To:"));
    filters.add(toSpinner);
    filters.add(loadBtn);

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
    JPanel bar = new JPanel(new BorderLayout());
    bar.setBackground(new Color(0xECEDEF));
    bar.setBorder(
      BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xD8DBDF)),
        BorderFactory.createEmptyBorder(6, 16, 6, 16)
      )
    );

    summaryLabel.setFont(new Font(FONT, Font.PLAIN, 12));
    summaryLabel.setForeground(BRAND_DARK);

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    actions.setOpaque(false);
    addBtn = plainButton("Add Punch");
    editBtn = brandButton("Edit Punch");
    addBtn.setEnabled(false);
    editBtn.setEnabled(false);
    actions.add(addBtn);
    actions.add(editBtn);

    bar.add(summaryLabel, BorderLayout.WEST);
    bar.add(actions, BorderLayout.EAST);
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
    loadBtn.addActionListener(e -> onLoad());
    editBtn.addActionListener(e -> onEdit());
    addBtn.addActionListener(e -> onAdd());
    table.getSelectionModel().addListSelectionListener(e -> syncButtons());
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

  private EmpDetail selectedEmployee() {
    return (EmpDetail) employeePicker.getSelectedItem();
  }

  private void onLoad() {
    EmpDetail emp = selectedEmployee();
    if (emp == null) {
      return;
    }
    LocalDate from = toLocalDate((Date) fromSpinner.getValue());
    LocalDate to = toLocalDate((Date) toSpinner.getValue());
    if (from.isAfter(to)) {
      JOptionPane.showMessageDialog(
        this,
        "The From date must be on or before the To date.",
        "Punch Correction",
        JOptionPane.WARNING_MESSAGE
      );
      return;
    }

    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    try {
      List<Attendance> rows = process.GetAttendance(
        emp.GetEmployeeId(),
        from,
        to
      );
      tableModel.setData(rows);
      summaryLabel.setText(
        emp.GetFullName() +
          "  \u2014  " +
          rows.size() +
          (rows.size() == 1 ? " row" : " rows")
      );
    } finally {
      setCursor(Cursor.getDefaultCursor());
    }
    syncButtons();
  }

  private void syncButtons() {
    addBtn.setEnabled(canEdit && selectedEmployee() != null);
    editBtn.setEnabled(canEdit && table.getSelectedRow() >= 0);
  }

  private void onEdit() {
    int viewRow = table.getSelectedRow();
    if (viewRow < 0) {
      return;
    }
    Attendance row = tableModel.getAt(table.convertRowIndexToModel(viewRow));

    PunchInput in = promptPunch(
      "Edit punch \u2014 " + row.GetAttendanceDate(),
      row.GetTimeIn(),
      row.GetTimeOut(),
      null
    );
    if (in == null) {
      return;
    }

    SaveResult<Void> result = process.EditPunch(
      row.GetAttendanceId(),
      in.timeIn,
      in.timeOut,
      in.reason,
      Session.GetUsername()
    );
    switch (result.GetOutcome()) {
      case SUCCESS -> {
        JOptionPane.showMessageDialog(
          this,
          "Punch updated.",
          "Done",
          JOptionPane.INFORMATION_MESSAGE
        );
        onLoad();
      }
      default -> JOptionPane.showMessageDialog(
        this,
        result.GetMessage() != null
          ? result.GetMessage()
          : "Could not update the punch.",
        "Error",
        JOptionPane.ERROR_MESSAGE
      );
    }
  }

  private void onAdd() {
    EmpDetail emp = selectedEmployee();
    if (emp == null) {
      return;
    }
    PunchInput in = promptPunch(
      "Add punch \u2014 " + emp.GetFullName(),
      null,
      null,
      toLocalDate((Date) toSpinner.getValue())
    );
    if (in == null) {
      return;
    }
    if (in.date == null) {
      return;
    }

    SaveResult<Long> result = process.AddPunch(
      emp.GetEmployeeId(),
      in.date,
      in.timeIn,
      in.timeOut,
      in.reason,
      Session.GetUsername()
    );
    switch (result.GetOutcome()) {
      case SUCCESS -> {
        JOptionPane.showMessageDialog(
          this,
          "Punch added.",
          "Done",
          JOptionPane.INFORMATION_MESSAGE
        );
        onLoad();
      }
      case VALIDATION_FAILED -> JOptionPane.showMessageDialog(
        this,
        result.GetMessage(),
        "Duplicate date",
        JOptionPane.WARNING_MESSAGE
      );
      default -> JOptionPane.showMessageDialog(
        this,
        result.GetMessage() != null
          ? result.GetMessage()
          : "Could not add the punch.",
        "Error",
        JOptionPane.ERROR_MESSAGE
      );
    }
  }

  // -------------------------------------------------------------------------
  // Punch dialog
  // -------------------------------------------------------------------------

  /** Captured values from the punch dialog. date is null for edits. */
  private static final class PunchInput {

    LocalDate date;
    LocalTime timeIn;
    LocalTime timeOut;
    String reason;
  }

  /**
   * Shows the add/edit dialog. {@code dateForAdd} non-null => an editable date
   * field is shown (add mode); null => date is fixed (edit mode).
   */
  private PunchInput promptPunch(
    String title,
    LocalTime initIn,
    LocalTime initOut,
    LocalDate dateForAdd
  ) {
    boolean addMode = dateForAdd != null;

    JSpinner dateSpinner = new JSpinner(new SpinnerDateModel());
    dateSpinner.setEditor(new JSpinner.DateEditor(dateSpinner, "yyyy-MM-dd"));
    if (addMode) {
      dateSpinner.setValue(toDate(dateForAdd));
    }

    JSpinner inSpinner = timeSpinner(
      initIn != null ? initIn : LocalTime.of(8, 0)
    );
    JSpinner outSpinner = timeSpinner(
      initOut != null ? initOut : LocalTime.of(17, 0)
    );

    JCheckBox noOut = new JCheckBox("No time-out (incomplete)");
    noOut.setSelected(initOut == null && !addMode ? true : false);
    outSpinner.setEnabled(!noOut.isSelected());
    noOut.addActionListener(e -> outSpinner.setEnabled(!noOut.isSelected()));

    JTextField reasonField = new JTextField();

    JPanel form = new JPanel(new GridLayout(0, 2, 8, 6));
    form.setPreferredSize(new Dimension(360, addMode ? 150 : 120));
    if (addMode) {
      form.add(new JLabel("Date:"));
      form.add(dateSpinner);
    }
    form.add(new JLabel("Time In:"));
    form.add(inSpinner);
    form.add(new JLabel("Time Out:"));
    form.add(outSpinner);
    form.add(new JLabel(""));
    form.add(noOut);
    form.add(new JLabel("Reason:"));
    form.add(reasonField);

    int choice = JOptionPane.showConfirmDialog(
      this,
      form,
      title,
      JOptionPane.OK_CANCEL_OPTION,
      JOptionPane.PLAIN_MESSAGE
    );
    if (choice != JOptionPane.OK_OPTION) {
      return null;
    }

    PunchInput result = new PunchInput();
    result.date = addMode ? toLocalDate((Date) dateSpinner.getValue()) : null;
    result.timeIn = toLocalTime((Date) inSpinner.getValue());
    result.timeOut = noOut.isSelected()
      ? null
      : toLocalTime((Date) outSpinner.getValue());
    result.reason = reasonField.getText();

    if (result.timeIn == null) {
      JOptionPane.showMessageDialog(
        this,
        "Time In is required.",
        "Punch Correction",
        JOptionPane.WARNING_MESSAGE
      );
      return null;
    }
    if (result.timeOut != null && !result.timeOut.isAfter(result.timeIn)) {
      JOptionPane.showMessageDialog(
        this,
        "Time Out must be after Time In.",
        "Punch Correction",
        JOptionPane.WARNING_MESSAGE
      );
      return null;
    }
    return result;
  }

  private JSpinner timeSpinner(LocalTime init) {
    JSpinner s = new JSpinner(new SpinnerDateModel());
    s.setEditor(new JSpinner.DateEditor(s, "HH:mm"));
    s.setValue(toDateTime(init));
    return s;
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

  private static Date toDateTime(LocalTime t) {
    return Date.from(
      LocalDate.now().atTime(t).atZone(ZoneId.systemDefault()).toInstant()
    );
  }

  private static LocalDate toLocalDate(Date d) {
    return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
  }

  private static LocalTime toLocalTime(Date d) {
    return d
      .toInstant()
      .atZone(ZoneId.systemDefault())
      .toLocalTime()
      .withSecond(0)
      .withNano(0);
  }

  // -------------------------------------------------------------------------
  // Employee combo renderer
  // -------------------------------------------------------------------------

  private static final class EmployeeCellRenderer
    extends DefaultListCellRenderer
  {

    @Override
    public Component getListCellRendererComponent(
      javax.swing.JList<?> list,
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

  private static final class PunchTableModel extends AbstractTableModel {

    private static final DateTimeFormatter DATE_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT =
      DateTimeFormatter.ofPattern("HH:mm");

    private final String[] cols = {
      "Att. ID",
      "Date",
      "Day",
      "Time In",
      "Time Out",
    };
    private List<Attendance> rows = new ArrayList<>();

    void setData(List<Attendance> r) {
      this.rows = (r != null) ? r : new ArrayList<>();
      fireTableDataChanged();
    }

    Attendance getAt(int row) {
      return rows.get(row);
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
      Attendance a = rows.get(r);
      return switch (c) {
        case 0 -> a.GetAttendanceId();
        case 1 -> a.GetAttendanceDate() != null
          ? a.GetAttendanceDate().format(DATE_FMT)
          : "";
        case 2 -> a.GetAttendanceDate() != null
          ? a
              .GetAttendanceDate()
              .getDayOfWeek()
              .getDisplayName(TextStyle.SHORT, Locale.US)
          : "";
        case 3 -> a.GetTimeIn() != null
          ? a.GetTimeIn().format(TIME_FMT)
          : "\u2014";
        case 4 -> a.GetTimeOut() != null
          ? a.GetTimeOut().format(TIME_FMT)
          : "\u2014";
        default -> null;
      };
    }
  }
}
