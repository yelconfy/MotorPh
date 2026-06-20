package Core.Component;

/**
 * Shared contract for self-validating form elements used by FormControlService.
 *
 * Any component implementing this is picked up by FormControlService.validate()
 * generically — to add a new smart element (date picker, spinner, etc.), just
 * implement this interface; no validator change is needed.
 */
public interface SmartField {

  /**
   * True when the element's current value satisfies its own rules
   * (format + mandatory). Implementations may format/normalize first.
   */
  boolean isContentValid();

  /** Toggle the element's error styling (e.g. red border). */
  void displayError(boolean hasError);
}