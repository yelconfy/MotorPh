package Processes;

import DataAccess.AuditLogDAO;
import DataAccess.DatabaseConnector;
import DataAccess.LoanLedgerReportDAO;
import Interface.ILoanLedgerReportProcess;
import Objects.enums.Status.AuditAction;
import Objects.models.LoanLedgerRow;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Process backing the Loan Ledger Report module. Reads degrade to an empty list
 * on failure; RecordPrint audits each export on one Connection, reprint-aware.
 * Audit RecordID is the status scope ("ALL"/"Active"/...), so a reprint is
 * per-filter.
 */
public class LoanLedgerReportProcess implements ILoanLedgerReportProcess {

  private static final String AUDIT_TABLE = "LoanLedgerReport";

  private final LoanLedgerReportDAO reportDAO;
  private final AuditLogDAO auditLogDAO;

  public LoanLedgerReportProcess(LoanLedgerReportDAO reportDAO, AuditLogDAO auditLogDAO) {
    this.reportDAO = reportDAO;
    this.auditLogDAO = auditLogDAO;
  }

  @Override
  public List<LoanLedgerRow> GetLoans(Integer statusCode) {
    try {
      return reportDAO.GetLoans(statusCode);
    } catch (SQLException e) {
      System.err.println("LoanLedgerReportProcess.GetLoans: " + e.getMessage());
      return Collections.emptyList();
    }
  }

  @Override
  public boolean RecordPrint(String scope, String username, String reason) throws SQLException {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      boolean isReprint =
        auditLogDAO.CountActions(conn, AUDIT_TABLE, scope, AuditAction.PRINT) > 0;
      auditLogDAO.Log(conn, username, AUDIT_TABLE, scope, AuditAction.PRINT, null, reason);
      return isReprint;
    }
  }
}