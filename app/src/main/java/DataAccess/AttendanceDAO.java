package DataAccess;

import Objects.models.Attendance;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * DAO for Attendance (02 - Core Employee Tables).
 *
 *   Attendance: AttendanceID, EmployeeID, AttendanceDate, TimeIn, TimeOut
 *
 * The query methods JOIN Employees to bring back FirstName and LastName for
 * display purposes (the Attendance model composes an EmployeeInfo stub from
 * those two columns).
 *
 * Phase 7c adds the write path (GetById / Update / Insert / ExistsForDate) used
 * by the audited Punch Correction module. The table has NO unique constraint on
 * (EmployeeID, AttendanceDate), so the calculator's "one row per date" invariant
 * is enforced in the app: callers must ExistsForDate before Insert (the
 * correction process re-checks inside its own transaction to close the TOCTOU
 * gap). All writes participate in the caller's Connection so the punch change
 * and its Audit_Log row commit atomically.
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

  // ==========================================================================
  // Phase 7c — write path for the audited Punch Correction module
  // ==========================================================================

  /**
   * GetById — single attendance row (JOINed for the display name), or null.
   * Used inside the correction transaction to capture the OLD value for audit.
   */
  public Attendance GetById(Connection conn, long attendanceId)
    throws SQLException {
    String sql =
      "SELECT a.*, e.FirstName, e.LastName " +
      "FROM Attendance a " +
      "JOIN Employees e ON a.EmployeeID = e.EmployeeID " +
      "WHERE a.AttendanceID = ?";
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, attendanceId);
      try (ResultSet rs = pstmt.executeQuery()) {
        return rs.next() ? new Attendance(rs) : null;
      }
    }
  }

  /**
   * ExistsForDate — true if the employee already has any attendance row on the
   * given date. The add-punch path must check this (no DB unique constraint) to
   * avoid creating a duplicate that would break the calculator.
   */
  public boolean ExistsForDate(Connection conn, long employeeId, LocalDate date)
    throws SQLException {
    String sql =
      "SELECT COUNT(*) FROM Attendance WHERE EmployeeID = ? AND AttendanceDate = ?";
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, employeeId);
      pstmt.setDate(2, java.sql.Date.valueOf(date));
      try (ResultSet rs = pstmt.executeQuery()) {
        return rs.next() && rs.getInt(1) > 0;
      }
    }
  }

  /**
   * Update — corrects TimeIn/TimeOut on an existing row. Either time may be null
   * (e.g. an incomplete day with no time-out). Date and employee are immutable
   * here; to move a punch to a different date, delete + add (out of 7c scope).
   */
  public boolean Update(
    Connection conn,
    long attendanceId,
    LocalTime timeIn,
    LocalTime timeOut
  ) throws SQLException {
    String sql =
      "UPDATE Attendance SET TimeIn = ?, TimeOut = ? WHERE AttendanceID = ?";
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      setTimeOrNull(pstmt, 1, timeIn);
      setTimeOrNull(pstmt, 2, timeOut);
      pstmt.setLong(3, attendanceId);
      return pstmt.executeUpdate() > 0;
    }
  }

  /**
   * Insert — adds a corrected punch for a date that has no row (e.g. a captured
   * absence that was actually worked). Returns the generated AttendanceID, or -1.
   * Callers MUST ExistsForDate first (enforced by the correction process).
   */
  public long Insert(
    Connection conn,
    long employeeId,
    LocalDate date,
    LocalTime timeIn,
    LocalTime timeOut
  ) throws SQLException {
    String sql =
      "INSERT INTO Attendance (EmployeeID, AttendanceDate, TimeIn, TimeOut) " +
      "OUTPUT INSERTED.AttendanceID VALUES (?, ?, ?, ?)";
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, employeeId);
      pstmt.setDate(2, java.sql.Date.valueOf(date));
      setTimeOrNull(pstmt, 3, timeIn);
      setTimeOrNull(pstmt, 4, timeOut);
      try (ResultSet rs = pstmt.executeQuery()) {
        return rs.next() ? rs.getLong(1) : -1;
      }
    }
  }

  private static void setTimeOrNull(PreparedStatement ps, int idx, LocalTime t)
    throws SQLException {
    if (t != null) {
      ps.setTime(idx, Time.valueOf(t));
    } else {
      ps.setNull(idx, Types.TIME);
    }
  }
}