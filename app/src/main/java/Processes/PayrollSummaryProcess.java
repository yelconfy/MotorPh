package Processes;

import DataAccess.AuditLogDAO;
import DataAccess.DatabaseConnector;
import DataAccess.PayrollSummaryDAO;
import Interface.IPayrollSummaryProcess;
import Objects.enums.Status.AuditAction;
import Objects.models.PayPeriodOption;
import Objects.models.PayrollSummaryRow;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Process backing the Payroll Summary Report module.
 *
 *   - reads (GetSummaryForPeriod / GetAvailablePeriods): self-open and degrade
 *     to an empty list on failure, matching the other read-only screens;
 *   - audit (RecordPrint): one Connection for the count+write, exactly like
 *     PayslipPrintProcess — a print whose prior PRINT count is > 0 is a reprint.
 *
 * The audit RecordID is the period key "YYYY-MM" against a logical
 * "PayrollSummaryReport" table name (Audit_Log.RecordID is a generic NVARCHAR
 * key, so no FK is needed).
 */
public class PayrollSummaryProcess implements IPayrollSummaryProcess {

  private static final String AUDIT_TABLE = "PayrollSummaryReport";

  private final PayrollSummaryDAO summaryDAO;
  private final AuditLogDAO auditLogDAO;

  public PayrollSummaryProcess(PayrollSummaryDAO summaryDAO, AuditLogDAO auditLogDAO) {
    this.summaryDAO = summaryDAO;
    this.auditLogDAO = auditLogDAO;
  }

  @Override
  public List<PayrollSummaryRow> GetSummaryForPeriod(int year, int month) {
    try {
      return summaryDAO.GetSummaryForPeriod(year, month);
    } catch (SQLException e) {
      System.err.println("PayrollSummaryProcess.GetSummaryForPeriod: " + e.getMessage());
      return Collections.emptyList();
    }
  }

  @Override
  public List<PayPeriodOption> GetAvailablePeriods() {
    try {
      return summaryDAO.GetAvailablePeriods();
    } catch (SQLException e) {
      System.err.println("PayrollSummaryProcess.GetAvailablePeriods: " + e.getMessage());
      return Collections.emptyList();
    }
  }

  @Override
  public boolean RecordPrint(int year, int month, String username, String reason)
    throws SQLException {
    String recordId = year + "-" + String.format("%02d", month);
    try (Connection conn = DatabaseConnector.GetConnection()) {
      boolean isReprint =
        auditLogDAO.CountActions(conn, AUDIT_TABLE, recordId, AuditAction.PRINT) > 0;
      auditLogDAO.Log(
        conn, username, AUDIT_TABLE, recordId, AuditAction.PRINT, null, reason
      );
      return isReprint;
    }
  }
}