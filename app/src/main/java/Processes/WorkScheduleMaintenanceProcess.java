package Processes;

import DataAccess.DatabaseConnector;
import DataAccess.WorkScheduleDAO;
import Interface.IMaintenanceProcess;
import Objects.models.WorkScheduleInfo;
import Objects.results.SaveResult;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

public class WorkScheduleMaintenanceProcess
  extends BaseMaintenanceProcess
  implements IMaintenanceProcess<WorkScheduleInfo>
{

  private final WorkScheduleDAO workScheduleDAO;

  public WorkScheduleMaintenanceProcess(WorkScheduleDAO workScheduleDAO) {
    this.workScheduleDAO = workScheduleDAO;
  }

  @Override
  public List<WorkScheduleInfo> GetAll() {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      return workScheduleDAO.GetAll(conn);
    } catch (SQLException e) {
      System.err.println("WorkScheduleMaintenanceProcess.GetAll: " + e.getMessage());
      return Collections.emptyList();
    }
  }

  @Override
  public SaveResult<Void> Add(WorkScheduleInfo item) {
    boolean ok = ExecuteAtomic(conn -> workScheduleDAO.Insert(conn, item));
    return ok ? SaveResult.success() : SaveResult.failed();
  }

  @Override
  public SaveResult<Void> Update(WorkScheduleInfo item) {
    boolean ok = ExecuteAtomic(conn -> workScheduleDAO.Update(conn, item));
    return ok ? SaveResult.success() : SaveResult.failed();
  }

  @Override
  public SaveResult<Void> Delete(long scheduleId) {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      conn.setAutoCommit(false);
      try {
        if (workScheduleDAO.IsInUse(conn, (int) scheduleId)) {
          conn.rollback();
          return SaveResult.inUse(
            "This work schedule can't be removed because one or more employees " +
            "are still assigned to it. Reassign them first."
          );
        }
        boolean ok = workScheduleDAO.Delete(conn, (int) scheduleId);
        if (ok) {
          conn.commit();
          return SaveResult.success();
        }
        conn.rollback();
        return SaveResult.failed();
      } catch (SQLException e) {
        conn.rollback();
        System.err.println("WorkScheduleMaintenanceProcess.Delete: " + e.getMessage());
        return SaveResult.failed();
      }
    } catch (SQLException e) {
      System.err.println("WorkScheduleMaintenanceProcess.Delete (connection): " + e.getMessage());
      return SaveResult.failed();
    }
  }
}