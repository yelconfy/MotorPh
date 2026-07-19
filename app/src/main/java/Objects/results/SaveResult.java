package Objects.results;

/**
 * Unified outcome of any state-changing operation across the app's process
 * layer — every write (create, update, delete, workflow action) returns one of
 * these instead of a bare boolean/int, so the process can tell the panel not
 * just whether it worked but WHY it didn't, and hand back a payload (a new ID)
 * when there is one.
 *
 * Replaces the former Objects.enums.DeleteOutcome (folded in here — delete now
 * returns SaveResult<Void>) and the ad-hoc signals it grew up alongside:
 *   - panels hardcoding "Save failed" because a boolean carried no reason;
 *   - AttendanceCorrectionProcess.AddPunch smuggling "-2 == duplicate date"
 *     through a magic-number long.
 * Both collapse onto (outcome, message, payload).
 *
 * ---- Outcome reachability (IMPORTANT) ----
 * The Outcome enum is intentionally WIDER than any single operation uses. Each
 * operation class only ever emits a subset; documenting that here is the
 * honest alternative to four near-identical enums:
 *
 *   Create / Update paths  -> SUCCESS, VALIDATION_FAILED, FAILED
 *                             (never IN_USE — you cannot referentially block a create)
 *   Delete paths           -> SUCCESS, IN_USE, FAILED
 *                             (never VALIDATION_FAILED — nothing was typed to validate)
 *   Workflow actions       -> SUCCESS, VALIDATION_FAILED, FAILED
 *                             (VALIDATION_FAILED covers an illegal decision/state)
 *
 * A reader should not expect, say, IN_USE back from Add — the type permits it,
 * the contract does not.
 *
 * P is the success payload type. Use SaveResult<Void> when there is nothing to
 * carry back (most maintenance + workflow writes); SaveResult<Long> when the
 * caller needs the generated key (AddEmployee -> new EmployeeID, AddPunch ->
 * new AttendanceID).
 *
 * Immutable; construct only through the static factories.
 */
public final class SaveResult<P> {

  public enum Outcome {
    /** The operation committed. */
    SUCCESS,
    /** A business/validation rule rejected the input before (or instead of) writing. */
    VALIDATION_FAILED,
    /** A delete was blocked because the row is still referenced elsewhere. */
    IN_USE,
    /** An unexpected/technical failure (exception, no rows affected, lost connection). */
    FAILED,
  }

  private final Outcome outcome;
  private final String message; // human-facing reason; null on SUCCESS
  private final P payload;      // generated key etc.; null when N/A

  private SaveResult(Outcome outcome, String message, P payload) {
    this.outcome = outcome;
    this.message = message;
    this.payload = payload;
  }

  // ---- Factories ----------------------------------------------------------

  /** SUCCESS with no payload. */
  public static <P> SaveResult<P> success() {
    return new SaveResult<>(Outcome.SUCCESS, null, null);
  }

  /** SUCCESS carrying a payload (e.g. the generated ID). */
  public static <P> SaveResult<P> success(P payload) {
    return new SaveResult<>(Outcome.SUCCESS, null, payload);
  }

  /** VALIDATION_FAILED with the reason to show the user. */
  public static <P> SaveResult<P> invalid(String message) {
    return new SaveResult<>(Outcome.VALIDATION_FAILED, message, null);
  }

  /** IN_USE (delete blocked) with the reason to show the user. */
  public static <P> SaveResult<P> inUse(String message) {
    return new SaveResult<>(Outcome.IN_USE, message, null);
  }

  /** FAILED with no specific message (panel supplies a generic one). */
  public static <P> SaveResult<P> failed() {
    return new SaveResult<>(Outcome.FAILED, null, null);
  }

  /** FAILED with a specific message. */
  public static <P> SaveResult<P> failed(String message) {
    return new SaveResult<>(Outcome.FAILED, message, null);
  }

  // ---- Accessors ----------------------------------------------------------

  public Outcome GetOutcome() {
    return outcome;
  }

  public boolean IsSuccess() {
    return outcome == Outcome.SUCCESS;
  }

  /** Human-facing reason for a non-success outcome; null on SUCCESS. */
  public String GetMessage() {
    return message;
  }

  /** Success payload (e.g. generated ID); null when none / not SUCCESS. */
  public P GetPayload() {
    return payload;
  }
}