package Forms;

import Core.Service.ThirteenthMonthPdfRenderer;
import Interface.IThirteenthMonthProcess;
import Objects.models.IAM.Session;
import Objects.models.ThirteenthMonthRow;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.FlowLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.sql.SQLException;
import java.text.DecimalFormat;
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
 * 13th Month Pay report screen (Reporting layer) — read-only view over
 * vw_ThirteenthMonth (script 17). One row per employee for the selected year,
 * with total basic earned, the count of cutoffs summed, and the 13th-month
 * amount (total basic / 12), plus a bold TOTAL row.
 *
 * Export PDF renders the same rows via ThirteenthMonthPdfRenderer (pure, no
 * recompute) and logs each export through RecordPrint (reprint-aware), exactly
 * like the Payslip Register and Payroll Summary. Granted VIEW; the export is a
 * read + an audit row.
 *
 * Mirrors PayrollSummaryPanel; the only structural difference is the picker is a
 * YEAR picker (13th month is a calendar-year figure) rather than a month picker.
 */
public class ThirteenthMonthPanel extends JPanel {

  private static final Color BRAND_DARK = new Color(0x0D1B2A);
  private static final Color MUTED = new Color(0x6B7682);
  private static final Color TOTAL_BG = new Color(0xF0F2F5);
  private static final String FONT = "Segoe UI";

  private final IThirteenthMonthProcess process;
  private final ThirteenthMonthPdfRenderer renderer;

  private final ReportTableModel tableModel = new ReportTableModel();
  private final JTable table = new JTable(tableModel);
  private final JComboBox<Integer> yearPicker = new JComboBox<>();
  private final JLabel summaryLabel = new JLabel(" ");
  private JButton refreshBtn;
  private JButton exportBtn;

  private List<ThirteenthMonthRow> currentRows = new ArrayList<>();

  public ThirteenthMonthPanel(
    IThirteenthMonthProcess process,
    ThirteenthMonthPdfRenderer renderer
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

  // ---- Layout ------------------------------------------------------------

  private JComponent buildTop() {
    JPanel top = new JPanel(new BorderLayout());
    top.setBackground(Color.WHITE);
    top.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

    JLabel title = new JLabel("13th Month Pay Report");
    title.setFont(new Font(FONT, Font.BOLD, 20));
    title.setForeground(BRAND_DARK);

    JLabel sub = new JLabel(
      "Total basic salary earned in the year \u00f7 12 (PD 851), per employee."
    );
    sub.setFont(new Font(FONT, Font.PLAIN, 12));
    sub.setForeground(MUTED);

    JPanel titleBox = new JPanel();
    titleBox.setBackground(Color.WHITE);
    titleBox.setLayout(new BorderLayout());
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

    // Right-align the numeric columns (Basic Earned, Cutoffs, 13th Month Pay).
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

  // ---- Behaviour ---------------------------------------------------------

  private void wireListeners() {
    refreshBtn.addActionListener(e -> reload());
    yearPicker.addActionListener(e -> reload());
    exportBtn.addActionListener(e -> exportPdf());
  }

  private void loadYears() {
    yearPicker.removeAllItems();
    List<Integer> years = process.GetAvailableYears();
    for (Integer y : years) {
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
      summaryLabel.setText("No payroll data available.");
      exportBtn.setEnabled(false);
      return;
    }
    currentRows = process.GetReportForYear(year);
    tableModel.setRows(currentRows);

    double total = 0d;
    for (ThirteenthMonthRow r : currentRows) {
      total += r.GetThirteenthMonthPay();
    }
    DecimalFormat money = new DecimalFormat("#,##0.00");
    summaryLabel.setText(
      currentRows.size() +
      " employee(s)   -   Total 13th month pay: PHP " +
      money.format(total)
    );
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
      chooser.setSelectedFile(
        new File(ThirteenthMonthPdfRenderer.SuggestFileName(year))
      );
      if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
        return;
      }
      File target = chooser.getSelectedFile();
      try (FileOutputStream fos = new FileOutputStream(target)) {
        fos.write(pdf);
      }

      boolean isReprint = process.RecordPrint(
        year,
        Session.GetUsername(),
        "13th Month Pay report export"
      );

      JOptionPane.showMessageDialog(
        this,
        (isReprint ? "Re-exported" : "Exported") +
        " 13th Month Pay report for " +
        year +
        ".",
        "Export complete",
        JOptionPane.INFORMATION_MESSAGE
      );
    } catch (SQLException ex) {
      JOptionPane.showMessageDialog(
        this,
        "The PDF was saved, but the print could not be audited: " +
        ex.getMessage(),
        "Audit warning",
        JOptionPane.WARNING_MESSAGE
      );
    } catch (Exception ex) {
      JOptionPane.showMessageDialog(
        this,
        "Could not export the report: " + ex.getMessage(),
        "Export failed",
        JOptionPane.ERROR_MESSAGE
      );
    }
  }

  // ---- Table model -------------------------------------------------------

  private static final class ReportTableModel extends AbstractTableModel {

    private final String[] cols = {
      "Emp No",
      "Employee Full Name",
      "Position",
      "Department",
      "Basic Earned",
      "Cutoffs",
      "13th Month Pay",
    };
    private final DecimalFormat money = new DecimalFormat("#,##0.00");
    private List<ThirteenthMonthRow> rows = new ArrayList<>();

    void setRows(List<ThirteenthMonthRow> rows) {
      this.rows = (rows != null) ? rows : new ArrayList<>();
      fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
      return rows.size();
    }

    @Override
    public int getColumnCount() {
      return cols.length;
    }

    @Override
    public String getColumnName(int c) {
      return cols[c];
    }

    @Override
    public Object getValueAt(int r, int c) {
      ThirteenthMonthRow row = rows.get(r);
      switch (c) {
        case 0:
          return row.GetEmployeeNo();
        case 1:
          return row.GetEmployeeFullName();
        case 2:
          return row.GetPosition();
        case 3:
          return row.GetDepartment();
        case 4:
          return money.format(row.GetTotalBasicEarned());
        case 5:
          return row.GetPayslipsIncluded();
        case 6:
          return money.format(row.GetThirteenthMonthPay());
        default:
          return "";
      }
    }

    @Override
    public boolean isCellEditable(int r, int c) {
      return false;
    }
  }
}