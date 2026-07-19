package Processes;

import DataAccess.AuditLogDAO;
import DataAccess.ThirteenthMonthDAO;
import Interface.IThirteenthMonthProcess;
import Objects.models.ThirteenthMonthRow;
import java.sql.SQLException;
import java.util.List;

/**
 * Process backing the 13th Month Pay report module.
 *
 * Shared mechanics (degrading reads, reprint-aware print audit on a single
 * Connection) live in BaseReportProcess. This class supplies only the report's
 * DAO, its audit table name ("ThirteenthMonthReport"), and its recordId shape
 * ("YYYY").
 */
public class ThirteenthMonthProcess extends BaseReportProcess
    implements IThirteenthMonthProcess {

  private final ThirteenthMonthDAO reportDAO;

  public ThirteenthMonthProcess(ThirteenthMonthDAO reportDAO, AuditLogDAO auditLogDAO) {
    super("ThirteenthMonthReport", auditLogDAO);
    this.reportDAO = reportDAO;
  }

  @Override
  public List<ThirteenthMonthRow> GetReportForYear(int year) {
    return DegradingRead(
      "ThirteenthMonthProcess.GetReportForYear",
      () -> reportDAO.GetReportForYear(year)
    );
  }

  @Override
  public List<Integer> GetAvailableYears() {
    return DegradingRead(
      "ThirteenthMonthProcess.GetAvailableYears",
      reportDAO::GetAvailableYears
    );
  }

  @Override
  public boolean RecordPrint(int year, String username, String reason)
      throws SQLException {
    return LogPrint(String.valueOf(year), username, reason);
  }
}