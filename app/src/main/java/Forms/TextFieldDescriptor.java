package Forms;

import Core.Enum.SmartFieldType;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** TEXT — SmartFieldType governs validation/formatting, same as pre-3b behavior. */
final class TextFieldDescriptor<T> implements FieldDescriptor<T> {
  private final String key;
  private final String label;
  private final SmartFieldType type;
  private final Function<T, String> getter;
  private final BiConsumer<T, String> setter;

  TextFieldDescriptor(
    String key, String label, SmartFieldType type,
    Function<T, String> getter, BiConsumer<T, String> setter
  ) {
    this.key = key;
    this.label = label;
    this.type = type;
    this.getter = getter;
    this.setter = setter;
  }

  @Override public String GetKey() { return key; }
  @Override public String GetLabel() { return label; }
  public SmartFieldType GetType() { return type; }
  public Function<T, String> GetGetter() { return getter; }
  public BiConsumer<T, String> GetSetter() { return setter; }
}