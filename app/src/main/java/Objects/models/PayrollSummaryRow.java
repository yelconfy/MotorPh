package Objects.models;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

/**
 * Read-only projection of vw_MonthlyPayrollSummary (script 17 - Reporting layer).
 *
 *   vw_MonthlyPayrollSummary: EmployeeNo, EmployeeFullName, Position, Department,
 *     PayYear, PayMonth, PayMonthName, PeriodStart, PeriodEnd, PayslipsIncluded,
 *     SocialSecurityNo, PhilHealthNo, PagIbigNo, TIN,
 *     GrossIncome, SocialSecurityContribution, PhilHealthContribution,
 *     PagIbigContribution, WithholdingTax, NetPay
 *
 * One row per employee per calendar month — the body of the Monthly Payroll
 * Summary Report. Like SystemActivity, this is a view row with no primary key,
 * so it is a plain display DTO rather than a BaseObject.
 */
public class PayrollSummaryRow {

  private final long employeeNo;
  private final String employeeFullName;
  private final String position;
  private final String department;

  private final int payYear;
  private final int payMonth;
  private final String payMonthName;
  private final LocalDate periodStart;
  private final LocalDate periodEnd;
  private final int payslipsIncluded;

  private final String socialSecurityNo;
  private final String philHealthNo;
  private final String pagIbigNo;
  private final String tin;

  private final double grossIncome;
  private final double socialSecurityContribution;
  private final double philHealthContribution;
  private final double pagIbigContribution;
  private final double withholdingTax;
  private final double netPay;

  /** Smart constructor — maps one vw_MonthlyPayrollSummary row. */
  public PayrollSummaryRow(ResultSet rs) throws SQLException {
    this.employeeNo = rs.getLong("EmployeeNo");
    this.employeeFullName = rs.getString("EmployeeFullName");
    this.position = rs.getString("Position");
    this.department = rs.getString("Department");

    this.payYear = rs.getInt("PayYear");
    this.payMonth = rs.getInt("PayMonth");
    this.payMonthName = rs.getString("PayMonthName");

    java.sql.Date ps = rs.getDate("PeriodStart");
    this.periodStart = (ps != null) ? ps.toLocalDate() : null;
    java.sql.Date pe = rs.getDate("PeriodEnd");
    this.periodEnd = (pe != null) ? pe.toLocalDate() : null;

    this.payslipsIncluded = rs.getInt("PayslipsIncluded");

    this.socialSecurityNo = rs.getString("SocialSecurityNo");
    this.philHealthNo = rs.getString("PhilHealthNo");
    this.pagIbigNo = rs.getString("PagIbigNo");
    this.tin = rs.getString("TIN");

    this.grossIncome = rs.getDouble("GrossIncome");
    this.socialSecurityContribution = rs.getDouble("SocialSecurityContribution");
    this.philHealthContribution = rs.getDouble("PhilHealthContribution");
    this.pagIbigContribution = rs.getDouble("PagIbigContribution");
    this.withholdingTax = rs.getDouble("WithholdingTax");
    this.netPay = rs.getDouble("NetPay");
  }

  public long GetEmployeeNo() {
    return employeeNo;
  }

  public String GetEmployeeFullName() {
    return employeeFullName;
  }

  public String GetPosition() {
    return position;
  }

  public String GetDepartment() {
    return department;
  }

  public int GetPayYear() {
    return payYear;
  }

  public int GetPayMonth() {
    return payMonth;
  }

  public String GetPayMonthName() {
    return payMonthName;
  }

  public LocalDate GetPeriodStart() {
    return periodStart;
  }

  public LocalDate GetPeriodEnd() {
    return periodEnd;
  }

  public int GetPayslipsIncluded() {
    return payslipsIncluded;
  }

  public String GetSocialSecurityNo() {
    return socialSecurityNo;
  }

  public String GetPhilHealthNo() {
    return philHealthNo;
  }

  public String GetPagIbigNo() {
    return pagIbigNo;
  }

  public String GetTin() {
    return tin;
  }

  public double GetGrossIncome() {
    return grossIncome;
  }

  public double GetSocialSecurityContribution() {
    return socialSecurityContribution;
  }

  public double GetPhilHealthContribution() {
    return philHealthContribution;
  }

  public double GetPagIbigContribution() {
    return pagIbigContribution;
  }

  public double GetWithholdingTax() {
    return withholdingTax;
  }

  public double GetNetPay() {
    return netPay;
  }
}