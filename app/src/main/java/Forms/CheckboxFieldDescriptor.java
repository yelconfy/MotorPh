package Forms;

import java.util.function.BiConsumer;
import java.util.function.Function;

/** CHECKBOX — plain boolean, no formatting/validation needed. */
final class CheckboxFieldDescriptor<T> implements FieldDescriptor<T> {
  private final String key;
  private final String label;
  private final Function<T, Boolean> getter;
  private final BiConsumer<T, Boolean> setter;

  CheckboxFieldDescriptor(
    String key, String label, Function<T, Boolean> getter, BiConsumer<T, Boolean> setter
  ) {
    this.key = key;
    this.label = label;
    this.getter = getter;
    this.setter = setter;
  }

  @Override public String GetKey() { return key; }
  @Override public String GetLabel() { return label; }
  public Function<T, Boolean> GetGetter() { return getter; }
  public BiConsumer<T, Boolean> GetSetter() { return setter; }
}