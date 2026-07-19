package Processes;

import Core.Service.PasswordService;
import DataAccess.DatabaseConnector;
import DataAccess.SessionDAO;
import DataAccess.UserDAO;
import Interface.ILoginProcess;
import Objects.models.IAM.SessionContext;
import Objects.models.LoginResult;
import Objects.models.User;

import java.net.InetAddress;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Orchestrates the full IAM login flow:
 *   1. Look up user by username (JOIN gives us RoleCode/RoleName).
 *   2. Reject if account is inactive.
 *   3. Reject if account is currently locked out.
 *   4. Detect ETL placeholder hash -> force password change on first login.
 *   5. Verify BCrypt hash; track failed attempts and apply lockout policy.
 *   6. Reset failed-attempt counter on success.
 *   7. Log every attempt to User_Access_Log.
 *   8. Signal MustChangePassword when the flag is set in the DB.
 *   9. Transparently migrate an out-of-policy BCrypt cost factor (MPH-46).
 *
 * Session establishment is a SEPARATE step (EstablishSession), invoked by the
 * UI at the point the user actually enters the shell. This keeps the
 * must-change-password path (which returns before the shell opens) from
 * creating a premature session, while ensuring both entry paths funnel through
 * one place.
 *
 * THREADING: PerformLogin is now called from a SwingWorker background thread
 * (MPH-46), not the EDT. That is only safe because DatabaseConnector pools
 * connections (MPH-43) and hands each thread its own; under the old shared
 * single Connection this would have raced the UI.
 */
public class LoginProcess implements ILoginProcess {

    private final UserDAO userDAO;
    private final SessionDAO sessionDAO;

    public LoginProcess(UserDAO userDAO, SessionDAO sessionDAO) {
        this.userDAO = userDAO;
        this.sessionDAO = sessionDAO;
    }

    @Override
    public LoginResult PerformLogin(String username, String password) {

        User user = userDAO.FindByUsername(username);

        // Unknown user or inactive account — return generic message (no user enumeration)
        if (user == null || !user.IsActive()) {
            userDAO.LogAccess(username, false, "Invalid credentials");
            return LoginResult.Invalid();
        }

        // Locked out — tell the user to wait, do not check password
        if (user.IsLockedOut()) {
            userDAO.LogAccess(username, false, "Account locked");
            return LoginResult.Locked();
        }

        // First-login placeholder: accept any input and force a password reset
        if (PasswordService.IsPlaceholder(user.GetPasswordHash())) {
            userDAO.ResetFailedLogin(user.GetUserId());
            userDAO.LogAccess(username, true, null);
            return LoginResult.MustChange(user);
        }

        // Normal BCrypt verification
        if (!PasswordService.Verify(password, user.GetPasswordHash())) {
            userDAO.IncrementFailedLogin(user.GetUserId());
            userDAO.LogAccess(username, false, "Wrong password");
            return LoginResult.Invalid();
        }

        // Successful authentication
        userDAO.ResetFailedLogin(user.GetUserId());
        userDAO.LogAccess(username, true, null);

        // DB flag set by an admin (e.g. after a password reset)
        if (user.IsMustChangePassword()) {
            return LoginResult.MustChange(user);
        }

        // MPH-46: cost-factor migration. MUST come after the MustChangePassword
        // check above — UserDAO.UpdatePasswordHash also sets MustChangePassword = 0,
        // so re-hashing before that check would silently clear an admin-forced
        // password reset and the user would never be prompted.
        MigrateHashIfStale(user, password);

        return LoginResult.Success(user);
    }

    /**
     * Re-hashes a verified password when its stored cost factor no longer matches
     * policy (PasswordService.WORK_FACTOR). Legacy hashes were written at cost 12
     * (~1250 ms per verify); the policy is now 10 (~310 ms). BCrypt.checkpw reads
     * the cost from the hash itself, so without this every existing account would
     * keep paying the old cost forever.
     *
     * Runs at most ONCE per account: the next login reads the new hash, NeedsRehash
     * returns false, and this is a no-op. Best-effort by design — a failure here
     * must never block a login that has already authenticated, so it is logged and
     * swallowed. The user simply pays the old cost again next time.
     */
    private void MigrateHashIfStale(User user, String plaintextPassword) {
        try {
            if (!PasswordService.NeedsRehash(user.GetPasswordHash())) {
                return;
            }
            userDAO.UpdatePasswordHash(
                user.GetUserId(),
                PasswordService.Hash(plaintextPassword));
            System.out.println(
                "LoginProcess: migrated password hash to the current cost factor for user "
                + user.GetUserId());
        } catch (Exception e) {
            // Non-fatal: authentication already succeeded.
            System.err.println("LoginProcess.MigrateHashIfStale: " + e.getMessage());
        }
    }

    /**
     * Creates the single live session for the user (takeover policy), inside
     * one transaction on a pooled connection — mirrors the
     * BaseMaintenanceProcess.ExecuteAtomic idiom. Returns null on failure so
     * the UI can keep the user on the login screen.
     */
    @Override
    public SessionContext EstablishSession(User user) {
        String ip     = localIp();
        String device = deviceInfo();

        try (Connection conn = DatabaseConnector.GetConnection()) {
            conn.setAutoCommit(false);
            try {
                SessionContext ctx = sessionDAO.EstablishSession(
                    conn, user.GetUserId(), "Signed in on another device", ip, device);
                conn.commit();
                return ctx;
            } catch (SQLException ex) {
                conn.rollback();
                System.err.println("LoginProcess.EstablishSession (tx): " + ex.getMessage());
                return null;
            }
        } catch (SQLException e) {
            System.err.println("LoginProcess.EstablishSession: " + e.getMessage());
            return null;
        }
    }

    // -------------------------------------------------------------------------
    // Best-effort environment context for the session row (null-safe).
    // -------------------------------------------------------------------------

    private static String localIp() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return null;
        }
    }

    private static String deviceInfo() {
        String os = System.getProperty("os.name");
        try {
            String host = InetAddress.getLocalHost().getHostName();
            String user = System.getProperty("user.name");
            return user + "@" + host + " (" + os + ")";
        } catch (Exception e) {
            return os;
        }
    }
}