package Forms;

import Core.Component.ComponentFactory;
import Core.Component.SmartComboBox;
import Core.Component.SmartTextField;
import Core.Enum.SmartFieldType;
import Core.Service.FormControlService;
import Interface.IEmpMgmtProcess;
import Objects.enums.Status.EmploymentStatus;
import Objects.models.AllowanceInfo;
import Objects.models.DepartmentInfo;
import Objects.models.EmpDetail;
import Objects.models.EmployeeAddressInfo;
import Objects.models.EmployeeSalaryInfo;
import Objects.models.PositionInfo;
import Objects.models.StatutoryInfo;
import Objects.models.WorkScheduleInfo;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

/**
 * Employee Management — master-detail CRUD screen for the EMPMGMT module.
 *
 * Layout: directory TABLE on top, editable detail FORM below (disabled by
 * default). Flow (D-U1): select a row -> form populates (VIEW, read-only) ->
 * Edit enables the fields -> Accept validates and (P2-3) persists -> back to
 * VIEW. Add New blanks the form into ADD mode; Delete soft-deletes.
 *
 * Talks only to IEmpMgmtProcess (no raw DAOs) and validates via
 * FormControlService over the SmartField elements. Mandatory-ness lives on the
 * field types (SmartFieldType) and on the SmartComboBox mandatory flag.
 *
 * P2 COMPLETE: table + load/populate + state machine, add/update writes
 * (P2-3) and soft-delete + RBAC gating (P2-4). Talks only to IEmpMgmtProcess
 * (+ a granted-permission list); validates via FormControlService.
 */
public class EmployeeManagementPanel extends JPanel {

  // Brand tokens (match ShellFrame / PayrollPanel)
  private static final Color BRAND_DARK = new Color(0x0D1B2A);
  private static final Color BRAND_RED = new Color(0xE53935);
  private static final Color MUTED = new Color(0x6B7682);
  private static final String FONT = "Segoe UI";
  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern(
    "MM/dd/yyyy"
  );

  private enum Mode {
    VIEW,
    ADD,
    EDIT,
  }

  private final IEmpMgmtProcess process;
  private final FormControlService validator;
  private final boolean canAdd;
  private final boolean canEdit;
  private final boolean canDelete;

  // ---- Master: search + table -------------------------------------------
  private final JTextField searchField = new JTextField(18);
  private final EmployeeTableModel tableModel = new EmployeeTableModel();
  private final JTable table = new JTable(tableModel);

  // ---- Detail: identity --------------------------------------------------
  private final JLabel empIdValue = new JLabel("\u2014");
  private final SmartTextField lastNameField =
    ComponentFactory.createSmartField(SmartFieldType.NAME);
  private final SmartTextField firstNameField =
    ComponentFactory.createSmartField(SmartFieldType.NAME);
  private final SmartTextField birthdayField =
    ComponentFactory.createSmartField(SmartFieldType.DATE);
  private final SmartTextField emailField = ComponentFactory.createSmartField(
    SmartFieldType.EMAIL
  );
  private final SmartTextField phoneField = ComponentFactory.createSmartField(
    SmartFieldType.PHONE
  );

  // ---- Detail: address (street/barangay/house often blank in data -> optional)
  private final SmartTextField houseField = ComponentFactory.createSmartField(
    SmartFieldType.GENERIC
  );
  private final SmartTextField streetField = ComponentFactory.createSmartField(
    SmartFieldType.GENERIC
  );
  private final SmartTextField barangayField =
    ComponentFactory.createSmartField(SmartFieldType.GENERIC);
  private final SmartTextField cityField = ComponentFactory.createSmartField(
    SmartFieldType.MANDATORY_GENERIC
  );
  private final SmartTextField provinceField =
    ComponentFactory.createSmartField(SmartFieldType.MANDATORY_GENERIC);
  private final SmartTextField zipField = ComponentFactory.createSmartField(
    SmartFieldType.MANDATORY_INTEGER
  );

  // ---- Detail: statutory IDs --------------------------------------------
  private final SmartTextField sssField = ComponentFactory.createSmartField(
    SmartFieldType.SSS
  );
  private final SmartTextField philHealthField =
    ComponentFactory.createSmartField(SmartFieldType.PHILHEALTH);
  private final SmartTextField tinField = ComponentFactory.createSmartField(
    SmartFieldType.TIN
  );
  private final SmartTextField pagIbigField = ComponentFactory.createSmartField(
    SmartFieldType.PAGIBIG
  );

