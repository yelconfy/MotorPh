package Processes;

import DataAccess.AuditLogDAO;
import DataAccess.DatabaseConnector;
import DataAccess.LeaveBalanceReportDAO;
import Interface.ILeaveBalanceReportProcess;
import Objects.enums.Status.AuditAction;
import Objects.models.LeaveBalanceRow;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Process backing the Leave Balance Report module. Reads degrade to an empty
 * list on failure; RecordPrint audits each export on one Connection,
 * reprint-aware, matching the other report processes.
 */
public class LeaveBalanceReportProcess implements ILeaveBalanceReportProcess {

  private static final String AUDIT_TABLE = "LeaveBalanceReport";

  private final LeaveBalanceReportDAO reportDAO;
  private final AuditLogDAO auditLogDAO;

  public LeaveBalanceReportProcess(LeaveBalanceReportDAO reportDAO, AuditLogDAO auditLogDAO) {
    this.reportDAO = reportDAO;
    this.auditLogDAO = auditLogDAO;
  }

  @Override
  public List<LeaveBalanceRow> GetForYear(int year) {
    try {
      return reportDAO.GetForYear(year);
    } catch (SQLException e) {
      System.err.println("LeaveBalanceReportProcess.GetForYear: " + e.getMessage());
      return Collections.emptyList();
    }
  }

  @Override
  public List<Integer> GetAvailableYears() {
    try {
      return reportDAO.GetAvailableYears();
    } catch (SQLException e) {
      System.err.println("LeaveBalanceReportProcess.GetAvailableYears: " + e.getMessage());
      return Collections.emptyList();
    }
  }

  @Override
  public boolean RecordPrint(int year, String username, String reason) throws SQLException {
    String recordId = String.valueOf(year);
    try (Connection conn = DatabaseConnector.GetConnection()) {
      boolean isReprint =
        auditLogDAO.CountActions(conn, AUDIT_TABLE, recordId, AuditAction.PRINT) > 0;
      auditLogDAO.Log(conn, username, AUDIT_TABLE, recordId, AuditAction.PRINT, null, reason);
      return isReprint;
    }
  }
}