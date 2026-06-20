package Objects.models;

import Objects.enums.Status.DeductionCategory;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps to Employee_Deduction joined with Deduction_Type (04 - Leave & Compensation).
 *
 * Employee_Deduction columns:
 *   EmployeeDeductionID, EmployeeID, DeductionTypeID, Amount, Status
 *   UNIQUE (EmployeeID, DeductionTypeID)
 *
 * Parallel to AllowanceInfo — same pattern, different table.
 * Primarily used for voluntary/recurring per-employee deductions
 * (e.g. a fixed monthly contribution the employee chose).
 * Statutory amounts are computed dynamically in PayrollProcess.
 */
public class DeductionInfo extends BaseObject {

  private long EmployeeDeductionId;
  private long EmployeeId;
  private int DeductionTypeId;
  private String DeductionName;
  private DeductionCategory Category;
  private double Amount;

  public DeductionInfo() {}

  /**
   * Smart Constructor — requires JOIN with Deduction_Type.
   */
  public DeductionInfo(ResultSet rs) throws SQLException {
    this.EmployeeDeductionId = rs.getLong("EmployeeDeductionID");
    this.EmployeeId = rs.getLong("EmployeeID");
    this.DeductionTypeId = rs.getInt("DeductionTypeID");
    this.DeductionName = rs.getString("DeductionName");
    this.Category = DeductionCategory.fromInt(rs.getInt("Category"));
    this.Amount = rs.getDouble("Amount");
    this.SetActive(rs.getBoolean("Status"));
  }

  @Override
  public Object GetIdentity() {
    return GetEmployeeDeductionId();
  }

  public long GetEmployeeDeductionId() {
    return EmployeeDeductionId;
  }

  public void SetEmployeeDeductionId(long v) {
    this.EmployeeDeductionId = v;
  }

  public long GetEmployeeId() {
    return EmployeeId;
  }

  public void SetEmployeeId(long v) {
    this.EmployeeId = v;
  }

  public int GetDeductionTypeId() {
    return DeductionTypeId;
  }

  public void SetDeductionTypeId(int v) {
    this.DeductionTypeId = v;
  }

  public String GetDeductionName() {
    return DeductionName;
  }

  public void SetDeductionName(String v) {
    this.DeductionName = v;
  }

  public DeductionCategory GetCategory() {
    return Category;
  }

  public void SetCategory(DeductionCategory v) {
    this.Category = v;
  }

  public double GetAmount() {
    return Amount;
  }

  public void SetAmount(double v) {
    this.Amount = v;
  }
}
