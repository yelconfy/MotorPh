package Forms;

import javax.swing.JComponent;

/**
 * The live Swing counterpart to a FieldDescriptor<T> (BKL-26 field-kind
 * extension). ReferenceMaintenancePanel<T> builds exactly one of these per
 * field, once, at construction — this replaces what used to be a flat
 * List<SmartTextField> before COMBO/CHECKBOX needed to coexist with TEXT.
 *
 * Sealed + a pattern-matching switch in From(): adding a new FieldDescriptor
 * kind without a matching binding here fails to compile, rather than
 * silently rendering nothing at runtime.
 *
 * One file per implementation (TextBinding.java / ComboBinding.java /
 * CheckboxBinding.java / NumericBinding.java / TimeBinding.java), same
 * reasoning as FieldDescriptor's split.
 */
public sealed interface FieldBinding<T> permits TextBinding, ComboBinding, CheckboxBinding, NumericBinding, TimeBinding {

  JComponent GetComponent();

  /** Row selected in the master table -> push its value into the widget. */
  void PopulateFrom(T item);

  /** Accept clicked -> read the widget back into the item being saved. */
  void ApplyTo(T item);

  /** VIEW vs ADD/EDIT — the on/off switch every field kind needs, worded per widget. */
  void SetEditable(boolean editable);

  /** Add New — blank the widget to its empty state. */
  void Clear();

  static <T> FieldBinding<T> From(FieldDescriptor<T> fd) {
    return switch (fd) {
      case TextFieldDescriptor<T> t -> new TextBinding<>(t);
      case ComboFieldDescriptor<T, ?> c -> ComboBinding.Of(c);
      case CheckboxFieldDescriptor<T> b -> new CheckboxBinding<>(b);
      case NumericFieldDescriptor<T> n -> new NumericBinding<>(n);
      case TimeFieldDescriptor<T> tm -> new TimeBinding<>(tm);
    };
  }
}