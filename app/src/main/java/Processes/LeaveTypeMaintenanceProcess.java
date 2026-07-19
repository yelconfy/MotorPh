package Processes;

import DataAccess.DatabaseConnector;
import DataAccess.LeaveDAO;
import Interface.IMaintenanceProcess;
import Objects.models.LeaveTypeInfo;
import Objects.results.SaveResult;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

/**
 * Process backing the Leave Type Maintenance module (LEAVETYPE).
 *
 * BKL-01 stage 3b — third of the four flat tables, and the first to carry a
 * real cross-field business rule: CK_LeaveType_CarryOver (01 - Reference
 * Tables) requires CarryOverAllowed = 1 OR MaxCarryOverDays IS NULL.
 * ValidateCarryOver enforces the same rule up front, before any connection
 * opens, so a mismatched combination comes back as SaveResult.invalid(...)
 * with a specific message instead of a raw DB CHECK-constraint SQLException
 * surfacing as a generic FAILED — BKL-35's first VALIDATION_FAILED consumer,
 * per the B-rollout plan. LeaveDAO.Insert/Update still write NULL correctly
 * on their own as a mechanical mirror of the same rule; this validation is
 * what turns that mirror into a message the user can act on, not the only
 * thing standing between the app and the constraint.
 *
 * Like Deduction_Type/Allowance_Type (and unlike Departments/Positions),
 * Leave_Type has its own Status column, so Delete is a soft delete and
 * IN_USE is checked explicitly via LeaveDAO.IsInUse before the write, rather
 * than caught from a thrown SQLException.
 */
public class LeaveTypeMaintenanceProcess
  extends BaseMaintenanceProcess
  implements IMaintenanceProcess<LeaveTypeInfo>
{

  private final LeaveDAO leaveDAO;

  public LeaveTypeMaintenanceProcess(LeaveDAO leaveDAO) {
    this.leaveDAO = leaveDAO;
  }

  // -------------------------------------------------------------------------
  // Read
  // -------------------------------------------------------------------------

  @Override
  public List<LeaveTypeInfo> GetAll() {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      return leaveDAO.GetAllLeaveTypes(conn);
    } catch (SQLException e) {
      System.err.println("LeaveTypeMaintenanceProcess.GetAll: " + e.getMessage());
      return Collections.emptyList();
    }
  }

  // -------------------------------------------------------------------------
  // Writes
  // -------------------------------------------------------------------------

  @Override
  public SaveResult<Void> Add(LeaveTypeInfo item) {
    String carryOverError = ValidateCarryOver(item);
    if (carryOverError != null) {
      return SaveResult.invalid(carryOverError);
    }
    boolean ok = ExecuteAtomic(conn -> leaveDAO.Insert(conn, item));
    return ok ? SaveResult.success() : SaveResult.failed();
  }

  @Override
  public SaveResult<Void> Update(LeaveTypeInfo item) {
    String carryOverError = ValidateCarryOver(item);
    if (carryOverError != null) {
      return SaveResult.invalid(carryOverError);
    }
    boolean ok = ExecuteAtomic(conn -> leaveDAO.Update(conn, item));
    return ok ? SaveResult.success() : SaveResult.failed();
  }

  @Override
  public SaveResult<Void> Delete(long leaveTypeId) {
    // Hand-rolled (not ExecuteAtomic) so IsInUse can be checked and the
    // transaction rolled back before any write — same shape as
    // DeductionTypeMaintenanceProcess.Delete / AllowanceTypeMaintenanceProcess.Delete.
    try (Connection conn = DatabaseConnector.GetConnection()) {
      conn.setAutoCommit(false);
      try {
        if (leaveDAO.IsInUse(conn, (int) leaveTypeId)) {
          conn.rollback();
          return SaveResult.inUse(
            "This leave type is still referenced by one or more leave requests " +
            "or entitlements and can't be removed."
          );
        }
        boolean ok = leaveDAO.Delete(conn, (int) leaveTypeId);
        if (ok) {
          conn.commit();
          return SaveResult.success();
        }
        conn.rollback();
        return SaveResult.failed();
      } catch (SQLException e) {
        conn.rollback();
        System.err.println("LeaveTypeMaintenanceProcess.Delete: " + e.getMessage());
        return SaveResult.failed();
      }
    } catch (SQLException e) {
      System.err.println("LeaveTypeMaintenanceProcess.Delete (connection): " + e.getMessage());
      return SaveResult.failed();
    }
  }

  /**
   * Mirrors CK_LeaveType_CarryOver (CarryOverAllowed = 1 OR MaxCarryOverDays
   * IS NULL) at the app layer: a nonzero max carry-over with carry-over not
   * allowed is a genuine input mistake, not a technical failure. A blank/zero
   * max carry-over is always fine either way — LeaveDAO writes NULL whenever
   * CarryOverAllowed is false regardless of what this field holds.
   */
  private String ValidateCarryOver(LeaveTypeInfo item) {
    if (!item.IsCarryOverAllowed() && item.GetMaxCarryOverDays() != 0) {
      return "Max carry-over days must be blank when carry-over isn't allowed.";
    }
    return null;
  }
}