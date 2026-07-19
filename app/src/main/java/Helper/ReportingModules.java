package Helper;

import Core.Service.Bir2316PdfRenderer;
import Core.Service.LeaveBalanceReportPdfRenderer;
import Core.Service.LoanLedgerReportPdfRenderer;
import Core.Service.PayrollSummaryPdfRenderer;
import Core.Service.StatutoryRemittancePdfRenderer;
import Core.Service.ThirteenthMonthPdfRenderer;
import Forms.Bir2316Panel;
import Forms.LeaveBalanceReportPanel;
import Forms.LoanLedgerReportPanel;
import Forms.PayrollSummaryPanel;
import Forms.StatutoryRemittancePanel;
import Forms.ThirteenthMonthPanel;
import Objects.enums.ModuleCode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.swing.JComponent;

/**
 * Reporting module views — read-only, cross-employee reports that render a
 * PDF: payroll summary, 13th month, statutory remittance, BIR 2316, leave
 * balance, loan ledger.
 *
 * Pulls processes from Injector (the shared DI core) and pairs each with its
 * per-document-type PDF renderer. These views take no permission list — they
 * are VIEW-only by grant, so presence in the nav is the whole gate.
 *
 * Keys come from ModuleCode (BKL-24), never raw strings: a typo is now a
 * compile error rather than a module that quietly never appears.
 *
 * BKL-25: registrations are Function<List<String>, JComponent>, matching
 * OperationsModules / ReferenceModules, so all three packs share one shape.
 * None of these gate on permissions, so every lambda below ignores its
 * argument — that's expected, not dead plumbing.
 */
final class ReportingModules {

  private ReportingModules() {}

  static void register(Map<String, Function<List<String>, JComponent>> views) {
    // Read-only cross-employee monthly payroll summary (+ PDF export).
    views.put(ModuleCode.PAYSUMMARY.GetCode(), perms ->
      new PayrollSummaryPanel(
        Injector.CreatePayrollSummaryProcess(),
        new PayrollSummaryPdfRenderer()
      )
    );

    // Read-only cross-employee 13th Month Pay report (+ PDF export).
    views.put(ModuleCode.THIRTEENTHMONTH.GetCode(), perms ->
      new ThirteenthMonthPanel(
        Injector.CreateThirteenthMonthProcess(),
        new ThirteenthMonthPdfRenderer()
      )
    );

    // SSS R-3 / PhilHealth RF-1 / Pag-IBIG M1-1 remittance forms.
    views.put(ModuleCode.REMITTANCE.GetCode(), perms ->
      new StatutoryRemittancePanel(
        Injector.CreateStatutoryRemittanceProcess(),
        new StatutoryRemittancePdfRenderer()
      )
    );

    // Annual BIR Form 2316 certificate (per employee).
    views.put(ModuleCode.BIR2316.GetCode(), perms ->
      new Bir2316Panel(
        Injector.CreateBir2316Process(),
        new Bir2316PdfRenderer()
      )
    );

    // Leave balance (entitled / used / remaining) per employee.
    views.put(ModuleCode.LEAVEBAL.GetCode(), perms ->
      new LeaveBalanceReportPanel(
        Injector.CreateLeaveBalanceReportProcess(),
        new LeaveBalanceReportPdfRenderer()
      )
    );

    // Loan ledger — principal / payable / paid / outstanding, per loan.
    views.put(ModuleCode.LOANLEDGER.GetCode(), perms ->
      new LoanLedgerReportPanel(
        Injector.CreateLoanLedgerReportProcess(),
        new LoanLedgerReportPdfRenderer()
      )
    );
  }
}