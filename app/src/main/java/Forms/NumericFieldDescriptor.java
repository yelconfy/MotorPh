package Forms;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * NUMERIC — plain double, SmartFieldType.NUMERIC-backed (BKL-01 stage 3b, added
 * for LEAVETYPE's DefaultDaysPerYear / MaxCarryOverDays).
 *
 * Structurally the fourth sealed kind alongside TEXT/COMBO/CHECKBOX: unlike TEXT
 * (which stays String end-to-end and lets SmartFieldType.NUMERIC's own regex do
 * the validation), this descriptor speaks the model's native double, the same
 * way COMBO speaks E and CHECKBOX speaks Boolean — the String<->double
 * conversion lives in NumericBinding, not scattered across every .field(...)
 * call site in ReferenceModules.
 */
final class NumericFieldDescriptor<T> implements FieldDescriptor<T> {
  private final String key;
  private final String label;
  private final Function<T, Double> getter;
  private final BiConsumer<T, Double> setter;

  NumericFieldDescriptor(
    String key, String label, Function<T, Double> getter, BiConsumer<T, Double> setter
  ) {
    this.key = key;
    this.label = label;
    this.getter = getter;
    this.setter = setter;
  }

  @Override public String GetKey() { return key; }
  @Override public String GetLabel() { return label; }
  public Function<T, Double> GetGetter() { return getter; }
  public BiConsumer<T, Double> GetSetter() { return setter; }
}