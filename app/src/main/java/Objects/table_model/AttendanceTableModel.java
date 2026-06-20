package Objects.table_model;

import Objects.models.Attendance;
import java.util.List;
import javax.swing.table.AbstractTableModel;

public class AttendanceTableModel extends AbstractTableModel {

  private List<Attendance> pageData;

  public AttendanceTableModel(List<Attendance> _pageData) {
    this.pageData = _pageData;
  }

  public void setPageData(List<Attendance> _pageData) {
    this.pageData = _pageData;
    fireTableDataChanged(); // refresh table
  }

  @Override
  public int getRowCount() {
    return pageData.size();
  }

  @Override
  public int getColumnCount() {
    return Attendance.DISPLAY_FIELDS.length;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex) {
    return pageData.get(rowIndex).GetDisplayFieldValue(columnIndex);
  }

  @Override
  public String getColumnName(int column) {
    return Attendance.DISPLAY_FIELDS[column];
  }
}
