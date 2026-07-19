package Forms;

import Core.Service.LeaveBalanceReportPdfRenderer;
import Interface.ILeaveBalanceReportProcess;
import Objects.models.IAM.Session;
import Objects.models.LeaveBalanceRow;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.io.FileOutputStream;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Leave Balance Report screen (Reporting layer) — read-only view over
 * vw_LeaveBalanceReport (script 17). Pick a year; the grid shows each employee's
 * entitled / used / remaining days per leave type. Export renders the same rows
 * via LeaveBalanceReportPdfRenderer and logs the print (reprint-aware).
 * Granted VIEW; mirrors the other report panels.
 */
public class LeaveBalanceReportPanel extends JPanel {

  private static final Color BRAND_DARK = new Color(0x0D1B2A);
  private static final Color MUTED = new Color(0x6B7682);
  private static final String FONT = "Segoe UI";

  private final ILeaveBalanceReportProcess process;
  private final LeaveBalanceReportPdfRenderer renderer;

  private final BalanceTableModel tableModel = new BalanceTableModel();
  private final JTable table = new JTable(tableModel);
  private final JComboBox<Integer> yearPicker = new JComboBox<>();
  private final JLabel summaryLabel = new JLabel(" ");
  private JButton refreshBtn;
  private JButton exportBtn;

  private List<LeaveBalanceRow> currentRows = new ArrayList<>();

  public LeaveBalanceReportPanel(
    ILeaveBalanceReportProcess process,
    LeaveBalanceReportPdfRenderer renderer
  ) {
    this.process = process;
    this.renderer = renderer;
    setLayout(new BorderLayout());
    setBackground(Color.WHITE);
    add(buildTop(), BorderLayout.NORTH);
    add(buildCenter(), BorderLayout.CENTER);
    add(buildBottom(), BorderLayout.SOUTH);
    loadYears();
    wireListeners();
    reload();
  }

