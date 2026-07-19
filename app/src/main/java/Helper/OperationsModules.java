package Helper;

import Core.Service.DtrPdfRenderer;
import Core.Service.PayslipPdfRenderer;
import Forms.ActivityLogPanel;
import Forms.AttendanceCorrectionPanel;
import Forms.DtrReportPanel;
import Forms.EmployeeManagementPanel;
import Forms.LeaveApprovalPanel;
import Forms.OvertimeApprovalPanel;
import Forms.PayrollPanel;
import Forms.PayslipRegisterPanel;
import Forms.TimeKeepingPanel;
import Objects.enums.ModuleCode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.swing.JComponent;

/**
 * Operational / transactional module views — the day-to-day work screens
 * (payroll run, employee mgmt, timekeeping, approvals, DTR, punch correction,
 * activity log).
 *
 * Pulls processes from Injector (the shared DI core) and constructs the panels
 * + their renderers here.
 *
 * Keys come from ModuleCode (BKL-24), never raw strings.
 *
 * BKL-25: each registration is a Function<List<String>, JComponent> instead of
 * a bare Supplier. Injector.CreateModuleViews() resolves the permission list
 * itself from the map key (Session's cached matrix) and hands it to the
 * function, so a gated panel below just takes `perms` as a parameter — the
 * module code is written exactly once per screen (the map key), never a
 * second time inside the lambda body. Ungated views (TIMEKEEPING, PAYSLIP,
 * PAYROLL, ACTIVITY) simply ignore the argument.
 */
final class OperationsModules {

  private OperationsModules() {}

  static void register(Map<String, Function<List<String>, JComponent>> views) {
    views.put(ModuleCode.PAYROLL.GetCode(), perms ->
      new PayrollPanel(
        Injector.CreatePayrollProcess(),
        Injector.CreateEmpMgmtProcess()
      )
    );

    views.put(ModuleCode.EMPMGMT.GetCode(), perms ->
      new EmployeeManagementPanel(
        Injector.CreateEmpMgmtProcess(),
        Injector.CreateValidator(),
        perms
      )
    );

    views.put(ModuleCode.TIMEKEEPING.GetCode(), perms ->
      new TimeKeepingPanel(Injector.CreateTimeKeepingProcess())
    );

    views.put(ModuleCode.PAYSLIP.GetCode(), perms ->
      new PayslipRegisterPanel(
        Injector.CreatePayslipPrintProcess(),
        Injector.CreateEmpMgmtProcess(),
        new PayslipPdfRenderer()
      )
    );

    // Phase 7a — admin-facing approval queues (read pending, approve/reject).
    views.put(ModuleCode.LEAVE.GetCode(), perms ->
      new LeaveApprovalPanel(Injector.CreateApprovalProcess(), perms)
    );

    views.put(ModuleCode.OVERTIME.GetCode(), perms ->
      new OvertimeApprovalPanel(Injector.CreateApprovalProcess(), perms)
    );

    // Phase 7b — per-employee Daily Time Record (read-only; PDF export).
    views.put(ModuleCode.DTR.GetCode(), perms ->
      new DtrReportPanel(
        Injector.CreateTimeKeepingProcess(),
        Injector.CreateEmpMgmtProcess(),
        new DtrPdfRenderer()
      )
    );

    // Phase 7c — audited punch correction (add/edit attendance).
    views.put(ModuleCode.PUNCHFIX.GetCode(), perms ->
      new AttendanceCorrectionPanel(
        Injector.CreateAttendanceCorrectionProcess(),
        Injector.CreateEmpMgmtProcess(),
        perms
      )
    );

    // Phase 7c — read-only audit + access timeline.
    views.put(ModuleCode.ACTIVITY.GetCode(), perms ->
      new ActivityLogPanel(Injector.CreateActivityLogProcess())
    );
  }
}