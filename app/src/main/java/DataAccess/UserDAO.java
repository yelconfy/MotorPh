package DataAccess;

import Objects.models.User;
import java.sql.*;

/**
 * Data-access layer for Users and related IAM tables.
 *
 * Lockout policy: 5 consecutive failures → 15-minute lockout window.
 */
public class UserDAO {

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCKOUT_MINUTES      = 15;

    // -------------------------------------------------------------------------
    // Lookup
    // -------------------------------------------------------------------------

    /**
     * Fetches a user by username, JOINing Account_Role for RoleCode / RoleName.
     * Returns null when no matching record exists.
     * The caller is responsible for checking IsActive() and IsLockedOut().
     */
    public User FindByUsername(String username) {
        String sql =
            "SELECT u.UserID, u.EmployeeID, u.Username, u.PasswordHash, " +
            "       u.RoleID, u.Status, u.FailedLoginCount, u.LockoutUntil, " +
            "       u.MustChangePassword, " +
            "       r.RoleCode, r.RoleName " +
            "FROM   Users u " +
            "JOIN   Account_Role r ON r.RoleID = u.RoleID " +
            "WHERE  u.Username = ?";

        try (Connection conn = DatabaseConnector.GetConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new User(rs);
            }
        } catch (SQLException e) {
            System.err.println("UserDAO.FindByUsername: " + e.getMessage());
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Lockout management
    // -------------------------------------------------------------------------

    /**
     * Increments FailedLoginCount. On reaching MAX_FAILED_ATTEMPTS, sets
     * LockoutUntil to LOCKOUT_MINUTES from now.
     */
    public void IncrementFailedLogin(long userId) {
        String sql =
            "UPDATE Users " +
            "SET    FailedLoginCount = FailedLoginCount + 1, " +
            "       LockoutUntil = CASE " +
            "           WHEN FailedLoginCount + 1 >= ? " +
            "           THEN DATEADD(MINUTE, ?, SYSDATETIME()) " +
            "           ELSE LockoutUntil END, " +
            "       LastUpdatedDate = SYSDATETIME() " +
            "WHERE  UserID = ?";

        try (Connection conn = DatabaseConnector.GetConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt (1, MAX_FAILED_ATTEMPTS);
            ps.setInt (2, LOCKOUT_MINUTES);
            ps.setLong(3, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("UserDAO.IncrementFailedLogin: " + e.getMessage());
        }
    }

    /** Clears FailedLoginCount and LockoutUntil after a successful login. */
    public void ResetFailedLogin(long userId) {
        String sql =
            "UPDATE Users " +
            "SET    FailedLoginCount = 0, " +
            "       LockoutUntil    = NULL, " +
            "       LastUpdatedDate = SYSDATETIME() " +
            "WHERE  UserID = ?";

        try (Connection conn = DatabaseConnector.GetConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setLong(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("UserDAO.ResetFailedLogin: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Password management
    // -------------------------------------------------------------------------

    /**
     * Stores a new BCrypt hash and clears the MustChangePassword flag.
     * Also resets any lingering lockout.
     */
    public void UpdatePasswordHash(long userId, String newHash) {
        String sql =
            "UPDATE Users " +
            "SET    PasswordHash       = ?, " +
            "       MustChangePassword = 0, " +
            "       FailedLoginCount   = 0, " +
            "       LockoutUntil       = NULL, " +
            "       LastPasswordChange = SYSDATETIME(), " +
            "       LastUpdatedDate    = SYSDATETIME() " +
            "WHERE  UserID = ?";

        try (Connection conn = DatabaseConnector.GetConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, newHash);
            ps.setLong  (2, userId);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("UserDAO.UpdatePasswordHash: " + e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Access logging
    // -------------------------------------------------------------------------

    /**
     * Appends a row to User_Access_Log.
     * LoginStatus: 1 = success, 0 = failure. failureReason may be null on success.
     */
    public void LogAccess(String username, boolean success, String failureReason) {
        String sql =
            "INSERT INTO User_Access_Log (Username, LoginStatus, FailureReason) " +
            "VALUES (?, ?, ?)";

        try (Connection conn = DatabaseConnector.GetConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            ps.setInt   (2, success ? 1 : 0);
            ps.setString(3, failureReason);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.err.println("UserDAO.LogAccess: " + e.getMessage());
        }
    }
}
