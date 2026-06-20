package DataAccess;

import Objects.enums.Status.RequestStatus;
import Objects.models.OvertimeRequest;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for Overtime_Request (04 - Leave & Compensation Tables).
 *
 * Columns: OvertimeRequestID, EmployeeID, OvertimeDate, OvertimeStart,
 *          OvertimeEnd, Reason, Status, ActionedBy, DateFiled, DateActioned
 */
public class OvertimeDAO {

    /**
     * File — inserts a new pending overtime request.
     * Returns the generated OvertimeRequestID.
     */
    public long File(Connection conn, OvertimeRequest req) throws SQLException {
        String sql =
            "INSERT INTO Overtime_Request " +
            "(EmployeeID, OvertimeDate, OvertimeStart, OvertimeEnd, Reason) " +
            "OUTPUT INSERTED.OvertimeRequestID " +
            "VALUES (?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, req.GetEmployeeId());
            pstmt.setDate(2, Date.valueOf(req.GetOvertimeDate()));
            pstmt.setTime(3, Time.valueOf(req.GetOvertimeStart()));
            pstmt.setTime(4, Time.valueOf(req.GetOvertimeEnd()));
            pstmt.setString(5, req.GetReason());

            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getLong(1) : -1;
            }
        }
    }

    /**
     * Action — approves, rejects, or cancels a request.
     */
    public boolean Action(Connection conn, long overtimeRequestId,
                          RequestStatus newStatus, long actionedByUserId)
            throws SQLException {

        String sql =
            "UPDATE Overtime_Request SET Status = ?, ActionedBy = ?, " +
            "DateActioned = SYSDATETIME() WHERE OvertimeRequestID = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, newStatus.getValue());
            pstmt.setLong(2, actionedByUserId);
            pstmt.setLong(3, overtimeRequestId);
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * GetByEmployee — all requests for an employee, newest first.
     */
    public List<OvertimeRequest> GetByEmployee(Connection conn, long employeeId)
            throws SQLException {

        List<OvertimeRequest> list = new ArrayList<>();
        String sql =
            "SELECT * FROM Overtime_Request " +
            "WHERE EmployeeID = ? ORDER BY DateFiled DESC";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, employeeId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) list.add(new OvertimeRequest(rs));
            }
        }
        return list;
    }

    /**
     * GetPending — all pending requests for the approval screen.
     */
    public List<OvertimeRequest> GetPending(Connection conn) throws SQLException {
        List<OvertimeRequest> list = new ArrayList<>();
        String sql =
            "SELECT * FROM Overtime_Request WHERE Status = 0 ORDER BY DateFiled ASC";

        try (Statement stmt = conn.createStatement();
             ResultSet rs   = stmt.executeQuery(sql)) {
            while (rs.next()) list.add(new OvertimeRequest(rs));
        }
        return list;
    }

    /**
     * GetApprovedForPeriod — approved overtime records within a date range.
     * Used by PayrollProcess when computing overtime pay for a payroll cut-off.
     */
    public List<OvertimeRequest> GetApprovedForPeriod(
            Connection conn, long employeeId,
            java.time.LocalDate from, java.time.LocalDate to)
            throws SQLException {

        List<OvertimeRequest> list = new ArrayList<>();
        String sql =
            "SELECT * FROM Overtime_Request " +
            "WHERE EmployeeID = ? AND Status = 1 " +
            "AND OvertimeDate BETWEEN ? AND ? " +
            "ORDER BY OvertimeDate ASC";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, employeeId);
            pstmt.setDate(2, Date.valueOf(from));
            pstmt.setDate(3, Date.valueOf(to));
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) list.add(new OvertimeRequest(rs));
            }
        }
        return list;
    }
}