package Objects.models.IAM;

import Objects.models.User;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Holds the authenticated user — and the identity of the backing User_Session
 * row — for the lifetime of the desktop session.
 *
 * Set once by LoginForm immediately after a successful authentication; read
 * everywhere the app needs the current identity or role. It is the bridge into
 * ShellFrame, which is constructed with only (AccessDAO, EmployeeDAO) and
 * therefore reads the logged-in user from here:
 *
 *   - GetRoleId()     -> AccessDAO.GetModulesForRole(...) builds the
 *                        role-scoped navigation tree from Role_Permission.
 *   - GetEmployeeId() -> EmployeeDAO.GetEmployeeNameById(...) for the greeting.
 *
 * This is a single-session desktop app, so a process-wide holder is sufficient;
 * there is no per-thread or per-window session.
 *
 * SESSION BACKING (single-session takeover):
 *   sessionId / sessionToken correspond to the live row in User_Session
 *   (03 - Security and Audit Tables). LoginProcess creates that row and calls
 *   Start(); the ShellFrame heartbeat (SessionMonitor) refreshes it every few
 *   seconds and, the moment it finds the row revoked by a login on another
 *   workstation, forces a logout. End() clears the holder on logout.
 *
 * RBAC PERMISSION CACHE (BKL-25):
 *   permissionMatrix holds the whole role x module x permission grant matrix
 *   for the logged-in role, keyed by ModuleCode. It is populated exactly once,
 *   by Injector.CreateShell() right after login (via AccessDAO.GetPermissionMatrix),
 *   and read by every gated module registration through GetPermissions(code) —
 *   replacing what used to be one AccessDAO query per panel mount. Session
 *   itself never touches AccessDAO: Model-layer code must not depend on
 *   DataAccess, so the DI layer resolves the matrix and pushes it in via
 *   SetPermissionMatrix(). Cleared on End() so a later login (a different role,
 *   or the same role re-granted) never reads a stale matrix.
 *
 * THREADING: every read/write happens on the Swing event-dispatch thread
 *   (login, heartbeat via javax.swing.Timer, and logout are all on the EDT),
 *   so no synchronization is required on these static fields.
 */
public final class Session {

  private static User currentUser;
  private static long sessionId;
  private static String sessionToken;
  private static Map<String, List<String>> permissionMatrix = Collections.emptyMap();

  private Session() {}

  /**
   * Establishes the session. Call exactly once, right after the backing
   * User_Session row has been created.
   *
   * @param user      the authenticated user
   * @param sessionId the generated User_Session.SessionID
   * @param token     the raw session token (its hash is what was persisted)
   */
  public static void Start(User user, long sessionId, String token) {
    Session.currentUser  = user;
    Session.sessionId    = sessionId;
    Session.sessionToken = token;
  }

  /** The authenticated user, or null if no one is logged in. */
  public static User GetCurrentUser() {
    return currentUser;
  }

  /** True when a user is currently logged in. */
  public static boolean IsActive() {
    return currentUser != null;
  }

  /** The backing User_Session.SessionID, or 0 when no session is active. */
  public static long GetSessionId() {
    return sessionId;
  }

  /** The raw session token, or null when no session is active. */
  public static String GetSessionToken() {
    return sessionToken;
  }

  // -------------------------------------------------------------------------
  // RBAC permission cache (BKL-25) — see class javadoc.
  // -------------------------------------------------------------------------

  /**
   * Caches the full role x module x permission grant matrix for this session.
   * Called once by Injector.CreateShell(), before any module view is built.
   * Not part of Start() itself: loading it requires AccessDAO, which the
   * Model layer (this class) must not depend on — the DI layer resolves it
   * and pushes the result in here.
   */
  public static void SetPermissionMatrix(Map<String, List<String>> matrix) {
    Session.permissionMatrix = (matrix != null) ? matrix : Collections.emptyMap();
  }

  /**
   * Granted permission codes for one module, read from the session-scoped
   * cache — no DB access. Empty list if the role holds nothing on that
   * module (mirrors AccessDAO.GetPermissionCodes' empty-on-miss behavior).
   */
  public static List<String> GetPermissions(String moduleCode) {
    return permissionMatrix.getOrDefault(moduleCode, Collections.emptyList());
  }

  // -------------------------------------------------------------------------
  // Convenience facades — read through the composed User (single source of
  // truth). Safe to call before login: they return neutral defaults.
  // -------------------------------------------------------------------------

  public static long GetUserId() {
    return currentUser != null ? currentUser.GetUserId() : 0L;
  }

  public static long GetEmployeeId() {
    return currentUser != null ? currentUser.GetEmployeeId() : 0L;
  }

  public static int GetRoleId() {
    return currentUser != null ? currentUser.GetRoleId() : 0;
  }

  public static String GetRoleCode() {
    return currentUser != null ? currentUser.GetRoleCode() : null;
  }

  public static String GetRoleName() {
    return currentUser != null ? currentUser.GetRoleName() : null;
  }

  public static String GetUsername() {
    return currentUser != null ? currentUser.GetUsername() : null;
  }

  /** Clears the session (logout). */
  public static void End() {
    currentUser  = null;
    sessionId    = 0L;
    sessionToken = null;
    permissionMatrix = Collections.emptyMap();
  }
}