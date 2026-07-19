package Forms;

import java.util.function.Function;

/**
 * Describes one column of the master table in a ReferenceMaintenancePanel<T>.
 * Part of the BKL-26 descriptor set (see MaintenanceDescriptor).
 */
public final class ColumnDescriptor<T> {

  private final String header;
  private final Function<T, Object> valueFn;

  public ColumnDescriptor(String header, Function<T, Object> valueFn) {
    this.header = header;
    this.valueFn = valueFn;
  }

  public String GetHeader() {
    return header;
  }

  public Function<T, Object> GetValueFn() {
    return valueFn;
  }
}