package Helper;

import DataAccess.AccessDAO;
import Objects.enums.ModuleCode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Startup integrity check for module wiring (BKL-24).
 *
 * A module is only reachable if THREE things agree:
 *
 *   (1) CODE — a ModuleCode constant exists
 *   (2) DB   — a Module row is seeded (script 09)
 *   (3) VIEW — a view supplier is registered in one of the module packs
 *
 * Any two-out-of-three is a bug, and every one of them used to fail quietly.
 * The proof: LOANLEDGER shipped with a DAO, Interface, Process, Panel and PDF
 * renderer, and a seeded Module row — but no Injector factory and no
 * ReportingModules registration. It was DB+CODE without VIEW, so the nav row
 * never appeared for anyone and nothing said so. That is the exact class of
 * failure this reconciler exists to make loud.
 *
 * Run once from Injector.CreateShell(), BEFORE the shell is constructed, so the
 * report lands in the console before any UI noise.
 *
 * NOTE this is deliberately non-fatal. It prints; it does not throw. A missing
 * registration should not stop a demo or a grading run — the app degrades to
 * "that module isn't in the nav", which is what happens today anyway. The point
 * is that you now find out WHY, immediately, instead of hunting an RBAC ghost.
 *
 * NOTE also: this is not the same as ShellFrame.resolveNavModules(), which
 * already warns when the CURRENT ROLE is granted a module with no registered
 * view. That check is role-scoped, so a module no role happens to be granted is
 * invisible to it, and it cannot see registered-but-not-seeded at all. This one
 * is role-independent and checks all three sets both ways.
 */
public final class ModuleReconciler {

  private ModuleReconciler() {}

  /**
   * Cross-checks the ModuleCode enum, the live Module table, and the registered
   * view keys. Prints a report; returns the number of problems found (0 = clean)
   * so a caller or a future test can assert on it.
   */
  public static int Reconcile(Set<String> registeredKeys, AccessDAO accessDAO) {
    Set<String> declared = ModuleCode.AllCodes();
    Set<String> seeded = new LinkedHashSet<>(accessDAO.GetAllModuleCodes());
    Set<String> registered = (registeredKeys == null)
      ? new LinkedHashSet<>()
      : new LinkedHashSet<>(registeredKeys);

    // If the Module table is empty, script 09 was never run. Every other finding
    // would be noise downstream of that one fact, so say it and stop.
    if (seeded.isEmpty()) {
      System.err.println(
        "[ModuleReconciler] Module table is EMPTY -- script 09 has not been run " +
        "against this database. No module will appear in the nav for any role."
      );
      return 1;
    }

    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();

    // ---- ERROR: registered, but not a declared ModuleCode -------------------
    // A pack registered a key the enum doesn't know. Only reachable via a raw
    // string literal, so it means a pack bypassed ModuleCode — or the enum is
    // missing a constant it should have.
    for (String key : registered) {
      if (!declared.contains(key)) {
        errors.add(
          "REGISTERED '" + key + "' is not a declared ModuleCode. The pack is " +
          "using a raw string, or ModuleCode is missing this constant."
        );
      }
    }

    // ---- ERROR: seeded in the DB, but not a declared ModuleCode -------------
    // Script 09 is ahead of the code. The module exists and is grantable, but
    // nothing in Java can name it.
    for (String code : seeded) {
      if (!declared.contains(code)) {
        errors.add(
          "SEEDED '" + code + "' has no ModuleCode constant. Script 09 is ahead " +
          "of the code -- add the constant."
        );
      }
    }

    // ---- ERROR: declared + registered, but NOT seeded -----------------------
    // The screen is fully wired in Java but no Module row exists, so no role can
    // ever be granted it and the nav can never show it. Script 09 wasn't re-run.
    for (String code : declared) {
      if (registered.contains(code) && !seeded.contains(code)) {
        errors.add(
          "'" + code + "' is registered and wired, but NOT seeded in the Module " +
          "table. Re-run script 09 -- no role can be granted an unseeded module."
        );
      }
    }

    // ---- WARN: declared + seeded, but NO registered view --------------------
    // The module exists in the DB and can be granted, but selecting it can never
    // work because no pack builds a panel for it. This is the LOANLEDGER bug,
    // and it is ALSO the honest running TODO list for the 12 unbuilt maintenance
    // screens — it should shrink to zero as BKL-01 lands.
    for (String code : declared) {
      if (seeded.contains(code) && !registered.contains(code)) {
        ModuleCode m = ModuleCode.From(code);
        warnings.add(
          "'" + code + "' (" + (m == null ? "?" : m.GetDisplayName()) + ") is " +
          "seeded but has NO registered view -- it can be granted, but it will " +
          "never appear in the nav."
        );
      }
    }

    Report(declared.size(), seeded.size(), registered.size(), errors, warnings);
    return errors.size();
  }

  private static void Report(
    int declaredCount,
    int seededCount,
    int registeredCount,
    List<String> errors,
    List<String> warnings
  ) {
    if (errors.isEmpty() && warnings.isEmpty()) {
      System.out.println(
        "[ModuleReconciler] OK -- " + declaredCount + " declared / " +
        seededCount + " seeded / " + registeredCount + " registered; all agree."
      );
      return;
    }

    System.out.println(
      "[ModuleReconciler] " + declaredCount + " declared / " + seededCount +
      " seeded / " + registeredCount + " registered."
    );

    for (String e : errors) {
      System.err.println("[ModuleReconciler] ERROR: " + e);
    }
    for (String w : warnings) {
      System.out.println("[ModuleReconciler] WARN : " + w);
    }

    if (!warnings.isEmpty() && errors.isEmpty()) {
      System.out.println(
        "[ModuleReconciler] " + warnings.size() + " module(s) awaiting a view " +
        "registration. Expected while BKL-01 (maintenance screens) is in flight."
      );
    }
  }
}