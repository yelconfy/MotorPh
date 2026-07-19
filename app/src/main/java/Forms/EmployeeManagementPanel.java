package Forms;

import Core.Component.SmartTextField;
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
import Objects.results.SaveResult;
import UI.EmployeeFormLayout;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/**
 * Employee Management — master-detail CRUD controller for the EMPMGMT module.
 *
 * Layout/logic split (mirrors Forms.LoginForm + UI.LoginFormLayout): this
 * class owns state (Mode, current, rows), talks to IEmpMgmtProcess, and maps
 * EmpDetail <-> form fields. All Swing construction lives in
 * UI.EmployeeFormLayout, composed here as `ui` — this class never builds a
 * component, only reads/writes the ones `ui` exposes via getters.
 *
 * Flow (D-U1): select a row -> form populates (VIEW, read-only) -> Edit
 * enables the fields -> Accept validates and persists -> back to VIEW.
 * Add New blanks the form into ADD mode; Delete soft-deletes.
 *
 * Talks only to IEmpMgmtProcess (no raw DAOs) and validates via
 * FormControlService over the SmartField elements exposed by `ui`.
 *
 * BKL-35 B-rollout (step 2): AddEmployee/UpdateEmployee report through
 * SaveResult<Long>; the allowances-must-be-positive rule now lives in
 * EmpMgmtProcess, not here.
 */
public class EmployeeManagementPanel extends JPanel {

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
  private final EmployeeFormLayout ui;
  private final boolean canAdd;
  private final boolean canEdit;
  private final boolean canDelete;

  // ---- State -------------------------------------------------------------
  private List<EmpDetail> rows = new ArrayList<>();
  private EmpDetail current; // currently selected / loaded employee
  private Mode mode = Mode.VIEW;

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
    this.ui = new EmployeeFormLayout();

    setLayout(new BorderLayout());
    setBackground(Color.WHITE);
    add(ui.build(), BorderLayout.CENTER);

