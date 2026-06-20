package Processes;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import DataAccess.AllowanceDAO;
import DataAccess.DatabaseConnector;
import DataAccess.DepartmentDAO;
import DataAccess.EmployeeAddressesDao;
import DataAccess.EmployeeDAO;
import DataAccess.EmployeeSalaryDAO;
import DataAccess.PositionDAO;
import DataAccess.StatutoryDAO;
import DataAccess.WorkScheduleDAO;
import Interface.IEmpMgmtProcess;
import Objects.models.AllowanceInfo;
import Objects.models.DepartmentInfo;
import Objects.models.EmpDetail;
import Objects.models.EmployeeInfo;
import Objects.models.EmployeeSalaryInfo;
import Objects.models.PositionInfo;
import Objects.models.WorkScheduleInfo;

/**
 * Employee-management orchestration. Wraps the employee-detail DAOs in atomic
 * transactions (BaseMaintenanceProcess.ExecuteAtomic).
 *
 * NOTE on statutory: statDAO is the ID-NUMBER DAO (StatutoryDetails:
 * SssNo/PhilHealthNo/TinNo/PagIbigNo). It is NOT the rate-lookup class
 * (StatutoryRateDAO) used by PayrollProcess — do not conflate the two.
 *
 * NOTE on salary: EmployeeSalary is VERSIONED and EmployeeSalaryDAO is
 * insert-only (no UPDATE — history must be preserved). An edit to pay is
 * therefore recorded as a NEW effective-dated row, i.e. a salary adjustment.
 * See ApplySalaryAdjustment.
 */
