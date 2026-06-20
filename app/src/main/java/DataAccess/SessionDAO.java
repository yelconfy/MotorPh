package DataAccess;

import Objects.models.IAM.SessionContext;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Base64;

/**
 * Data-access layer for User_Session (03 - Security and Audit Tables).
 *
 *   User_Session: SessionID, UserID, SessionTokenHash, IssuedAt, ExpiresAt,
 *                 LastActivityAt, IPAddress, DeviceInfo, IsRevoked, RevokedReason
 *
 * Single-session enforcement uses the TAKEOVER policy ("newest login wins"):
 *   - EstablishSession revokes any currently-live session for the user and
 *     inserts a fresh one, all inside the CALLER's transaction.
 *   - The displaced client's next heartbeat (TouchSession) finds its row
 *     IsRevoked = 1 and self-logs-out.
 *
 * A session is LIVE when  IsRevoked = 0 AND ExpiresAt > now.  ExpiresAt is
 * pushed forward on every heartbeat, so a crashed / force-killed client whose
 * heartbeat stops is automatically reclaimable once the TTL lapses.
 *
 * Convention notes:
 *   - EstablishSession takes a shared Connection + throws SQLException; the
 *     transaction boundary (commit/rollback) lives in LoginProcess.
 *   - TouchSession / RevokeSession are one-shot self-opening calls (heartbeat
 *     and logout have no surrounding transaction).
 *   - SQLException is always propagated, never swallowed.
 *   - The token's SHA-256 hash is stored; the raw token is returned to the
 *     caller and held only in memory.
 */
public class SessionDAO {

    /** Session lifetime, refreshed on each heartbeat. Must exceed the heartbeat interval. */
    public static final int SESSION_TTL_MINUTES = 2;

    private static final SecureRandom RNG = new SecureRandom();

    // =========================================================================
    // Establish (transactional — runs on the caller's Connection)
    // =========================================================================

    /**
     * Takeover establish: serialize on the user row, revoke any live session,
     * insert a new one. Returns the new SessionID + raw token.
     *
     * Caller owns the transaction (setAutoCommit(false) -> commit / rollback).
     */
    public SessionContext EstablishSession(Connection conn,
                                           long userId,
                                           String revokeReason,
                                           String ip,
                                           String device) throws SQLException {

        // 1) Per-user gate: forces two simultaneous logins of the SAME account
        //    (e.g. both workstations at once) to queue, so the revoke below
        //    always sees the other login's insert. One Users row per user.
        final String lockSql =
            "SELECT UserID FROM Users WITH (UPDLOCK, HOLDLOCK) WHERE UserID = ?";
        try (PreparedStatement ps = conn.prepareStatement(lockSql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next(); // hold the lock; row is guaranteed to exist
            }
        }

        // 2) Revoke any currently-live session for this user (takeover).
        final String revokeSql =
            "UPDATE User_Session " +
            "SET    IsRevoked = 1, RevokedReason = ? " +
            "WHERE  UserID = ? AND IsRevoked = 0";
        try (PreparedStatement ps = conn.prepareStatement(revokeSql)) {
            ps.setString(1, revokeReason);
            ps.setLong  (2, userId);
            ps.executeUpdate();
        }

        // 3) Insert the new live session and return its generated id.
        String rawToken  = NewToken();
        String tokenHash = HashToken(rawToken);

        final String insertSql =
            "INSERT INTO User_Session " +
            "    (UserID, SessionTokenHash, ExpiresAt, LastActivityAt, IPAddress, DeviceInfo) " +
            "OUTPUT INSERTED.SessionID " +
            "VALUES (?, ?, DATEADD(MINUTE, ?, SYSDATETIME()), SYSDATETIME(), ?, ?)";
        try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setLong  (1, userId);
            ps.setString(2, tokenHash);
            ps.setInt   (3, SESSION_TTL_MINUTES);
            ps.setString(4, ip);
            ps.setString(5, device);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new SessionContext(rs.getLong(1), rawToken);
            }
        }
    }

    // =========================================================================
    // Heartbeat & revoke (one-shot, self-opening)
    // =========================================================================

    /**
     * Heartbeat: refresh LastActivityAt / ExpiresAt for a live session and
     * report whether it is still live in the same round trip.
     *
     * @return true if the row was still live (and has been refreshed);
     *         false if it was revoked elsewhere or has expired.
     */
    public boolean TouchSession(long sessionId) throws SQLException {
        final String sql =
            "UPDATE User_Session " +
            "SET    LastActivityAt = SYSDATETIME(), " +
            "       ExpiresAt      = DATEADD(MINUTE, ?, SYSDATETIME()) " +
            "WHERE  SessionID = ? AND IsRevoked = 0 AND ExpiresAt > SYSDATETIME()";

        try (Connection conn = DatabaseConnector.GetConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt (1, SESSION_TTL_MINUTES);
            ps.setLong(2, sessionId);
            return ps.executeUpdate() == 1;
        }
    }

    /** Marks a session revoked (clean logout). No-op if already revoked. */
    public void RevokeSession(long sessionId, String reason) throws SQLException {
        final String sql =
            "UPDATE User_Session " +
            "SET    IsRevoked = 1, RevokedReason = ? " +
            "WHERE  SessionID = ? AND IsRevoked = 0";

        try (Connection conn = DatabaseConnector.GetConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reason);
            ps.setLong  (2, sessionId);
            ps.executeUpdate();
        }
    }

    // =========================================================================
    // Token helpers
    // =========================================================================

    /** 256 bits of randomness, URL-safe Base64, no padding (~43 chars). */
    static String NewToken() {
        byte[] bytes = new byte[32];
        RNG.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 of the raw token, lower-case hex (64 chars) — what gets stored. */
    static String HashToken(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS; this branch is unreachable.
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}