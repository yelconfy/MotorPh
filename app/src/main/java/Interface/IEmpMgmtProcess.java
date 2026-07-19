package Interface;

import Objects.models.DepartmentInfo;
import Objects.models.EmpDetail;
import Objects.models.PositionInfo;
import Objects.models.WorkScheduleInfo;
import Objects.results.SaveResult;
import java.util.List;

/**
 * BKL-35 B-rollout (step 2): AddEmployee/UpdateEmployee now report through
 * SaveResult<Long> instead of a bare boolean. The payload is the employee's
 * ID in both cases — newly generated on Add, echoed back on Update — so the
 * panel can reselect the saved row the same way regardless of mode, instead
 * of branching on Mode to decide where the ID comes from.
 *
 * DeleteEmployee stays boolean; it is not in scope for this slice (only
 * Add/Update were called out in the B-rollout plan).
 */
public interface IEmpMgmtProcess {
  List<EmpDetail> GetEmpDetails();

  List<EmpDetail> SearchEmployee(String query);

  EmpDetail GetCompleteEmployee(long empNo);

  SaveResult<Long> AddEmployee(EmpDetail newEmployee);

  SaveResult<Long> UpdateEmployee(EmpDetail updatedEmp);

  boolean DeleteEmployee(long empNo);

  // --- Reference-data reads for editor dropdowns (P2-0) ---
  List<PositionInfo> GetAllPositions();

  List<DepartmentInfo> GetAllDepartments();

  List<WorkScheduleInfo> GetAllSchedules();
}