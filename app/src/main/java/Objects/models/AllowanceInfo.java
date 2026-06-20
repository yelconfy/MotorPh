package Objects.models;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps to Employee_Allowance joined with Allowance_Type.
 *
 * SCHEMA (04 - Leave & Compensation Tables):
 *   Employee_Allowance: EmployeeAllowanceID, EmployeeID, AllowanceTypeID, Amount, Status
 *
 * SCHEMA (01 - Reference Tables):
 *   Allowance_Type: AllowanceTypeID, AllowanceName, IsTaxable, IsRecurring, Status
 *
 * Well-known AllowanceName values seeded by 09_Seed_Access_Control_RBAC.sql:
 *   "Rice Subsidy", "Phone Allowance", "Clothing Allowance"
 */
public class AllowanceInfo extends BaseObject {

  private long EmployeeAllowanceId;
  private long EmployeeId;
  private int AllowanceTypeId;
  private String AllowanceName;
  private double Amount;
  private boolean IsTaxable;
  private boolean IsRecurring;

  // -------------------------------------------------------------------------
  // Constructors
  // -------------------------------------------------------------------------

  public AllowanceInfo() {}

  /**
   * Smart Constructor — requires a JOIN between Employee_Allowance and Allowance_Type.
   * AllowanceDAO.GetByEmployeeID uses this constructor.
   */
  public AllowanceInfo(ResultSet rs) throws SQLException {
    this.EmployeeAllowanceId = rs.getLong("EmployeeAllowanceID");
    this.EmployeeId = rs.getLong("EmployeeID");
    this.AllowanceTypeId = rs.getInt("AllowanceTypeID");
    this.AllowanceName = rs.getString("AllowanceName");
    this.Amount = rs.getDouble("Amount");
    this.IsTaxable = rs.getBoolean("IsTaxable");
    this.IsRecurring = rs.getBoolean("IsRecurring");
    SetActive(rs.getBoolean("Status"));
  }

  @Override
  public Object GetIdentity() {
    return GetEmployeeAllowanceId();
  }

  // -------------------------------------------------------------------------
  // Getters & Setters
  // -------------------------------------------------------------------------

  public long GetEmployeeAllowanceId() {
    return EmployeeAllowanceId;
  }

  public void SetEmployeeAllowanceId(long v) {
    this.EmployeeAllowanceId = v;
  }

  public long GetEmployeeId() {
    return EmployeeId;
  }

  public void SetEmployeeId(long v) {
    this.EmployeeId = v;
  }

  public int GetAllowanceTypeId() {
    return AllowanceTypeId;
  }

  public void SetAllowanceTypeId(int v) {
    this.AllowanceTypeId = v;
  }

  public String GetAllowanceName() {
    return AllowanceName;
  }

  public void SetAllowanceName(String v) {
    this.AllowanceName = v;
  }

  public double GetAmount() {
    return Amount;
  }

  public void SetAmount(double v) {
    this.Amount = v;
  }

  public boolean IsTaxable() {
    return IsTaxable;
  }

  public void SetTaxable(boolean v) {
    this.IsTaxable = v;
  }

  public boolean IsRecurring() {
    return IsRecurring;
  }

  public void SetRecurring(boolean v) {
    this.IsRecurring = v;
  }
}
