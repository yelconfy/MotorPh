package Objects.models;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps to Allowance_Type (01 - Reference Tables).
 *
 * Columns: AllowanceTypeID, AllowanceName, IsTaxable, IsRecurring, Status
 *
 * Well-known names (seeded in 07 - ETL for Employees.sql):
 *   "Rice Subsidy", "Phone Allowance", "Clothing Allowance"
 *
 * Parallel to DeductionTypeInfo — the standalone REFERENCE type, distinct from
 * AllowanceInfo, which is the per-employee Employee_Allowance row (a JOIN onto
 * this table). AllowanceInfo already carried IsTaxable / IsRecurring as
 * read-through columns from the join; this class owns them as the editable
 * source. Introduced for the ALLOWANCETYPE maintenance module (BKL-01 stage 3b)
 * — before it, Allowance_Type had no model of its own (AllowanceDAO resolved
 * names straight to IDs via GetTypeIdsByName), which is why this is a new file
 * rather than a reused one.
 */
public class AllowanceTypeInfo extends BaseObject {

  private int AllowanceTypeId;
  private String AllowanceName;
  private boolean IsTaxable;
  private boolean IsRecurring;

  public AllowanceTypeInfo() {}

  public AllowanceTypeInfo(ResultSet rs) throws SQLException {
    this.AllowanceTypeId = rs.getInt("AllowanceTypeID");
    this.AllowanceName = rs.getString("AllowanceName");
    this.IsTaxable = rs.getBoolean("IsTaxable");
    this.IsRecurring = rs.getBoolean("IsRecurring");
    this.SetActive(rs.getBoolean("Status"));
  }

  @Override
  public Object GetIdentity() {
    return GetAllowanceTypeId();
  }

  @Override
  public String toString() {
    return AllowanceName != null ? AllowanceName : "";
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