package Objects.models;

import java.sql.ResultSet;
import java.sql.SQLException;

/** One Payroll_Deduction row joined with its Deduction_Type name. Read-only display DTO. */
public class PayslipDeductionLine {

  private final long PayrollDeductionId;
  private final long PayslipId;
  private final int DeductionTypeId;
  private final String DeductionName;
  private final int SourceType; // 0=Manual,1=Statutory,2=Loan,3=Voluntary
  private final double Amount;
  private final String Remarks;

  public PayslipDeductionLine(ResultSet rs) throws SQLException {
    this.PayrollDeductionId = rs.getLong("PayrollDeductionID");
    this.PayslipId = rs.getLong("PayslipID");
    this.DeductionTypeId = rs.getInt("DeductionTypeID");
    this.DeductionName = rs.getString("DeductionName");
    this.SourceType = rs.getInt("SourceType");
    this.Amount = rs.getDouble("Amount");
    this.Remarks = rs.getString("Remarks");
  }

  public long GetPayrollDeductionId() { return PayrollDeductionId; }
  public long GetPayslipId() { return PayslipId; }
  public int GetDeductionTypeId() { return DeductionTypeId; }
  public String GetDeductionName() { return DeductionName; }
  public int GetSourceType() { return SourceType; }
  public double GetAmount() { return Amount; }
  public String GetRemarks() { return Remarks; }
}