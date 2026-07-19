package DataAccess;

import Objects.models.AccountRole;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for Account_Role (01 - Reference Tables) — role maintenance (ROLEMGMT).
 *
 * Distinct from AccessDAO, which is scoped to the Role_Permission GRANT
 * matrix (Role_Permission joined with Module/Permission) — this DAO owns
 * Account_Role's own CRUD (RoleName/RoleCode). Anticipated by AccountRole's
 * own javadoc: its Smart Constructor deliberately skips Status (the login
 * JOIN in UserDAO would otherwise ambiguously capture Users.Status instead
 * of Account_Role.Status), so this DAO calls SetActive(...) itself after
 * construction — the one thing a dedicated RoleDAO was always meant to own.
 */
public class RoleDAO {

  /** All active roles for the Role Maintenance grid. */
  public List<AccountRole> GetAll(Connection conn) throws SQLException {
    List<AccountRole> list = new ArrayList<>();
    String sql = "SELECT * FROM Account_Role WHERE Status = 1 ORDER BY RoleName";

    try (
      Statement stmt = conn.createStatement();
      ResultSet rs = stmt.executeQuery(sql)
    ) {
      while (rs.next()) {
        AccountRole role = new AccountRole(rs);
        role.SetActive(rs.getBoolean("Status"));
        list.add(role);
      }
    }
    return list;
  }

  /**
   * Insert — add a new role (Role Maintenance / Add). Status defaults to 1
   * (active) at the schema level. RoleCode is nullable at the schema level
   * (unlike RoleName) — written as SQL NULL when blank rather than an empty
   * string, matching the DB constraint exactly rather than imposing a
   * stricter rule than Account_Role itself requires.
   */
  public boolean Insert(Connection conn, AccountRole role) throws SQLException {
    String sql = "INSERT INTO Account_Role (RoleName, RoleCode) VALUES (?, ?)";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, role.GetRoleName());
      if (role.GetRoleCode() == null || role.GetRoleCode().isBlank()) {
        pstmt.setNull(2, Types.NVARCHAR);
      } else {
        pstmt.setString(2, role.GetRoleCode());
      }
      return pstmt.executeUpdate() > 0;
    }
  }

  /**
   * Update — rename / recode an existing role (Role Maintenance / Edit).
   * Same NULL handling on RoleCode as Insert.
   */
  public boolean Update(Connection conn, AccountRole role) throws SQLException {
    String sql = "UPDATE Account_Role SET RoleName = ?, RoleCode = ? WHERE RoleID = ?";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, role.GetRoleName());
      if (role.GetRoleCode() == null || role.GetRoleCode().isBlank()) {
        pstmt.setNull(2, Types.NVARCHAR);
      } else {
        pstmt.setString(2, role.GetRoleCode());
      }
      pstmt.setInt(3, role.GetRoleId());
      return pstmt.executeUpdate() > 0;
    }
  }

  /**
   * IsInUse — true if any user account currently holds this role
   * (Users.RoleID, a real NOT NULL FK). A soft delete here never trips a real
   * FK exception, so this is the explicit stand-in, same role as every other
   * soft-delete reference table's IsInUse tonight.
   */
  public boolean IsInUse(Connection conn, int roleId) throws SQLException {
    String sql = "SELECT 1 FROM Users WHERE RoleID = ?";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setInt(1, roleId);
      try (ResultSet rs = pstmt.executeQuery()) {
        return rs.next();
      }
    }
  }

  /**
   * Delete — soft delete (Account_Role has its own Status column). Caller
   * (RoleMaintenanceProcess) is responsible for checking IsInUse first —
   * this method does not guard itself.
   */
  public boolean Delete(Connection conn, int roleId) throws SQLException {
    String sql = "UPDATE Account_Role SET Status = 0 WHERE RoleID = ?";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setInt(1, roleId);
      return pstmt.executeUpdate() > 0;
    }
  }
}