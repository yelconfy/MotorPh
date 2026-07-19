package DataAccess;

import Objects.models.Bir2316Row;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only DAO over vw_Bir2316 (script 17 - Reporting layer). Backs the BIR
 * Form 2316 certificate screen: a list of employees for a chosen year (each row
 * is one employee's full annual tax summary), plus the year picker feed.
 *
 * The view does all aggregation and the TRAIN tax-due computation, so this DAO
 * is a plain read. Same shared-Connection convention as the other report DAOs.
 */
public class Bir2316DAO {

  /** All 2316 rows for one year, employee order. */
  public List<Bir2316Row> GetForYear(Connection conn, int year) throws SQLException {
    List<Bir2316Row> list = new ArrayList<>();
    String sql =
      "SELECT EmployeeNo, EmployeeFullName, TIN, Position, RegisteredAddress, PayYear, " +
      "       GrossCompensation, TaxableAllowances, NonTaxableAllowances, " +
      "       SssContribution, PhilHealthContribution, PagIbigContribution, MandatoryContributions, " +
      "       ThirteenthMonthPay, ThirteenthMonthNonTaxable, ThirteenthMonthTaxable, " +
      "       TaxableCompensation, TaxWithheld, TaxDue, OverUnderWithheld " +
      "FROM vw_Bir2316 " +
      "WHERE PayYear = ? " +
      "ORDER BY EmployeeNo";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, year);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          list.add(new Bir2316Row(rs));
        }
      }
    }
    return list;
  }

  /** Self-opening overload for one-shot UI reads. */
  public List<Bir2316Row> GetForYear(int year) throws SQLException {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      return GetForYear(conn, year);
    }
  }

  /** Distinct years with data, newest first — for the picker. */
  public List<Integer> GetAvailableYears(Connection conn) throws SQLException {
    List<Integer> years = new ArrayList<>();
    String sql = "SELECT DISTINCT PayYear FROM vw_Bir2316 ORDER BY PayYear DESC";
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