  // ---- Detail: assignment ------------------------------------------------
  private final SmartComboBox<PositionInfo> positionCombo =
    ComponentFactory.createSmartCombo(true);
  private final SmartComboBox<DepartmentInfo> departmentCombo =
    ComponentFactory.createSmartCombo(true);
  private final SmartComboBox<WorkScheduleInfo> scheduleCombo =
    ComponentFactory.createSmartCombo(true);
  private final SmartComboBox<EmploymentStatus> statusCombo =
    ComponentFactory.createSmartCombo(true);
  private final SmartTextField dateHiredField =
    ComponentFactory.createSmartField(SmartFieldType.DATE);

  // ---- Detail: compensation + allowances --------------------------------
  private final SmartTextField basicSalaryField =
    ComponentFactory.createSmartField(SmartFieldType.CURRENCY);
  private final SmartTextField hourlyRateField =
    ComponentFactory.createSmartField(SmartFieldType.CURRENCY);
  private final SmartTextField riceField = ComponentFactory.createSmartField(
    SmartFieldType.CURRENCY
  );
  private final SmartTextField phoneAllowanceField =
    ComponentFactory.createSmartField(SmartFieldType.CURRENCY);
  private final SmartTextField clothingField =
    ComponentFactory.createSmartField(SmartFieldType.CURRENCY);

  // ---- Toolbar -----------------------------------------------------------
  private final JButton addBtn = plainButton("Add New");
  private final JButton editBtn = plainButton("Edit");
  private final JButton deleteBtn = dangerButton("Delete");
  private final JButton acceptBtn = brandButton("Accept");
  private final JButton cancelBtn = plainButton("Cancel");

  // ---- State -------------------------------------------------------------
  private List<EmpDetail> rows = new ArrayList<>();
  private EmpDetail current; // currently selected / loaded employee
  private Mode mode = Mode.VIEW;
  private List<JComponent> editableInputs;

  public EmployeeManagementPanel(
    IEmpMgmtProcess process,
    FormControlService validator,
    List<String> permissions
  ) {
    this.process = process;
    this.validator = validator;
    this.canAdd = permissions != null && permissions.contains("ADD");
    this.canEdit = permissions != null && permissions.contains("EDIT");
    this.canDelete = permissions != null && permissions.contains("DELETE");

    setLayout(new BorderLayout());
    setBackground(Color.WHITE);

    JSplitPane split = new JSplitPane(
      JSplitPane.VERTICAL_SPLIT,
      buildMaster(),
      buildDetail()
    );
    split.setResizeWeight(0.42);
    split.setEnabled(false); // user can't drag the divider
    split.setDividerSize(1); // thin separator, no grab handle
    split.setBorder(BorderFactory.createEmptyBorder());
    add(split, BorderLayout.CENTER);
    hourlyRateField.setEditable(false); // derived from basic salary, not typed

    // RBAC: hide actions the role isn't granted (HR holds ADD/EDIT/DELETE).
    addBtn.setVisible(canAdd);
    editBtn.setVisible(canEdit);
    deleteBtn.setVisible(canDelete);

    collectEditableInputs();
    loadDropdowns();
    wireListeners();
    reloadTable(null);
    setMode(Mode.VIEW);
  }

