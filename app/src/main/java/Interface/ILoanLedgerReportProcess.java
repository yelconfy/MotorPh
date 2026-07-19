package Interface;

import Objects.models.LoanLedgerRow;
import java.sql.SQLException;
import java.util.List;

/**
 * Contract for the Loan Ledger Report screen — read-only access to
 * vw_LoanLedgerReport (script 17) plus a reprint-aware print-audit write.
 */
public interface ILoanLedgerReportProcess {

  /** Loan rows, optionally filtered by status code (null = all statuses). */
  List<LoanLedgerRow> GetLoans(Integer statusCode);

  /** Records a report PDF export to the audit trail; true if a prior export exists (reprint). */
  boolean RecordPrint(String scope, String username, String reason) throws SQLException;
}