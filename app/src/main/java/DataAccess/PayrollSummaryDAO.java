package DataAccess;

import Objects.models.PayPeriodOption;
import Objects.models.PayrollSummaryRow;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only DAO over vw_MonthlyPayrollSummary (script 17 - Reporting layer) —
 * backs the Payroll Summary Report screen.
 *
 *   vw_MonthlyPayrollSummary: one row per employee per calendar month, with
 *   statutory numbers, per-statutory contribution amounts, gross and net pay.
 *
 * The view itself does all the joining/aggregating, so this DAO never touches
 * the base payroll tables directly. Follows the shared-Connection convention:
 * a conn-param method for callers in a transaction, plus a self-opening overload
 * for one-shot UI reads. Reads are inherently read-only; no transaction needed.
 */
public class PayrollSummaryDAO {

  /** All summary rows for one (year, month), employee order. */
  public List<PayrollSummaryRow> GetSummaryForPeriod(Connection conn, int year, int month)
    throws SQLException {
    List<PayrollSummaryRow> list = new ArrayList<>();
    String sql =
      "SELECT EmployeeNo, EmployeeFullName, Position, Department, " +
      "       PayYear, PayMonth, PayMonthName, PeriodStart, PeriodEnd, PayslipsIncluded, " +
      "       SocialSecurityNo, PhilHealthNo, PagIbigNo, TIN, " +
      "       GrossIncome, SocialSecurityContribution, PhilHealthContribution, " +
      "       PagIbigContribution, WithholdingTax, NetPay " +
      "FROM vw_MonthlyPayrollSummary " +
      "WHERE PayYear = ? AND PayMonth = ? " +
      "ORDER BY EmployeeNo";
    try (PreparedStatement ps = conn.prepareStatement(sql)) {
      ps.setInt(1, year);
      ps.setInt(2, month);
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          list.add(new PayrollSummaryRow(rs));
        }
      }
    }
    return list;
  }

  /** Self-opening overload for one-shot UI reads. */
  public List<PayrollSummaryRow> GetSummaryForPeriod(int year, int month)
    throws SQLException {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      return GetSummaryForPeriod(conn, year, month);
    }
  }

  /** Distinct (year, month) periods that have payslips, newest first — for the picker. */
  public List<PayPeriodOption> GetAvailablePeriods(Connection conn) throws SQLException {
    List<PayPeriodOption> list = new ArrayList<>();
    String sql =
      "SELECT DISTINCT PayYear, PayMonth, PayMonthName " +
      "FROM vw_MonthlyPayrollSummary " +
      "ORDER BY PayYear DESC, PayMonth DESC";
    try (PreparedStatement ps = conn.prepareStatement(sql);
         ResultSet rs = ps.executeQuery()) {
      while (rs.next()) {
        list.add(new PayPeriodOption(rs));
      }
    }
    return list;
  }

  /** Self-opening overload for one-shot UI reads. */
  public List<PayPeriodOption> GetAvailablePeriods() throws SQLException {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      return GetAvailablePeriods(conn);
    }
  }
}