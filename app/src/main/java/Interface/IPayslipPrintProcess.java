package Interface;

import Objects.models.Payslip;
import Objects.models.PayslipDetail;
import java.sql.SQLException;
import java.util.List;

/**
 * Payslip distribution contract — read-only access to locked payslips plus the
 * print-audit write. Kept separate from IPayrollProcess so the run engine and
 * the distribution screen don't bleed into each other.
 */
public interface IPayslipPrintProcess {

  /** All LOCKED (Finalized or Paid) payslips for an employee, newest first. */
  List<Payslip> GetPrintableHistory(long employeeId) throws SQLException;

  /** Frozen header + persisted allowance/deduction lines for one payslip. */
  PayslipDetail GetPayslipDetail(long payslipId) throws SQLException;

  /** Records a print to the audit trail; returns true if this was a reprint. */
  boolean RecordPrint(long payslipId, String username, String reason) throws SQLException;
}