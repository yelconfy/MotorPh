package Interface;

import Objects.models.PayPeriodOption;
import Objects.models.PayrollSummaryRow;
import java.sql.SQLException;
import java.util.List;

/**
 * Contract for the Payroll Summary Report screen — read-only access to
 * vw_MonthlyPayrollSummary (script 17), plus the print-audit write. One row per
 * employee per month; the available periods feed the picker; RecordPrint logs
 * each PDF export to the audit trail (reprint-aware), mirroring the payslip
 * print path.
 */
public interface IPayrollSummaryProcess {

  /** Summary rows for the chosen (year, month), employee order. */
  List<PayrollSummaryRow> GetSummaryForPeriod(int year, int month);

  /** Distinct periods that have payslips, newest first (for the period picker). */
  List<PayPeriodOption> GetAvailablePeriods();

  /**
   * Records a Payroll Summary PDF export to the audit trail; returns true if a
   * prior export of the same period already exists (i.e. this is a reprint).
   */
  boolean RecordPrint(int year, int month, String username, String reason)
    throws SQLException;
}