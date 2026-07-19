package DataAccess;

import Objects.models.StatutoryRemittanceRow;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only DAO over vw_StatutoryRemittance (script 17 - Reporting layer).
 * Backs the three government remittance reports (SSS R-3, PhilHealth RF-1,
 * Pag-IBIG M1-1), which all read one month of rows and lay them out per agency.
 *
 * The view does the aggregation and employer-share derivation, so this DAO is
 * a plain read. Same shared-Connection convention as the other report DAOs:
 * conn-param method for a transaction, self-opening overload for one-shot UI.
 */
public class StatutoryRemittanceDAO {

  /** All remittance rows for one year+month, employee order. */
  public List<StatutoryRemittanceRow> GetForMonth(Connection conn, int year, int month)
    throws SQLException {
    List<StatutoryRemittanceRow> list = new ArrayList<>();
    String sql =
      "SELECT EmployeeNo, EmployeeFullName, SssNo, PhilHealthNo, PagIbigNo, " +
      "       PayYear, PayMonth, " +
      "       SssEmployeeShare, SssEmployerShare, SssTotal, " +
      "       PhicEmployeeShare, PhicEmployerShare, PhicTotal, " +
      "       HdmfEmployeeShare, HdmfEmployerShare, HdmfTotal " +
      "FROM vw_StatutoryRemittance " +
      "WHERE PayYear = ? AND PayMonth = ? " +
      "ORDER BY EmployeeNo";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, year);
      ps.setInt(2, month);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          list.add(new StatutoryRemittanceRow(rs));
        }
      }
    }
    return list;
  }

  /** Self-opening overload for one-shot UI reads. */
  public List<StatutoryRemittanceRow> GetForMonth(int year, int month) throws SQLException {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      return GetForMonth(conn, year, month);
    }
  }

  /**
   * Distinct (year, month) periods that have remittance data, newest first —
   * for the picker. Returned as int[]{year, month} pairs.
   */
  public List<int[]> GetAvailablePeriods(Connection conn) throws SQLException {
    List<int[]> periods = new ArrayList<>();
    String sql =
      "SELECT DISTINCT PayYear, PayMonth FROM vw_StatutoryRemittance " +
      "ORDER BY PayYear DESC, PayMonth DESC";
    try (
      Statement stmt = conn.createStatement();
      ResultSet rs = stmt.executeQuery(sql)
    ) {
      while (rs.next()) {
        periods.add(new int[] { rs.getInt("PayYear"), rs.getInt("PayMonth") });
      }
    }
    return periods;
  }

  /** Self-opening overload for one-shot UI reads. */
  public List<int[]> GetAvailablePeriods() throws SQLException {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      return GetAvailablePeriods(conn);
    }
  }
}