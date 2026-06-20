package Helper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

import javax.swing.JComponent;

import Core.Form.ShellFrame;
import Core.Service.FormControlService;
import Core.Service.PayrollCalculator;
import DataAccess.AccessDAO;
import DataAccess.AllowanceDAO;
import DataAccess.AttendanceDAO;
import DataAccess.DeductionDAO;
import DataAccess.DepartmentDAO;
import DataAccess.EmployeeAddressesDao;
import DataAccess.EmployeeDAO;
import DataAccess.EmployeeSalaryDAO;
import DataAccess.PayrollDAO;
import DataAccess.PositionDAO;
import DataAccess.SessionDAO;
import DataAccess.StatutoryDAO;
import DataAccess.StatutoryRateDAO;
import DataAccess.UserDAO;
import DataAccess.WorkScheduleDAO;
import Forms.EmployeeManagementPanel;
import Forms.LoginForm;
import Forms.PayrollPanel;
import Forms.TimeKeepingPanel;
import Interface.IEmpMgmtProcess;
import Interface.ILoginProcess;
import Interface.IPayrollProcess;
import Objects.models.IAM.Session;
import Processes.EmpMgmtProcess;
import Processes.LoginProcess;
import Processes.PayrollProcess;
import Processes.TimeKeepingProcess;

/**
 * Central DI factory.
 *
 * Post-shell migration:
 *   - Module navigation now lives in ShellFrame (one persistent window). The old
 *     per-role frame factories (CreatePayrollForm / CreateEmpMgmtForm /
 *     CreateTimeKeepingForm) have been removed — modules are mounted as
 *     JComponent views inside the shell's work area, keyed by Module.ModuleCode.
 *   - CreateShell() assembles that ModuleCode -> view-supplier map. Each entry is
 *     lazy; the shell only instantiates the views the logged-in role is granted.
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

  // -------------------------------------------------------------------------
  // Process factories
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
      new PayrollCalculator()
    );
  }

  // -------------------------------------------------------------------------
  // Form / view factories
  // -------------------------------------------------------------------------

  public static LoginForm CreateLoginForm() {
    UserDAO userDAO = new UserDAO();
    SessionDAO sessionDAO = new SessionDAO();
    ILoginProcess loginProcess = new LoginProcess(userDAO, sessionDAO);
    return new LoginForm(loginProcess);
  }

  public static ShellFrame CreateShell() {
    return new ShellFrame(new AccessDAO(), getEmpDAO(), CreateModuleViews());
  }

  /**
   * ModuleCode -> lazy work-area view. Keys MUST match Module.ModuleCode from
   * the RBAC seed (script 09): PAYROLL / EMPMGMT / TIMEKEEPING. The shell shows
   * only the entries the logged-in role is granted.
   *
   * TEMPORARY: entries are placeholders until each NetBeans form is converted
   * from JFrame to JPanel. To go live, replace a placeholder with the real
   * view — the exact construction is in each TODO below.
   */
  private static Map<String, Supplier<JComponent>> CreateModuleViews() {
    Map<String, Supplier<JComponent>> views = new LinkedHashMap<>();

    // TODO swap once PayrollForm is a JPanel:
    //   () -> new PayrollForm(CreatePayrollProcess(), CreateEmpMgmtProcess())
    views.put("PAYROLL", () ->
      new PayrollPanel(CreatePayrollProcess(), CreateEmpMgmtProcess())
    );

    // TODO swap once EmpMgmtForm is a JPanel:
    //   () -> new EmpMgmtForm(CreateEmpMgmtProcess(), getPosDAO(), getDeptDAO(), getScheduleDAO())
    views.put("EMPMGMT", () ->
      new EmployeeManagementPanel(
        CreateEmpMgmtProcess(),
        CreateValidator(),
        new AccessDAO().GetPermissionCodes(Session.GetRoleId(), "EMPMGMT")
      )
    );

    // TODO swap once TimeKeepingForm is a JPanel:
    //   () -> new TimeKeepingForm(new TimeKeepingProcess(getAttendanceDAO()))
    views.put("TIMEKEEPING", () ->
      new TimeKeepingPanel(new TimeKeepingProcess(getAttendanceDAO()))
    );

    return views;
  }

  public static FormControlService CreateValidator() {
    return new FormControlService();
  }
}
