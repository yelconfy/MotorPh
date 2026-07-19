package DataAccess;

import Objects.models.SystemActivity;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only DAO over vw_SystemActivity (06 - Views) — the merged audit + access
 * timeline that backs the Phase 7c Activity Log screen.
 *
 *   vw_SystemActivity: Source, Username, EventTime, Detail
 *
 * The view itself UNIONs Audit_Log and User_Access_Log, so this DAO never
 * touches those base tables directly.
 */
public class SystemActivityDAO {

  /** Most recent activity rows, newest first, capped at {@code limit}. */
  public List<SystemActivity> GetRecent(Connection conn, int limit)
    throws SQLException {
    List<SystemActivity> list = new ArrayList<>();
    String sql =
      "SELECT TOP (?) Source, Username, EventTime, Detail " +
      "FROM vw_SystemActivity ORDER BY EventTime DESC";
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setInt(1, limit);
      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          list.add(new SystemActivity(rs));
        }
      }
    }
    return list;
  }

  /** Self-opening overload for one-shot UI reads. */
  public List<SystemActivity> GetRecent(int limit) throws SQLException {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      return GetRecent(conn, limit);
    }
  }
}