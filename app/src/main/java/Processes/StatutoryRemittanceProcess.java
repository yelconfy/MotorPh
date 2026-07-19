package Processes;

import DataAccess.AuditLogDAO;
import DataAccess.StatutoryRemittanceDAO;
import Interface.IStatutoryRemittanceProcess;
import Objects.models.StatutoryRemittanceRow;
import java.sql.SQLException;
import java.util.List;

/**
 * Process backing the Statutory Remittance report module (SSS R-3 /
 * PhilHealth RF-1 / Pag-IBIG M1-1).
 *
 * Shared mechanics (degrading reads, reprint-aware print audit on a single
 * Connection) live in BaseReportProcess. This class supplies only the report's
 * DAO, its audit table name ("StatutoryRemittanceReport"), and its recordId
 * shape ("{agency}:{year}-{month}").
 */
public class StatutoryRemittanceProcess extends BaseReportProcess
    implements IStatutoryRemittanceProcess {

  private final StatutoryRemittanceDAO reportDAO;

  public StatutoryRemittanceProcess(
    StatutoryRemittanceDAO reportDAO,
    AuditLogDAO auditLogDAO
  ) {
    super("StatutoryRemittanceReport", auditLogDAO);
    this.reportDAO = reportDAO;
  }

  @Override
  public List<StatutoryRemittanceRow> GetForMonth(int year, int month) {
    return DegradingRead(
      "StatutoryRemittanceProcess.GetForMonth",
      () -> reportDAO.GetForMonth(year, month)
    );
  }

  @Override
  public List<int[]> GetAvailablePeriods() {
    return DegradingRead(
      "StatutoryRemittanceProcess.GetAvailablePeriods",
      reportDAO::GetAvailablePeriods
    );
  }

  @Override
  public boolean RecordPrint(
    String agency,
    int year,
    int month,
    String username,
    String reason
  ) throws SQLException {
    String recordId = agency + ":" + year + "-" + month;
    return LogPrint(recordId, username, reason);
  }
}