package Forms;

import Core.Component.ComponentFactory;
import Core.Component.SmartTextField;
import Core.Enum.SmartFieldType;
import javax.swing.JComponent;

/**
 * Live Swing counterpart to NumericFieldDescriptor<T>. Backed by a plain
 * SmartTextField(NUMERIC) — same widget class TEXT uses, just a different
 * SmartFieldType, so it gets the live digit+dot filter and blur-time
 * validation for free (Core.Component.SmartTextField, no changes needed there
 * beyond the one new isValidInput branch).
 *
 * 0.0 displays as blank, matching the model convention documented on
 * LeaveTypeInfo ("nullable in DB — 0.0 when null"): a blank field round-trips
 * to NULL in the DB and back to 0.0 in the model, so this keeps the widget's
 * empty state consistent with what "unset" actually means for these columns.
 *
 * ApplyTo parses without a try/catch: by the time Accept reaches applyFieldsTo,
 * ReferenceMaintenancePanel.doSave() has already run FormControlService.validate,
 * which calls SmartField.isContentValid() -> SmartFieldType.NUMERIC.isValid(...)
 * on this exact widget — so the text is already known to match NUMERIC's regex
 * (or be blank) before ApplyTo ever runs. Same trust boundary TextBinding /
 * ComboBinding / CheckboxBinding already rely on.
 */
final class NumericBinding<T> implements FieldBinding<T> {
  private final NumericFieldDescriptor<T> fd;
  private final SmartTextField input;

  NumericBinding(NumericFieldDescriptor<T> fd) {
    this.fd = fd;
    this.input = ComponentFactory.createSmartField(SmartFieldType.NUMERIC);
  }

  @Override public JComponent GetComponent() { return input; }

  @Override
  public void PopulateFrom(T item) {
    Double v = fd.GetGetter().apply(item);
    input.setText(v != null && v != 0.0 ? String.valueOf(v) : "");
  }

  @Override
  public void ApplyTo(T item) {
    String text = input.getCleanValue();
    double value = text.isEmpty() ? 0.0 : Double.parseDouble(text);
    fd.GetSetter().accept(item, value);
  }

  @Override public void SetEditable(boolean editable) { input.setEditable(editable); }
  @Override public void Clear() { input.setText(""); }
}