package Objects.models;

import Objects.enums.Status.DeductionCategory;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps to Deduction_Type (01 - Reference Tables).
 *
 * Columns: DeductionTypeID, DeductionName, Category (0=Statutory,1=Loan,2=Voluntary), Status
 *
 * Well-known names (seeded in 09_Seed_Access_Control_RBAC.sql):
 *   "SSS", "PhilHealth", "Pag-IBIG", "Withholding Tax" (all Statutory)
 */
public class DeductionTypeInfo extends BaseObject {

  private int DeductionTypeId;
  private String DeductionName;
  private DeductionCategory Category;

  public DeductionTypeInfo() {}

  public DeductionTypeInfo(ResultSet rs) throws SQLException {
    this.DeductionTypeId = rs.getInt("DeductionTypeID");
    this.DeductionName = rs.getString("DeductionName");
    this.Category = DeductionCategory.fromInt(rs.getInt("Category"));
    this.SetActive(rs.getBoolean("Status"));
  }

  @Override
  public Object GetIdentity() {
    return GetDeductionTypeId();
  }

  @Override
  public String toString() {
    return DeductionName != null ? DeductionName : "";
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
}
