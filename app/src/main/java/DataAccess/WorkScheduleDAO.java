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
 * NOTE: WorkScheduleInfo's smart constructor only reads ScheduleID + ScheduleName
 * (that is all vw_EmployeeCompleteDetails exposes). SELECT * here returns the
 * full row; the extra columns are simply ignored by the constructor. If you
 * later extend WorkScheduleInfo to map TimeStart/TimeEnd/etc., no DAO change is
 * needed — the columns are already in the result set.
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
}