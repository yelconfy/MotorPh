package Processes;

import DataAccess.DatabaseConnector;
import DataAccess.PositionDAO;
import Interface.IMaintenanceProcess;
import Objects.models.PositionInfo;
import Objects.results.SaveResult;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Process backing the Position Maintenance module (POSITIONS).
 *
 * BKL-26 retrofit: implements the generic IMaintenanceProcess<PositionInfo>
 * directly instead of the retired IPositionMaintenanceProcess. Method bodies
 * are unchanged from the original — only the names moved to match the
 * generic contract (GetAllPositions -> GetAll, UpdatePosition -> Update,
 * DeletePosition -> Delete) and Add now takes the whole PositionInfo the
 * generic panel builds, extracting the name itself instead of receiving it
 * as a bare String.
 *
 * Transaction discipline mirrors the rest of the layer:
 *   - reads self-open a Connection and degrade to an empty list on failure;
 *   - writes go through BaseMaintenanceProcess.ExecuteAtomic, except Delete,
 *     which needs to inspect the SQLException itself to tell IN_USE (FK)
 *     apart from a generic failure.
 *
 * Talks only to PositionDAO — the Panel talks only to this interface, never
 * to the DAO directly.
 */
public class PositionMaintenanceProcess
  extends BaseMaintenanceProcess
  implements IMaintenanceProcess<PositionInfo>
{

  // SQL Server raises error 547 on a FK / constraint conflict (e.g. deleting a
  // position an employee still points at). Used to map a blocked delete to IN_USE.
  private static final int SQL_FK_VIOLATION = 547;

  private final PositionDAO positionDAO;

  public PositionMaintenanceProcess(PositionDAO positionDAO) {
    this.positionDAO = positionDAO;
  }

  // -------------------------------------------------------------------------
  // Read
  // -------------------------------------------------------------------------

  @Override
  public List<PositionInfo> GetAll() {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      return positionDAO.GetAll(conn);
    } catch (SQLException e) {
      System.err.println(
        "PositionMaintenanceProcess.GetAll: " + e.getMessage()
      );
      return Collections.emptyList();
    }
  }

  // -------------------------------------------------------------------------
  // Writes
  // -------------------------------------------------------------------------

  @Override
  public SaveResult<Void> Add(PositionInfo item) {
    boolean ok = ExecuteAtomic(conn ->
      positionDAO.Insert(conn, item.GetPositionName())
    );
    return ok ? SaveResult.success() : SaveResult.failed();
  }

  @Override
  public SaveResult<Void> Update(PositionInfo item) {
    boolean ok = ExecuteAtomic(conn -> positionDAO.Update(conn, item));
    return ok ? SaveResult.success() : SaveResult.failed();
  }

  @Override
  public SaveResult<Void> Delete(long positionID) {
    // Not routed through ExecuteAtomic: that helper swallows the SQLException,
    // and we specifically need to inspect it to tell IN_USE (FK) apart from a
    // generic failure. Same commit/rollback shape, just with the error surfaced.
    try (Connection conn = DatabaseConnector.GetConnection()) {
      conn.setAutoCommit(false);
      try {
        boolean ok = positionDAO.Delete(conn, positionID);
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
            "This position is still assigned to employees and can't be removed."
          );
        }
        System.err.println(
          "PositionMaintenanceProcess.Delete: " + e.getMessage()
        );
        return SaveResult.failed();
      }
    } catch (SQLException e) {
      System.err.println(
        "PositionMaintenanceProcess.Delete (connection): " + e.getMessage()
      );
      return SaveResult.failed();
    }
  }
}
