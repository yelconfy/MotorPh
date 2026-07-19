package Forms;

import Core.Enum.SmartFieldType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * The small descriptor that drives a ReferenceMaintenancePanel<T> (BKL-26) —
 * everything screen-specific about one reference-table maintenance module,
 * with no Swing in it.
 *
 * Built via the fluent Builder; see ReferenceModules.register() for the
 * POSITIONS retrofit, which is the worked example this shape is validated
 * against.
 *
 * inUseMessage / saveFailedMessage are optional — sensible generic defaults
 * are supplied in build() if omitted. POSITIONS sets both explicitly so its
 * retrofit reproduces the exact original dialog wording.
 *
 * BKL-26 field-kind extension: fields is now a keyed LinkedHashMap rather
 * than a positional List. Two reasons, both from the same root cause —
 * position-only lookup broke down the moment a field needed to be reached
 * for something other than "the next box in the form": (1) row-level
 * protection (below) needs to identify which field a rule is about without
 * caring where it sits in the form, and (2) ReferenceMaintenancePanel's
 * FieldBinding map needs the same stable key to hand a value back to the
 * right widget. Insertion order is preserved (LinkedHashMap), so form layout
 * is unaffected — .field() calls still declare fields top-to-bottom exactly
 * as before.
 *
 * protectedPredicate / protectedMessage (BKL-26 field-kind extension):
 * row-level lock, orthogonal to field kind — e.g. DeductionType's four
 * statutory rows (SSS, PhilHealth, Pag-IBIG, Withholding Tax), which
 * PayrollProcess matches by name and therefore can't be safely renamed or
 * removed from this screen. Defaults to "nothing is protected" so tables
 * without this concern (Departments, Positions) don't have to opt out.
 */
public final class MaintenanceDescriptor<T> {

  private final String title;
  private final String subtitle;
  private final String itemLabel;
  private final String inUseMessage;
  private final String saveFailedMessage;
  private final String protectedMessage;
  private final List<ColumnDescriptor<T>> columns;
  private final Map<String, FieldDescriptor<T>> fields;
  private final Function<T, Long> idFn;
  private final Function<T, String> labelFn;
  private final Supplier<T> factory;
  private final Predicate<T> protectedPredicate;

  private MaintenanceDescriptor(Builder<T> b) {
    this.title = b.title;
    this.subtitle = b.subtitle;
    this.itemLabel = b.itemLabel;
    this.inUseMessage = b.inUseMessage;
    this.saveFailedMessage = b.saveFailedMessage;
    this.protectedMessage = b.protectedMessage;
    this.columns = List.copyOf(b.columns);
    this.fields = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(b.fields));
    this.idFn = b.idFn;
    this.labelFn = b.labelFn;
    this.factory = b.factory;
    this.protectedPredicate = b.protectedPredicate;
  }

  public static <T> Builder<T> builder() {
    return new Builder<>();
  }

  public String GetTitle() { return title; }
  public String GetSubtitle() { return subtitle; }
  public String GetItemLabel() { return itemLabel; }
  public String GetInUseMessage() { return inUseMessage; }
  public String GetSaveFailedMessage() { return saveFailedMessage; }
  public String GetProtectedMessage() { return protectedMessage; }
  public List<ColumnDescriptor<T>> GetColumns() { return columns; }
  public Map<String, FieldDescriptor<T>> GetFields() { return fields; }
  public Function<T, Long> GetIdFn() { return idFn; }
  public Function<T, String> GetLabelFn() { return labelFn; }
  public Supplier<T> GetFactory() { return factory; }
  public boolean IsProtected(T item) { return protectedPredicate.test(item); }

  public static final class Builder<T> {
    private String title;
    private String subtitle = "";
    private String itemLabel;
    private String inUseMessage;
    private String saveFailedMessage;
    private String protectedMessage;
    private final List<ColumnDescriptor<T>> columns = new ArrayList<>();
    private final Map<String, FieldDescriptor<T>> fields = new LinkedHashMap<>();
    private Function<T, Long> idFn;
    private Function<T, String> labelFn;
    private Supplier<T> factory;
    private Predicate<T> protectedPredicate = t -> false;

    private Builder() {}

    public Builder<T> title(String v) { this.title = v; return this; }
    public Builder<T> subtitle(String v) { this.subtitle = v; return this; }
    public Builder<T> itemLabel(String v) { this.itemLabel = v; return this; }
    public Builder<T> inUseMessage(String v) { this.inUseMessage = v; return this; }
    public Builder<T> saveFailedMessage(String v) { this.saveFailedMessage = v; return this; }
    public Builder<T> protectedMessage(String v) { this.protectedMessage = v; return this; }
    public Builder<T> idFn(Function<T, Long> v) { this.idFn = v; return this; }
    public Builder<T> labelFn(Function<T, String> v) { this.labelFn = v; return this; }
    public Builder<T> factory(Supplier<T> v) { this.factory = v; return this; }
    public Builder<T> protectedWhen(Predicate<T> v) { this.protectedPredicate = v; return this; }

    public Builder<T> column(String header, Function<T, Object> valueFn) {
      this.columns.add(new ColumnDescriptor<>(header, valueFn));
      return this;
    }

    /**
     * Registers one field. Construct via FieldDescriptor.text(...) / .combo(...) /
     * .checkbox(...) — this method just files it under its key, in call order.
     */
    public Builder<T> field(FieldDescriptor<T> fd) {
      if (fields.containsKey(fd.GetKey())) {
        throw new IllegalStateException("Duplicate field key: " + fd.GetKey());
      }
      this.fields.put(fd.GetKey(), fd);
      return this;
    }

    /** Convenience overload — avoids FieldDescriptor.text(...) boilerplate for the common case. */
    public Builder<T> field(
      String key,
      String label,
      SmartFieldType type,
      Function<T, String> getter,
      BiConsumer<T, String> setter
    ) {
      return field(FieldDescriptor.text(key, label, type, getter, setter));
    }

    public MaintenanceDescriptor<T> build() {
      if (title == null || itemLabel == null || idFn == null || labelFn == null || factory == null) {
        throw new IllegalStateException(
          "MaintenanceDescriptor requires title, itemLabel, idFn, labelFn, and factory."
        );
      }
      if (inUseMessage == null) {
        inUseMessage =
          "This " + itemLabel + " can't be deleted because it's still in use elsewhere.";
      }
      if (saveFailedMessage == null) {
        saveFailedMessage =
          "Could not save the " + itemLabel + ". Please check the values and try again.";
      }
      if (protectedMessage == null) {
        protectedMessage =
          "This " + itemLabel + " is a system default and can't be edited or removed here.";
      }
      return new MaintenanceDescriptor<>(this);
    }
  }
}