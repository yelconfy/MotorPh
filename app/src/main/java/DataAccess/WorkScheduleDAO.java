package DataAccess;

import Objects.models.WorkScheduleInfo;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for Work_Schedule (01 - Reference Tables).
 *
 *   Work_Schedule: ScheduleID, ScheduleName, TimeStart, TimeEnd, BreakMinutes,
 *                  GracePeriodMinutes, WorksMon..WorksSun, Status
 *
 * Read-only reference lookup, following the same convention as PositionDAO /
 * DepartmentDAO: every method takes the shared transaction Connection, and
 * mapping is delegated to WorkScheduleInfo's smart constructor.
 *
 * NOTE: WorkScheduleInfo's smart constructor reads the full row. The view-backed
 * EmpDetail only carries a ScheduleID + ScheduleName stub, so attendance/payroll
 * hydrate the full schedule (times, break, grace, Works*) via GetByID /
 * GetByEmployee here.
 */
public class WorkScheduleDAO {

    /**
     * GetAll — for the schedule dropdown in the Employee form.
     */
    public List<WorkScheduleInfo> GetAll(Connection conn) throws SQLException {
        List<WorkScheduleInfo> list = new ArrayList<>();
        String sql = "SELECT * FROM Work_Schedule WHERE Status = 1 ORDER BY ScheduleName";

        try (
            Statement stmt = conn.createStatement();
            ResultSet rs   = stmt.executeQuery(sql)
        ) {
            while (rs.next()) {
                list.add(new WorkScheduleInfo(rs));
            }
        }
        return list;
    }

    /**
     * GetByID — used when hydrating EmpDetail's WorkSchedule reference.
     */
    public WorkScheduleInfo GetByID(Connection conn, int scheduleId) throws SQLException {
        String sql = "SELECT * FROM Work_Schedule WHERE ScheduleID = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, scheduleId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? new WorkScheduleInfo(rs) : null;
            }
        }
    }

    /**
     * GetByEmployee — the full schedule assigned to an employee
     * (Employees.WorkScheduleID -> Work_Schedule). Returns null when the
     * employee has no schedule assigned; callers fall back to
     * WorkScheduleInfo.Default(). Used by the attendance/payroll layers, which
     * only hold the employee ID, not the schedule ID.
     */
    public WorkScheduleInfo GetByEmployee(Connection conn, long employeeId) throws SQLException {
        String sql =
            "SELECT ws.* FROM Work_Schedule ws " +
            "JOIN Employees e ON e.WorkScheduleID = ws.ScheduleID " +
            "WHERE e.EmployeeID = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, employeeId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? new WorkScheduleInfo(rs) : null;
            }
        }
    }

    /**
     * Insert — add a new work schedule (Work Schedule Maintenance / Add).
     * Status defaults to 1 (active) at the schema level.
     */
    public boolean Insert(Connection conn, WorkScheduleInfo ws) throws SQLException {
        String sql =
            "INSERT INTO Work_Schedule " +
            "(ScheduleName, TimeStart, TimeEnd, BreakMinutes, GracePeriodMinutes, " +
            "WorksMon, WorksTue, WorksWed, WorksThu, WorksFri, WorksSat, WorksSun) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ws.GetScheduleName());
            pstmt.setTime(2, Time.valueOf(ws.GetTimeStart()));
            pstmt.setTime(3, Time.valueOf(ws.GetTimeEnd()));
            pstmt.setInt(4, ws.GetBreakMinutes());
            pstmt.setInt(5, ws.GetGracePeriodMinutes());
            pstmt.setBoolean(6, ws.GetWorksMon());
            pstmt.setBoolean(7, ws.GetWorksTue());
            pstmt.setBoolean(8, ws.GetWorksWed());
            pstmt.setBoolean(9, ws.GetWorksThu());
            pstmt.setBoolean(10, ws.GetWorksFri());
            pstmt.setBoolean(11, ws.GetWorksSat());
            pstmt.setBoolean(12, ws.GetWorksSun());
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * Update — edit an existing work schedule (Work Schedule Maintenance / Edit).
     */
    public boolean Update(Connection conn, WorkScheduleInfo ws) throws SQLException {
        String sql =
            "UPDATE Work_Schedule SET ScheduleName = ?, TimeStart = ?, TimeEnd = ?, " +
            "BreakMinutes = ?, GracePeriodMinutes = ?, WorksMon = ?, WorksTue = ?, " +
            "WorksWed = ?, WorksThu = ?, WorksFri = ?, WorksSat = ?, WorksSun = ? " +
            "WHERE ScheduleID = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, ws.GetScheduleName());
            pstmt.setTime(2, Time.valueOf(ws.GetTimeStart()));
            pstmt.setTime(3, Time.valueOf(ws.GetTimeEnd()));
            pstmt.setInt(4, ws.GetBreakMinutes());
            pstmt.setInt(5, ws.GetGracePeriodMinutes());
            pstmt.setBoolean(6, ws.GetWorksMon());
            pstmt.setBoolean(7, ws.GetWorksTue());
            pstmt.setBoolean(8, ws.GetWorksWed());
            pstmt.setBoolean(9, ws.GetWorksThu());
            pstmt.setBoolean(10, ws.GetWorksFri());
            pstmt.setBoolean(11, ws.GetWorksSat());
            pstmt.setBoolean(12, ws.GetWorksSun());
            pstmt.setInt(13, ws.GetScheduleId());
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * IsInUse — true if any employee is currently assigned this schedule
     * (Employees.WorkScheduleID). A soft delete here never trips a real FK
     * exception, so this is the explicit stand-in — same role as
     * DeductionDAO.IsInUse / LeaveDAO.IsInUse, but checking Employees
     * directly rather than a Type-usage table, since that's the only
     * real-world reference to a schedule.
     */
    public boolean IsInUse(Connection conn, int scheduleId) throws SQLException {
        String sql = "SELECT 1 FROM Employees WHERE WorkScheduleID = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, scheduleId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Delete — soft delete (Work_Schedule has its own Status column). Caller
     * (WorkScheduleMaintenanceProcess) is responsible for checking IsInUse
     * first — this method does not guard itself.
     */
    public boolean Delete(Connection conn, int scheduleId) throws SQLException {
        String sql = "UPDATE Work_Schedule SET Status = 0 WHERE ScheduleID = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, scheduleId);
            return pstmt.executeUpdate() > 0;
        }
    }
}