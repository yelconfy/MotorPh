package DataAccess;

import Objects.models.EmpDetail;
import Objects.models.StatutoryInfo;
import java.sql.*;

/**
 * StatutoryDAO — CRUD for per-employee statutory ID NUMBERS.
 *
 *   StatutoryDetails (02 - Core Employee Tables):
 *     EmployeeID (PK), SssNo, PhilHealthNo, TinNo, PagIbigNo
 *
 * This is employee MASTER DATA — the government identifiers a specific employee
 * was issued. It is NOT the rate/bracket lookup: that is StatutoryRateDAO, which
 * reads SSS_Contribution_Table / Contribution_Rate / WithholdingTax_Table to
 * compute contribution amounts for payroll. Different table, different consumer
 * (EmpMgmtProcess vs PayrollProcess), different reason to change.
 *
 * Shared-Connection convention (like EmployeeSalaryDAO / EmployeeAddressesDao /
 * AllowanceDAO) so it participates in the EmpMgmtProcess transaction.
 */
public class StatutoryDAO {

  /** Insert the StatutoryDetails row for a (new) employee. */
  public boolean Insert(Connection conn, long empID, StatutoryInfo stat)
      throws SQLException {
    if (stat == null) stat = new StatutoryInfo();
    String sql =
      "INSERT INTO StatutoryDetails (EmployeeID, SssNo, PhilHealthNo, TinNo, PagIbigNo) " +
      "VALUES (?, ?, ?, ?, ?)";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setLong(1, empID);
      ps.setString(2, stat.GetSssNo());
      ps.setString(3, stat.GetPhilHealthNo());
      ps.setString(4, stat.GetTinNo());
      ps.setString(5, stat.GetPagIbigNo());
      return ps.executeUpdate() > 0;
    }
  }

  /** Update the StatutoryDetails row for an existing employee. */
  public boolean Update(Connection conn, long empID, StatutoryInfo stat)
      throws SQLException {
    if (stat == null) stat = new StatutoryInfo();
    String sql =
      "UPDATE StatutoryDetails SET SssNo = ?, PhilHealthNo = ?, TinNo = ?, PagIbigNo = ? " +
      "WHERE EmployeeID = ?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setString(1, stat.GetSssNo());
      ps.setString(2, stat.GetPhilHealthNo());
      ps.setString(3, stat.GetTinNo());
      ps.setString(4, stat.GetPagIbigNo());
      ps.setLong(5, empID);
      return ps.executeUpdate() > 0;
    }
  }

  /** GetByEmployeeID — the StatutoryDetails row, or null if none exists. */
  public StatutoryInfo GetByEmployeeID(Connection conn, long empID)
      throws SQLException {
    String sql = "SELECT * FROM StatutoryDetails WHERE EmployeeID = ?";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setLong(1, empID);
      try (ResultSet rs = ps.executeQuery()) {
        return rs.next() ? new StatutoryInfo(rs) : null;
      }
    }
  }

  /**
   * FillStatutoryDetails — hydration helper used by
   * EmpMgmtProcess.CompleteEmployee: loads the row and sets it onto the
   * EmpDetail. If no row exists, sets an empty StatutoryInfo so callers never
   * see null.
   */
  public void FillStatutoryDetails(Connection conn, EmpDetail emp)
      throws SQLException {
    StatutoryInfo stat = GetByEmployeeID(conn, emp.GetEmployeeId());
    emp.SetStatutory(stat != null ? stat : new StatutoryInfo());
  }
}