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
    public List<LeaveTypeInfo> GetAllLeaveTypes(Connection conn) throws SQLException {
        List<LeaveTypeInfo> list = new ArrayList<>();
        String sql = "SELECT * FROM Leave_Type WHERE Status = 1 ORDER BY LeaveTypeName";

        try (Statement stmt = conn.createStatement();
             ResultSet rs   = stmt.executeQuery(sql)) {
            while (rs.next()) list.add(new LeaveTypeInfo(rs));
        }
        return list;
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
    public boolean Action(Connection conn, long leaveRequestId,
                          RequestStatus newStatus, long actionedByUserId)
            throws SQLException {

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

        try (Statement stmt = conn.createStatement();
             ResultSet rs   = stmt.executeQuery(sql)) {
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
    public double GetRemainingDays(Connection conn, long employeeId,
                                   int leaveTypeId, int year)
            throws SQLException {

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
    public boolean UpsertEntitlement(Connection conn, long employeeId,
                                     int leaveTypeId, int year,
                                     double entitledDays, double carriedOverDays)
            throws SQLException {

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
}