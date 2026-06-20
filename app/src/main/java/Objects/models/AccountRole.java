package Objects.models;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps to Account_Role (01 - Reference Tables).
 *   Account_Role: RoleID, RoleName, RoleCode, Status
 *
 * Mirrors PositionInfo / DepartmentInfo: a leaf reference entity that is
 * COMPOSED into its owning aggregate (User) rather than flattened onto it.
 * This brings User in line with the rest of the model, where every
 * table-backed reference is held as its own object.
 */
public class AccountRole extends BaseObject {

  private int RoleId;
  private String RoleName; // display label, e.g. "Payroll Officer"
  private String RoleCode; // stable app key, e.g. "PR" / "HR" / "TK"

  // -------------------------------------------------------------------------
  // Constructors
  // -------------------------------------------------------------------------

  public AccountRole() {}

  public AccountRole(int roleId, String roleName, String roleCode) {
    this.RoleId = roleId;
    this.RoleName = roleName;
    this.RoleCode = roleCode;
  }

  /**
   * Smart Constructor — maps from a ResultSet.
   *
   * Reads RoleID / RoleName / RoleCode only. The role's own Status column is
   * intentionally NOT read here: in the login JOIN (UserDAO.FindByUsername)
   * the "Status" column belongs to Users, not Account_Role, so reading it
   * would silently capture the wrong value. A dedicated RoleDAO that runs
   * SELECT * FROM Account_Role can call SetActive(rs.getBoolean("Status"))
   * itself after construction.
   */
  public AccountRole(ResultSet rs) throws SQLException {
    this.RoleId = rs.getInt("RoleID");
    this.RoleName = rs.getString("RoleName");
    this.RoleCode = rs.getString("RoleCode");
  }

  @Override
  public Object GetIdentity() {
    return GetRoleId();
  }

  // -------------------------------------------------------------------------
  // Getters & Setters
  // -------------------------------------------------------------------------

  public int GetRoleId() {
    return RoleId;
  }

  public void SetRoleId(int v) {
    this.RoleId = v;
  }

  public String GetRoleName() {
    return RoleName;
  }

  public void SetRoleName(String v) {
    this.RoleName = v;
  }

  public String GetRoleCode() {
    return RoleCode;
  }

  public void SetRoleCode(String v) {
    this.RoleCode = v;
  }

  @Override
  public String toString() {
    // Useful for JComboBoxes in a future Role-maintenance screen,
    // matching the PositionInfo / DepartmentInfo convention.
    return RoleName;
  }
}
