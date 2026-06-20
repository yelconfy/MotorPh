package Objects.models;

import java.sql.ResultSet;
import java.sql.SQLException;

/** One Payroll_Allowance row joined with its Allowance_Type name. Read-only display DTO. */
public class PayslipAllowanceLine {

  private final long PayrollAllowanceId;
  private final long PayslipId;
  private final int AllowanceTypeId;
  private final String AllowanceName;
  private final double Amount;
  private final String Remarks;

  public PayslipAllowanceLine(ResultSet rs) throws SQLException {
    this.PayrollAllowanceId = rs.getLong("PayrollAllowanceID");
    this.PayslipId = rs.getLong("PayslipID");
    this.AllowanceTypeId = rs.getInt("AllowanceTypeID");
    this.AllowanceName = rs.getString("AllowanceName");
    this.Amount = rs.getDouble("Amount");
    this.Remarks = rs.getString("Remarks");
  }

  public long GetPayrollAllowanceId() { return PayrollAllowanceId; }
  public long GetPayslipId() { return PayslipId; }
  public int GetAllowanceTypeId() { return AllowanceTypeId; }
  public String GetAllowanceName() { return AllowanceName; }
  public double GetAmount() { return Amount; }
  public String GetRemarks() { return Remarks; }
}