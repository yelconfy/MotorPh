package Interface;

import Objects.models.PayrollPeriod;
import Objects.models.Payslip;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/**
 * Public payroll contract: the period run / lifecycle, which is all PayrollPanel
 * consumes. The per-employee compute methods (hours / deductions / payslip / OT)
 * are no longer part of this interface — they moved to PayrollCalculator, which
 * PayrollProcess uses internally.
 */
public interface IPayrollProcess {

  long RunPeriod(PayrollPeriod period, LocalDate asOf, long generatedByUserId) throws SQLException;

  boolean FinalizePeriod(long periodId) throws SQLException;

  int PayPeriod(long periodId, LocalDate payDate) throws SQLException;

  List<Payslip> GetPayslipsForPeriod(long periodId) throws SQLException;

  List<PayrollPeriod> GetOpenPeriods() throws SQLException;
}