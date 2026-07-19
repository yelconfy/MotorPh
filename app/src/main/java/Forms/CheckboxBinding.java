package Forms;

import javax.swing.JCheckBox;
import javax.swing.JComponent;

final class CheckboxBinding<T> implements FieldBinding<T> {
  private final CheckboxFieldDescriptor<T> fd;
  private final JCheckBox input;

  CheckboxBinding(CheckboxFieldDescriptor<T> fd) {
    this.fd = fd;
    this.input = new JCheckBox();
  }

  @Override public JComponent GetComponent() { return input; }
  @Override public void PopulateFrom(T item) {
    input.setSelected(Boolean.TRUE.equals(fd.GetGetter().apply(item)));
  }
  @Override public void ApplyTo(T item) { fd.GetSetter().accept(item, input.isSelected()); }
  @Override public void SetEditable(boolean editable) { input.setEnabled(editable); }
  @Override public void Clear() { input.setSelected(false); }
}