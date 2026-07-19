package DataAccess;

import Objects.enums.Status.RequestStatus;
import Objects.models.LeaveRequest;
import Objects.models.LeaveTypeInfo;
import java.sql.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for Leave_Request, Leave_Entitlement, and Leave_Type.
 *
 * Schema (04 - Leave & Compensation Tables):
 *   Leave_Request: LeaveRequestID, EmployeeID, LeaveTypeID, StartDate, EndDate,
 *                  NumberOfDays, Reason, Status, ActionedBy, DateFiled, DateActioned
 *   Leave_Entitlement: EntitlementID, EmployeeID, LeaveTypeID, Year,
 *                      EntitledDays, CarriedOverDays, TotalEntitled (computed)
 *
 * Schema (01 - Reference Tables):
 *   Leave_Type: LeaveTypeID, LeaveTypeName, IsPaid, DefaultDaysPerYear,
 *               CarryOverAllowed, MaxCarryOverDays, Status
 *
 * Balance queries use vw_LeaveBalance (06 - Views).
 */
public class LeaveDAO {

  // =========================================================================
  // Leave_Type reference
  // =========================================================================

  /** All active leave types for dropdowns. */
  public List<LeaveTypeInfo> GetAllLeaveTypes(Connection conn)
    throws SQLException {
    List<LeaveTypeInfo> list = new ArrayList<>();
    String sql =
      "SELECT * FROM Leave_Type WHERE Status = 1 ORDER BY LeaveTypeName";

    try (
      Statement stmt = conn.createStatement();
      ResultSet rs = stmt.executeQuery(sql)
    ) {
      while (rs.next()) list.add(new LeaveTypeInfo(rs));
    }
    return list;
  }

