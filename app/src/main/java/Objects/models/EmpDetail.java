package Objects.models;

import Objects.enums.Status.EmploymentStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Full employee aggregate — populated from vw_EmployeeCompleteDetails.
 *
 * View columns (06 - Views.sql):
 *   EmployeeID, LastName, FirstName, Birthday, Email, PhoneNo,
 *   EmploymentStatus, DateHired, Status, SupervisorID,
 *   PositionID, PositionName,
 *   DepartmentID, DepartmentCode, DepartmentName,
 *   ScheduleID, ScheduleName,
 *   SssNo, PhilHealthNo, TinNo, PagIbigNo,
 *   AddressID, HouseBlockLot, Street, Barangay, CityMunicipality, Province, ZipCode,
 *   SalaryID, BasicSalary, HourlyRate, EffectiveDate
 *
 * NOTE: RiceSubsidy / PhoneAllowance / ClothingAllowance are NO LONGER in this
 * view.  They are fetched separately via AllowanceDAO.GetByEmployeeID() and
 * stored in the Allowances list.
 */
public class EmpDetail extends EmployeeInfo {

  private String Email;
  private String PhoneNo;
  private StatutoryInfo Statutory;
  private PositionInfo Position;
  private DepartmentInfo Department;
  private WorkScheduleInfo WorkSchedule;
  private EmployeeInfo ImmSupervisor;
  private EmploymentStatus EmpStatus;
  private EmployeeSalaryInfo Compensation;
  private LocalDate DateHired;

  // Allowances are loaded on-demand by AllowanceDAO; null until hydrated.
  private java.util.List<AllowanceInfo> Allowances;

  public static final String[] DISPLAY_FIELDS = {
    "EmpNo",
    "LastName",
    "FirstName",
    "Position",
    "EmpStatus",
  };

  // -------------------------------------------------------------------------
  // Constructors
  // -------------------------------------------------------------------------

  public EmpDetail() {}

  /**
   * Full manual constructor — used by tests and the add-employee form flow.
   */
  public EmpDetail(
    long empNo,
    String lastName,
    String firstName,
    LocalDate birthday,
    EmployeeAddressInfo address,
    String email,
    String phoneNo,
    StatutoryInfo statutory,
    EmploymentStatus empStatus,
    PositionInfo position,
    DepartmentInfo department,
    WorkScheduleInfo workSchedule,
    EmployeeInfo immSupervisor,
    EmployeeSalaryInfo compensation,
    LocalDate dateHired
  ) {
    super(empNo, lastName, firstName, birthday, address);
    this.Email = email;
    this.PhoneNo = phoneNo;
    this.Statutory = statutory;
    this.EmpStatus = empStatus;
    this.Position = position;
    this.Department = department;
    this.WorkSchedule = workSchedule;
    this.ImmSupervisor = immSupervisor;
    this.Compensation = compensation;
    this.DateHired = dateHired;
  }

  /**
   * Smart Constructor (Database) — maps from vw_EmployeeCompleteDetails.
   *
   * Statutory is NOT mapped here because StatutoryDAO.FillStatutoryDetails()
   * handles it in a second pass (it was already wired this way).
   * Allowances are also NOT mapped here — use AllowanceDAO separately.
   */
  public EmpDetail(ResultSet rs) throws SQLException {
    super(rs);
    this.Email = rs.getString("Email");
    this.PhoneNo = rs.getString("PhoneNo");

    // Supervisor — stub with ID only; EmpMgmtProcess hydrates the name later
    long supervisorId = rs.getLong("SupervisorID");
    if (supervisorId > 0) {
      this.ImmSupervisor = new EmployeeInfo();
      this.ImmSupervisor.SetEmployeeId(supervisorId);
    }

    this.EmpStatus = EmploymentStatus.fromInt(rs.getInt("EmploymentStatus"));

    // Nested objects — each uses its own Smart Constructor
    this.Position = new PositionInfo(rs);
    this.Department = new DepartmentInfo(rs);

    // WorkSchedule stub: vw_EmployeeCompleteDetails only exposes ScheduleID +
    // ScheduleName, so we cannot use the full WorkScheduleInfo(rs) constructor
    // (it reads TimeStart/TimeEnd/Works*/Status which the view lacks). Build a
    // light stub here; hydrate the full schedule on demand via
    // WorkScheduleDAO.GetByID when payroll needs the times.
    int scheduleId = rs.getInt("ScheduleID");
    if (scheduleId > 0) {
      WorkScheduleInfo ws = new WorkScheduleInfo();
      ws.SetScheduleId(scheduleId);
      ws.SetScheduleName(rs.getString("ScheduleName"));
      this.WorkSchedule = ws;
    }

    java.sql.Date hiredDate = rs.getDate("DateHired");
    this.DateHired = (hiredDate != null) ? hiredDate.toLocalDate() : null;

    this.SetAddress(new EmployeeAddressInfo(rs));

    // EmployeeSalaryInfo now maps only BasicSalary + HourlyRate + EffectiveDate
    // (vw_CurrentSalary columns joined in the view).
    this.Compensation = new EmployeeSalaryInfo(rs);

    // Statutory IDs are exposed by vw_EmployeeCompleteDetails, so map them here
    // directly — no separate StatutoryDAO.FillStatutoryDetails round-trip.
    this.Statutory = new StatutoryInfo(rs);
  }

