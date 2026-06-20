package Helper;

import java.awt.Dimension;
import java.util.List;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableModel;

public class TableUtil {

  /**
   * Generic populate method for any custom TableModel.
   *
   * @param table the JTable to populate
   * @param data the list of objects to display
   * @param creator a function that creates the custom TableModel from the
   * list
   * @param <T> the object type
   * @param <M> the TableModel type
   */
  public static <T, M extends TableModel> void populate(
    JTable table,
    List<T> data,
    TableModelCreator<T, M> creator
  ) {
    M model = creator.create(data);
    SwingUtilities.invokeLater(() -> {
      table.setModel(model);
    });
  }

  /**
   * Clear all rows in a DefaultTableModel.
   */
  public static void cleanTable(DefaultTableModel model) {
    model.setRowCount(0);
  }

  /**
   * Utility for getting a sublist (page) safely.
   */
  public static <T> List<T> getPage(List<T> allData, int page, int pageSize) {
    int from = page * pageSize;
    int to = Math.min(from + pageSize, allData.size());
    return allData.subList(from, to);
  }

  /**
   * Create a TableModel from a list.
   */
  @FunctionalInterface
  public interface TableModelCreator<T, M extends TableModel> {
    M create(List<T> data);
  }
}
