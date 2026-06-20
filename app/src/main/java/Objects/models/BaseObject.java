package Objects.models;

/**
 * Root of every DB-mapped object.
 *
 * CHANGE: GetStatus() / SetStatus(boolean) renamed to IsActive() / SetActive(boolean).
 *
 * Reason: Several subclasses (LeaveRequest, OvertimeRequest, Payslip, etc.) have
 * their own domain-specific Status fields with enum return types.  Java does not
 * allow two methods with the same name but different return types, so the old
 * boolean GetStatus() caused an incompatible-return-type compile error in those
 * subclasses.  "IsActive" is also the semantically correct name for a soft-delete
 * flag — it reads correctly at every call site:
 *
 *   if (emp.IsActive()) { ... }          ← reads like a question
 *   .filter(EmpDetail::IsActive)         ← reads naturally as a predicate
 *
 * MIGRATION: Every caller of the old GetStatus() / SetStatus(boolean) must be
 * updated.  Search project-wide for:
 *   .IsActive()   → .IsActive()
 *   .SetStatus(    → .SetActive(
 *   :IsActive    → ::IsActive
 *
 * Subclasses that mapped the DB "Status" column via SetActive(rs.getBoolean("Status"))
 * must change that call to SetActive(rs.getBoolean("Status")).
 */
public abstract class BaseObject {

  private boolean active;

  public abstract Object GetIdentity();

  /** Returns true if this record is active (not soft-deleted). */
  public boolean IsActive() {
    return active;
  }

  /** Sets the active/soft-delete flag. */
  public void SetActive(boolean active) {
    this.active = active;
  }
}
