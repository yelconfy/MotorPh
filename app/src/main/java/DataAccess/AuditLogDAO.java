package DataAccess;

import Objects.enums.Status.AuditAction;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * DAO for Audit_Log (03 - Security & Audit Tables) — the generic, append-only
 * activity trail.
 *
 *   Audit_Log: AuditLogID, Username, TableName, RecordID, ActionType,
 *              OldValue, NewValue, ActionTimestamp
 *
 * Username is a string stamp (no FK) and RecordID is a generic NVARCHAR key, so
 * any table's row can be referenced without a foreign key. ActionType is coded
 * by AuditAction (0=Insert, 1=Update, 2=Delete, 3=Print).
 *
 * Shared-Connection convention: the conn-param Log participates in an existing
 * transaction; a self-opening overload is provided for one-shot writes. When a
 * caller needs BOTH a count and a write (e.g. recording a payslip print), it
 * should pass ONE Connection to both conn-param methods rather than chaining the
 * self-opening overloads — DatabaseConnector hands out a single shared
 * connection, so a self-opening call would close it out from under the next.
 */
public class AuditLogDAO {

  /** Appends one audit row on the caller's Connection. oldValue/newValue may be null. */
  public void Log(
    Connection conn,
    String username,
    String tableName,
    String recordId,
    AuditAction action,
    String oldValue,
    String newValue
  ) throws SQLException {
    String sql =
      "INSERT INTO Audit_Log " +
      "(Username, TableName, RecordID, ActionType, OldValue, NewValue) " +
      "VALUES (?, ?, ?, ?, ?, ?)";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, username);
      ps.setString(2, tableName);
      ps.setString(3, recordId);
      ps.setInt(4, action.getValue());
      ps.setString(5, oldValue);
      ps.setString(6, newValue);
      ps.executeUpdate();
    }
  }

  /** Self-opening overload for a one-shot audit write with no surrounding transaction. */
  public void Log(
    String username,
    String tableName,
    String recordId,
    AuditAction action,
    String oldValue,
    String newValue
  ) throws SQLException {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      Log(conn, username, tableName, recordId, action, oldValue, newValue);
    }
  }

  /**
   * CountActions — rows for a (table, record, action) tuple. Used to detect a
   * payslip REPRINT: a Print whose prior count is already > 0 is a reprint.
   */
  public int CountActions(
    Connection conn,
    String tableName,
    String recordId,
    AuditAction action
  ) throws SQLException {
    String sql =
      "SELECT COUNT(*) FROM Audit_Log " +
      "WHERE TableName = ? AND RecordID = ? AND ActionType = ?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, tableName);
      ps.setString(2, recordId);
      ps.setInt(3, action.getValue());
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? rs.getInt(1) : 0;
      }
    }
  }

  /** Self-opening CountActions overload. */
  public int CountActions(String tableName, String recordId, AuditAction action)
    throws SQLException {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      return CountActions(conn, tableName, recordId, action);
    }
  }
}