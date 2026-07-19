package DataAccess;

import Objects.models.DeductionInfo;
import Objects.models.DeductionTypeInfo;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO for Employee_Deduction and Deduction_Type.
 *
 * Schema (04 - Leave & Compensation Tables):
 *   Employee_Deduction: EmployeeDeductionID, EmployeeID, DeductionTypeID, Amount, Status
 *   UNIQUE constraint on (EmployeeID, DeductionTypeID)
 *
 * Schema (01 - Reference Tables):
 *   Deduction_Type: DeductionTypeID, DeductionName, Category, Status
 *
 * Parallel to AllowanceDAO — same upsert pattern.
 * Handles per-employee VOLUNTARY/recurring deductions.
 * Statutory amounts are computed dynamically — do NOT store them here.
 *
 * BKL-01 stage 3b: write methods for Deduction_Type (Insert / Update / Delete /
 * IsInUse) added so the Deduction Type Maintenance module can drive it through
 * DeductionTypeMaintenanceProcess. Unlike Departments/Positions, Deduction_Type
 * has its own Status column (GetAllTypes already filters on it), so Delete here
 * is a soft delete, not a hard DELETE guarded by an FK exception. IsInUse is the
 * explicit guard that replaces what SQL error 547 would have caught for free on
 * a hard delete — see DeductionTypeMaintenanceProcess.Delete for how the two
 * combine into a SaveResult.
 */
public class DeductionDAO {

    // =========================================================================
    // Deduction_Type reference
    // =========================================================================

    /** All active deduction types for dropdowns / lookup. */
    public List<DeductionTypeInfo> GetAllTypes(Connection conn) throws SQLException {
        List<DeductionTypeInfo> list = new ArrayList<>();
        String sql = "SELECT * FROM Deduction_Type WHERE Status = 1 ORDER BY DeductionName";

        try (Statement stmt = conn.createStatement();
             ResultSet rs   = stmt.executeQuery(sql)) {
            while (rs.next()) list.add(new DeductionTypeInfo(rs));
        }
        return list;
    }

