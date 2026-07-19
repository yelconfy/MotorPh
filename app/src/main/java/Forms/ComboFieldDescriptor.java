package Forms;

import java.util.function.BiConsumer;
import java.util.function.Function;

/** COMBO — E is typically an enum; displayFn is usually just E::toString. */
final class ComboFieldDescriptor<T, E> implements FieldDescriptor<T> {
  private final String key;
  private final String label;
  private final E[] options;
  private final Function<E, String> displayFn;
  private final Function<T, E> getter;
  private final BiConsumer<T, E> setter;

  ComboFieldDescriptor(
    String key, String label, E[] options, Function<E, String> displayFn,
    Function<T, E> getter, BiConsumer<T, E> setter
  ) {
    this.key = key;
    this.label = label;
    this.options = options;
    this.displayFn = displayFn;
    this.getter = getter;
    this.setter = setter;
  }

  @Override public String GetKey() { return key; }
  @Override public String GetLabel() { return label; }
  public E[] GetOptions() { return options; }
  public Function<E, String> GetDisplayFn() { return displayFn; }
  public Function<T, E> GetGetter() { return getter; }
  public BiConsumer<T, E> GetSetter() { return setter; }
}