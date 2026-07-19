package Interface;

import Objects.enums.Status.RequestStatus;
import Objects.models.LeaveRequest;
import Objects.models.OvertimeRequest;
import java.util.List;
import java.util.Map;

/**
 * Contract for the admin-facing approval screens (Phase 7a; audited in 7c).
 *
 * This ERP is back-office only: requests are FILED by a separate employee-facing
 * app that inserts PENDING rows into the shared Leave_Request / Overtime_Request
 * tables. Here HR only REVIEWS and ACTIONS (approve / reject) those pending rows.
 *
 * Phase 7c: each action now also writes an Audit_Log UPDATE (old status -> new)
 * on the same transaction as the status change, stamped with the acting
 * username — so approvals are traceable. The actionedByUserId is still written
 * to *_Request.ActionedBy (FK to Users); the username is the audit stamp.
 */
public interface IApprovalProcess {

  /** Pending leave requests across all employees (Leave_Type joined for the name). */
  List<LeaveRequest> GetPendingLeave();

  /** Pending overtime requests across all employees. */
  List<OvertimeRequest> GetPendingOvertime();

  /**
   * EmployeeID -> display name ("Last, First"), built once so the panels can
   * label rows without an N+1 lookup.
   */
  Map<Long, String> GetEmployeeDirectory();

  /**
   * Approve or reject a leave request. {@code decision} must be APPROVED or
   * REJECTED. Stamps ActionedBy = actionedByUserId and audits the change as
   * username.
   */
  boolean ActionLeave(
    long leaveRequestId,
    RequestStatus decision,
    long actionedByUserId,
    String username
  );

  /**
   * Approve or reject an overtime request. {@code decision} must be APPROVED or
   * REJECTED. Stamps ActionedBy = actionedByUserId and audits the change as
   * username.
   */
  boolean ActionOvertime(
    long overtimeRequestId,
    RequestStatus decision,
    long actionedByUserId,
    String username
  );
}