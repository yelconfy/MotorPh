package DataAccess;

import Objects.models.AllowanceInfo;
import Objects.models.AllowanceTypeInfo;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * DAO for Employee_Allowance (04 - Leave & Compensation Tables), joined with
 * Allowance_Type (01 - Reference Tables) to resolve the allowance name.
 *
 *   Employee_Allowance: EmployeeAllowanceID, EmployeeID, AllowanceTypeID, Amount, Status
 *   Allowance_Type     : AllowanceTypeID, AllowanceName, IsTaxable, IsRecurring, Status
 *
 * This DAO replaces the old RiceSubsidy / PhoneAllowance / ClothingAllowance
 * columns that used to live on EmployeeSalary. Per-employee allowance rows are
 * loaded on demand and stored in EmpDetail.Allowances.
 *
 * Follows the shared-Connection convention of the other employee-detail DAOs
 * (EmployeeSalaryDAO, StatutoryDAO, EmployeeAddressesDao) so it participates in
 * the EmpMgmtProcess transaction.
 *
 * AllowanceInfo(rs) reads: EmployeeAllowanceID, EmployeeID, AllowanceTypeID,
 * AllowanceName, Amount, IsTaxable, IsRecurring, Status (confirmed against the
 * model). The JOIN below exposes exactly those columns — IsTaxable / IsRecurring
 * come from Allowance_Type, the rest from Employee_Allowance.
 *
 * BKL-01 stage 3b: reference-table write methods for Allowance_Type
 * (GetAllTypes / Insert / Update / Delete / IsInUse) added so the Allowance
 * Type Maintenance module can drive it through AllowanceTypeMaintenanceProcess.
 * Like Deduction_Type (and unlike Departments/Positions), Allowance_Type has
 * its own Status column, so Delete is a soft delete and IsInUse is the explicit
 * guard that stands in for the FK exception a hard delete would have thrown.
 * These sit alongside the existing per-employee methods, which are unchanged.
 */
public class AllowanceDAO {

  // =========================================================================
  // Allowance_Type reference (BKL-01 stage 3b)
  // =========================================================================

  /**
   * GetAllTypes — all active allowance types as standalone models, for the
   * Allowance Type Maintenance grid. Distinct from GetTypeIdsByName below,
   * which returns only a name->id map for the employee-save write path and
   * predates the AllowanceTypeInfo model.
   */
  public List<AllowanceTypeInfo> GetAllTypes(Connection conn) throws SQLException {
    List<AllowanceTypeInfo> list = new ArrayList<>();
    String sql = "SELECT * FROM Allowance_Type WHERE Status = 1 ORDER BY AllowanceName";

    try (Statement stmt = conn.createStatement();
         ResultSet rs   = stmt.executeQuery(sql)) {
      while (rs.next()) list.add(new AllowanceTypeInfo(rs));
    }
    return list;
  }

