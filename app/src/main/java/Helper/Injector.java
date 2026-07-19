package Helper;

import Core.Form.ShellFrame;
import Core.Service.FormControlService;
import Core.Service.PayrollCalculator;
import DataAccess.AccessDAO;
import DataAccess.AllowanceDAO;
import DataAccess.AttendanceDAO;
import DataAccess.AuditLogDAO;
import DataAccess.Bir2316DAO;
import DataAccess.DeductionDAO;
import DataAccess.DepartmentDAO;
import DataAccess.EmployeeAddressesDao;
import DataAccess.EmployeeDAO;
import DataAccess.EmployeeSalaryDAO;
import DataAccess.HolidayDAO;
import DataAccess.LeaveBalanceReportDAO;
import DataAccess.LeaveDAO;
import DataAccess.LoanLedgerReportDAO;
import DataAccess.OvertimeDAO;
import DataAccess.PayrollDAO;
import DataAccess.PayrollSummaryDAO;
import DataAccess.PositionDAO;
import DataAccess.PremiumRateDAO;
import DataAccess.SessionDAO;
import DataAccess.StatutoryDAO;
import DataAccess.StatutoryRateDAO;
import DataAccess.StatutoryRemittanceDAO;
import DataAccess.SystemActivityDAO;
import DataAccess.ThirteenthMonthDAO;
import DataAccess.UserDAO;
import DataAccess.WorkScheduleDAO;
import Forms.LoginForm;
import Interface.IActivityLogProcess;
import Interface.IApprovalProcess;
import Interface.IAttendanceCorrectionProcess;
import Interface.IBir2316Process;
import Interface.IEmpMgmtProcess;
import Interface.ILeaveBalanceReportProcess;
import Interface.ILoanLedgerReportProcess;
import Interface.ILoginProcess;
import Interface.IMaintenanceProcess;
import Interface.IPayrollProcess;
import Interface.IPayrollSummaryProcess;
import Interface.IPayslipPrintProcess;
import Interface.IStatutoryRemittanceProcess;
import Interface.IThirteenthMonthProcess;
import Interface.ITimeKeepingProcess;
import Objects.models.AllowanceTypeInfo;
import Objects.models.DeductionTypeInfo;
import Objects.models.DepartmentInfo;
import Objects.models.IAM.Session;
import Objects.models.LeaveTypeInfo;
import Objects.models.PositionInfo;
import Objects.models.WorkScheduleInfo;
import Objects.models.WorkScheduleInfo;
import Processes.ActivityLogProcess;
import Processes.AllowanceTypeMaintenanceProcess;
import Processes.ApprovalProcess;
import Processes.AttendanceCorrectionProcess;
import Processes.Bir2316Process;
import Processes.DeductionTypeMaintenanceProcess;
import Processes.DepartmentMaintenanceProcess;
import Processes.EmpMgmtProcess;
import Processes.LeaveBalanceReportProcess;
import Processes.LeaveTypeMaintenanceProcess;
import Processes.LoanLedgerReportProcess;
import Processes.LoginProcess;
import Processes.PayrollProcess;
import Processes.PayrollSummaryProcess;
import Processes.PayslipPrintProcess;
import Processes.PositionMaintenanceProcess;
import Processes.StatutoryRemittanceProcess;
import Processes.ThirteenthMonthProcess;
import Processes.TimeKeepingProcess;
import Processes.WorkScheduleMaintenanceProcess;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JComponent;
import DataAccess.RoleDAO;
import Objects.models.AccountRole;
import Processes.RoleMaintenanceProcess;

/**
 * Central DI factory (core).
 *
 * Responsibilities kept here:
 *   - DAO factories (leaf providers, private).
 *   - Process factories (the DI wiring — public, so the module packs can
 *     assemble their panels from them).
 *   - App-level assembly: CreateLoginForm, CreateShell, CreateValidator.
 *
 * What moved OUT (to keep this file from ballooning): the ModuleCode -> view
 * supplier registrations. Those are now split by nav category across three
 * packs in this package — OperationsModules, ReportingModules, ReferenceModules
 * — each owning its own Forms.* / *PdfRenderer imports. CreateModuleViews()
 * simply merges them. Adding a screen touches only its category's pack (plus a
 * one-line process factory here), so an Admin-screen change never collides with
 * a payroll change in the same file.
 *
 * Laziness note: the map stores Suppliers, so a view's panel + its process are
 * only constructed when the shell actually navigates to that ModuleCode. This
 * was already true before the split; the packs preserve it.
 */
public class Injector {

  // -------------------------------------------------------------------------
  // DAO factories (private, stateless)
  // -------------------------------------------------------------------------

  private static EmployeeDAO getEmpDAO() {
    return new EmployeeDAO();
  }

  private static EmployeeAddressesDao getAddrDAO() {
    return new EmployeeAddressesDao();
  }

  private static EmployeeSalaryDAO getSalaryDAO() {
    return new EmployeeSalaryDAO();
  }

  private static StatutoryDAO getStatDAO() {
    return new StatutoryDAO();
  }

