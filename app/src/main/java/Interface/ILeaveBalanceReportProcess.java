package Interface;

import Objects.models.LeaveBalanceRow;
import java.sql.SQLException;
import java.util.List;

/**
 * Contract for the Leave Balance Report screen — read-only access to
 * vw_LeaveBalanceReport (script 17) plus a reprint-aware print-audit write.
 */
public interface ILeaveBalanceReportProcess {

  /** Leave-balance rows for the chosen year. */
  List<LeaveBalanceRow> GetForYear(int year);

  /** Distinct years with data, newest first — for the picker. */
  List<Integer> GetAvailableYears();

  /** Records a report PDF export to the audit trail; true if a prior export exists (reprint). */
  boolean RecordPrint(int year, String username, String reason) throws SQLException;
}