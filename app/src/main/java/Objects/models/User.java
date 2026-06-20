package Objects.models;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * Maps to Users (03 - Security and Audit Tables), JOINed with Account_Role.
 *
 * Schema columns used:
 *   Users: UserID, EmployeeID, Username, PasswordHash, RoleID,
 *          Status, FailedLoginCount, LockoutUntil, MustChangePassword
 *   Account_Role (JOIN): RoleCode, RoleName
 *
 * CHANGE: the role is now COMPOSED as an AccountRole object instead of being
 * flattened into roleId / roleCode / roleName primitives. This matches the
 * rest of the model (EmpDetail holds PositionInfo, DepartmentInfo, etc.).
 *
 * GetRoleId() / GetRoleCode() / GetRoleName() are kept as convenience facades
 * that read from the composed Role, so existing call sites keep compiling.
 * They delegate to a single source of truth (the AccountRole object) — there
 * is no duplicate state. New code should prefer GetRole().GetRoleCode().
 */
public class User extends BaseObject {

  private long userId;
  private long employeeId;
  private String username;
  private String passwordHash;
  private AccountRole Role; // composed reference (was roleId/roleCode/roleName)
  private int failedLoginCount;
  private LocalDateTime lockoutUntil; // null = not locked
  private boolean mustChangePassword;

  public User() {}

  public User(ResultSet rs) throws SQLException {
    this.userId = rs.getLong("UserID");
    this.employeeId = rs.getLong("EmployeeID");
    this.username = rs.getString("Username");
    this.passwordHash = rs.getString("PasswordHash");

    // Nested object — uses its own Smart Constructor (RoleID/RoleName/RoleCode).
    this.Role = new AccountRole(rs);

    this.failedLoginCount = rs.getInt("FailedLoginCount");
    this.mustChangePassword = rs.getBoolean("MustChangePassword");
    SetActive(rs.getBoolean("Status"));

    java.sql.Timestamp lockout = rs.getTimestamp("LockoutUntil");
    this.lockoutUntil = (lockout != null) ? lockout.toLocalDateTime() : null;
  }

  @Override
  public Object GetIdentity() {
    return GetUserId();
  }

  /** True if the account is currently within a lockout window. */
  public boolean IsLockedOut() {
    return lockoutUntil != null && lockoutUntil.isAfter(LocalDateTime.now());
  }

  // -------------------------------------------------------------------------
  // Getters & Setters
  // -------------------------------------------------------------------------

  public long GetUserId() {
    return userId;
  }

  public void SetUserId(long v) {
    this.userId = v;
  }

  public long GetEmployeeId() {
    return employeeId;
  }

  public void SetEmployeeId(long v) {
    this.employeeId = v;
  }

  public String GetUsername() {
    return username;
  }

  public void SetUsername(String v) {
    this.username = v;
  }

  public String GetPasswordHash() {
    return passwordHash;
  }

  public void SetPasswordHash(String v) {
    this.passwordHash = v;
  }

  // Canonical accessor for the composed role.
  public AccountRole GetRole() {
    return Role;
  }

  public void SetRole(AccountRole v) {
    this.Role = v;
  }

  // Convenience facades — read through the composed Role (single source of truth).
  public int GetRoleId() {
    return Role != null ? Role.GetRoleId() : 0;
  }

  public String GetRoleCode() {
    return Role != null ? Role.GetRoleCode() : null;
  }

  public String GetRoleName() {
    return Role != null ? Role.GetRoleName() : null;
  }

  public int GetFailedLoginCount() {
    return failedLoginCount;
  }

  public void SetFailedLoginCount(int v) {
    this.failedLoginCount = v;
  }

  public LocalDateTime GetLockoutUntil() {
    return lockoutUntil;
  }

  public void SetLockoutUntil(LocalDateTime v) {
    this.lockoutUntil = v;
  }

  public boolean IsMustChangePassword() {
    return mustChangePassword;
  }

  public void SetMustChangePassword(boolean v) {
    this.mustChangePassword = v;
  }
}
