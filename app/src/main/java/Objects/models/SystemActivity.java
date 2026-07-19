package Objects.models;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * Read-only projection of vw_SystemActivity (06 - Views), the merged
 * audit + access timeline:
 *
 *   vw_SystemActivity: Source ('AUDIT' | 'ACCESS'), Username, EventTime, Detail
 *
 * Not a table-backed entity (a view row has no primary key), so this is a plain
 * display DTO rather than a BaseObject.
 */
public class SystemActivity {

  private final String source;
  private final String username;
  private final LocalDateTime eventTime;
  private final String detail;

  public SystemActivity(
    String source,
    String username,
    LocalDateTime eventTime,
    String detail
  ) {
    this.source = source;
    this.username = username;
    this.eventTime = eventTime;
    this.detail = detail;
  }

  /** Smart constructor — maps a vw_SystemActivity row. */
  public SystemActivity(ResultSet rs) throws SQLException {
    this.source = rs.getString("Source");
    this.username = rs.getString("Username");
    Timestamp ts = rs.getTimestamp("EventTime");
    this.eventTime = (ts != null) ? ts.toLocalDateTime() : null;
    this.detail = rs.getString("Detail");
  }

  public String GetSource() {
    return source;
  }

  public String GetUsername() {
    return username;
  }

  public LocalDateTime GetEventTime() {
    return eventTime;
  }

  public String GetDetail() {
    return detail;
  }
}