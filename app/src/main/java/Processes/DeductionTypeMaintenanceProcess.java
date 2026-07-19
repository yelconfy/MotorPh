package Processes;

import DataAccess.DatabaseConnector;
import DataAccess.DeductionDAO;
import Interface.IMaintenanceProcess;
import Objects.models.DeductionTypeInfo;
import Objects.results.SaveResult;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Process backing the Deduction Type Maintenance module (DEDUCTIONTYPE).
 *
 * BKL-01 stage 3b — first of the four remaining flat tables, and the only one
 * that needed nothing beyond the BKL-26 field-kind extension already shipped
 * (COMBO for Category + protectedWhen for the four statutory rows).
 *
 * Shape difference vs Department/PositionMaintenanceProcess: Deduction_Type
 * has its own Status column, so Delete is a soft delete (DeductionDAO.Delete
 * -> UPDATE ... SET Status = 0), not a hard DELETE guarded by an FK exception.
 * That means IN_USE has to be checked explicitly before the write rather than
 * caught from a thrown SQLException — see DeductionDAO.IsInUse.
 */
public class DeductionTypeMaintenanceProcess
  extends BaseMaintenanceProcess
  implements IMaintenanceProcess<DeductionTypeInfo>
{

  private final DeductionDAO deductionDAO;

  public DeductionTypeMaintenanceProcess(DeductionDAO deductionDAO) {
    this.deductionDAO = deductionDAO;
  }

  // -------------------------------------------------------------------------
  // Read
  // -------------------------------------------------------------------------

  @Override
  public List<DeductionTypeInfo> GetAll() {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      return deductionDAO.GetAllTypes(conn);
    } catch (SQLException e) {
      System.err.println(
        "DeductionTypeMaintenanceProcess.GetAll: " + e.getMessage()
      );
      return Collections.emptyList();
    }
  }

  // -------------------------------------------------------------------------
  // Writes
  // -------------------------------------------------------------------------

  @Override
  public SaveResult<Void> Add(DeductionTypeInfo item) {
    boolean ok = ExecuteAtomic(conn -> deductionDAO.Insert(conn, item));
    return ok ? SaveResult.success() : SaveResult.failed();
  }

  @Override
  public SaveResult<Void> Update(DeductionTypeInfo item) {
    boolean ok = ExecuteAtomic(conn -> deductionDAO.Update(conn, item));
    return ok ? SaveResult.success() : SaveResult.failed();
  }

  @Override
  public SaveResult<Void> Delete(long deductionTypeId) {
    // Hand-rolled (not ExecuteAtomic) so IsInUse can be checked and the
    // transaction rolled back before any write — same shape as
    // DepartmentMaintenanceProcess.Delete, just guarding proactively instead
    // of catching SQL error 547 after the fact.
    try (Connection conn = DatabaseConnector.GetConnection()) {
      conn.setAutoCommit(false);
      try {
        if (deductionDAO.IsInUse(conn, (int) deductionTypeId)) {
          conn.rollback();
          return SaveResult.inUse(
            "This deduction type is still in use and can't be removed."
          );
        }
        boolean ok = deductionDAO.Delete(conn, (int) deductionTypeId);
        if (ok) {
          conn.commit();
          return SaveResult.success();
        }
        conn.rollback();
        return SaveResult.failed();
      } catch (SQLException e) {
        conn.rollback();
        System.err.println(
          "DeductionTypeMaintenanceProcess.Delete: " + e.getMessage()
        );
        return SaveResult.failed();
      }
    } catch (SQLException e) {
      System.err.println(
        "DeductionTypeMaintenanceProcess.Delete (connection): " + e.getMessage()
      );
      return SaveResult.failed();
    }
  }
}
