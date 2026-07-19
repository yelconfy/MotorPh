package Forms;

import Core.Component.ComponentFactory;
import Core.Component.SmartTextField;
import Core.Enum.SmartFieldType;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javax.swing.JComponent;

/**
 * Live Swing counterpart to TimeFieldDescriptor<T>. Backed by a plain
 * SmartTextField(TIME) — gets the digit-only live filter and auto-colon
 * blur-formatting for free, the same way DATE already works.
 *
 * ApplyTo parses without a try/catch, same trust boundary as every other
 * binding: FormControlService.validate() has already confirmed the widget's
 * text matches SmartFieldType.TIME's regex (or the field isn't mandatory and
 * is blank) before ApplyTo runs.
 */
final class TimeBinding<T> implements FieldBinding<T> {
  private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("HH:mm");
  private static final DateTimeFormatter DIGIT_FMT = DateTimeFormatter.ofPattern("HHmm");

  private final TimeFieldDescriptor<T> fd;
  private final SmartTextField input;

  TimeBinding(TimeFieldDescriptor<T> fd) {
    this.fd = fd;
    this.input = ComponentFactory.createSmartField(SmartFieldType.TIME);
  }

  @Override public JComponent GetComponent() { return input; }

  @Override
  public void PopulateFrom(T item) {
    LocalTime v = fd.GetGetter().apply(item);
    input.setText(v != null ? v.format(DIGIT_FMT) : "");
  }

  @Override
  public void ApplyTo(T item) {
    String text = input.getCleanValue();
    LocalTime value = text.isEmpty() ? null : LocalTime.parse(text, DISPLAY_FMT);
    fd.GetSetter().accept(item, value);
  }

  @Override public void SetEditable(boolean editable) { input.setEditable(editable); }
  @Override public void Clear() { input.setText(""); }
}