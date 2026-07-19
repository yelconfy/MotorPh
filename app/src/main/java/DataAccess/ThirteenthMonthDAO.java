package DataAccess;

import Objects.models.ThirteenthMonthRow;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only DAO over vw_ThirteenthMonth (script 17 - Reporting layer) — backs
 * the 13th Month Pay report screen.
 *
 *   vw_ThirteenthMonth: one row per employee per calendar year, with total
 *   basic earned, the count of cutoffs summed, and the 13th-month amount
 *   (total basic / 12).
 *
 * The view itself does all the joining/aggregating/filtering (Finalized + Paid
 * only), so this DAO never touches the base payroll tables directly. Follows the
 * shared-Connection convention: a conn-param method for callers in a
 * transaction, plus a self-opening overload for one-shot UI reads. Reads are
 * inherently read-only; no transaction needed.
 */
public class ThirteenthMonthDAO {

  /** All 13th-month rows for one year, employee order. */
  public List<ThirteenthMonthRow> GetReportForYear(Connection conn, int year)
    throws SQLException {
    List<ThirteenthMonthRow> list = new ArrayList<>();
    String sql =
      "SELECT EmployeeNo, EmployeeFullName, Position, Department, " +
      "       PayYear, TotalBasicEarned, PayslipsIncluded, ThirteenthMonthPay " +
      "FROM vw_ThirteenthMonth " +
      "WHERE PayYear = ? " +
      "ORDER BY EmployeeNo";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, year);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          list.add(new ThirteenthMonthRow(rs));
        }
      }
    }
    return list;
  }

  /** Self-opening overload for one-shot UI reads. */
  public List<ThirteenthMonthRow> GetReportForYear(int year) throws SQLException {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      return GetReportForYear(conn, year);
    }
  }

  /** Distinct years that have qualifying payslips, newest first — for the picker. */
  public List<Integer> GetAvailableYears(Connection conn) throws SQLException {
    List<Integer> years = new ArrayList<>();
    String sql =
      "SELECT DISTINCT PayYear FROM vw_ThirteenthMonth ORDER BY PayYear DESC";
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