    /**
     * GetDeductionTypeId — name → ID lookup.
     * Returns -1 if not found.
     */
    public int GetDeductionTypeId(Connection conn, String deductionName)
            throws SQLException {

        String sql = "SELECT DeductionTypeID FROM Deduction_Type WHERE DeductionName = ? AND Status = 1";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, deductionName);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() ? rs.getInt("DeductionTypeID") : -1;
            }
        }
    }

    /**
     * Insert — add a new deduction type (Deduction Type Maintenance / Add).
     * Status defaults to 1 (active) at the schema level.
     */
    public boolean Insert(Connection conn, DeductionTypeInfo dt) throws SQLException {
        String sql = "INSERT INTO Deduction_Type (DeductionName, Category) VALUES (?, ?)";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, dt.GetDeductionName());
            pstmt.setInt(2, dt.GetCategory().getValue());
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * Update — rename / recategorize an existing deduction type
     * (Deduction Type Maintenance / Edit). The four statutory rows never reach
     * this call — ReferenceMaintenancePanel blocks Edit on them via
     * MaintenanceDescriptor.protectedWhen before Accept can fire.
     */
    public boolean Update(Connection conn, DeductionTypeInfo dt) throws SQLException {
        String sql =
            "UPDATE Deduction_Type SET DeductionName = ?, Category = ? WHERE DeductionTypeID = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, dt.GetDeductionName());
            pstmt.setInt(2, dt.GetCategory().getValue());
            pstmt.setInt(3, dt.GetDeductionTypeId());
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * IsInUse — true if any employee currently has an active standing deduction
     * or an active loan against this type. Unlike Departments/Positions, a soft
     * delete here never trips a real FK exception, so this is the explicit
     * stand-in for that guard: PayrollProcess and the loan ledger both key off
     * Deduction_Type staying meaningful for any row an employee is actively
     * drawing against. Payroll_Deduction (historical, already-generated
     * payslips) and Contribution_Rate (statutory-only, and those rows never
     * reach Delete at all — see protectedWhen) are deliberately NOT checked.
     */
    public boolean IsInUse(Connection conn, int deductionTypeId) throws SQLException {
        String sql =
            "SELECT CASE WHEN EXISTS (" +
            "    SELECT 1 FROM Employee_Deduction WHERE DeductionTypeID = ? AND Status = 1" +
            ") OR EXISTS (" +
            "    SELECT 1 FROM Employee_Loan WHERE DeductionTypeID = ? AND Status = 0" +
            ") THEN 1 ELSE 0 END";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, deductionTypeId);
            pstmt.setInt(2, deductionTypeId);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    /**
     * Delete — soft delete (Deduction_Type has its own Status column, unlike
     * Departments/Positions). Caller (DeductionTypeMaintenanceProcess) is
     * responsible for checking IsInUse first — this method does not guard
     * itself, matching how DepartmentDAO.Delete leaves the FK check to the DB
     * and DepartmentMaintenanceProcess to interpret the exception.
     */
    public boolean Delete(Connection conn, int deductionTypeId) throws SQLException {
        String sql = "UPDATE Deduction_Type SET Status = 0 WHERE DeductionTypeID = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, deductionTypeId);
            return pstmt.executeUpdate() > 0;
        }
    }

    // =========================================================================
    // Employee_Deduction CRUD
    // =========================================================================

    /**
     * GetByEmployeeID — all active per-employee deductions, joined with type info.
     */
    public List<DeductionInfo> GetByEmployeeID(Connection conn, long empID)
            throws SQLException {

        List<DeductionInfo> list = new ArrayList<>();
        String sql =
            "SELECT ed.EmployeeDeductionID, ed.EmployeeID, ed.DeductionTypeID, " +
            "       ed.Amount, ed.Status, " +
            "       dt.DeductionName, dt.Category " +
            "FROM Employee_Deduction ed " +
            "JOIN Deduction_Type dt ON dt.DeductionTypeID = ed.DeductionTypeID " +
            "WHERE ed.EmployeeID = ? AND ed.Status = 1 " +
            "ORDER BY dt.DeductionName";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, empID);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) list.add(new DeductionInfo(rs));
            }
        }
        return list;
    }

    /**
     * Upsert — inserts a new deduction row or updates Amount if
     * the (EmployeeID, DeductionTypeID) row already exists.
     */
    public boolean Upsert(Connection conn, long empID, int deductionTypeId, double amount)
            throws SQLException {

        String checkSql =
            "SELECT EmployeeDeductionID FROM Employee_Deduction " +
            "WHERE EmployeeID = ? AND DeductionTypeID = ?";

        try (PreparedStatement check = conn.prepareStatement(checkSql)) {
            check.setLong(1, empID);
            check.setInt(2, deductionTypeId);

            try (ResultSet rs = check.executeQuery()) {
                if (rs.next()) {
                    String updateSql =
                        "UPDATE Employee_Deduction SET Amount = ?, Status = 1 " +
                        "WHERE EmployeeID = ? AND DeductionTypeID = ?";
                    try (PreparedStatement upd = conn.prepareStatement(updateSql)) {
                        upd.setDouble(1, amount);
                        upd.setLong(2, empID);
                        upd.setInt(3, deductionTypeId);
                        return upd.executeUpdate() > 0;
                    }
                } else {
                    String insertSql =
                        "INSERT INTO Employee_Deduction (EmployeeID, DeductionTypeID, Amount, Status) " +
                        "VALUES (?, ?, ?, 1)";
                    try (PreparedStatement ins = conn.prepareStatement(insertSql)) {
                        ins.setLong(1, empID);
                        ins.setInt(2, deductionTypeId);
                        ins.setDouble(3, amount);
                        return ins.executeUpdate() > 0;
                    }
                }
            }
        }
    }

    /** Deactivate — soft-deletes a deduction row. */
    public boolean Deactivate(Connection conn, long empID, int deductionTypeId)
            throws SQLException {

        String sql =
            "UPDATE Employee_Deduction SET Status = 0 " +
            "WHERE EmployeeID = ? AND DeductionTypeID = ?";

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, empID);
            pstmt.setInt(2, deductionTypeId);
            return pstmt.executeUpdate() > 0;
        }
    }
}