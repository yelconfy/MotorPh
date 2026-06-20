package Objects.models;

import Objects.enums.Status.PayslipStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Maps to the Payslip table (05 - Payroll Tables).
 *
 * This is the DB-persisted payslip SNAPSHOT — distinct from EmpPaySlip,
 * which is an in-memory computation object used by PayrollProcess.
 *
 * Columns: PayslipID, EmployeeID, PayrollPeriodID, BasicPay, TotalAllowances,
 *          GrossPay, TotalDeductions, TotalAdjustments, NetPay, DaysWorked,
 *          HoursWorked, Status, GeneratedBy, GeneratedDate
 *
 * When loaded for display, the DAO should JOIN:
 *   - Employees (FirstName, LastName)
 *   - Payroll_Period (PeriodName, StartDate, EndDate)
 */
public class Payslip extends BaseObject {

  private long PayslipId;
  private long EmployeeId;
  private long PayrollPeriodId;

  // Snapshot totals
  private double BasicPay;
  private double TotalAllowances;
  private double GrossPay;
  private double TotalDeductions;
  private double TotalAdjustments;
  private double NetPay;
  private double DaysWorked;
  private double HoursWorked;

  private PayslipStatus Status;
  private Long GeneratedBy; // nullable FK → Users
  private LocalDateTime GeneratedDate;

  // From JOINs (optional — null if DAO doesn't join)
  private String EmployeeFirstName;
  private String EmployeeLastName;
  private String PeriodName;
  private LocalDate PeriodStart;
  private LocalDate PeriodEnd;

  public static final String[] DISPLAY_FIELDS = {
    "PayslipId",
    "EmployeeId",
    "EmployeeLastName",
    "EmployeeFirstName",
    "PeriodName",
    "GrossPay",
    "TotalDeductions",
    "NetPay",
    "Status",
  };

  public Payslip() {}

  public Payslip(ResultSet rs) throws SQLException {
    this.PayslipId = rs.getLong("PayslipID");
    this.EmployeeId = rs.getLong("EmployeeID");
    this.PayrollPeriodId = rs.getLong("PayrollPeriodID");
    this.BasicPay = rs.getDouble("BasicPay");
    this.TotalAllowances = rs.getDouble("TotalAllowances");
    this.GrossPay = rs.getDouble("GrossPay");
    this.TotalDeductions = rs.getDouble("TotalDeductions");
    this.TotalAdjustments = rs.getDouble("TotalAdjustments");
    this.NetPay = rs.getDouble("NetPay");
    this.DaysWorked = rs.getDouble("DaysWorked");
    this.HoursWorked = rs.getDouble("HoursWorked");
    this.Status = PayslipStatus.fromInt(rs.getInt("Status"));

    long generatedBy = rs.getLong("GeneratedBy");
    this.GeneratedBy = rs.wasNull() ? null : generatedBy;

    java.sql.Timestamp gd = rs.getTimestamp("GeneratedDate");
    this.GeneratedDate = (gd != null) ? gd.toLocalDateTime() : null;

    // Optional JOIN columns — safe to skip if not in result set
    try {
      this.EmployeeFirstName = rs.getString("FirstName");
    } catch (SQLException ignored) {}
    try {
      this.EmployeeLastName = rs.getString("LastName");
    } catch (SQLException ignored) {}
    try {
      this.PeriodName = rs.getString("PeriodName");
    } catch (SQLException ignored) {}
    try {
      java.sql.Date ps = rs.getDate("StartDate");
      this.PeriodStart = (ps != null) ? ps.toLocalDate() : null;
    } catch (SQLException ignored) {}
    try {
      java.sql.Date pe = rs.getDate("EndDate");
      this.PeriodEnd = (pe != null) ? pe.toLocalDate() : null;
    } catch (SQLException ignored) {}
  }

  @Override
  public Object GetIdentity() {
    return GetPayslipId();
  }

  /** True when this payslip is locked (Status >= Finalized). */
  public boolean IsLocked() {
    return (
      Status != null && Status.getValue() >= PayslipStatus.FINALIZED.getValue()
    );
  }

  /** Full name helper for display. */
  public String GetEmployeeFullName() {
    if (EmployeeFirstName == null && EmployeeLastName == null) return "";
    return (
      (EmployeeFirstName != null ? EmployeeFirstName : "") +
      " " +
      (EmployeeLastName != null ? EmployeeLastName : "")
    );
  }

  public long GetPayslipId() {
    return PayslipId;
  }

  public void SetPayslipId(long v) {
    this.PayslipId = v;
  }

  public long GetEmployeeId() {
    return EmployeeId;
  }

  public void SetEmployeeId(long v) {
    this.EmployeeId = v;
  }

  public long GetPayrollPeriodId() {
    return PayrollPeriodId;
  }

  public void SetPayrollPeriodId(long v) {
    this.PayrollPeriodId = v;
  }

  public double GetBasicPay() {
    return BasicPay;
  }

  public void SetBasicPay(double v) {
    this.BasicPay = v;
  }

  public double GetTotalAllowances() {
    return TotalAllowances;
  }

  public void SetTotalAllowances(double v) {
    this.TotalAllowances = v;
  }

  public double GetGrossPay() {
    return GrossPay;
  }

  public void SetGrossPay(double v) {
    this.GrossPay = v;
  }

  public double GetTotalDeductions() {
    return TotalDeductions;
  }

  public void SetTotalDeductions(double v) {
    this.TotalDeductions = v;
  }

  public double GetTotalAdjustments() {
    return TotalAdjustments;
  }

  public void SetTotalAdjustments(double v) {
    this.TotalAdjustments = v;
  }

  public double GetNetPay() {
    return NetPay;
  }

  public void SetNetPay(double v) {
    this.NetPay = v;
  }

  public double GetDaysWorked() {
    return DaysWorked;
  }

  public void SetDaysWorked(double v) {
    this.DaysWorked = v;
  }

  public double GetHoursWorked() {
    return HoursWorked;
  }

  public void SetHoursWorked(double v) {
    this.HoursWorked = v;
  }

  public PayslipStatus GetPayslipStatus() {
    return Status;
  }

  public void SetPayslipStatus(PayslipStatus v) {
    this.Status = v;
  }

  public Long GetGeneratedBy() {
    return GeneratedBy;
  }

  public void SetGeneratedBy(Long v) {
    this.GeneratedBy = v;
  }

  public LocalDateTime GetGeneratedDate() {
    return GeneratedDate;
  }

  public void SetGeneratedDate(LocalDateTime v) {
    this.GeneratedDate = v;
  }

  public String GetEmployeeFirstName() {
    return EmployeeFirstName;
  }

  public void SetEmployeeFirstName(String v) {
    this.EmployeeFirstName = v;
  }

  public String GetEmployeeLastName() {
    return EmployeeLastName;
  }

  public void SetEmployeeLastName(String v) {
    this.EmployeeLastName = v;
  }

  public String GetPeriodName() {
    return PeriodName;
  }

  public void SetPeriodName(String v) {
    this.PeriodName = v;
  }

  public LocalDate GetPeriodStart() {
    return PeriodStart;
  }

  public void SetPeriodStart(LocalDate v) {
    this.PeriodStart = v;
  }

  public LocalDate GetPeriodEnd() {
    return PeriodEnd;
  }

  public void SetPeriodEnd(LocalDate v) {
    this.PeriodEnd = v;
  }
}
