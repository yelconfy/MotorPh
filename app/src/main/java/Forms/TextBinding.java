package Forms;

import Core.Component.ComponentFactory;
import Core.Component.SmartTextField;
import javax.swing.JComponent;

final class TextBinding<T> implements FieldBinding<T> {
  private final TextFieldDescriptor<T> fd;
  private final SmartTextField input;

  TextBinding(TextFieldDescriptor<T> fd) {
    this.fd = fd;
    this.input = ComponentFactory.createSmartField(fd.GetType());
  }

  @Override public JComponent GetComponent() { return input; }
  @Override public void PopulateFrom(T item) { input.setText(fd.GetGetter().apply(item)); }
  @Override public void ApplyTo(T item) { fd.GetSetter().accept(item, input.getText().trim()); }
  @Override public void SetEditable(boolean editable) { input.setEditable(editable); }
  @Override public void Clear() { input.setText(""); }
}