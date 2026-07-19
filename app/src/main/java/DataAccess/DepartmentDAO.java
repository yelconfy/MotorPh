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
 *
 * BKL-01 stage 3a: write methods (Insert / Update / Delete) added so the
 * Department Maintenance module can drive it through
 * DepartmentMaintenanceProcess. The reads were already here for the
 * Employee-form dropdown. Every method takes the shared transaction
 * Connection + throws SQLException; the process owns the commit/rollback
 * boundary — same shape as PositionDAO.
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

    /**
     * Insert — add a new department (Department Maintenance / Add).
     * Both DepartmentCode and DepartmentName are user-entered, so this takes
     * the whole DepartmentInfo rather than a bare name (the one shape
     * difference vs PositionDAO.Insert, which had a single column).
     */
    public boolean Insert(Connection conn, DepartmentInfo dept) throws SQLException {
        String sql = "INSERT INTO Departments (DepartmentCode, DepartmentName) VALUES (?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, dept.GetDepartmentCode());
            pstmt.setString(2, dept.GetDepartmentName());
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * Update — recode / rename an existing department (Department Maintenance / Edit).
     * Both columns are written: the generic ReferenceMaintenancePanel toggles the
     * whole detail form editable at once, so there is no code-immutable-on-edit
     * path to honor here.
     */
    public boolean Update(Connection conn, DepartmentInfo dept) throws SQLException {
        String sql =
            "UPDATE Departments SET DepartmentCode = ?, DepartmentName = ? WHERE DepartmentID = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, dept.GetDepartmentCode());
            pstmt.setString(2, dept.GetDepartmentName());
            pstmt.setInt(3, dept.GetDepartmentId());
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * Delete — hard delete from the lookup table.
     * Fails with SQL error 547 if any employee still points at this DepartmentID;
     * DepartmentMaintenanceProcess maps that to DeleteOutcome.IN_USE.
     */
    public boolean Delete(Connection conn, int departmentId) throws SQLException {
        String sql = "DELETE FROM Departments WHERE DepartmentID = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, departmentId);
            return pstmt.executeUpdate() > 0;
        }
    }
}