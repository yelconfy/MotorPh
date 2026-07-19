package Objects.enums;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * The single source of truth for Module.ModuleCode (BKL-24).
 *
 * Before this enum, a module code such as "POSITIONS" was a raw string written
 * in four independent places with nothing checking they agreed:
 *
 *   1. the views.put(...) map key in a module pack
 *   2. the AccessDAO.GetPermissionCodes(..., "POSITIONS") argument in the SAME
 *      registration
 *   3. the Module seed in "09 - Seed Access Control RBAC.sql"
 *   4. by convention, the nav icon at Assets/Icons/positions.svg
 *
 * Both ways of getting it wrong failed SILENTLY:
 *
 *   - Typo the map key      -> ShellFrame intersects granted modules against
 *                              registered keys, so the module simply never
 *                              appears in the nav.
 *   - Typo the perms lookup -> AccessDAO.GetPermissionCodes swallows the
 *                              SQLException and returns an empty list; an empty
 *                              list makes canAdd/canEdit/canDelete all false, so
 *                              the screen renders with an EMPTY TOOLBAR and no
 *                              error. Looks like an RBAC seed bug. Isn't one.
 *
 * The enum makes both a compile error. ModuleReconciler then cross-checks these
 * constants against the live Module table AND the registered view suppliers at
 * startup, so a code that drifts out of sync with script 09 is reported loudly
 * instead of quietly hiding a screen.
 *
 * INVARIANT: the constants below MUST mirror the Module seed in script 09,
 * one-for-one. Adding a module = add a constant here + a row in script 09 +
 * a registration in the owning pack. ModuleReconciler enforces all three.
 *
 * The icon convention (Constants.ModuleIcons) is unchanged: it lower-cases the
 * code and looks for Assets/Icons/<code>.svg, falling back to _default.svg — so
 * nothing here needs to know about icons.
 */
public enum ModuleCode {
  // ---- Operations (day-to-day transactional screens) ----------------------
  PAYROLL("PAYROLL", "Payroll"),
  EMPMGMT("EMPMGMT", "Employee Management"),
  TIMEKEEPING("TIMEKEEPING", "Timekeeping"),
  PAYSLIP("PAYSLIP", "Payslip Register"),
  LEAVE("LEAVE", "Leave Approvals"),
  OVERTIME("OVERTIME", "Overtime Approvals"),
  DTR("DTR", "Daily Time Record"),
  PUNCHFIX("PUNCHFIX", "Punch Correction"),
  ACTIVITY("ACTIVITY", "Activity Log"),

  // ---- Reporting (read-only, cross-employee, PDF-rendering) ---------------
  PAYSUMMARY("PAYSUMMARY", "Payroll Summary"),
  THIRTEENTHMONTH("THIRTEENTHMONTH", "13th Month Pay"),
  REMITTANCE("REMITTANCE", "Statutory Remittance"),
  BIR2316("BIR2316", "BIR Form 2316"),
  LEAVEBAL("LEAVEBAL", "Leave Balance Report"),
  LOANLEDGER("LOANLEDGER", "Loan Ledger Report"),

  // ---- Reference-data maintenance (Admin-owned, one screen per table) -----
  POSITIONS("POSITIONS", "Position Maintenance"),
  DEPARTMENTS("DEPARTMENTS", "Department Maintenance"),
  LEAVETYPE("LEAVETYPE", "Leave Type Maintenance"),
  ALLOWANCETYPE("ALLOWANCETYPE", "Allowance Type Maintenance"),
  DEDUCTIONTYPE("DEDUCTIONTYPE", "Deduction Type Maintenance"),
  WORKSCHEDULE("WORKSCHEDULE", "Work Schedule Maintenance"),
  HOLIDAY("HOLIDAY", "Holiday Maintenance"),
  PREMIUMRATE("PREMIUMRATE", "Premium Rate Maintenance"),
  SSSTABLE("SSSTABLE", "SSS Contribution Table"),
  CONTRIBRATE("CONTRIBRATE", "Contribution Rate Table"),
  WHTTABLE("WHTTABLE", "Withholding Tax Table"),
  ROLEMGMT("ROLEMGMT", "Role Maintenance"),
  RBACGRANTS("RBACGRANTS", "Access Control Grants");

  private final String code;
  private final String displayName;

  ModuleCode(String code, String displayName) {
    this.code = code;
    this.displayName = displayName;
  }

  /** The stable app key — matches Module.ModuleCode in the DB. */
  public String GetCode() {
    return code;
  }

  /**
   * The seed's display label. NOTE: the nav still renders
   * AppModule.GetModuleName() (i.e. the DB value), not this — this exists so the
   * reconciler can report a human-readable name for a module that is declared
   * here but missing from the DB, where no AppModule row exists to name it.
   */
  public String GetDisplayName() {
    return displayName;
  }

  /** Every declared code, in declaration order. */
  public static Set<String> AllCodes() {
    Set<String> codes = new LinkedHashSet<>();
    for (ModuleCode m : values()) {
      codes.add(m.code);
    }
    return codes;
  }

  /** Resolves a raw DB/string code, or null if it is not a declared module. */
  public static ModuleCode From(String raw) {
    if (raw == null) {
      return null;
    }
    String needle = raw.trim();
    return Arrays.stream(values())
      .filter(m -> m.code.equalsIgnoreCase(needle))
      .findFirst()
      .orElse(null);
  }

  @Override
  public String toString() {
    return code;
  }
}