  private JComponent buildTop() {
    JPanel top = new JPanel(new BorderLayout());
    top.setBackground(Color.WHITE);
    top.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));
    JLabel title = new JLabel("Leave Balance Report");
    title.setFont(new Font(FONT, Font.BOLD, 20));
    title.setForeground(BRAND_DARK);
    JLabel sub = new JLabel("Entitled, used, and remaining leave days per employee and type.");
    sub.setFont(new Font(FONT, Font.PLAIN, 12));
    sub.setForeground(MUTED);
    JPanel titleBox = new JPanel(new BorderLayout());
    titleBox.setBackground(Color.WHITE);
    titleBox.add(title, BorderLayout.NORTH);
    titleBox.add(sub, BorderLayout.SOUTH);
    JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    controls.setBackground(Color.WHITE);
    controls.add(new JLabel("Year:"));
    controls.add(yearPicker);
    refreshBtn = new JButton("Refresh");
    exportBtn = new JButton("Export PDF");
    controls.add(refreshBtn);
    controls.add(exportBtn);
    top.add(titleBox, BorderLayout.WEST);
    top.add(controls, BorderLayout.EAST);
    return top;
  }

  private JComponent buildCenter() {
    table.setRowHeight(24);
    table.setFont(new Font(FONT, Font.PLAIN, 12));
    table.getTableHeader().setFont(new Font(FONT, Font.BOLD, 12));
    table.setGridColor(new Color(0xE0E0E0));
    table.setFillsViewportHeight(true);
    DefaultTableCellRenderer right = new DefaultTableCellRenderer();
    right.setHorizontalAlignment(SwingConstants.RIGHT);
    for (int col : new int[] { 4, 5, 6 }) {
      table.getColumnModel().getColumn(col).setCellRenderer(right);
    }
    JScrollPane scroll = new JScrollPane(table);
    scroll.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
    return scroll;
  }

  private JComponent buildBottom() {
    JPanel bottom = new JPanel(new BorderLayout());
    bottom.setBackground(Color.WHITE);
    bottom.setBorder(BorderFactory.createEmptyBorder(8, 16, 12, 16));
    summaryLabel.setFont(new Font(FONT, Font.BOLD, 12));
    summaryLabel.setForeground(BRAND_DARK);
    bottom.add(summaryLabel, BorderLayout.WEST);
    return bottom;
  }

  private void wireListeners() {
    refreshBtn.addActionListener(e -> reload());
    yearPicker.addActionListener(e -> reload());
    exportBtn.addActionListener(e -> exportPdf());
  }

  private void loadYears() {
    yearPicker.removeAllItems();
    for (Integer y : process.GetAvailableYears()) {
      yearPicker.addItem(y);
    }
  }

  private Integer selectedYear() {
    return (Integer) yearPicker.getSelectedItem();
  }

  private void reload() {
    Integer year = selectedYear();
    if (year == null) {
      currentRows = new ArrayList<>();
      tableModel.setRows(currentRows);
      summaryLabel.setText("No leave data available.");
      exportBtn.setEnabled(false);
      return;
    }
    currentRows = process.GetForYear(year);
    tableModel.setRows(currentRows);
    summaryLabel.setText(currentRows.size() + " balance row(s) for " + year + ".");
    exportBtn.setEnabled(!currentRows.isEmpty());
  }

  private void exportPdf() {
    Integer year = selectedYear();
    if (year == null || currentRows.isEmpty()) {
      return;
    }
    try {
      byte[] pdf = renderer.Render(year, currentRows);
      JFileChooser chooser = new JFileChooser();
      chooser.setSelectedFile(new File(LeaveBalanceReportPdfRenderer.SuggestFileName(year)));
      if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
        return;
      }
      File target = chooser.getSelectedFile();
      try (FileOutputStream fos = new FileOutputStream(target)) {
        fos.write(pdf);
      }
      boolean isReprint = process.RecordPrint(year, Session.GetUsername(), "Leave Balance report export");
      JOptionPane.showMessageDialog(this,
        (isReprint ? "Re-exported" : "Exported") + " Leave Balance report for " + year + ".",
        "Export complete", JOptionPane.INFORMATION_MESSAGE);
    } catch (SQLException ex) {
      JOptionPane.showMessageDialog(this,
        "The PDF was saved, but the print could not be audited: " + ex.getMessage(),
        "Audit warning", JOptionPane.WARNING_MESSAGE);
    } catch (Exception ex) {
      JOptionPane.showMessageDialog(this,
        "Could not export the report: " + ex.getMessage(),
        "Export failed", JOptionPane.ERROR_MESSAGE);
    }
  }

  private static final class BalanceTableModel extends AbstractTableModel {
    private final String[] cols = {
      "Emp No", "Employee Full Name", "Department", "Leave Type", "Entitled", "Used", "Remaining",
    };
    private final java.text.DecimalFormat days = new java.text.DecimalFormat("#,##0.##");
    private List<LeaveBalanceRow> rows = new ArrayList<>();

    void setRows(List<LeaveBalanceRow> rows) {
      this.rows = (rows != null) ? rows : new ArrayList<>();
      fireTableDataChanged();
    }

    @Override public int getRowCount() { return rows.size(); }
    @Override public int getColumnCount() { return cols.length; }
    @Override public String getColumnName(int c) { return cols[c]; }

    @Override
    public Object getValueAt(int r, int c) {
      LeaveBalanceRow row = rows.get(r);
      switch (c) {
        case 0: return row.GetEmployeeNo();
        case 1: return row.GetEmployeeFullName();
        case 2: return row.GetDepartment();
        case 3: return row.GetLeaveType();
        case 4: return days.format(row.GetEntitledDays());
        case 5: return days.format(row.GetUsedDays());
        case 6: return days.format(row.GetRemainingDays());
        default: return "";
      }
    }

    @Override public boolean isCellEditable(int r, int c) { return false; }
  }
}