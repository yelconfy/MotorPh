package Forms;

import java.awt.Component;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JList;

final class ComboBinding<T, E> implements FieldBinding<T> {
  private final ComboFieldDescriptor<T, E> fd;
  private final JComboBox<E> input;

  private ComboBinding(ComboFieldDescriptor<T, E> fd) {
    this.fd = fd;
    this.input = new JComboBox<>(fd.GetOptions());
    input.setRenderer(new DefaultListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(
        JList<?> list, Object value, int index, boolean isSelected, boolean hasFocus
      ) {
        super.getListCellRendererComponent(list, value, index, isSelected, hasFocus);
        if (value != null) {
          @SuppressWarnings("unchecked")
          E e = (E) value;
          setText(fd.GetDisplayFn().apply(e));
        }
        return this;
      }
    });
  }

  static <T, E> ComboBinding<T, E> Of(ComboFieldDescriptor<T, E> fd) {
    return new ComboBinding<>(fd);
  }

  @Override public JComponent GetComponent() { return input; }
  @Override public void PopulateFrom(T item) { input.setSelectedItem(fd.GetGetter().apply(item)); }

  @Override
  public void ApplyTo(T item) {
    @SuppressWarnings("unchecked")
    E selected = (E) input.getSelectedItem();
    fd.GetSetter().accept(item, selected);
  }

  @Override public void SetEditable(boolean editable) { input.setEnabled(editable); }
  @Override public void Clear() { if (input.getItemCount() > 0) input.setSelectedIndex(0); }
}