  /**
   * Insert — add a new leave type (Leave Type Maintenance / Add). Status
   * defaults to 1 (active) at the schema level.
   *
   * MaxCarryOverDays is written as SQL NULL whenever CarryOverAllowed is
   * false — CK_LeaveType_CarryOver requires it (CarryOverAllowed = 1 OR
   * MaxCarryOverDays IS NULL). LeaveTypeMaintenanceProcess validates the
   * same rule up front as a SaveResult.invalid(...) before this is ever
   * reached; this is a mechanical mirror of that rule, not the primary gate.
   */
  public boolean Insert(Connection conn, LeaveTypeInfo lt) throws SQLException {
    String sql =
      "INSERT INTO Leave_Type " +
      "(LeaveTypeName, IsPaid, DefaultDaysPerYear, CarryOverAllowed, MaxCarryOverDays) " +
      "VALUES (?, ?, ?, ?, ?)";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, lt.GetLeaveTypeName());
      pstmt.setBoolean(2, lt.IsPaid());
      pstmt.setDouble(3, lt.GetDefaultDaysPerYear());
      pstmt.setBoolean(4, lt.IsCarryOverAllowed());
      if (lt.IsCarryOverAllowed()) {
        pstmt.setDouble(5, lt.GetMaxCarryOverDays());
      } else {
        pstmt.setNull(5, Types.DECIMAL);
      }
      return pstmt.executeUpdate() > 0;
    }
  }

  /**
   * Update — rename / retoggle an existing leave type (Leave Type
   * Maintenance / Edit). Same NULL handling on MaxCarryOverDays as Insert.
   */
  public boolean Update(Connection conn, LeaveTypeInfo lt) throws SQLException {
    String sql =
      "UPDATE Leave_Type SET LeaveTypeName = ?, IsPaid = ?, DefaultDaysPerYear = ?, " +
      "CarryOverAllowed = ?, MaxCarryOverDays = ? WHERE LeaveTypeID = ?";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, lt.GetLeaveTypeName());
      pstmt.setBoolean(2, lt.IsPaid());
      pstmt.setDouble(3, lt.GetDefaultDaysPerYear());
      pstmt.setBoolean(4, lt.IsCarryOverAllowed());
      if (lt.IsCarryOverAllowed()) {
        pstmt.setDouble(5, lt.GetMaxCarryOverDays());
      } else {
        pstmt.setNull(5, Types.DECIMAL);
      }
      pstmt.setInt(6, lt.GetLeaveTypeId());
      return pstmt.executeUpdate() > 0;
    }
  }

  /**
   * IsInUse — true if any leave request or entitlement row still references
   * this leave type. A soft delete here never trips a real FK exception, so
   * this is the explicit stand-in, mirroring DeductionDAO.IsInUse /
   * AllowanceDAO.IsInUse. Unlike those two, this does NOT filter to
   * "currently active" rows — Leave_Request.Status is an outcome (Pending /
   * Approved / Rejected / Cancelled), not an active/inactive toggle the way
   * Employee_Deduction.Status is, so even a historical, already-actioned
   * request is a legitimate reason to keep the type around.
   */
  public boolean IsInUse(Connection conn, int leaveTypeId) throws SQLException {
    String sql =
      "SELECT CASE WHEN EXISTS (" +
      "    SELECT 1 FROM Leave_Request WHERE LeaveTypeID = ?" +
      ") OR EXISTS (" +
      "    SELECT 1 FROM Leave_Entitlement WHERE LeaveTypeID = ?" +
      ") THEN 1 ELSE 0 END";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setInt(1, leaveTypeId);
      pstmt.setInt(2, leaveTypeId);
      try (ResultSet rs = pstmt.executeQuery()) {
        return rs.next() && rs.getBoolean(1);
      }
    }
  }

  /**
   * Delete — soft delete (Leave_Type has its own Status column). Caller
   * (LeaveTypeMaintenanceProcess) is responsible for checking IsInUse first —
   * this method does not guard itself, matching DeductionDAO.Delete /
   * AllowanceDAO.Delete.
   */
  public boolean Delete(Connection conn, int leaveTypeId) throws SQLException {
    String sql = "UPDATE Leave_Type SET Status = 0 WHERE LeaveTypeID = ?";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setInt(1, leaveTypeId);
      return pstmt.executeUpdate() > 0;
    }
  }

  // =========================================================================
  // Leave_Request CRUD
  // =========================================================================

  /**
   * File — inserts a new pending leave request.
   * Returns the generated LeaveRequestID.
   */
  public long File(Connection conn, LeaveRequest req) throws SQLException {
    String sql =
      "INSERT INTO Leave_Request " +
      "(EmployeeID, LeaveTypeID, StartDate, EndDate, NumberOfDays, Reason) " +
      "OUTPUT INSERTED.LeaveRequestID " +
      "VALUES (?, ?, ?, ?, ?, ?)";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, req.GetEmployeeId());
      pstmt.setInt(2, req.GetLeaveTypeId());
      pstmt.setDate(3, Date.valueOf(req.GetStartDate()));
      pstmt.setDate(4, Date.valueOf(req.GetEndDate()));
      pstmt.setDouble(5, req.GetNumberOfDays());
      pstmt.setString(6, req.GetReason());

      try (ResultSet rs = pstmt.executeQuery()) {
        return rs.next() ? rs.getLong(1) : -1;
      }
    }
  }

  /**
   * Action — approves, rejects, or cancels a request.
   * @param actionedByUserId the UserID of the HR/manager taking action
   */
  public boolean Action(
    Connection conn,
    long leaveRequestId,
    RequestStatus newStatus,
    long actionedByUserId
  ) throws SQLException {
    String sql =
      "UPDATE Leave_Request SET Status = ?, ActionedBy = ?, " +
      "DateActioned = SYSDATETIME() WHERE LeaveRequestID = ?";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setInt(1, newStatus.getValue());
      pstmt.setLong(2, actionedByUserId);
      pstmt.setLong(3, leaveRequestId);
      return pstmt.executeUpdate() > 0;
    }
  }

  /**
   * GetByEmployee — all requests for a given employee, newest first.
   */
  public List<LeaveRequest> GetByEmployee(Connection conn, long employeeId)
    throws SQLException {
    List<LeaveRequest> list = new ArrayList<>();
    String sql =
      "SELECT lr.*, lt.LeaveTypeName " +
      "FROM Leave_Request lr " +
      "JOIN Leave_Type lt ON lt.LeaveTypeID = lr.LeaveTypeID " +
      "WHERE lr.EmployeeID = ? " +
      "ORDER BY lr.DateFiled DESC";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, employeeId);
      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) list.add(new LeaveRequest(rs));
      }
    }
    return list;
  }

  /**
   * GetPending — all PENDING requests across all employees (for HR approval screen).
   */
  public List<LeaveRequest> GetPending(Connection conn) throws SQLException {
    List<LeaveRequest> list = new ArrayList<>();
    String sql =
      "SELECT lr.*, lt.LeaveTypeName " +
      "FROM Leave_Request lr " +
      "JOIN Leave_Type lt ON lt.LeaveTypeID = lr.LeaveTypeID " +
      "WHERE lr.Status = 0 " +
      "ORDER BY lr.DateFiled ASC";

    try (
      Statement stmt = conn.createStatement();
      ResultSet rs = stmt.executeQuery(sql)
    ) {
      while (rs.next()) list.add(new LeaveRequest(rs));
    }
    return list;
  }

  // =========================================================================
  // Leave balance via vw_LeaveBalance
  // =========================================================================

  /**
   * GetRemainingDays — remaining leave balance for a specific type and year.
   * Returns 0.0 if no entitlement row exists.
   */
  public double GetRemainingDays(
    Connection conn,
    long employeeId,
    int leaveTypeId,
    int year
  ) throws SQLException {
    String sql =
      "SELECT RemainingDays FROM vw_LeaveBalance " +
      "WHERE EmployeeID = ? AND LeaveTypeID = ? AND [Year] = ?";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, employeeId);
      pstmt.setInt(2, leaveTypeId);
      pstmt.setInt(3, year);
      try (ResultSet rs = pstmt.executeQuery()) {
        return rs.next() ? rs.getDouble("RemainingDays") : 0.0;
      }
    }
  }

  /**
   * UpsertEntitlement — creates or updates a Leave_Entitlement row.
   * Used during year-end roll-over or initial employee setup.
   */
  public boolean UpsertEntitlement(
    Connection conn,
    long employeeId,
    int leaveTypeId,
    int year,
    double entitledDays,
    double carriedOverDays
  ) throws SQLException {
    // MERGE is cleaner for upsert in MSSQL
    String sql =
      "MERGE Leave_Entitlement AS target " +
      "USING (SELECT ? AS EmployeeID, ? AS LeaveTypeID, ? AS [Year]) AS source " +
      "ON target.EmployeeID = source.EmployeeID " +
      "   AND target.LeaveTypeID = source.LeaveTypeID " +
      "   AND target.[Year] = source.[Year] " +
      "WHEN MATCHED THEN " +
      "  UPDATE SET EntitledDays = ?, CarriedOverDays = ? " +
      "WHEN NOT MATCHED THEN " +
      "  INSERT (EmployeeID, LeaveTypeID, [Year], EntitledDays, CarriedOverDays) " +
      "  VALUES (?, ?, ?, ?, ?);";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, employeeId);
      pstmt.setInt(2, leaveTypeId);
      pstmt.setInt(3, year);
      pstmt.setDouble(4, entitledDays);
      pstmt.setDouble(5, carriedOverDays);
      pstmt.setLong(6, employeeId);
      pstmt.setInt(7, leaveTypeId);
      pstmt.setInt(8, year);
      pstmt.setDouble(9, entitledDays);
      pstmt.setDouble(10, carriedOverDays);
      return pstmt.executeUpdate() > 0;
    }
  }

  /** Approved leave overlapping [from, to], with Leave_Type.IsPaid populated. */
  public List<LeaveRequest> GetApprovedForPeriod(
    Connection conn,
    long employeeId,
    LocalDate from,
    LocalDate to
  ) throws SQLException {
    List<LeaveRequest> list = new ArrayList<>();
    String sql =
      "SELECT lr.*, lt.LeaveTypeName, lt.IsPaid " +
      "FROM Leave_Request lr " +
      "JOIN Leave_Type lt ON lt.LeaveTypeID = lr.LeaveTypeID " +
      "WHERE lr.EmployeeID = ? AND lr.Status = 1 " +
      "AND lr.StartDate <= ? AND lr.EndDate >= ? " +
      "ORDER BY lr.StartDate ASC";
    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, employeeId);
      pstmt.setDate(2, Date.valueOf(to)); // StartDate <= to
      pstmt.setDate(3, Date.valueOf(from)); // EndDate   >= from
      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          LeaveRequest req = new LeaveRequest(rs);
          req.SetPaid(rs.getBoolean("IsPaid"));
          list.add(req);
        }
      }
    }
    return list;
  }
}
