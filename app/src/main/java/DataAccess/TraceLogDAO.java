package DataAccess;

import Objects.enums.LogLevel;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * DAO for MPH_TRACE.dbo.Trace_Log — the diagnostic trace trail (script 18).
 *
 * Unlike every other DAO in this project, this one talks to the SEPARATE trace
 * database, so it borrows from DatabaseConnector.GetTraceConnection() (a second,
 * small Hikari pool) rather than the main pool. It never participates in a main-
 * DB transaction — trace writes must not be able to affect operational data.
 *
 * There is deliberately NO self-opening-then-transactional split here (the
 * pattern the audit DAO uses): trace is always a single, standalone insert on a
 * borrowed trace connection. Insert() throws; LoggingService is the layer that
 * decides trace failures are non-fatal and swallows them.
 */
public class TraceLogDAO {

  /**
   * Appends one trace row to MPH_TRACE. Borrows and returns a trace-pool
   * connection. Throws on failure — the caller (LoggingService) is responsible
   * for treating that as non-fatal.
   */
  public void Insert(
    LogLevel level,
    String source,
    String message,
    String threadName,
    String username,
    Long sessionId
  ) throws SQLException {
    String sql =
      "INSERT INTO dbo.Trace_Log " +
      "(LogLevel, Source, Message, ThreadName, Username, SessionId) " +
      "VALUES (?, ?, ?, ?, ?, ?)";

    try (
      Connection conn = DatabaseConnector.GetTraceConnection();
      PreparedStatement ps = conn.prepareStatement(sql)
    ) {
      ps.setString(1, level.name());
      ps.setString(2, source);
      ps.setString(3, message);
      ps.setString(4, threadName);
      ps.setString(5, username);
      if (sessionId != null) {
        ps.setLong(6, sessionId);
      } else {
        ps.setNull(6, java.sql.Types.BIGINT);
      }
      ps.executeUpdate();
    }
  }
}