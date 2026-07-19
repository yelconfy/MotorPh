package Interface;

import Objects.models.ThirteenthMonthRow;
import java.sql.SQLException;
import java.util.List;

/**
 * Contract for the 13th Month Pay report screen — read-only access to
 * vw_ThirteenthMonth (script 17), plus the print-audit write. One row per
 * employee per year; the available years feed the picker; RecordPrint logs each
 * PDF export to the audit trail (reprint-aware), mirroring the payslip and
 * payroll-summary print paths.
 */
public interface IThirteenthMonthProcess {

  /** 13th-month rows for the chosen year, employee order. */
  List<ThirteenthMonthRow> GetReportForYear(int year);

  /** Distinct years that have qualifying payslips, newest first (for the picker). */
  List<Integer> GetAvailableYears();

  /**
   * Records a 13th Month Pay PDF export to the audit trail; returns true if a
   * prior export of the same year already exists (i.e. this is a reprint).
   */
  boolean RecordPrint(int year, String username, String reason)
    throws SQLException;
}