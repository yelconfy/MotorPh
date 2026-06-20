package Interface;

import Objects.models.DepartmentInfo;
import Objects.models.EmpDetail;
import Objects.models.PositionInfo;
import Objects.models.WorkScheduleInfo;
import java.util.List;

public interface IEmpMgmtProcess {
  List<EmpDetail> GetEmpDetails();

  List<EmpDetail> SearchEmployee(String query);

  EmpDetail GetCompleteEmployee(long empNo);

  boolean AddEmployee(EmpDetail newEmployee);

  boolean UpdateEmployee(EmpDetail updatedEmp);

  boolean DeleteEmployee(long empNo);

  // --- Reference-data reads for editor dropdowns (P2-0) ---
  List<PositionInfo> GetAllPositions();

  List<DepartmentInfo> GetAllDepartments();

  List<WorkScheduleInfo> GetAllSchedules();
}