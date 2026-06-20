package DataAccess;

import Objects.models.Attendance;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO for Attendance (02 - Core Employee Tables).
 *
 *   Attendance: AttendanceID, EmployeeID, AttendanceDate, TimeIn, TimeOut
 *
 * Both query methods JOIN Employees to bring back FirstName and LastName for
 * display purposes (the Attendance model composes an EmployeeInfo stub from
 * those two columns).
 */
public class AttendanceDAO {

  /**
   * GetByDateRange — used by PayrollProcess to calculate worked hours.
   * Returns all attendance rows for a single employee within [startDate, endDate].
   *
   * Shared-Connection overload: participates in the caller's connection so a
   * payroll run keeps every read on one connection. Does NOT open or close.
   */
  public List<Attendance> GetByDateRange(
    Connection conn,
    long employeeId,
    LocalDate startDate,
    LocalDate endDate
  ) throws SQLException {
    List<Attendance> logs = new ArrayList<>();
    String sql =
      "SELECT a.*, e.FirstName, e.LastName " +
      "FROM Attendance a " +
      "JOIN Employees e ON a.EmployeeID = e.EmployeeID " +
      "WHERE a.EmployeeID = ? " +
      "AND a.AttendanceDate BETWEEN ? AND ? " +
      "ORDER BY a.AttendanceDate ASC";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, employeeId);
      pstmt.setDate(2, java.sql.Date.valueOf(startDate));
      pstmt.setDate(3, java.sql.Date.valueOf(endDate));

      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          logs.add(new Attendance(rs));
        }
      }
    }
    return logs;
  }

  /**
   * Self-opening overload for one-shot UI reads (no surrounding transaction).
   * Do NOT call this from inside a payroll run that holds a shared Connection —
   * it would close the single shared connection out from under the caller.
   */
  public List<Attendance> GetByDateRange(
    long employeeId,
    LocalDate startDate,
    LocalDate endDate
  ) throws SQLException {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      return GetByDateRange(conn, employeeId, startDate, endDate);
    }
  }

  /**
   * SearchAttendance — used by TimeKeepingForm for the main grid.
   * Filters by optional free-text criteria (employee ID, first name, last name)
   * and an optional date range.
   *
   * FIX: EmployeeID is BIGINT; MSSQL rejects LIKE on a BIGINT without an
   * explicit cast.  Changed to CAST(e.EmployeeID AS NVARCHAR) LIKE ?,
   * matching the pattern already used in EmployeeDAO.Search.
   */
  public List<Attendance> SearchAttendance(
    String criteria,
    Optional<LocalDate> from,
    Optional<LocalDate> to
  ) throws SQLException {
    List<Attendance> results = new ArrayList<>();

    StringBuilder sql = new StringBuilder(
      "SELECT a.*, e.FirstName, e.LastName " +
        "FROM Attendance a " +
        "JOIN Employees e ON a.EmployeeID = e.EmployeeID " +
        "WHERE 1=1 "
    );

    if (!criteria.isEmpty()) {
      // CAST required: EmployeeID is BIGINT; LIKE is only valid on character types.
      sql.append(
        "AND (CAST(e.EmployeeID AS NVARCHAR) LIKE ? " +
          " OR e.FirstName LIKE ? " +
          " OR e.LastName  LIKE ?) "
      );
    }
    if (from.isPresent()) {
      sql.append("AND a.AttendanceDate >= ? ");
    }
    if (to.isPresent()) {
      sql.append("AND a.AttendanceDate <= ? ");
    }

    try (
      Connection conn = DatabaseConnector.GetConnection();
      PreparedStatement pstmt = conn.prepareStatement(sql.toString())
    ) {
      int paramIdx = 1;
      if (!criteria.isEmpty()) {
        String search = "%" + criteria + "%";
        pstmt.setString(paramIdx++, search);
        pstmt.setString(paramIdx++, search);
        pstmt.setString(paramIdx++, search);
      }
      if (from.isPresent()) {
        pstmt.setDate(paramIdx++, java.sql.Date.valueOf(from.get()));
      }
      if (to.isPresent()) {
        pstmt.setDate(paramIdx++, java.sql.Date.valueOf(to.get()));
      }

      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          results.add(new Attendance(rs));
        }
      }
    }
    return results;
  }
}
