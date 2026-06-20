package DataAccess;

import Objects.models.DepartmentInfo;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for Departments (01 - Reference Tables).
 * Columns: DepartmentID, DepartmentCode, DepartmentName
 *
 * Previously returned a Department object — updated to use DepartmentInfo
 * which follows the same naming convention as PositionInfo etc.
 */
public class DepartmentDAO {

    /**
     * GetAll — for dropdowns in the Employee form.
     */
    public List<DepartmentInfo> GetAll(Connection conn) throws SQLException {
        List<DepartmentInfo> list = new ArrayList<>();
        String sql = "SELECT * FROM Departments ORDER BY DepartmentName";

        try (
            Statement stmt = conn.createStatement();
            ResultSet rs   = stmt.executeQuery(sql)
        ) {
            while (rs.next()) {
                list.add(new DepartmentInfo(rs));
            }
        }
        return list;
    }

    /**
     * GetByID — used when hydrating EmpDetail.
     */
    public DepartmentInfo GetByID(Connection conn, int departmentId) throws SQLException {
        String sql = "SELECT * FROM Departments WHERE DepartmentID = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, departmentId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? new DepartmentInfo(rs) : null;
            }
        }
    }

    /**
     * GetByCode — alternative lookup used during ETL / CSV import.
     */
    public DepartmentInfo GetByCode(Connection conn, String code) throws SQLException {
        String sql = "SELECT * FROM Departments WHERE DepartmentCode = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, code);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? new DepartmentInfo(rs) : null;
            }
        }
    }
}