  /**
   * Insert — add a new allowance type (Allowance Type Maintenance / Add).
   * Status defaults to 1 (active) at the schema level.
   */
  public boolean Insert(Connection conn, AllowanceTypeInfo at) throws SQLException {
    String sql =
      "INSERT INTO Allowance_Type (AllowanceName, IsTaxable, IsRecurring) VALUES (?, ?, ?)";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, at.GetAllowanceName());
      pstmt.setBoolean(2, at.IsTaxable());
      pstmt.setBoolean(3, at.IsRecurring());
      return pstmt.executeUpdate() > 0;
    }
  }

  /**
   * Update — rename / retoggle an existing allowance type (Allowance Type
   * Maintenance / Edit). The three seeded rows never reach this call —
   * ReferenceMaintenancePanel blocks Edit on them via
   * MaintenanceDescriptor.protectedWhen before Accept can fire (see MPH-48).
   */
  public boolean Update(Connection conn, AllowanceTypeInfo at) throws SQLException {
    String sql =
      "UPDATE Allowance_Type SET AllowanceName = ?, IsTaxable = ?, IsRecurring = ? " +
      "WHERE AllowanceTypeID = ?";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setString(1, at.GetAllowanceName());
      pstmt.setBoolean(2, at.IsTaxable());
      pstmt.setBoolean(3, at.IsRecurring());
      pstmt.setInt(4, at.GetAllowanceTypeId());
      return pstmt.executeUpdate() > 0;
    }
  }

  /**
   * IsInUse — true if any employee currently has an active allowance of this
   * type. A soft delete here never trips a real FK exception, so this is the
   * explicit stand-in for that guard, mirroring DeductionDAO.IsInUse. Only
   * active (Status = 1) Employee_Allowance rows count — a deactivated
   * assignment shouldn't keep the type undeletable.
   */
  public boolean IsInUse(Connection conn, int allowanceTypeId) throws SQLException {
    String sql =
      "SELECT CASE WHEN EXISTS (" +
      "    SELECT 1 FROM Employee_Allowance WHERE AllowanceTypeID = ? AND Status = 1" +
      ") THEN 1 ELSE 0 END";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setInt(1, allowanceTypeId);
      try (ResultSet rs = pstmt.executeQuery()) {
        return rs.next() && rs.getBoolean(1);
      }
    }
  }

  /**
   * Delete — soft delete (Allowance_Type has its own Status column). Caller
   * (AllowanceTypeMaintenanceProcess) checks IsInUse first — this method does
   * not guard itself, matching DeductionDAO.Delete.
   */
  public boolean Delete(Connection conn, int allowanceTypeId) throws SQLException {
    String sql = "UPDATE Allowance_Type SET Status = 0 WHERE AllowanceTypeID = ?";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setInt(1, allowanceTypeId);
      return pstmt.executeUpdate() > 0;
    }
  }

  // =========================================================================
  // Employee_Allowance CRUD (unchanged)
  // =========================================================================

  /**
   * GetByEmployeeID — returns the active allowance rows for one employee.
   * Used by EmpMgmtProcess to hydrate EmpDetail.Allowances, which PayrollForm
   * then reads via EmpDetail.GetAllowanceAmount("Rice Subsidy") etc.
   *
   * Only Status = 1 rows are returned (active allowances), matching the
   * IsActive() filter already applied in EmpDetail.GetAllowanceAmount.
   */
  public List<AllowanceInfo> GetByEmployeeID(Connection conn, long empID)
    throws SQLException {
    List<AllowanceInfo> list = new ArrayList<>();
    String sql =
      "SELECT ea.EmployeeAllowanceID, ea.EmployeeID, ea.AllowanceTypeID, " +
      "       t.AllowanceName, ea.Amount, t.IsTaxable, t.IsRecurring, ea.Status " +
      "FROM Employee_Allowance ea " +
      "JOIN Allowance_Type t ON t.AllowanceTypeID = ea.AllowanceTypeID " +
      "WHERE ea.EmployeeID = ? AND ea.Status = 1 " +
      "ORDER BY t.AllowanceName";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, empID);
      try (ResultSet rs = pstmt.executeQuery()) {
        while (rs.next()) {
          list.add(new AllowanceInfo(rs));
        }
      }
    }
    return list;
  }

  /**
   * Upsert — inserts a new allowance amount or updates the existing one for
   * the (EmployeeID, AllowanceTypeID) pair, respecting UQ_EmployeeAllowance.
   *
   * Provided for the add/update-employee flow. Kept parameter-driven (IDs +
   * amount) rather than taking an AllowanceInfo, so it does not depend on the
   * model's writable getters.
   */
  public boolean Upsert(
    Connection conn,
    long empID,
    int allowanceTypeID,
    double amount
  ) throws SQLException {
    String sql =
      "MERGE Employee_Allowance AS target " +
      "USING (SELECT ? AS EmployeeID, ? AS AllowanceTypeID) AS src " +
      "   ON target.EmployeeID = src.EmployeeID " +
      "  AND target.AllowanceTypeID = src.AllowanceTypeID " +
      "WHEN MATCHED THEN " +
      "   UPDATE SET Amount = ?, Status = 1 " +
      "WHEN NOT MATCHED THEN " +
      "   INSERT (EmployeeID, AllowanceTypeID, Amount, Status) " +
      "   VALUES (src.EmployeeID, src.AllowanceTypeID, ?, 1);";

    try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
      pstmt.setLong(1, empID);
      pstmt.setInt(2, allowanceTypeID);
      pstmt.setDouble(3, amount);
      pstmt.setDouble(4, amount);
      return pstmt.executeUpdate() > 0;
    }
  }

  /**
   * GetTypeIdsByName — name -> AllowanceTypeID for the active allowance types,
   * loaded once per save so the write path can resolve editor-entered rows
   * (name + amount, no ID) to their AllowanceTypeID.
   *
   * Case-insensitive map, matching EmpDetail.GetAllowanceAmount's lookup.
   *
   * Retained alongside the new GetAllTypes: this returns only the name->id map
   * the employee-save path needs, avoiding constructing full AllowanceTypeInfo
   * models on the hot save path.
   */
  public Map<String, Integer> GetTypeIdsByName(Connection conn)
    throws SQLException {
    Map<String, Integer> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    String sql =
      "SELECT AllowanceTypeID, AllowanceName FROM Allowance_Type WHERE Status = 1";
    try (
      Statement stmt = conn.createStatement();
      ResultSet rs = stmt.executeQuery(sql)
    ) {
      while (rs.next()) {
        map.put(rs.getString("AllowanceName"), rs.getInt("AllowanceTypeID"));
      }
    }
    return map;
  }
}