package Forms;

import Core.Service.Bir2316PdfRenderer;
import Interface.IBir2316Process;
import Objects.models.IAM.Session;
import Objects.models.Bir2316Row;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
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
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * BIR Form 2316 certificate screen (Reporting layer) — read-only view over
 * vw_Bir2316 (script 17). Pick a year; the grid shows each employee's annual
 * gross, taxable compensation, tax due, tax withheld, and over/under. Select an
 * employee and export their 2316 certificate PDF (one at a time) via
 * Bir2316PdfRenderer, logged through RecordPrint (reprint-aware).
 *
 * Granted VIEW; each export is a read + an audit row. Mirrors ThirteenthMonthPanel;
 * export acts on the selected row rather than the whole list.
 */
public class Bir2316Panel extends JPanel {

  private static final Color BRAND_DARK = new Color(0x0D1B2A);
  private static final Color MUTED = new Color(0x6B7682);
  private static final String FONT = "Segoe UI";

  private final IBir2316Process process;
  private final Bir2316PdfRenderer renderer;

  private final CertTableModel tableModel = new CertTableModel();
  private final JTable table = new JTable(tableModel);
  private final JComboBox<Integer> yearPicker = new JComboBox<>();
  private final JLabel summaryLabel = new JLabel(" ");
  private JButton refreshBtn;
  private JButton exportBtn;

  private List<Bir2316Row> currentRows = new ArrayList<>();

  public Bir2316Panel(IBir2316Process process, Bir2316PdfRenderer renderer) {
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

    JLabel title = new JLabel("BIR Form 2316");
    title.setFont(new Font(FONT, Font.BOLD, 20));
    title.setForeground(BRAND_DARK);

    JLabel sub = new JLabel(
      "Annual Certificate of Compensation Payment / Tax Withheld (per employee)."
    );
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
    exportBtn = new JButton("Export 2316 (selected)");
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
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    DefaultTableCellRenderer right = new DefaultTableCellRenderer();
    right.setHorizontalAlignment(SwingConstants.RIGHT);
    for (int col : new int[] { 2, 3, 4, 5, 6 }) {
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
    exportBtn.addActionListener(e -> exportSelected());
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
      summaryLabel.setText("No payroll data available.");
      exportBtn.setEnabled(false);
      return;
    }
    currentRows = process.GetForYear(year);
    tableModel.setRows(currentRows);

    double withheld = 0, due = 0;
    for (Bir2316Row r : currentRows) {
      withheld += r.GetTaxWithheld();
      due += r.GetTaxDue();
    }
    DecimalFormat m = new DecimalFormat("#,##0.00");
    summaryLabel.setText(
      currentRows.size() + " employee(s)   -   Tax withheld PHP " + m.format(withheld) +
      "   |   Tax due PHP " + m.format(due) +
      "   -   select a row to export its 2316."
    );
    exportBtn.setEnabled(!currentRows.isEmpty());
  }

  private void exportSelected() {
    int viewRow = table.getSelectedRow();
    if (viewRow < 0) {
      JOptionPane.showMessageDialog(
        this,
        "Select an employee row first.",
        "No selection",
        JOptionPane.INFORMATION_MESSAGE
      );
      return;
    }
    int modelRow = table.convertRowIndexToModel(viewRow);
    Bir2316Row r = currentRows.get(modelRow);
    try {
      byte[] pdf = renderer.Render(r);

      JFileChooser chooser = new JFileChooser();
      chooser.setSelectedFile(
        new File(Bir2316PdfRenderer.SuggestFileName(r.GetEmployeeNo(), r.GetPayYear()))
      );
      if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
        return;
      }
      File target = chooser.getSelectedFile();
      try (FileOutputStream fos = new FileOutputStream(target)) {
        fos.write(pdf);
      }

      boolean isReprint = process.RecordPrint(
        r.GetEmployeeNo(), r.GetPayYear(), Session.GetUsername(),
        "BIR 2316 certificate export"
      );

      JOptionPane.showMessageDialog(
        this,
        (isReprint ? "Re-exported" : "Exported") + " 2316 for " +
        r.GetEmployeeFullName() + " (" + r.GetPayYear() + ").",
        "Export complete",
        JOptionPane.INFORMATION_MESSAGE
      );
    } catch (SQLException ex) {
      JOptionPane.showMessageDialog(
        this,
        "The PDF was saved, but the print could not be audited: " + ex.getMessage(),
        "Audit warning",
        JOptionPane.WARNING_MESSAGE
      );
    } catch (Exception ex) {
      JOptionPane.showMessageDialog(
        this,
        "Could not export the certificate: " + ex.getMessage(),
        "Export failed",
        JOptionPane.ERROR_MESSAGE
      );
    }
  }

  // ---- Table model -------------------------------------------------------

  private static final class CertTableModel extends AbstractTableModel {

    private final String[] cols = {
      "Emp No", "Employee Full Name", "Gross", "Taxable", "Tax Due", "Tax Withheld", "Over/(Under)",
    };
    private final DecimalFormat money = new DecimalFormat("#,##0.00");
    private List<Bir2316Row> rows = new ArrayList<>();

    void setRows(List<Bir2316Row> rows) {
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
      Bir2316Row row = rows.get(r);
      switch (c) {
        case 0:
          return row.GetEmployeeNo();
        case 1:
          return row.GetEmployeeFullName();
        case 2:
          return money.format(row.GetGrossCompensation());
        case 3:
          return money.format(row.GetTaxableCompensation());
        case 4:
          return money.format(row.GetTaxDue());
        case 5:
          return money.format(row.GetTaxWithheld());
        case 6:
          return money.format(row.GetOverUnderWithheld());
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