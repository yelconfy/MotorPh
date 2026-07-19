package Forms;

import Core.Enum.SmartFieldType;
import java.time.LocalTime;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Describes one editable field in the detail form of a
 * ReferenceMaintenancePanel<T>. Part of the BKL-26 descriptor set (see
 * MaintenanceDescriptor) — no Swing in here; that lives in the matching
 * FieldBinding<T> the Panel builds from this at construction.
 *
 * Sealed rather than one class with a `kind` tag: a tag-and-branch design
 * means every new field kind stretches both this class (nullable fields only
 * some kinds use) and every place that reads it (a switch that has to stay
 * in sync by hand). A sealed hierarchy makes each kind's data self-contained,
 * and FieldBinding.From()'s pattern-matching switch won't compile if a new
 * permitted kind is added without a matching binding — no silent gap.
 *
 * One file per implementation (TextFieldDescriptor.java / ComboFieldDescriptor.java /
 * CheckboxFieldDescriptor.java / NumericFieldDescriptor.java) rather than colocated
 * in this file — matches the DeleteOutcome precedent (Objects.enums) of giving
 * each type its own file, and avoids javac's auxiliary-class warning at the
 * FieldBinding call site.
 *
 * key is the stable lookup identity — used by MaintenanceDescriptor's field
 * map and by row-protection logic that needs to reach a specific field.
 * label is display text only. The two are deliberately decoupled: relabeling
 * a field for the UI can't break a lookup by key.
 */
public sealed interface FieldDescriptor<T>
    permits TextFieldDescriptor, ComboFieldDescriptor, CheckboxFieldDescriptor, NumericFieldDescriptor, TimeFieldDescriptor {

  String GetKey();
  String GetLabel();

  /** Plain text, SmartTextField-backed — the only kind that existed pre-3b. */
  static <T> FieldDescriptor<T> text(
    String key,
    String label,
    SmartFieldType type,
    Function<T, String> getter,
    BiConsumer<T, String> setter
  ) {
    return new TextFieldDescriptor<>(key, label, type, getter, setter);
  }

  /** Enum-backed dropdown (e.g. Deduction_Type.Category). displayFn drives the rendered text. */
  static <T, E> FieldDescriptor<T> combo(
    String key,
    String label,
    E[] options,
    Function<E, String> displayFn,
    Function<T, E> getter,
    BiConsumer<T, E> setter
  ) {
    return new ComboFieldDescriptor<>(key, label, options, displayFn, getter, setter);
  }

  /** Plain boolean (e.g. WorkSchedule's day-of-week flags). */
  static <T> FieldDescriptor<T> checkbox(
    String key,
    String label,
    Function<T, Boolean> getter,
    BiConsumer<T, Boolean> setter
  ) {
    return new CheckboxFieldDescriptor<>(key, label, getter, setter);
  }

  /** Plain double (e.g. Leave_Type's day counts). SmartFieldType.NUMERIC-backed. */
  static <T> FieldDescriptor<T> numeric(
    String key,
    String label,
    Function<T, Double> getter,
    BiConsumer<T, Double> setter
  ) {
    return new NumericFieldDescriptor<>(key, label, getter, setter);
  }

  /** Plain LocalTime (e.g. Work_Schedule's TimeStart/TimeEnd). SmartFieldType.TIME-backed. */
  static <T> FieldDescriptor<T> time(
    String key,
    String label,
    Function<T, LocalTime> getter,
    BiConsumer<T, LocalTime> setter
  ) {
    return new TimeFieldDescriptor<>(key, label, getter, setter);
  }
}