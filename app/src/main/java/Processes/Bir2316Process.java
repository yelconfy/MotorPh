package Processes;

import DataAccess.AuditLogDAO;
import DataAccess.Bir2316DAO;
import Interface.IBir2316Process;
import Objects.models.Bir2316Row;
import java.sql.SQLException;
import java.util.List;

/**
 * Process backing the BIR Form 2316 certificate module.
 *
 * Shared mechanics (degrading reads, reprint-aware print audit on a single
 * Connection) live in BaseReportProcess. This class supplies only the report's
 * DAO, its audit table name ("Bir2316Report"), and its recordId shape
 * ("{employeeNo}:{year}").
 */
public class Bir2316Process extends BaseReportProcess implements IBir2316Process {

  private final Bir2316DAO reportDAO;

  public Bir2316Process(Bir2316DAO reportDAO, AuditLogDAO auditLogDAO) {
    super("Bir2316Report", auditLogDAO);
    this.reportDAO = reportDAO;
  }

  @Override
  public List<Bir2316Row> GetForYear(int year) {
    return DegradingRead(
      "Bir2316Process.GetForYear",
      () -> reportDAO.GetForYear(year)
    );
  }

  @Override
  public List<Integer> GetAvailableYears() {
    return DegradingRead(
      "Bir2316Process.GetAvailableYears",
      reportDAO::GetAvailableYears
    );
  }

  @Override
  public boolean RecordPrint(long employeeNo, int year, String username, String reason)
      throws SQLException {
    String recordId = employeeNo + ":" + year;
    return LogPrint(recordId, username, reason);
  }
}