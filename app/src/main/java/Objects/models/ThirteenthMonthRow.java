package Objects.models;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Read-only projection of vw_ThirteenthMonth (script 17 - Reporting layer).
 *
 *   vw_ThirteenthMonth: EmployeeNo, EmployeeFullName, Position, Department,
 *     PayYear, TotalBasicEarned, PayslipsIncluded, ThirteenthMonthPay
 *
 * One row per employee per calendar year — the body of the 13th Month Pay
 * report. Like PayrollSummaryRow / SystemActivity, this is a view row with no
 * primary key, so it is a plain display DTO rather than a BaseObject.
 *
 * Basis (PD 851): total BASIC salary EARNED in the year (sum of the frozen
 * Payslip.BasicPay snapshots for Finalized + Paid cutoffs) divided by 12.
 * The view divides by 12 regardless of months worked, so a mid-year hire is
 * prorated naturally by having earned less basic — not by changing the divisor.
 * TotalBasicEarned and PayslipsIncluded are exposed so the figure is auditable.
 */
public class ThirteenthMonthRow {

  private final long employeeNo;
  private final String employeeFullName;
  private final String position;
  private final String department;

  private final int payYear;
  private final double totalBasicEarned;
  private final int payslipsIncluded;
  private final double thirteenthMonthPay;

  /** Smart constructor — maps one vw_ThirteenthMonth row. */
  public ThirteenthMonthRow(ResultSet rs) throws SQLException {
    this.employeeNo = rs.getLong("EmployeeNo");
    this.employeeFullName = rs.getString("EmployeeFullName");
    this.position = rs.getString("Position");
    this.department = rs.getString("Department");

    this.payYear = rs.getInt("PayYear");
    this.totalBasicEarned = rs.getDouble("TotalBasicEarned");
    this.payslipsIncluded = rs.getInt("PayslipsIncluded");
    this.thirteenthMonthPay = rs.getDouble("ThirteenthMonthPay");
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

  public double GetTotalBasicEarned() {
    return totalBasicEarned;
  }

  public int GetPayslipsIncluded() {
    return payslipsIncluded;
  }

  public double GetThirteenthMonthPay() {
    return thirteenthMonthPay;
  }
}