package Processes;

import DataAccess.AllowanceDAO;
import DataAccess.DatabaseConnector;
import Interface.IMaintenanceProcess;
import Objects.models.AllowanceTypeInfo;
import Objects.results.SaveResult;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Process backing the Allowance Type Maintenance module (ALLOWANCETYPE).
 *
 * BKL-01 stage 3b — second of the four flat tables, and the first to use the
 * CHECKBOX field kind (IsTaxable / IsRecurring). Structurally identical to
 * DeductionTypeMaintenanceProcess: Allowance_Type has its own Status column, so
 * Delete is a soft delete (AllowanceDAO.Delete -> UPDATE ... SET Status = 0),
 * and IN_USE is checked explicitly via AllowanceDAO.IsInUse before the write
 * rather than caught from a thrown SQLException.
 */
public class AllowanceTypeMaintenanceProcess
  extends BaseMaintenanceProcess
  implements IMaintenanceProcess<AllowanceTypeInfo>
{

  private final AllowanceDAO allowanceDAO;

  public AllowanceTypeMaintenanceProcess(AllowanceDAO allowanceDAO) {
    this.allowanceDAO = allowanceDAO;
  }

  // -------------------------------------------------------------------------
  // Read
  // -------------------------------------------------------------------------

  @Override
  public List<AllowanceTypeInfo> GetAll() {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      return allowanceDAO.GetAllTypes(conn);
    } catch (SQLException e) {
      System.err.println(
        "AllowanceTypeMaintenanceProcess.GetAll: " + e.getMessage()
      );
      return Collections.emptyList();
    }
  }

  // -------------------------------------------------------------------------
  // Writes
  // -------------------------------------------------------------------------

  @Override
  public SaveResult<Void> Add(AllowanceTypeInfo item) {
    boolean ok = ExecuteAtomic(conn -> allowanceDAO.Insert(conn, item));
    return ok ? SaveResult.success() : SaveResult.failed();
  }

  @Override
  public SaveResult<Void> Update(AllowanceTypeInfo item) {
    boolean ok = ExecuteAtomic(conn -> allowanceDAO.Update(conn, item));
    return ok ? SaveResult.success() : SaveResult.failed();
  }

  @Override
  public SaveResult<Void> Delete(long allowanceTypeId) {
    // Hand-rolled (not ExecuteAtomic) so IsInUse can be checked and the
    // transaction rolled back before any write — same shape as
    // DeductionTypeMaintenanceProcess.Delete.
    try (Connection conn = DatabaseConnector.GetConnection()) {
      conn.setAutoCommit(false);
      try {
        if (allowanceDAO.IsInUse(conn, (int) allowanceTypeId)) {
          conn.rollback();
          return SaveResult.inUse(
            "This allowance type is still in use and can't be removed."
          );
        }
        boolean ok = allowanceDAO.Delete(conn, (int) allowanceTypeId);
        if (ok) {
          conn.commit();
          return SaveResult.success();
        }
        conn.rollback();
        return SaveResult.failed();
      } catch (SQLException e) {
        conn.rollback();
        System.err.println(
          "AllowanceTypeMaintenanceProcess.Delete: " + e.getMessage()
        );
        return SaveResult.failed();
      }
    } catch (SQLException e) {
      System.err.println(
        "AllowanceTypeMaintenanceProcess.Delete (connection): " + e.getMessage()
      );
      return SaveResult.failed();
    }
  }
}
