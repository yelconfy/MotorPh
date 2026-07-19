package DataAccess;

import Objects.models.HolidayInfo;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for the Holiday table (01 - Reference Tables, seeded by 14).
 *
 *   Holiday: HolidayID, HolidayDate, HolidayName, HolidayType (0=Regular,
 *            1=Special Non-Working), IsRecurring, Status
 *
 * Read-only reference lookup, same convention as WorkScheduleDAO: every method
 * takes the shared Connection and delegates mapping to HolidayInfo's smart
 * constructor. The process layer folds the result into a HolidayCalendar.
 *
 * Matching is by EXACT date (correct for the seeded CY2024 set). Recurring
 * (year-agnostic) expansion via IsRecurring is a later enhancement.
 */
public class HolidayDAO {

  /** Active holidays whose date falls within [from, to] inclusive. */
  public List<HolidayInfo> GetByDateRange(Connection conn, LocalDate from, LocalDate to)
    throws SQLException {
    List<HolidayInfo> list = new ArrayList<>();
    if (from == null || to == null) {
      return list;
    }
    String sql =
      "SELECT * FROM Holiday " +
      "WHERE Status = 1 AND HolidayDate BETWEEN ? AND ? " +
      "ORDER BY HolidayDate";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setDate(1, Date.valueOf(from));
      pstmt.setDate(2, Date.valueOf(to));
      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          list.add(new HolidayInfo(rs));
        }
      }
    }
    return list;
  }

  /** All active holidays (e.g. for a future maintenance screen). */
  public List<HolidayInfo> GetAll(Connection conn) throws SQLException {
    List<HolidayInfo> list = new ArrayList<>();
    String sql = "SELECT * FROM Holiday WHERE Status = 1 ORDER BY HolidayDate";
    try (
      Statement stmt = conn.createStatement();
      ResultSet rs = stmt.executeQuery(sql)
    ) {
      while (rs.next()) {
        list.add(new HolidayInfo(rs));
      }
    }
    return list;
  }
}