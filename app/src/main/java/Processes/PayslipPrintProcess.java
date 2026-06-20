package Processes;

import DataAccess.AuditLogDAO;
import DataAccess.DatabaseConnector;
import DataAccess.PayrollDAO;
import Interface.IPayslipPrintProcess;
import Objects.enums.Status.AuditAction;
import Objects.models.Payslip;
import Objects.models.PayslipAllowanceLine;
import Objects.models.PayslipDeductionLine;
import Objects.models.PayslipDetail;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Read-only payslip distribution + print auditing.
 *
 *   - history: locked-only payslips for an employee (the print screen never
 *     shows Drafts); the UI derives the year filter from this list;
 *   - detail: the frozen Payslip header plus its persisted line items, all read
 *     on ONE connection (no recompute — a reprint matches the finalized slip);
 *   - audit: every print appends an Audit_Log PRINT row; a print whose prior
 *     PRINT count is > 0 is flagged a reprint.
 *
 * Every method owns one Connection for its whole operation and uses the
 * conn-param DAO overloads, so nothing self-opens and closes the single shared
 * connection mid-operation.
 */
public class PayslipPrintProcess implements IPayslipPrintProcess {

  private static final String AUDIT_TABLE = "Payslip";

  private final PayrollDAO payrollDAO;
  private final AuditLogDAO auditLogDAO;

  public PayslipPrintProcess(PayrollDAO payrollDAO, AuditLogDAO auditLogDAO) {
    this.payrollDAO = payrollDAO;
    this.auditLogDAO = auditLogDAO;
  }

  @Override
  public List<Payslip> GetPrintableHistory(long employeeId) throws SQLException {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      List<Payslip> locked = new ArrayList<>();
      for (Payslip s : payrollDAO.GetByEmployee(conn, employeeId)) {
        if (s.IsLocked()) {
          locked.add(s); // GetByEmployee already orders newest-first
        }
      }
      return locked;
    }
  }

  @Override
  public PayslipDetail GetPayslipDetail(long payslipId) throws SQLException {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      Payslip header = payrollDAO.GetById(conn, payslipId);
      if (header == null) {
        return null;
      }
      List<PayslipAllowanceLine> allowances = payrollDAO.GetAllowanceLines(conn, payslipId);
      List<PayslipDeductionLine> deductions = payrollDAO.GetDeductionLines(conn, payslipId);
      return new PayslipDetail(header, allowances, deductions);
    }
  }

  @Override
  public boolean RecordPrint(long payslipId, String username, String reason)
    throws SQLException {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      String recordId = String.valueOf(payslipId);
      boolean isReprint =
        auditLogDAO.CountActions(conn, AUDIT_TABLE, recordId, AuditAction.PRINT) > 0;
      auditLogDAO.Log(
        conn, username, AUDIT_TABLE, recordId, AuditAction.PRINT, null, reason
      );
      return isReprint;
    }
  }
}