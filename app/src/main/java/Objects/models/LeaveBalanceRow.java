package Objects.models;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Read-only projection of vw_LeaveBalanceReport (script 17 - Reporting layer).
 * One row per employee per leave type per year: entitled / used / remaining.
 * Balance math lives in vw_LeaveBalance (script 06); this is display only.
 */
public class LeaveBalanceRow {

  private final long employeeNo;
  private final String employeeFullName;
  private final String department;
  private final String leaveType;
  private final int payYear;
  private final double entitledDays;
  private final double usedDays;
  private final double remainingDays;

  public LeaveBalanceRow(ResultSet rs) throws SQLException {
    this.employeeNo = rs.getLong("EmployeeNo");
    this.employeeFullName = rs.getString("EmployeeFullName");
    this.department = rs.getString("Department");
    this.leaveType = rs.getString("LeaveType");
    this.payYear = rs.getInt("PayYear");
    this.entitledDays = rs.getDouble("EntitledDays");
    this.usedDays = rs.getDouble("UsedDays");
    this.remainingDays = rs.getDouble("RemainingDays");
  }

  public long GetEmployeeNo() { return employeeNo; }
  public String GetEmployeeFullName() { return employeeFullName; }
  public String GetDepartment() { return department; }
  public String GetLeaveType() { return leaveType; }
  public int GetPayYear() { return payYear; }
  public double GetEntitledDays() { return entitledDays; }
  public double GetUsedDays() { return usedDays; }
  public double GetRemainingDays() { return remainingDays; }
}