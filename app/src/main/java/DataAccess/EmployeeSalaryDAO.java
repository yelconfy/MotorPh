package DataAccess;

import Objects.models.EmployeeSalaryInfo;
import java.sql.*;
import java.time.LocalDate;

/**
 * DAO for EmployeeSalary (versioned) and vw_CurrentSalary.
 *
 * SCHEMA REALITY (02 - Core Employee Tables):
 *   EmployeeSalary: SalaryID, EmployeeID, BasicSalary, HourlyRate, EffectiveDate
 *
 * The old RiceSubsidy / PhoneAllowance / ClothingAllowance columns no longer
 * exist here — they are in Employee_Allowance.  Use AllowanceDAO for those.
 *
 * This table is VERSIONED (one row per rate change).  Insert always adds a new
 * row; the view vw_CurrentSalary resolves the latest effective row.
 * There is intentionally no UPDATE method — salary history must be preserved.
 */
public class EmployeeSalaryDAO {

    /**
     * Insert — adds a new versioned salary row for an employee.
     * Call this on new hire AND on every subsequent rate change.
     *
     * @param conn    shared transaction connection
     * @param empID   target EmployeeID
     * @param salary  object carrying BasicSalary + HourlyRate
     * @return true on success
     */
    public boolean Insert(Connection conn, long empID, EmployeeSalaryInfo salary)
            throws SQLException {

        String sql =
            "INSERT INTO EmployeeSalary (EmployeeID, BasicSalary, HourlyRate, EffectiveDate) " +
            "VALUES (?, ?, ?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, empID);
            pstmt.setDouble(2, salary.GetBasicSalary());
            pstmt.setDouble(3, salary.GetHourlyRate());

            LocalDate effective = salary.GetEffectiveDate();
            pstmt.setDate(4, java.sql.Date.valueOf(
                effective != null ? effective : LocalDate.now()
            ));

            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * GetCurrent — fetches the current effective salary row for an employee
     * by querying vw_CurrentSalary directly.
     * Returns null if no salary record exists yet.
     */
    public EmployeeSalaryInfo GetCurrent(Connection conn, long empID)
            throws SQLException {

        String sql = "SELECT * FROM vw_CurrentSalary WHERE EmployeeID = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, empID);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new EmployeeSalaryInfo(rs);
                }
            }
        }
        return null;
    }

    /**
     * GetHistory — returns the full salary history for an employee,
     * ordered newest first.  Useful for an audit/history screen.
     */
    public java.util.List<EmployeeSalaryInfo> GetHistory(Connection conn, long empID)
            throws SQLException {

        java.util.List<EmployeeSalaryInfo> history = new java.util.ArrayList<>();
        String sql =
            "SELECT * FROM EmployeeSalary WHERE EmployeeID = ? ORDER BY EffectiveDate DESC";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, empID);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    history.add(new EmployeeSalaryInfo(rs));
                }
            }
        }
        return history;
    }
}