    loadDropdowns();
    wireListeners();
    reloadTable(null);
    setMode(Mode.VIEW); // also applies the initial RBAC visibility (canAdd/canEdit/canDelete)
  }

  // =========================================================================
  // Data load
  // =========================================================================
  private void loadDropdowns() {
    for (PositionInfo p : process.GetAllPositions()) {
      ui.getPositionCombo().addItem(p);
    }
    for (DepartmentInfo d : process.GetAllDepartments()) {
      ui.getDepartmentCombo().addItem(d);
    }
    for (WorkScheduleInfo s : process.GetAllSchedules()) {
      ui.getScheduleCombo().addItem(s);
    }
    // EmploymentStatus needs no process data — EmployeeFormLayout.build()
    // already populated statusCombo and wired both combo renderers.
  }

  private void reloadTable(List<EmpDetail> data) {
    reloadTable(data, -1L); // -1 -> default to the first row
  }

  // preferredId: select that employee's row after reload; falls back to row 0.
  private void reloadTable(List<EmpDetail> data, long preferredId) {
    rows = (data != null) ? data : process.GetEmpDetails();
    ui.getTableModel().setData(rows);
    ui.clearForm();
    current = null;
    setMode(Mode.VIEW);
    if (!rows.isEmpty()) {
      int idx = (preferredId > 0) ? indexOfEmployee(preferredId) : 0;
      if (idx < 0) idx = 0; // not found (e.g. filtered out) -> first row
      ui.getTable().setRowSelectionInterval(idx, idx);
    }
  }

  private int indexOfEmployee(long empId) {
    for (int i = 0; i < rows.size(); i++) {
      if (rows.get(i).GetEmployeeId() == empId) return i;
    }
    return -1;
  }

  private void applySearch() {
    String q = ui.getSearchField().getText().trim();
    reloadTable(q.isEmpty() ? null : process.SearchEmployee(q));
  }

  // =========================================================================
  // Selection -> load full record into the form (read-only)
  // =========================================================================
  private void onRowSelected() {
    if (mode != Mode.VIEW) {
      return; // ignore selection changes while editing/adding
    }
    int viewRow = ui.getTable().getSelectedRow();
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
      ui.clearForm();
      return;
    }
    ui.getEmpIdValue().setText(String.valueOf(e.GetEmployeeId()));
    ui.getLastNameField().setText(EmployeeFormLayout.nz(e.GetLastName()));
    ui.getFirstNameField().setText(EmployeeFormLayout.nz(e.GetFirstName()));
    ui.getBirthdayField().setText(EmployeeFormLayout.fmtDate(e.GetBirthday()));
    ui.getEmailField().setText(EmployeeFormLayout.nz(e.GetEmail()));
    ui.getPhoneField().setText(EmployeeFormLayout.nz(e.GetPhoneNo()));

    EmployeeAddressInfo a = e.GetAddress();
    ui.getHouseField().setText(a != null ? EmployeeFormLayout.nz(a.GetHouseBlkLotNo()) : "");
    ui.getStreetField().setText(a != null ? EmployeeFormLayout.nz(a.GetStreet()) : "");
    ui.getBarangayField().setText(a != null ? EmployeeFormLayout.nz(a.GetBarangay()) : "");
    ui.getCityField().setText(a != null ? EmployeeFormLayout.nz(a.GetCityMunicipality()) : "");
    ui.getProvinceField().setText(a != null ? EmployeeFormLayout.nz(a.GetProvince()) : "");
    ui.getZipField().setText(a != null ? EmployeeFormLayout.nz(a.GetZipCode()) : "");

    StatutoryInfo s = e.GetStatutory();
    ui.getSssField().setText(s != null ? EmployeeFormLayout.nz(s.GetSssNo()) : "");
    ui.getPhilHealthField().setText(s != null ? EmployeeFormLayout.nz(s.GetPhilHealthNo()) : "");
    ui.getTinField().setText(s != null ? EmployeeFormLayout.nz(s.GetTinNo()) : "");
    ui.getPagIbigField().setText(s != null ? EmployeeFormLayout.nz(s.GetPagIbigNo()) : "");

    selectComboItem(ui.getPositionCombo(), e.GetPosition(), PositionInfo::GetPositionID);
    selectComboItem(ui.getDepartmentCombo(), e.GetDepartment(), DepartmentInfo::GetDepartmentId);
    selectComboItem(ui.getScheduleCombo(), e.GetWorkSchedule(), WorkScheduleInfo::GetScheduleId);
    ui.getStatusCombo().setSelectedItem(e.GetEmpStatus());
    ui.getDateHiredField().setText(EmployeeFormLayout.fmtDate(e.GetDateHired()));

    EmployeeSalaryInfo c = e.GetCompensation();
    ui.getBasicSalaryField().setText(c != null ? EmployeeFormLayout.money(c.GetBasicSalary()) : "");
    recomputeHourly(); // hourly is derived from basic — keep display == formula

    ui.getRiceField().setText(allowanceAmount(e, "Rice Subsidy"));
    ui.getPhoneAllowanceField().setText(allowanceAmount(e, "Phone Allowance"));
    ui.getClothingField().setText(allowanceAmount(e, "Clothing Allowance"));
  }

  // =========================================================================
  // State machine
  // =========================================================================
  private void setMode(Mode m) {
    this.mode = m;
    boolean editing = (m == Mode.ADD || m == Mode.EDIT);

    ui.setInputsEnabled(editing);
    if (!editing) {
      ui.clearFieldErrors(); // VIEW is read-only — never show validation highlights
    }

    // Toolbar swaps: VIEW actions while viewing, edit actions while editing.
    ui.getAddBtn().setVisible(canAdd && !editing);
    ui.getEditBtn().setVisible(canEdit && !editing);
    ui.getDeleteBtn().setVisible(canDelete && !editing);
    ui.getAcceptBtn().setVisible(editing);
    ui.getCancelBtn().setVisible(editing);

    ui.getAddBtn().setEnabled(canAdd && m == Mode.VIEW);
    ui.getEditBtn().setEnabled(canEdit && m == Mode.VIEW && current != null);
    ui.getDeleteBtn().setEnabled(canDelete && m == Mode.VIEW && current != null);
    ui.getAcceptBtn().setEnabled(editing);
    ui.getCancelBtn().setEnabled(editing);

    // Lock the table while editing so selection can't swap the record mid-edit.
    ui.getTable().setEnabled(m == Mode.VIEW);
  }

  // =========================================================================
  // Listeners + actions
  // =========================================================================
  private void wireListeners() {
    ui
      .getTable()
      .getSelectionModel()
      .addListSelectionListener(e -> {
        if (!e.getValueIsAdjusting()) {
          onRowSelected();
        }
      });

    ui.getBasicSalaryField().addFocusListener(
      new FocusAdapter() {
        @Override
        public void focusLost(FocusEvent ev) {
          recomputeHourly();
        }
      }
    );

    ui.getAddBtn().addActionListener(e -> onAdd());
    ui.getEditBtn().addActionListener(e -> onEdit());
    ui.getDeleteBtn().addActionListener(e -> onDelete());
    ui.getCancelBtn().addActionListener(e -> onCancel());
    ui.getAcceptBtn().addActionListener(e -> onAccept());

    ui.getSearchBtn().addActionListener(e -> applySearch());
    ui.getRefreshBtn().addActionListener(e -> {
      ui.getSearchField().setText("");
      reloadTable(null);
    });
    ui.getSearchField().addActionListener(e -> applySearch());
  }

  private void onAdd() {
    ui.getTable().clearSelection();
    current = null;
    ui.clearForm();
    setMode(Mode.ADD);
    ui.getStatusCombo().setSelectedItem(EmploymentStatus.PROBATIONARY); // new hires start probationary
    ui.getLastNameField().requestFocusInWindow();
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
      ui.clearForm();
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
    // 2. Build the aggregate and persist (Add or Update by mode). The
    //    allowances-must-be-positive rule lives in EmpMgmtProcess (BKL-35
    //    B-rollout) and comes back as VALIDATION_FAILED.
    EmpDetail e = scrapeForm();
    SaveResult<Long> result = (mode == Mode.ADD)
      ? process.AddEmployee(e)
      : process.UpdateEmployee(e);

    switch (result.GetOutcome()) {
      case SUCCESS -> {
        JOptionPane.showMessageDialog(
          this,
          mode == Mode.ADD ? "Employee added." : "Employee updated.",
          "Success",
          JOptionPane.INFORMATION_MESSAGE
        );
        reloadTable(null, result.GetPayload()); // land on the just-saved record (Add or Edit)
      }
      case VALIDATION_FAILED -> JOptionPane.showMessageDialog(
        this,
        result.GetMessage(),
        "Validation",
        JOptionPane.ERROR_MESSAGE
      );
      default -> JOptionPane.showMessageDialog(
        this,
        result.GetMessage() != null ? result.GetMessage() : "Save failed. Please check the data and try again.",
        "Error",
        JOptionPane.ERROR_MESSAGE
      );
    }
  }

  // =========================================================================
  // Scrape: build an EmpDetail from the form (write path)
  // =========================================================================
  private EmpDetail scrapeForm() {
    EmpDetail e = new EmpDetail();
    if (mode == Mode.EDIT && current != null) {
      e.SetEmployeeId(current.GetEmployeeId()); // Update targets this row
    }
    e.SetLastName(ui.getLastNameField().getCleanValue());
    e.SetFirstName(ui.getFirstNameField().getCleanValue());
    e.SetBirthday(parseDate(ui.getBirthdayField().getCleanValue()));
    e.SetEmail(ui.getEmailField().getCleanValue());
    e.SetPhoneNo(ui.getPhoneField().getCleanValue());

    EmployeeAddressInfo addr = new EmployeeAddressInfo();
    addr.SetHouseBlkLotNo(ui.getHouseField().getCleanValue());
    addr.SetStreet(ui.getStreetField().getCleanValue());
    addr.SetBarangay(ui.getBarangayField().getCleanValue());
    addr.SetCityMunicipality(ui.getCityField().getCleanValue());
    addr.SetProvince(ui.getProvinceField().getCleanValue());
    addr.SetZipCode(ui.getZipField().getCleanValue());
    e.SetAddress(addr);

    e.SetStatutory(
      new StatutoryInfo(
        ui.getSssField().getCleanValue(),
        ui.getPhilHealthField().getCleanValue(),
        ui.getTinField().getCleanValue(),
        ui.getPagIbigField().getCleanValue()
      )
    );

    e.SetPosition((PositionInfo) ui.getPositionCombo().getSelectedItem());
    e.SetDepartment((DepartmentInfo) ui.getDepartmentCombo().getSelectedItem());
    e.SetWorkSchedule((WorkScheduleInfo) ui.getScheduleCombo().getSelectedItem());
    e.SetEmpStatus((EmploymentStatus) ui.getStatusCombo().getSelectedItem());
    e.SetDateHired(parseDate(ui.getDateHiredField().getCleanValue()));

    EmployeeSalaryInfo sal = new EmployeeSalaryInfo();
    sal.SetBasicSalary(parseMoney(ui.getBasicSalaryField().getCleanValue()));
    sal.CalculateHourlyRate(); // hourly DERIVED from basic (21.75 days x 8 hrs)
    sal.SetEffectiveDate(LocalDate.now());
    e.SetCompensation(sal);

    e.SetAllowances(scrapeAllowances());
    return e;
  }

  private void recomputeHourly() {
    EmployeeSalaryInfo tmp = new EmployeeSalaryInfo();
    tmp.SetBasicSalary(parseMoney(ui.getBasicSalaryField().getCleanValue()));
    tmp.CalculateHourlyRate();
    ui.getHourlyRateField().setText(
      tmp.GetBasicSalary() > 0 ? EmployeeFormLayout.money(tmp.GetHourlyRate()) : ""
    );
  }

  private List<AllowanceInfo> scrapeAllowances() {
    List<AllowanceInfo> list = new ArrayList<>();
    addAllowance(list, "Rice Subsidy", ui.getRiceField());
    addAllowance(list, "Phone Allowance", ui.getPhoneAllowanceField());
    addAllowance(list, "Clothing Allowance", ui.getClothingField());
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
  // Combo selection (BKL-3x: collapsed from three near-identical
  // selectPosition/selectDepartment/selectSchedule copies into one generic
  // helper — same "find by ID, select or -1" logic, parameterized by the ID
  // accessor instead of duplicated per type).
  // =========================================================================
  private static <T> void selectComboItem(
    JComboBox<T> combo,
    T target,
    Function<T, Object> idOf
  ) {
    if (target == null) {
      combo.setSelectedIndex(-1);
      return;
    }
    Object targetId = idOf.apply(target);
    for (int i = 0; i < combo.getItemCount(); i++) {
      if (targetId.equals(idOf.apply(combo.getItemAt(i)))) {
        combo.setSelectedIndex(i);
        return;
      }
    }
    combo.setSelectedIndex(-1);
  }
}