package Objects.models.table;

import Objects.enums.Status.AttendanceStatus;
import Objects.models.DailyAttendanceRecord;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.table.AbstractTableModel;

/**
 * Table model for the enhanced Timekeeping grid. Renders DailyAttendanceRecord
 * rows (raw punches + computed status / late / worked / overtime / day type).
 *
 * Exposes GetRecordAt / GetStatusAt so a cell renderer can colour whole rows by
 * status without re-deriving anything.
 */
public class DailyAttendanceTableModel extends AbstractTableModel {

  private static final String[] COLUMNS = {
    "Emp #",
    "Last Name",
    "First Name",
    "Date",
    "Time In",
    "Time Out",
    "Status",
    "Late (min)",
    "Worked",
    "OT",
    "Day Type",
  };

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern(
    "yyyy-MM-dd"
  );
  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern(
    "HH:mm"
  );

  private List<DailyAttendanceRecord> pageData;

  public DailyAttendanceTableModel(List<DailyAttendanceRecord> pageData) {
    this.pageData = (pageData != null) ? pageData : new ArrayList<>();
  }

  public void setPageData(List<DailyAttendanceRecord> pageData) {
    this.pageData = (pageData != null) ? pageData : new ArrayList<>();
    fireTableDataChanged();
  }

  public DailyAttendanceRecord GetRecordAt(int row) {
    return pageData.get(row);
  }

  public AttendanceStatus GetStatusAt(int row) {
    return pageData.get(row).GetStatus();
  }

  @Override
  public int getRowCount() {
    return pageData.size();
  }

  @Override
  public int getColumnCount() {
    return COLUMNS.length;
  }

  @Override
  public String getColumnName(int c) {
    return COLUMNS[c];
  }

  @Override
  public Object getValueAt(int row, int col) {
    DailyAttendanceRecord r = pageData.get(row);
    return switch (col) {
      case 0 -> r.GetEmployeeId();
      case 1 -> r.GetLastName();
      case 2 -> r.GetFirstName();
      case 3 -> r.GetDate() != null ? r.GetDate().format(DATE_FMT) : "";
      case 4 -> r.GetTimeIn() != null
        ? r.GetTimeIn().format(TIME_FMT)
        : "\u2014";
      case 5 -> r.GetTimeOut() != null
        ? r.GetTimeOut().format(TIME_FMT)
        : "\u2014";
      case 6 -> r.GetStatus().GetLabel();
      case 7 -> r.GetLateMinutes() > 0 ? r.GetLateMinutes() : "";
      case 8 -> formatHm(r.GetRegularMinutes());
      case 9 -> r.GetOvertimeMinutes() > 0
        ? formatHm(r.GetOvertimeMinutes())
        : "";
      case 10 -> switch (r.GetDayType()) {
        case HOLIDAY -> "Holiday";
        case WEEKEND -> "Weekend";
        case REGULAR -> "Regular";
      };
      default -> null;
    };
  }

  /** Formats a minute count as H:MM (e.g. 485 -> "8:05"). */
  private static String formatHm(long minutes) {
    if (minutes <= 0) return "0:00";
    return (minutes / 60) + ":" + String.format("%02d", minutes % 60);
  }
}
