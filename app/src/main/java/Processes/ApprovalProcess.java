package Processes;

import DataAccess.AuditLogDAO;
import DataAccess.DatabaseConnector;
import DataAccess.EmployeeDAO;
import DataAccess.LeaveDAO;
import DataAccess.OvertimeDAO;
import Interface.IApprovalProcess;
import Objects.enums.Status.AuditAction;
import Objects.enums.Status.RequestStatus;
import Objects.models.EmployeeInfo;
import Objects.models.LeaveRequest;
import Objects.models.OvertimeRequest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Process backing the Leave Approvals and Overtime Approvals modules (Phase 7a),
 * audited in Phase 7c.
 *
 * One process serves BOTH panels. Transaction discipline:
 *   - reads self-open a Connection and degrade to an empty list on failure;
 *   - writes go through BaseMaintenanceProcess.ExecuteAtomic, and the Audit_Log
 *     row is written on the SAME connection inside that transaction, so the
 *     status change and its audit trail commit or roll back together.
 *
 * The pending list is Status = PENDING by definition (GetPending filters it), so
 * the audited OldValue is always "PENDING"; NewValue is the decision name.
 */
public class ApprovalProcess
  extends BaseMaintenanceProcess
  implements IApprovalProcess {

  private static final String LEAVE_TABLE = "Leave_Request";
  private static final String OVERTIME_TABLE = "Overtime_Request";

  private final LeaveDAO leaveDAO;
  private final OvertimeDAO overtimeDAO;
  private final EmployeeDAO empDAO;
  private final AuditLogDAO auditLogDAO;

  public ApprovalProcess(
    LeaveDAO leaveDAO,
    OvertimeDAO overtimeDAO,
    EmployeeDAO empDAO,
    AuditLogDAO auditLogDAO
  ) {
    this.leaveDAO = leaveDAO;
    this.overtimeDAO = overtimeDAO;
    this.empDAO = empDAO;
    this.auditLogDAO = auditLogDAO;
  }

  // -------------------------------------------------------------------------
  // Reads
  // -------------------------------------------------------------------------

  @Override
  public List<LeaveRequest> GetPendingLeave() {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      return leaveDAO.GetPending(conn);
    } catch (SQLException e) {
      System.err.println("ApprovalProcess.GetPendingLeave: " + e.getMessage());
      return Collections.emptyList();
    }
  }

  @Override
  public List<OvertimeRequest> GetPendingOvertime() {
    try (Connection conn = DatabaseConnector.GetConnection()) {
      return overtimeDAO.GetPending(conn);
    } catch (SQLException e) {
      System.err.println("ApprovalProcess.GetPendingOvertime: " + e.getMessage());
      return Collections.emptyList();
    }
  }

  @Override
  public Map<Long, String> GetEmployeeDirectory() {
    Map<Long, String> directory = new LinkedHashMap<>();
    try {
      for (EmployeeInfo info : empDAO.GetAllBasicInfo()) {
        directory.put(info.GetEmployeeId(), FormatName(info));
      }
    } catch (SQLException e) {
      System.err.println("ApprovalProcess.GetEmployeeDirectory: " + e.getMessage());
    }
    return directory;
  }

  // -------------------------------------------------------------------------
  // Writes (atomic — status change + audit row in one transaction)
  // -------------------------------------------------------------------------

  @Override
  public boolean ActionLeave(
    long leaveRequestId,
    RequestStatus decision,
    long actionedByUserId,
    String username
  ) {
    if (!IsDecision(decision)) {
      return false;
    }
    return ExecuteAtomic(conn -> {
      boolean ok = leaveDAO.Action(conn, leaveRequestId, decision, actionedByUserId);
      if (!ok) {
        return false;
      }
      auditLogDAO.Log(
        conn,
        username,
        LEAVE_TABLE,
        String.valueOf(leaveRequestId),
        AuditAction.UPDATE,
        RequestStatus.PENDING.name(),
        decision.name()
      );
      return true;
    });
  }

  @Override
  public boolean ActionOvertime(
    long overtimeRequestId,
    RequestStatus decision,
    long actionedByUserId,
    String username
  ) {
    if (!IsDecision(decision)) {
      return false;
    }
    return ExecuteAtomic(conn -> {
      boolean ok = overtimeDAO.Action(conn, overtimeRequestId, decision, actionedByUserId);
      if (!ok) {
        return false;
      }
      auditLogDAO.Log(
        conn,
        username,
        OVERTIME_TABLE,
        String.valueOf(overtimeRequestId),
        AuditAction.UPDATE,
        RequestStatus.PENDING.name(),
        decision.name()
      );
      return true;
    });
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private static boolean IsDecision(RequestStatus s) {
    return s == RequestStatus.APPROVED || s == RequestStatus.REJECTED;
  }

  private static String FormatName(EmployeeInfo info) {
    String last = info.GetLastName();
    String first = info.GetFirstName();
    if (last == null && first == null) {
      return "Employee #" + info.GetEmployeeId();
    }
    if (last == null) {
      return first;
    }
    if (first == null) {
      return last;
    }
    return last + ", " + first;
  }
}