  // =========================================================================
  // Master (top)
  // =========================================================================
  private JComponent buildMaster() {
    JPanel master = new JPanel(new BorderLayout());
    master.setBackground(Color.WHITE);
    master.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

    JLabel title = new JLabel("Employee Management");
    title.setFont(new Font(FONT, Font.BOLD, 20));
    title.setForeground(BRAND_DARK);

    JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    searchRow.setBackground(Color.WHITE);
    searchField.setFont(new Font(FONT, Font.PLAIN, 13));
    searchField.setToolTipText("Employee #, name, or position");
    JButton searchBtn = plainButton("Search");
    JButton refreshBtn = plainButton("Refresh");
    searchRow.add(new JLabel("Search:"));
    searchRow.add(searchField);
    searchRow.add(searchBtn);
    searchRow.add(refreshBtn);

    JPanel header = new JPanel(new BorderLayout());
    header.setBackground(Color.WHITE);
    header.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0)); // gap above the grid
    header.add(title, BorderLayout.WEST);
    header.add(searchRow, BorderLayout.EAST);

    table.setFont(new Font(FONT, Font.PLAIN, 13));
    table.setRowHeight(24);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setFillsViewportHeight(true);
    table.getTableHeader().setFont(new Font(FONT, Font.BOLD, 12));

    master.add(header, BorderLayout.NORTH);
    master.add(new JScrollPane(table), BorderLayout.CENTER);

    searchBtn.addActionListener(e -> applySearch());
    refreshBtn.addActionListener(e -> {
      searchField.setText("");
      reloadTable(null);
    });
    searchField.addActionListener(e -> applySearch());
    return master;
  }

  // =========================================================================
  // Detail (bottom): form + toolbar
  // =========================================================================
  private JComponent buildDetail() {
    JPanel form = new JPanel();
    form.setBackground(Color.WHITE);
    form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
    form.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

    JPanel personal = section("Personal");
    field(personal, "Employee #", empIdValue);
    field(personal, "Last name", lastNameField);
    field(personal, "First name", firstNameField);
    field(personal, "Birthday (MM/DD/YYYY)", birthdayField);
    field(personal, "Email", emailField);
    field(personal, "Phone", phoneField);

    JPanel address = section("Address");
    field(address, "House/Block/Lot", houseField);
    field(address, "Street", streetField);
    field(address, "Barangay", barangayField);
    field(address, "City / Municipality", cityField);
    field(address, "Province", provinceField);
    field(address, "ZIP code", zipField);

    JPanel statutory = section("Statutory IDs");
    field(statutory, "SSS", sssField);
    field(statutory, "PhilHealth", philHealthField);
    field(statutory, "TIN", tinField);
    field(statutory, "Pag-IBIG", pagIbigField);

    JPanel assignment = section("Assignment");
    field(assignment, "Position", positionCombo);
    field(assignment, "Department", departmentCombo);
    field(assignment, "Work schedule", scheduleCombo);
    field(assignment, "Employment status", statusCombo);
    field(assignment, "Date hired (MM/DD/YYYY)", dateHiredField);

    JPanel pay = section("Compensation & Allowances");
    field(pay, "Basic salary", basicSalaryField);
    field(pay, "Hourly rate (auto)", hourlyRateField);
    field(pay, "Rice subsidy", riceField);
    field(pay, "Phone allowance", phoneAllowanceField);
    field(pay, "Clothing allowance", clothingField);

    form.add(personal);
    form.add(Box.createVerticalStrut(8));
    form.add(address);
    form.add(Box.createVerticalStrut(8));
    form.add(statutory);
    form.add(Box.createVerticalStrut(8));
    form.add(assignment);
    form.add(Box.createVerticalStrut(8));
    form.add(pay);

    JScrollPane formScroll = new JScrollPane(form);
    formScroll.setBorder(BorderFactory.createEmptyBorder());
    formScroll.getVerticalScrollBar().setUnitIncrement(16);

    JPanel detail = new JPanel(new BorderLayout());
    detail.setBackground(Color.WHITE);
    detail.add(formScroll, BorderLayout.CENTER);
    detail.add(buildToolbar(), BorderLayout.SOUTH);
    return detail;
  }

  private JComponent buildToolbar() {
    JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
    bar.setBackground(Color.WHITE);
    bar.setBorder(
      BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xE3E6EA))
    );
    bar.add(addBtn);
    bar.add(editBtn);
    bar.add(deleteBtn);
    bar.add(cancelBtn);
    bar.add(acceptBtn);
    return bar;
  }

  // =========================================================================
  // Section / field helpers (GridBag rows; row counter via client property)
  // =========================================================================
  private JPanel section(String title) {
    JPanel p = new JPanel(new GridBagLayout());
    p.setOpaque(false);
    p.setBorder(BorderFactory.createTitledBorder(title));
    p.putClientProperty("gy", 0);
    return p;
  }

  private void field(JPanel section, String label, JComponent comp) {
    int gy = (int) section.getClientProperty("gy");
    GridBagConstraints g = new GridBagConstraints();
    g.insets = new Insets(3, 6, 3, 6);
    g.gridx = 0;
    g.gridy = gy;
    g.anchor = GridBagConstraints.WEST;
    boolean required = isMandatory(comp) && comp != hourlyRateField; // hourly is auto-derived
    JLabel l = new JLabel(
      required
        ? "<html>" + label + " <font color='#E53935'>*</font></html>"
        : label
    );
    l.setFont(new Font(FONT, Font.PLAIN, 12));
    l.setForeground(MUTED);
    section.add(l, g);

    g.gridx = 1;
    g.weightx = 1;
    g.fill = GridBagConstraints.HORIZONTAL;
    if (comp instanceof JTextField || comp instanceof JComboBox) {
      comp.setPreferredSize(new Dimension(260, 26));
    }
    section.add(comp, g);
    section.putClientProperty("gy", gy + 1);
  }

  private boolean isMandatory(JComponent comp) {
    if (comp instanceof SmartTextField tf) return tf
      .getSmartType()
      .isMandatory();
    if (comp instanceof SmartComboBox<?> cb) return cb.isMandatory();
    return false;
  }

  // =========================================================================
  // Data load
  // =========================================================================
  private void loadDropdowns() {
    for (PositionInfo p : process.GetAllPositions()) {
      positionCombo.addItem(p);
    }
    for (DepartmentInfo d : process.GetAllDepartments()) {
      departmentCombo.addItem(d);
    }
    for (WorkScheduleInfo s : process.GetAllSchedules()) {
      scheduleCombo.addItem(s);
    }
    for (EmploymentStatus st : EmploymentStatus.values()) {
      statusCombo.addItem(st);
    }
    setRenderer(scheduleCombo, WorkScheduleInfo::GetScheduleName);
    setRenderer(statusCombo, EmployeeManagementPanel::statusLabel);
    // No deliberate selection until a record is loaded / Add is pressed.
    positionCombo.setSelectedIndex(-1);
    departmentCombo.setSelectedIndex(-1);
    scheduleCombo.setSelectedIndex(-1);
    statusCombo.setSelectedIndex(-1);
  }

  private void reloadTable(List<EmpDetail> data) {
    reloadTable(data, -1L); // -1 -> default to the first row
  }

  // preferredId: select that employee's row after reload; falls back to row 0.
  private void reloadTable(List<EmpDetail> data, long preferredId) {
    rows = (data != null) ? data : process.GetEmpDetails();
    tableModel.setData(rows);
    clearForm();
    current = null;
    setMode(Mode.VIEW);
    if (!rows.isEmpty()) {
      int idx = (preferredId > 0) ? indexOfEmployee(preferredId) : 0;
      if (idx < 0) idx = 0; // not found (e.g. filtered out) -> first row
      table.setRowSelectionInterval(idx, idx);
    }
  }

  private int indexOfEmployee(long empId) {
    for (int i = 0; i < rows.size(); i++) {
      if (rows.get(i).GetEmployeeId() == empId) return i;
    }
    return -1;
  }

  private void applySearch() {
    String q = searchField.getText().trim();
    reloadTable(q.isEmpty() ? null : process.SearchEmployee(q));
  }

  // =========================================================================
  // Selection -> load full record into the form (read-only)
  // =========================================================================
  private void onRowSelected() {
    if (mode != Mode.VIEW) {
      return; // ignore selection changes while editing/adding
    }
    int viewRow = table.getSelectedRow();
    if (viewRow < 0 || viewRow >= rows.size()) {
      return;
    }
    long empId = rows.get(viewRow).GetEmployeeId();
    EmpDetail full = process.GetCompleteEmployee(empId);
    if (full != null) {
      current = full;
      populateForm(full);
      setMode(Mode.VIEW); // refresh button enablement now that a record exists
    }
  }

  private void populateForm(EmpDetail e) {
    if (e == null) {
      clearForm();
      return;
    }
    empIdValue.setText(String.valueOf(e.GetEmployeeId()));
    lastNameField.setText(nz(e.GetLastName()));
    firstNameField.setText(nz(e.GetFirstName()));
    birthdayField.setText(fmtDate(e.GetBirthday()));
    emailField.setText(nz(e.GetEmail()));
    phoneField.setText(nz(e.GetPhoneNo()));

    EmployeeAddressInfo a = e.GetAddress();
    houseField.setText(a != null ? nz(a.GetHouseBlkLotNo()) : "");
    streetField.setText(a != null ? nz(a.GetStreet()) : "");
    barangayField.setText(a != null ? nz(a.GetBarangay()) : "");
    cityField.setText(a != null ? nz(a.GetCityMunicipality()) : "");
    provinceField.setText(a != null ? nz(a.GetProvince()) : "");
    zipField.setText(a != null ? nz(a.GetZipCode()) : "");

    StatutoryInfo s = e.GetStatutory();
    sssField.setText(s != null ? nz(s.GetSssNo()) : "");
    philHealthField.setText(s != null ? nz(s.GetPhilHealthNo()) : "");
    tinField.setText(s != null ? nz(s.GetTinNo()) : "");
    pagIbigField.setText(s != null ? nz(s.GetPagIbigNo()) : "");

    selectPosition(e.GetPosition());
    selectDepartment(e.GetDepartment());
    selectSchedule(e.GetWorkSchedule());
    statusCombo.setSelectedItem(e.GetEmpStatus());
    dateHiredField.setText(fmtDate(e.GetDateHired()));

    EmployeeSalaryInfo c = e.GetCompensation();
    basicSalaryField.setText(c != null ? money(c.GetBasicSalary()) : "");
    recomputeHourly(); // hourly is derived from basic — keep display == formula

    riceField.setText(allowanceAmount(e, "Rice Subsidy"));
    phoneAllowanceField.setText(allowanceAmount(e, "Phone Allowance"));
    clothingField.setText(allowanceAmount(e, "Clothing Allowance"));
  }

  private void clearForm() {
    empIdValue.setText("\u2014");
    for (JComponent c : editableInputs) {
      if (c instanceof JTextField tf) {
        tf.setText("");
      } else if (c instanceof JComboBox<?> combo) {
        combo.setSelectedIndex(-1);
      }
    }
    hourlyRateField.setText(""); // derived field, not in editableInputs -> clear explicitly
  }

  // =========================================================================
  // State machine
  // =========================================================================
  private void setMode(Mode m) {
    this.mode = m;
    boolean editing = (m == Mode.ADD || m == Mode.EDIT);

    setInputsEnabled(editing);
    if (!editing) {
      clearFieldErrors(); // VIEW is read-only — never show validation highlights
    }

    // Toolbar swaps: VIEW actions while viewing, edit actions while editing.
    addBtn.setVisible(canAdd && !editing);
    editBtn.setVisible(canEdit && !editing);
    deleteBtn.setVisible(canDelete && !editing);
    acceptBtn.setVisible(editing);
    cancelBtn.setVisible(editing);

    addBtn.setEnabled(canAdd && m == Mode.VIEW);
    editBtn.setEnabled(canEdit && m == Mode.VIEW && current != null);
    deleteBtn.setEnabled(canDelete && m == Mode.VIEW && current != null);
    acceptBtn.setEnabled(editing);
    cancelBtn.setEnabled(editing);

    // Lock the table while editing so selection can't swap the record mid-edit.
    table.setEnabled(m == Mode.VIEW);
  }

  private void clearFieldErrors() {
    for (JComponent c : editableInputs) {
      if (c instanceof Core.Component.SmartField sf) {
        sf.displayError(false);
      }
    }
  }

  private void setInputsEnabled(boolean enabled) {
    for (JComponent c : editableInputs) {
      c.setEnabled(enabled);
    }
  }

  private void collectEditableInputs() {
    editableInputs = new ArrayList<>();
    editableInputs.add(lastNameField);
    editableInputs.add(firstNameField);
    editableInputs.add(birthdayField);
    editableInputs.add(emailField);
    editableInputs.add(phoneField);
    editableInputs.add(houseField);
    editableInputs.add(streetField);
    editableInputs.add(barangayField);
    editableInputs.add(cityField);
    editableInputs.add(provinceField);
    editableInputs.add(zipField);
    editableInputs.add(sssField);
    editableInputs.add(philHealthField);
    editableInputs.add(tinField);
    editableInputs.add(pagIbigField);
    editableInputs.add(positionCombo);
    editableInputs.add(departmentCombo);
    editableInputs.add(scheduleCombo);
    editableInputs.add(statusCombo);
    editableInputs.add(dateHiredField);
    editableInputs.add(basicSalaryField);
    editableInputs.add(riceField);
    editableInputs.add(phoneAllowanceField);
    editableInputs.add(clothingField);
  }

  // =========================================================================
  // Listeners + actions
  // =========================================================================
  private void wireListeners() {
    table
      .getSelectionModel()
      .addListSelectionListener(e -> {
        if (!e.getValueIsAdjusting()) {
          onRowSelected();
        }
      });

    basicSalaryField.addFocusListener(
      new FocusAdapter() {
        @Override
        public void focusLost(FocusEvent ev) {
          recomputeHourly();
        }
      }
    );

    addBtn.addActionListener(e -> onAdd());
    editBtn.addActionListener(e -> onEdit());
    deleteBtn.addActionListener(e -> onDelete());
    cancelBtn.addActionListener(e -> onCancel());
    acceptBtn.addActionListener(e -> onAccept());
  }

  private void onAdd() {
    table.clearSelection();
    current = null;
    clearForm();
    setMode(Mode.ADD);
    statusCombo.setSelectedItem(EmploymentStatus.PROBATIONARY); // new hires start probationary
    lastNameField.requestFocusInWindow();
  }

  private void onEdit() {
    if (current == null) {
      return;
    }
    setMode(Mode.EDIT);
  }

  private void onCancel() {
    if (current != null) {
      populateForm(current); // revert any edits
    } else {
      clearForm();
    }
    setMode(Mode.VIEW);
  }

  private void onDelete() {
    if (!canDelete || current == null) {
      return;
    }
    int choice = JOptionPane.showConfirmDialog(
      this,
      "Deactivate employee " +
        current.GetEmployeeId() +
        " (" +
        current.GetFullName() +
        ")?\n\nThis is a soft delete: the record is " +
        "retained but excluded from the directory and from payroll runs.",
      "Confirm delete",
      JOptionPane.YES_NO_OPTION,
      JOptionPane.WARNING_MESSAGE
    );
    if (choice != JOptionPane.YES_OPTION) {
      return;
    }
    if (process.DeleteEmployee(current.GetEmployeeId())) {
      JOptionPane.showMessageDialog(
        this,
        "Employee deactivated.",
        "Success",
        JOptionPane.INFORMATION_MESSAGE
      );
      reloadTable(null);
    } else {
      JOptionPane.showMessageDialog(
        this,
        "Delete failed. Please try again.",
        "Error",
        JOptionPane.ERROR_MESSAGE
      );
    }
  }

  private void onAccept() {
    // 1. Field-level validation (format + mandatory + combo selection).
    if (!validator.validate(this)) {
      JOptionPane.showMessageDialog(
        this,
        "Please correct the highlighted fields.",
        "Validation",
        JOptionPane.ERROR_MESSAGE
      );
      return;
    }
    // 2. Business rule: allowances must be > 0 (D-U3 / D-A1 — never submit 0).
    if (!allowancesPositive()) {
      JOptionPane.showMessageDialog(
        this,
        "Allowance amounts must be greater than zero.",
        "Validation",
        JOptionPane.ERROR_MESSAGE
      );
      return;
    }
    // 3. Build the aggregate and persist (Add or Update by mode).
    EmpDetail e = scrapeForm();
    boolean ok = (mode == Mode.ADD)
      ? process.AddEmployee(e)
      : process.UpdateEmployee(e);

    if (ok) {
      JOptionPane.showMessageDialog(
        this,
        mode == Mode.ADD ? "Employee added." : "Employee updated.",
        "Success",
        JOptionPane.INFORMATION_MESSAGE
      );
      reloadTable(null, e.GetEmployeeId()); // land on the just-saved record (Add or Edit)
    } else {
      JOptionPane.showMessageDialog(
        this,
        "Save failed. Please check the data and try again.",
        "Error",
        JOptionPane.ERROR_MESSAGE
      );
    }
  }

  // =========================================================================
  // Scrape: build an EmpDetail from the form (P2-3 write path)
  // =========================================================================
  private EmpDetail scrapeForm() {
    EmpDetail e = new EmpDetail();
    if (mode == Mode.EDIT && current != null) {
      e.SetEmployeeId(current.GetEmployeeId()); // Update targets this row
    }
    e.SetLastName(lastNameField.getCleanValue());
    e.SetFirstName(firstNameField.getCleanValue());
    e.SetBirthday(parseDate(birthdayField.getCleanValue()));
    e.SetEmail(emailField.getCleanValue());
    e.SetPhoneNo(phoneField.getCleanValue());

    EmployeeAddressInfo addr = new EmployeeAddressInfo();
    addr.SetHouseBlkLotNo(houseField.getCleanValue());
    addr.SetStreet(streetField.getCleanValue());
    addr.SetBarangay(barangayField.getCleanValue());
    addr.SetCityMunicipality(cityField.getCleanValue());
    addr.SetProvince(provinceField.getCleanValue());
    addr.SetZipCode(zipField.getCleanValue());
    e.SetAddress(addr);

    e.SetStatutory(
      new StatutoryInfo(
        sssField.getCleanValue(),
        philHealthField.getCleanValue(),
        tinField.getCleanValue(),
        pagIbigField.getCleanValue()
      )
    );

    e.SetPosition((PositionInfo) positionCombo.getSelectedItem());
    e.SetDepartment((DepartmentInfo) departmentCombo.getSelectedItem());
    e.SetWorkSchedule((WorkScheduleInfo) scheduleCombo.getSelectedItem());
    e.SetEmpStatus((EmploymentStatus) statusCombo.getSelectedItem());
    e.SetDateHired(parseDate(dateHiredField.getCleanValue()));

    EmployeeSalaryInfo sal = new EmployeeSalaryInfo();
    sal.SetBasicSalary(parseMoney(basicSalaryField.getCleanValue()));
    sal.CalculateHourlyRate(); // hourly DERIVED from basic (21.75 days x 8 hrs)
    sal.SetEffectiveDate(LocalDate.now());
    e.SetCompensation(sal);

    e.SetAllowances(scrapeAllowances());
    return e;
  }

  private void recomputeHourly() {
    EmployeeSalaryInfo tmp = new EmployeeSalaryInfo();
    tmp.SetBasicSalary(parseMoney(basicSalaryField.getCleanValue()));
    tmp.CalculateHourlyRate();
    hourlyRateField.setText(
      tmp.GetBasicSalary() > 0 ? money(tmp.GetHourlyRate()) : ""
    );
  }

  private List<AllowanceInfo> scrapeAllowances() {
    List<AllowanceInfo> list = new ArrayList<>();
    addAllowance(list, "Rice Subsidy", riceField);
    addAllowance(list, "Phone Allowance", phoneAllowanceField);
    addAllowance(list, "Clothing Allowance", clothingField);
    return list;
  }

  private void addAllowance(
    List<AllowanceInfo> list,
    String name,
    SmartTextField field
  ) {
    AllowanceInfo a = new AllowanceInfo();
    a.SetAllowanceName(name); // TypeId stays 0 -> resolved by name in EmpMgmtProcess
    a.SetAmount(parseMoney(field.getCleanValue()));
    list.add(a);
  }

  private boolean allowancesPositive() {
    boolean ok = true;
    ok &= checkPositive(riceField);
    ok &= checkPositive(phoneAllowanceField);
    ok &= checkPositive(clothingField);
    return ok;
  }

  private boolean checkPositive(SmartTextField f) {
    boolean positive = parseMoney(f.getCleanValue()) > 0;
    f.displayError(!positive);
    return positive;
  }

  private static LocalDate parseDate(String s) {
    if (s == null || s.isBlank()) {
      return null;
    }
    try {
      return LocalDate.parse(s, DATE_FMT);
    } catch (Exception ex) {
      return null;
    }
  }

  private static double parseMoney(String cleaned) {
    if (cleaned == null || cleaned.isBlank()) {
      return 0;
    }
    try {
      return Double.parseDouble(cleaned);
    } catch (Exception ex) {
      return 0;
    }
  }

  // =========================================================================
  // Combo selection helpers (match by ID — populated list items and the
  // employee's value are different instances)
  // =========================================================================
  private void selectPosition(PositionInfo p) {
    if (p == null) {
      positionCombo.setSelectedIndex(-1);
      return;
    }
    for (int i = 0; i < positionCombo.getItemCount(); i++) {
      if (positionCombo.getItemAt(i).GetPositionID() == p.GetPositionID()) {
        positionCombo.setSelectedIndex(i);
        return;
      }
    }
    positionCombo.setSelectedIndex(-1);
  }

  private void selectDepartment(DepartmentInfo d) {
    if (d == null) {
      departmentCombo.setSelectedIndex(-1);
      return;
    }
    for (int i = 0; i < departmentCombo.getItemCount(); i++) {
      if (
        departmentCombo.getItemAt(i).GetDepartmentId() == d.GetDepartmentId()
      ) {
        departmentCombo.setSelectedIndex(i);
        return;
      }
    }
    departmentCombo.setSelectedIndex(-1);
  }

  private void selectSchedule(WorkScheduleInfo w) {
    if (w == null) {
      scheduleCombo.setSelectedIndex(-1);
      return;
    }
    for (int i = 0; i < scheduleCombo.getItemCount(); i++) {
      if (scheduleCombo.getItemAt(i).GetScheduleId() == w.GetScheduleId()) {
        scheduleCombo.setSelectedIndex(i);
        return;
      }
    }
    scheduleCombo.setSelectedIndex(-1);
  }

  // =========================================================================
  // Small helpers
  // =========================================================================
  private static String nz(String s) {
    return s == null ? "" : s;
  }

  // Feed the masked DATE field bare digits (MMddyyyy): its filter caps input at
  // 8 digits, so a pre-slashed "MM/dd/yyyy" (10 chars) gets rejected on setText.
  // The field re-adds the slashes via applyFormatting once the digits land.
  private static String fmtDate(LocalDate d) {
    return d != null ? d.format(DateTimeFormatter.ofPattern("MMddyyyy")) : "";
  }

  private static String money(double v) {
    // Plain number; the CURRENCY SmartTextField formats to "PHP #,##0.00".
    return String.valueOf(v);
  }

  private static String allowanceAmount(EmpDetail e, String name) {
    if (e.GetAllowances() == null) {
      return "";
    }
    for (AllowanceInfo a : e.GetAllowances()) {
      if (name.equalsIgnoreCase(a.GetAllowanceName())) {
        return String.valueOf(a.GetAmount());
      }
    }
    return "";
  }

  private static String statusLabel(EmploymentStatus s) {
    if (s == null) {
      return "";
    }
    return switch (s) {
      case PROBATIONARY -> "Probationary";
      case REGULAR -> "Regular";
      case TERMINATED -> "Terminated";
    };
  }

  private static <T> void setRenderer(
    JComboBox<T> combo,
    Function<T, String> fmt
  ) {
    combo.setRenderer(
      new DefaultListCellRenderer() {
        @Override
        @SuppressWarnings("unchecked")
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
          setText(value != null ? fmt.apply((T) value) : "");
          return this;
        }
      }
    );
  }

  private JButton brandButton(String text) {
    // Accept — primary confirm
    JButton b = baseButton(text);
    b.setBackground(BRAND_DARK);
    b.setForeground(Color.WHITE);
    return b;
  }

  private JButton dangerButton(String text) {
    // Delete — destructive
    JButton b = baseButton(text);
    b.setBackground(BRAND_RED);
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
    b.setBackground(new Color(0xE3E6EA)); // neutral flat chip
    b.setForeground(BRAND_DARK);
    b.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return b;
  }

  // =========================================================================
  // Table model — Emp #, Name, Position, Department, Status
  // =========================================================================
  private final class EmployeeTableModel extends AbstractTableModel {

    private final String[] cols = {
      "Emp #",
      "Name",
      "Position",
      "Department",
      "Status",
    };
    private List<EmpDetail> data = new ArrayList<>();

    void setData(List<EmpDetail> d) {
      this.data = (d != null) ? d : new ArrayList<>();
      fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
      return data.size();
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
      EmpDetail e = data.get(r);
      return switch (c) {
        case 0 -> e.GetEmployeeId();
        case 1 -> nz(e.GetFullName());
        case 2 -> e.GetPosition() != null
          ? nz(e.GetPosition().GetPositionName())
          : "\u2014";
        case 3 -> e.GetDepartment() != null
          ? nz(e.GetDepartment().GetDepartmentName())
          : "\u2014";
        case 4 -> statusLabel(e.GetEmpStatus());
        default -> null;
      };
    }
  }
}
