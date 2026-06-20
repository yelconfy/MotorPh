package DataAccess;

import Objects.models.AllowanceInfo;
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
 */
public class AllowanceDAO {

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
   * Resolved to a Map directly (rather than a List<TypeInfo> like
   * DeductionDAO.GetAllTypes) because allowance types have no standalone model —
   * they live embedded in AllowanceInfo via the GetByEmployeeID join.
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
