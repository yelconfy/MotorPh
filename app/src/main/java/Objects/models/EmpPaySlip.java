package Objects.models;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

public class EmpPaySlip {

  private EmployeeInfo Employee;
  private double GrossPay;
  private double NetPay;
  private EmpDeductions Deductions;
  private LocalDate PeriodStart;
  private LocalDate PeriodEnd;
  private double BasicPay;
  private double TotalAllowances;

  // <editor-fold defaultstate="collapsed" desc="Constructors">

  // Default Constructor for manual building
  public EmpPaySlip() {}

  // Manual Constructor updated to use Composition
  public EmpPaySlip(
    EmployeeInfo employee,
    double grossPay,
    double netPay,
    EmpDeductions deductions,
    LocalDate periodStart,
    LocalDate periodEnd
  ) {
    this.Employee = employee;
    this.GrossPay = grossPay;
    this.NetPay = netPay;
    this.Deductions = deductions;
    this.PeriodStart = periodStart;
    this.PeriodEnd = periodEnd;
  }

  /**
   * Smart Constructor (Database)
   * This allows you to build a payslip directly from a joined SQL row.
   */
  public EmpPaySlip(ResultSet rs) throws SQLException {
    // 1. Initialize the Employee Identity part
    this.Employee = new EmployeeInfo(rs);

    // 2. Map PaySlip specific fields
    this.GrossPay = rs.getDouble("GrossPay");
    this.NetPay = rs.getDouble("NetPay");

    java.sql.Date start = rs.getDate("PeriodStart");
    this.PeriodStart = (start != null) ? start.toLocalDate() : null;

    java.sql.Date end = rs.getDate("PeriodEnd");
    this.PeriodEnd = (end != null) ? end.toLocalDate() : null;

    // 3. Attach Deductions (using its own Smart Constructor)
    this.Deductions = new EmpDeductions(rs);
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="Getters and Setters">
  public EmployeeInfo GetEmployee() {
    return Employee;
  }

  public void SetEmployee(EmployeeInfo employee) {
    this.Employee = employee;
  }

  public double GetGrossPay() {
    return GrossPay;
  }

  public void SetGrossPay(double grossPay) {
    this.GrossPay = grossPay;
  }

  public double GetNetPay() {
    return NetPay;
  }

  public void SetNetPay(double netPay) {
    this.NetPay = netPay;
  }

  public EmpDeductions GetDeductions() {
    return Deductions;
  }

  public void SetDeductions(EmpDeductions deductions) {
    this.Deductions = deductions;
  }

  public LocalDate GetPeriodStart() {
    return PeriodStart;
  }

  public void SetPeriodStart(LocalDate periodStart) {
    this.PeriodStart = periodStart;
  }

  public LocalDate GetPeriodEnd() {
    return PeriodEnd;
  }

  public void SetPeriodEnd(LocalDate periodEnd) {
    this.PeriodEnd = periodEnd;
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
  // </editor-fold>
}
