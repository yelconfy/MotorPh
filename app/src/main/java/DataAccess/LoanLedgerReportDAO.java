package DataAccess;

import Objects.models.LoanLedgerRow;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only DAO over vw_LoanLedgerReport (script 17). Backs the Loan Ledger
 * Report screen. Loans are not year-scoped, so the filter is loan status
 * (null = all statuses). Same shared-Connection convention as the other DAOs.
 */
public class LoanLedgerReportDAO {

  /** Loan rows, optionally filtered by status code (null = all), employee order. */
  public List<LoanLedgerRow> GetLoans(Connection conn, Integer statusCode)
    throws SQLException {
    List<LoanLedgerRow> list = new ArrayList<>();
    StringBuilder sql = new StringBuilder(
      "SELECT LoanID, EmployeeNo, EmployeeFullName, Department, LoanType, " +
      "       Principal, TotalPayable, AmountPaid, OutstandingBalance, " +
      "       Installment, Terms, StartDate, StatusCode, StatusLabel " +
      "FROM vw_LoanLedgerReport "
    );
    if (statusCode != null) {
      sql.append("WHERE StatusCode = ? ");
    }
    sql.append("ORDER BY EmployeeNo, LoanID");

    try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
      if (statusCode != null) {
        ps.setInt(1, statusCode);
      }
      try (ResultSet rs = ps.executeQuery()) {
        while (rs.next()) {
          list.add(new LoanLedgerRow(rs));
        }
      }
    }
    return list;
  }

  /** Self-opening overload for one-shot UI reads. */
  public List<LoanLedgerRow> GetLoans(Integer statusCode) throws SQLException {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      return GetLoans(conn, statusCode);
    }
  }
}