  private static PositionDAO getPosDAO() {
    return new PositionDAO();
  }

  private static DepartmentDAO getDeptDAO() {
    return new DepartmentDAO();
  }

  private static WorkScheduleDAO getScheduleDAO() {
    return new WorkScheduleDAO();
  }

  private static PayrollDAO getPayrollDAO() {
    return new PayrollDAO();
  }

  private static AttendanceDAO getAttendanceDAO() {
    return new AttendanceDAO();
  }

  private static AllowanceDAO getAllowanceDAO() {
    return new AllowanceDAO();
  }

  private static StatutoryRateDAO getStatRateDAO() {
    return new StatutoryRateDAO();
  }

  private static DeductionDAO getDeductionDAO() {
    return new DeductionDAO();
  }

  private static AuditLogDAO getAuditLogDAO() {
    return new AuditLogDAO();
  }

  private static HolidayDAO getHolidayDAO() {
    return new HolidayDAO();
  }

  private static OvertimeDAO getOvertimeDAO() {
    return new OvertimeDAO();
  }

  private static LeaveDAO getLeaveDAO() {
    return new LeaveDAO();
  }

  private static PremiumRateDAO getPremiumRateDAO() {
    return new PremiumRateDAO();
  }

  private static SystemActivityDAO getSystemActivityDAO() {
    return new SystemActivityDAO();
  }

  private static PayrollSummaryDAO getPayrollSummaryDAO() {
    return new PayrollSummaryDAO();
  }

  private static RoleDAO getRoleDAO() {
    return new RoleDAO();
  }

  // -------------------------------------------------------------------------
  // Process factories (public — consumed by the module packs)
  // -------------------------------------------------------------------------

  public static IEmpMgmtProcess CreateEmpMgmtProcess() {
    return new EmpMgmtProcess(
      getEmpDAO(),
      getAddrDAO(),
      getSalaryDAO(),
      getStatDAO(),
      getPosDAO(),
      getDeptDAO(),
      getScheduleDAO(),
      getAllowanceDAO()
    );
  }

  public static IPayrollProcess CreatePayrollProcess() {
    return new PayrollProcess(
      getAttendanceDAO(),
      getStatRateDAO(),
      getPayrollDAO(),
      getEmpDAO(),
      getAllowanceDAO(),
      getDeductionDAO(),
      getScheduleDAO(),
      getHolidayDAO(),
      getOvertimeDAO(),
      getLeaveDAO(),
      getPremiumRateDAO(),
      new PayrollCalculator()
    );
  }

  public static IPayslipPrintProcess CreatePayslipPrintProcess() {
    return new PayslipPrintProcess(getPayrollDAO(), getAuditLogDAO());
  }

  /**
   * Timekeeping process — backs both the Timekeeping grid and the Phase 7b
   * Daily Time Record report (the DTR uses its exact single-employee read).
   */
  public static ITimeKeepingProcess CreateTimeKeepingProcess() {
    return new TimeKeepingProcess(
      getAttendanceDAO(),
      getScheduleDAO(),
      getHolidayDAO()
    );
  }

  /**
   * Approval process for the Leave / Overtime Approvals modules (Phase 7a).
   * Takes the AuditLogDAO so each approve/reject writes an Audit_Log row in the
   * same transaction (Phase 7c).
   */
  public static IApprovalProcess CreateApprovalProcess() {
    return new ApprovalProcess(
      getLeaveDAO(),
      getOvertimeDAO(),
      getEmpDAO(),
      getAuditLogDAO()
    );
  }

  /**
   * Punch Correction process (Phase 7c) — audited add/edit of attendance rows.
   */
  public static IAttendanceCorrectionProcess CreateAttendanceCorrectionProcess() {
    return new AttendanceCorrectionProcess(
      getAttendanceDAO(),
      getAuditLogDAO()
    );
  }

  /**
   * Activity Log process (Phase 7c) — read-only view over vw_SystemActivity.
   */
  public static IActivityLogProcess CreateActivityLogProcess() {
    return new ActivityLogProcess(getSystemActivityDAO());
  }

  /**
   * Payroll Summary process (Reporting) — read-only view over
   * vw_MonthlyPayrollSummary.
   */
  public static IPayrollSummaryProcess CreatePayrollSummaryProcess() {
    return new PayrollSummaryProcess(getPayrollSummaryDAO(), getAuditLogDAO());
  }

  /**
   * 13th Month Pay process (Reporting) — read-only view over vw_ThirteenthMonth.
   */
  public static IThirteenthMonthProcess CreateThirteenthMonthProcess() {
    return new ThirteenthMonthProcess(
      new ThirteenthMonthDAO(),
      getAuditLogDAO()
    );
  }

  public static IStatutoryRemittanceProcess CreateStatutoryRemittanceProcess() {
    return new StatutoryRemittanceProcess(
      new StatutoryRemittanceDAO(),
      getAuditLogDAO()
    );
  }

  public static IBir2316Process CreateBir2316Process() {
    return new Bir2316Process(new Bir2316DAO(), getAuditLogDAO());
  }