  // -------------------------------------------------------------------------
  // Getters & Setters
  // -------------------------------------------------------------------------

  public StatutoryInfo GetStatutory() {
    return Statutory;
  }

  public void SetStatutory(StatutoryInfo v) {
    this.Statutory = v;
  }

  public String GetEmail() {
    return Email;
  }

  public void SetEmail(String v) {
    this.Email = v;
  }

  public String GetPhoneNo() {
    return PhoneNo;
  }

  public void SetPhoneNo(String v) {
    this.PhoneNo = v;
  }

  public PositionInfo GetPosition() {
    return Position;
  }

  public void SetPosition(PositionInfo v) {
    this.Position = v;
  }

  public DepartmentInfo GetDepartment() {
    return Department;
  }

  public void SetDepartment(DepartmentInfo v) {
    this.Department = v;
  }

  public WorkScheduleInfo GetWorkSchedule() {
    return WorkSchedule;
  }

  public void SetWorkSchedule(WorkScheduleInfo v) {
    this.WorkSchedule = v;
  }

  public EmployeeInfo GetImmSupervisor() {
    return ImmSupervisor;
  }

  public void SetImmSupervisor(EmployeeInfo v) {
    this.ImmSupervisor = v;
  }

  public EmploymentStatus GetEmpStatus() {
    return EmpStatus;
  }

  public void SetEmpStatus(EmploymentStatus v) {
    this.EmpStatus = v;
  }

  public EmployeeSalaryInfo GetCompensation() {
    return Compensation;
  }

  public void SetCompensation(EmployeeSalaryInfo v) {
    this.Compensation = v;
  }

  public LocalDate GetDateHired() {
    return DateHired;
  }

  public void SetDateHired(LocalDate v) {
    this.DateHired = v;
  }

  public java.util.List<AllowanceInfo> GetAllowances() {
    return Allowances;
  }

  public void SetAllowances(java.util.List<AllowanceInfo> v) {
    this.Allowances = v;
  }

  // -------------------------------------------------------------------------
  // Display helper (used by EmpDetailTableModel)
  // -------------------------------------------------------------------------

  public Object GetDisplayFieldValue(int index) {
    return switch (index) {
      case 0 -> this.GetEmployeeId();
      case 1 -> this.GetLastName();
      case 2 -> this.GetFirstName();
      case 3 -> (Position != null) ? Position.GetPositionName() : "N/A";
      case 4 -> (EmpStatus != null) ? EmpStatus.toString() : "Unknown";
      default -> null;
    };
  }

  // -------------------------------------------------------------------------
  // Convenience helpers
  // -------------------------------------------------------------------------

  /**
   * Looks up an allowance amount by name from the loaded Allowances list.
   * Returns 0.0 if allowances are not yet loaded or the type is not found.
   * Example: GetAllowanceAmount("Rice Subsidy")
   */
  public double GetAllowanceAmount(String allowanceName) {
    if (Allowances == null) return 0.0;
    return Allowances.stream()
      .filter(
        a ->
          allowanceName.equalsIgnoreCase(a.GetAllowanceName()) && a.IsActive()
      )
      .mapToDouble(AllowanceInfo::GetAmount)
      .sum();
  }

  /** Convenience — total of all active allowances. */
  public double GetTotalAllowances() {
    if (Allowances == null) return 0.0;
    return Allowances.stream()
      .filter(AllowanceInfo::IsActive)
      .mapToDouble(AllowanceInfo::GetAmount)
      .sum();
  }
}
