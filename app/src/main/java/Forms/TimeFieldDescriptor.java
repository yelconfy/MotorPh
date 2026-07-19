package Forms;

import java.time.LocalTime;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * TIME — plain LocalTime, SmartFieldType.TIME-backed (BKL-01 stage 3b, added
 * for WORKSCHEDULE's TimeStart / TimeEnd).
 *
 * Same shape as NumericFieldDescriptor: speaks the model's native LocalTime,
 * not String — the String<->LocalTime conversion lives in TimeBinding.
 */
final class TimeFieldDescriptor<T> implements FieldDescriptor<T> {

  private final String key;
  private final String label;
  private final Function<T, LocalTime> getter;
  private final BiConsumer<T, LocalTime> setter;

  TimeFieldDescriptor(
    String key,
    String label,
    Function<T, LocalTime> getter,
    BiConsumer<T, LocalTime> setter
  ) {
    this.key = key;
    this.label = label;
    this.getter = getter;
    this.setter = setter;
  }

  @Override
  public String GetKey() {
    return key;
  }

  @Override
  public String GetLabel() {
    return label;
  }

  public Function<T, LocalTime> GetGetter() {
    return getter;
  }

  public BiConsumer<T, LocalTime> GetSetter() {
    return setter;
  }
}