  public static ILeaveBalanceReportProcess CreateLeaveBalanceReportProcess() {
    return new LeaveBalanceReportProcess(
      new LeaveBalanceReportDAO(),
      getAuditLogDAO()
    );
  }

  /**
   * Loan Ledger process (Reporting) — read-only view over vw_LoanLedgerReport.
   *
   * BKL-24 CATCH: this factory never existed. The whole LOANLEDGER slice shipped
   * under BKL-15 except its DI wiring, so the module was unreachable.
   */
  public static ILoanLedgerReportProcess CreateLoanLedgerReportProcess() {
    return new LoanLedgerReportProcess(
      new LoanLedgerReportDAO(),
      getAuditLogDAO()
    );
  }

  public static IMaintenanceProcess<
    PositionInfo
  > CreatePositionMaintenanceProcess() {
    return new PositionMaintenanceProcess(getPosDAO());
  }

  public static IMaintenanceProcess<
    DepartmentInfo
  > CreateDepartmentMaintenanceProcess() {
    return new DepartmentMaintenanceProcess(getDeptDAO());
  }

  public static IMaintenanceProcess<
    DeductionTypeInfo
  > CreateDeductionTypeMaintenanceProcess() {
    return new DeductionTypeMaintenanceProcess(getDeductionDAO());
  }

  public static IMaintenanceProcess<
    AllowanceTypeInfo
  > CreateAllowanceTypeMaintenanceProcess() {
    return new AllowanceTypeMaintenanceProcess(getAllowanceDAO());
  }

  public static IMaintenanceProcess<
    LeaveTypeInfo
  > CreateLeaveTypeMaintenanceProcess() {
    return new LeaveTypeMaintenanceProcess(getLeaveDAO());
  }

  public static IMaintenanceProcess<
    WorkScheduleInfo
  > CreateWorkScheduleMaintenanceProcess() {
    return new WorkScheduleMaintenanceProcess(getScheduleDAO());
  }

  public static IMaintenanceProcess<
    AccountRole
  > CreateRoleMaintenanceProcess() {
    return new RoleMaintenanceProcess(getRoleDAO());
  }

  // -------------------------------------------------------------------------
  // App assembly
  // -------------------------------------------------------------------------

  public static LoginForm CreateLoginForm() {
    UserDAO userDAO = new UserDAO();
    SessionDAO sessionDAO = new SessionDAO();
    ILoginProcess loginProcess = new LoginProcess(userDAO, sessionDAO);
    return new LoginForm(loginProcess);
  }

  public static ShellFrame CreateShell() {
    AccessDAO accessDAO = new AccessDAO();

    // BKL-25: load the whole role x module x permission grant matrix ONCE,
    // before any view is built, and cache it on Session. Gated registrations
    // read Session.GetPermissions(code) instead of each issuing their own
    // AccessDAO query per panel mount (see CreateModuleViews() below).
    Session.SetPermissionMatrix(
      accessDAO.GetPermissionMatrix(Session.GetRoleId())
    );

    Map<String, Supplier<JComponent>> views = CreateModuleViews();
    ModuleReconciler.Reconcile(views.keySet(), accessDAO); // BKL-24: report drift before the UI opens
    return new ShellFrame(accessDAO, getEmpDAO(), views);
  }

  public static FormControlService CreateValidator() {
    return new FormControlService();
  }

  /**
   * ModuleCode -> lazy work-area view. Keys MUST match Module.ModuleCode from
   * the RBAC seed (script 09). The shell shows only the entries the logged-in
   * role is granted AND that have a registered supplier here.
   *
   * The actual registrations live in the three category packs; this just merges
   * them, preserving insertion order (Operations -> Reporting -> Reference) so
   * the nav ordering is unchanged.
   *
   * TEMPORARY: the PAYROLL / EMPMGMT entries in OperationsModules are
   * placeholders until each NetBeans form is fully converted from JFrame to
   * JPanel.
   */
  private static Map<String, Supplier<JComponent>> CreateModuleViews() {
    // BKL-25: each pack registers a Function<List<String>, JComponent> instead
    // of a bare Supplier, so a gated panel receives its permission list as an
    // argument rather than looking it up itself -- the module code is then
    // written exactly once per screen (the map key), not twice. This wraps
    // each Function back into the Supplier<JComponent> ShellFrame already
    // consumes, resolving permissions from Session's cached matrix (loaded
    // once in CreateShell(), above) at the moment a module is first mounted --
    // laziness is preserved.
    Map<String, Function<List<String>, JComponent>> raw = new LinkedHashMap<>();
    OperationsModules.register(raw);
    ReportingModules.register(raw);
    ReferenceModules.register(raw);

    Map<String, Supplier<JComponent>> views = new LinkedHashMap<>();
    for (Map.Entry<
      String,
      Function<List<String>, JComponent>
    > e : raw.entrySet()) {
      String code = e.getKey();
      Function<List<String>, JComponent> factory = e.getValue();
      views.put(code, () -> factory.apply(Session.GetPermissions(code)));
    }
    return views;
  }
}