public class EmpMgmtProcess
  extends BaseMaintenanceProcess
  implements IEmpMgmtProcess
{

  private final EmployeeDAO empDAO;
  private final EmployeeAddressesDao addrDAO;
  private final EmployeeSalaryDAO salaryDAO;
  private final StatutoryDAO statDAO; // ID numbers (StatutoryDetails)
  private final PositionDAO posDAO;
  private final DepartmentDAO deptDAO;
  private final WorkScheduleDAO scheduleDAO;
  private final AllowanceDAO allowanceDAO;

  public EmpMgmtProcess(
    EmployeeDAO empDAO,
    EmployeeAddressesDao addrDAO,
    EmployeeSalaryDAO salaryDAO,
    StatutoryDAO statDAO,
    PositionDAO posDAO,
    DepartmentDAO deptDAO,
    WorkScheduleDAO scheduleDAO,
    AllowanceDAO allowanceDAO
  ) {
    this.empDAO = empDAO;
    this.addrDAO = addrDAO;
    this.salaryDAO = salaryDAO;
    this.statDAO = statDAO;
    this.posDAO = posDAO;
    this.deptDAO = deptDAO;
    this.scheduleDAO = scheduleDAO;
    this.allowanceDAO = allowanceDAO;
  }

  @Override
  public List<EmpDetail> GetEmpDetails() {
    try {
      return empDAO.GetAll();
    } catch (SQLException e) {
      e.printStackTrace();
      return Collections.emptyList();
    }
  }

  @Override
  public List<EmpDetail> SearchEmployee(String query) {
    try {
      return empDAO.Search(query);
    } catch (SQLException e) {
      e.printStackTrace();
      return Collections.emptyList();
    }
  }

  @Override
  public EmpDetail GetCompleteEmployee(long empNo) {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      EmpDetail emp = empDAO.GetByID(conn, empNo);
      if (emp != null) {
        CompleteEmployee(conn, emp, empNo);
      }
      return emp;
    } catch (SQLException e) {
      e.printStackTrace();
      return null;
    }
  }

  // -------------------------------------------------------------------------
  // Reference-data reads for editor dropdowns (P2-0)
  // Self-opening one-shot UI reads — open a connection, delegate to the DAO,
  // swallow to an empty list on failure (consistent with GetEmpDetails).
  // -------------------------------------------------------------------------

  @Override
  public List<PositionInfo> GetAllPositions() {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      return posDAO.GetAll(conn);
    } catch (SQLException e) {
      e.printStackTrace();
      return Collections.emptyList();
    }
  }

  @Override
  public List<DepartmentInfo> GetAllDepartments() {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      return deptDAO.GetAll(conn);
    } catch (SQLException e) {
      e.printStackTrace();
      return Collections.emptyList();
    }
  }

  @Override
  public List<WorkScheduleInfo> GetAllSchedules() {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      return scheduleDAO.GetAll(conn);
    } catch (SQLException e) {
      e.printStackTrace();
      return Collections.emptyList();
    }
  }

  @Override
  public boolean AddEmployee(EmpDetail newEmployee) {
    return ExecuteAtomic(conn -> {
      long newID = empDAO.Insert(conn, newEmployee);
      if (newID > 0) {
        newEmployee.SetEmployeeId(newID); // surface generated ID for post-save reselection
        addrDAO.Insert(conn, newID, newEmployee.GetAddress());
        salaryDAO.Insert(conn, newID, newEmployee.GetCompensation());
        statDAO.Insert(conn, newID, newEmployee.GetStatutory());
        SaveAllowances(conn, newID, newEmployee.GetAllowances());
        return true;
      }
      return false;
    });
  }

  @Override
  public boolean UpdateEmployee(EmpDetail updatedEmp) {
    return ExecuteAtomic(conn -> {
      long empID = updatedEmp.GetEmployeeId();
      empDAO.Update(conn, updatedEmp);
      addrDAO.Update(conn, empID, updatedEmp.GetAddress());
      // EmployeeSalary is versioned: a pay change is a NEW row, not an UPDATE.
      ApplySalaryAdjustment(conn, empID, updatedEmp.GetCompensation());
      statDAO.Update(conn, empID, updatedEmp.GetStatutory());
      SaveAllowances(conn, empID, updatedEmp.GetAllowances());
      return true;
    });
  }

  @Override
  public boolean DeleteEmployee(long empNo) {
    return ExecuteAtomic(conn -> empDAO.Delete(conn, empNo));
  }

  private void CompleteEmployee(Connection conn, EmpDetail emp, long empNo)
    throws SQLException {
    // 1. Fill normalized details.
    //    Statutory IDs are already mapped from vw_EmployeeCompleteDetails in
    //    the EmpDetail smart constructor, so there is no separate
    //    StatutoryDAO.FillStatutoryDetails round-trip here.
    emp.SetAddress(addrDAO.GetByEmployeeID(conn, empNo));
    emp.SetCompensation(salaryDAO.GetCurrent(conn, empNo));

    // 2. Hydrate position name
    if (emp.GetPosition() != null && emp.GetPosition().GetPositionID() > 0) {
      emp.SetPosition(posDAO.GetByID(conn, emp.GetPosition().GetPositionID()));
    }

    // 3. Hydrate supervisor name
    if (emp.GetImmSupervisor() != null) {
      long supervisorId = emp.GetImmSupervisor().GetEmployeeId();
      EmployeeInfo supervisor = empDAO.GetByID(conn, supervisorId);
      emp.SetImmSupervisor(supervisor);
    }

    // 4. Load allowances (Employee_Allowance) into EmpDetail.Allowances.
    //    Consumers read them via EmpDetail.GetTotalAllowances() /
    //    GetAllowanceAmount(name) — there is no mirroring onto compensation.
    emp.SetAllowances(allowanceDAO.GetByEmployeeID(conn, empNo));
  }

  // -------------------------------------------------------------------------
  // Salary adjustment (versioned-table write)
  // -------------------------------------------------------------------------

  /**
   * Records a pay change as a NEW effective-dated EmployeeSalary row.
   *
   * EmployeeSalaryDAO has no UPDATE by design — salary history must be
   * preserved — so an edit becomes a new version dated today. To avoid
   * polluting the history with a duplicate row on every unrelated profile
   * edit, a new version is written only when the incoming pay differs from the
   * current effective row (or when no salary row exists yet).
   */
  private void ApplySalaryAdjustment(
    Connection conn,
    long empID,
    EmployeeSalaryInfo incoming
  ) throws SQLException {
    if (incoming == null) {
      return; // nothing to adjust
    }

    EmployeeSalaryInfo current = salaryDAO.GetCurrent(conn, empID);

    if (current != null && SamePay(current, incoming)) {
      return; // no change → keep the version history clean
    }

    // This new row IS the salary adjustment; date it today so vw_CurrentSalary
    // (which resolves MAX(EffectiveDate)) picks it up as the live rate.
    incoming.SetEffectiveDate(LocalDate.now());
    salaryDAO.Insert(conn, empID, incoming);
  }

  // -------------------------------------------------------------------------
  // Allowance write-path (Employee_Allowance)
  // -------------------------------------------------------------------------

  /**
   * Persists the employee's allowances within the caller's transaction.
   *
   * Allowances flow ONLY via EmpDetail.Allowances — never mirrored onto
   * EmployeeSalaryInfo. Each row is resolved to its AllowanceTypeID (explicit ID
   * when present, else by name) and upserted on (EmployeeID, AllowanceTypeID).
   *
   * Amount is assumed > 0 (the editor validates this; a 0 only comes from bad
   * seed data, fixed at the script level). Non-positive amounts are skipped.
   *
   * An unknown allowance name (not seeded in Allowance_Type) ABORTS the save by
   * throwing — silently dropping a pay-affecting allowance is worse than a visible
   * failure the user can correct.
   *
   * SCOPED OUT for now: this upserts what the list contains; it does NOT
   * deactivate an allowance the user removed (an absent row stays Status=1).
   * Employee EXIT is handled at the employee level (soft delete, Status=0) and
   * likewise does not touch these rows — the employee-active gate is the single
   * source of truth, honored by the directory reads and RunPeriod.
   */
  private void SaveAllowances(
    Connection conn,
    long empID,
    List<AllowanceInfo> allowances
  ) throws SQLException {
    if (allowances == null || allowances.isEmpty()) {
      return;
    }
    Map<String, Integer> typeIds = allowanceDAO.GetTypeIdsByName(conn);
    for (AllowanceInfo a : allowances) {
      if (a == null || a.GetAmount() <= 0) {
        continue; // editor guarantees > 0; defensive skip
      }
      int typeId = ResolveAllowanceTypeId(a, typeIds);
      if (typeId <= 0) {
        throw new SQLException(
          "Unknown allowance type '" +
            a.GetAllowanceName() +
            "' — not seeded in Allowance_Type. Aborting employee save."
        );
      }
      allowanceDAO.Upsert(conn, empID, typeId, a.GetAmount());
    }
  }

  /**
   * Prefers an explicit AllowanceTypeID (DB-hydrated rows carry one); falls back
   * to a name lookup for editor-entered rows. Returns -1 if neither yields an ID.
   */
  private int ResolveAllowanceTypeId(
    AllowanceInfo a,
    Map<String, Integer> typeIds
  ) {
    if (a.GetAllowanceTypeId() > 0) {
      return a.GetAllowanceTypeId();
    }
    String name = a.GetAllowanceName();
    if (name != null) {
      Integer id = typeIds.get(name);
      if (id != null) {
        return id;
      }
    }
    return -1;
  }

  /** True when basic salary and hourly rate are unchanged (to 2 dp). */
  private boolean SamePay(EmployeeSalaryInfo a, EmployeeSalaryInfo b) {
    return (
      AmountsEqual(a.GetBasicSalary(), b.GetBasicSalary()) &&
      AmountsEqual(a.GetHourlyRate(), b.GetHourlyRate())
    );
  }

  /** Currency columns are DECIMAL(18,2); compare within half a centavo. */
  private boolean AmountsEqual(double x, double y) {
    return Math.abs(x - y) < 0.005;
  }
}
