package DataAccess;

import Objects.models.LeaveBalanceRow;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only DAO over vw_LeaveBalanceReport (script 17). Backs the Leave Balance
 * Report screen. Same shared-Connection convention as the other report DAOs.
 */
public class LeaveBalanceReportDAO {

  /** All leave-balance rows for one year, employee then leave-type order. */
  public List<LeaveBalanceRow> GetForYear(Connection conn, int year) throws SQLException {
    List<LeaveBalanceRow> list = new ArrayList<>();
    String sql =
      "SELECT EmployeeNo, EmployeeFullName, Department, LeaveType, PayYear, " +
      "       EntitledDays, UsedDays, RemainingDays " +
      "FROM vw_LeaveBalanceReport " +
      "WHERE PayYear = ? " +
      "ORDER BY EmployeeNo, LeaveType";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, year);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          list.add(new LeaveBalanceRow(rs));
        }
      }
    }
    return list;
  }

  /** Self-opening overload for one-shot UI reads. */
  public List<LeaveBalanceRow> GetForYear(int year) throws SQLException {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      return GetForYear(conn, year);
    }
  }

  /** Distinct years with data, newest first — for the picker. */
  public List<Integer> GetAvailableYears(Connection conn) throws SQLException {
    List<Integer> years = new ArrayList<>();
    String sql = "SELECT DISTINCT PayYear FROM vw_LeaveBalanceReport ORDER BY PayYear DESC";
    try (
      Statement stmt = conn.createStatement();
      ResultSet rs = stmt.executeQuery(sql)
    ) {
      while (rs.next()) {
        years.add(rs.getInt("PayYear"));
      }
    }
    return years;
  }

  /** Self-opening overload for one-shot UI reads. */
  public List<Integer> GetAvailableYears() throws SQLException {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      return GetAvailableYears(conn);
    }
  }
}