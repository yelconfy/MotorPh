package Processes;

import DataAccess.DatabaseConnector;
import DataAccess.RoleDAO;
import Interface.IMaintenanceProcess;
import Objects.models.AccountRole;
import Objects.results.SaveResult;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Process backing the Role Maintenance module (ROLEMGMT).
 *
 * BKL-01 stage 3e, resequenced ahead of 3c/3d (see TRACKER.md). First of the
 * two RBAC screens — the lower-risk half: Account_Role's own RoleName/RoleCode
 * are a flat 2-field table, same shape as DEPARTMENTS, and Delete is guarded
 * by RoleDAO.IsInUse against Users.RoleID (a real NOT NULL FK) before any
 * write, same discipline as every other soft-delete reference table tonight.
 *
 * RBACGRANTS (the role x module x permission grant matrix) is the genuinely
 * bespoke, higher-risk half — it does not fit IMaintenanceProcess<T> /
 * ReferenceMaintenancePanel<T> at all, and is deliberately NOT part of this
 * slice.
 *
 * No cross-field validation — Account_Role carries no CHECK constraint to
 * front-run (unlike LEAVETYPE's carry-over rule).
 */
public class RoleMaintenanceProcess
  extends BaseMaintenanceProcess
  implements IMaintenanceProcess<AccountRole>
{

  private final RoleDAO roleDAO;

  public RoleMaintenanceProcess(RoleDAO roleDAO) {
    this.roleDAO = roleDAO;
  }

  @Override
  public List<AccountRole> GetAll() {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      return roleDAO.GetAll(conn);
    } catch (SQLException e) {
      System.err.println("RoleMaintenanceProcess.GetAll: " + e.getMessage());
      return Collections.emptyList();
    }
  }

  @Override
  public SaveResult<Void> Add(AccountRole item) {
    boolean ok = ExecuteAtomic(conn -> roleDAO.Insert(conn, item));
    return ok ? SaveResult.success() : SaveResult.failed();
  }

  @Override
  public SaveResult<Void> Update(AccountRole item) {
    boolean ok = ExecuteAtomic(conn -> roleDAO.Update(conn, item));
    return ok ? SaveResult.success() : SaveResult.failed();
  }

  @Override
  public SaveResult<Void> Delete(long roleId) {
    // Hand-rolled (not ExecuteAtomic) so IsInUse can be checked and the
    // transaction rolled back before any write — same shape as every other
    // soft-delete maintenance process tonight.
    try (Connection conn = DatabaseConnector.GetConnection()) {
      conn.setAutoCommit(false);
      try {
        if (roleDAO.IsInUse(conn, (int) roleId)) {
          conn.rollback();
          return SaveResult.inUse(
            "This role can't be removed because one or more user accounts are " +
            "still assigned to it. Reassign them first."
          );
        }
        boolean ok = roleDAO.Delete(conn, (int) roleId);
        if (ok) {
          conn.commit();
          return SaveResult.success();
        }
        conn.rollback();
        return SaveResult.failed();
      } catch (SQLException e) {
        conn.rollback();
        System.err.println("RoleMaintenanceProcess.Delete: " + e.getMessage());
        return SaveResult.failed();
      }
    } catch (SQLException e) {
      System.err.println("RoleMaintenanceProcess.Delete (connection): " + e.getMessage());
      return SaveResult.failed();
    }
  }
}