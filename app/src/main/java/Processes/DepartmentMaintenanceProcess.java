package Processes;

import DataAccess.DatabaseConnector;
import DataAccess.DepartmentDAO;
import Interface.IMaintenanceProcess;
import Objects.models.DepartmentInfo;
import Objects.results.SaveResult;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Process backing the Department Maintenance module (DEPARTMENTS).
 *
 * BKL-01 stage 3a — the first genuinely descriptor-driven maintenance slice
 * (POSITIONS was the BKL-26 retrofit proof). Structurally identical to
 * PositionMaintenanceProcess: implements the generic
 * IMaintenanceProcess<DepartmentInfo>, extends BaseMaintenanceProcess, and
 * talks only to DepartmentDAO — the Panel talks only to this interface, never
 * to the DAO directly.
 *
 * The one shape difference vs POSITIONS: a department carries two user-entered
 * columns (DepartmentCode + DepartmentName), so Add hands the whole
 * DepartmentInfo the generic panel built to the DAO rather than a bare String.
 *
 * Transaction discipline mirrors the rest of the layer:
 *   - GetAll self-opens a Connection and degrades to an empty list on failure;
 *   - Add / Update go through BaseMaintenanceProcess.ExecuteAtomic;
 *   - Delete is hand-rolled (NOT via ExecuteAtomic) so it can inspect the
 *     SQLException and tell IN_USE (FK 547) apart from a generic failure —
 *     the exact same reasoning as PositionMaintenanceProcess.Delete.
 */
public class DepartmentMaintenanceProcess
  extends BaseMaintenanceProcess
  implements IMaintenanceProcess<DepartmentInfo>
{

  // SQL Server raises error 547 on a FK / constraint conflict (e.g. deleting a
  // department an employee still points at). Used to map a blocked delete to IN_USE.
  private static final int SQL_FK_VIOLATION = 547;

  private final DepartmentDAO departmentDAO;

  public DepartmentMaintenanceProcess(DepartmentDAO departmentDAO) {
    this.departmentDAO = departmentDAO;
  }

  // -------------------------------------------------------------------------
  // Read
  // -------------------------------------------------------------------------

  @Override
  public List<DepartmentInfo> GetAll() {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      return departmentDAO.GetAll(conn);
    } catch (SQLException e) {
      System.err.println(
        "DepartmentMaintenanceProcess.GetAll: " + e.getMessage()
      );
      return Collections.emptyList();
    }
  }

  // -------------------------------------------------------------------------
  // Writes
  // -------------------------------------------------------------------------

  @Override
  public SaveResult<Void> Add(DepartmentInfo item) {
    boolean ok = ExecuteAtomic(conn -> departmentDAO.Insert(conn, item));
    return ok ? SaveResult.success() : SaveResult.failed();
  }

  @Override
  public SaveResult<Void> Update(DepartmentInfo item) {
    boolean ok = ExecuteAtomic(conn -> departmentDAO.Update(conn, item));
    return ok ? SaveResult.success() : SaveResult.failed();
  }

  @Override
  public SaveResult<Void> Delete(long departmentID) {
    // Not routed through ExecuteAtomic: that helper swallows the SQLException,
    // and we specifically need to inspect it to tell IN_USE (FK) apart from a
    // generic failure. Same commit/rollback shape, just with the error surfaced.
    try (Connection conn = DatabaseConnector.GetConnection()) {
      conn.setAutoCommit(false);
      try {
        boolean ok = departmentDAO.Delete(conn, (int) departmentID);
        if (ok) {
          conn.commit();
          return SaveResult.success();
        }
        conn.rollback();
        return SaveResult.failed();
      } catch (SQLException e) {
        conn.rollback();
        if (e.getErrorCode() == SQL_FK_VIOLATION) {
          return SaveResult.inUse(
            "This department is still assigned to employees and can't be removed."
          );
        }
        System.err.println(
          "DepartmentMaintenanceProcess.Delete: " + e.getMessage()
        );
        return SaveResult.failed();
      }
    } catch (SQLException e) {
      System.err.println(
        "DepartmentMaintenanceProcess.Delete (connection): " + e.getMessage()
      );
      return SaveResult.failed();
    }
  }
}
