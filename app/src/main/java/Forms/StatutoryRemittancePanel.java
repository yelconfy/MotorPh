package Forms;

import Core.Service.StatutoryRemittancePdfRenderer;
import Interface.IStatutoryRemittanceProcess;
import Objects.models.IAM.Session;
import Objects.models.StatutoryRemittanceRow;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.io.File;
import java.io.FileOutputStream;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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
 * Statutory Remittance report screen (Reporting layer) — read-only view over
 * vw_StatutoryRemittance (script 17). Pick a month; the grid shows each
 * employee's monthly SSS / PhilHealth / Pag-IBIG totals. Three export buttons
 * render the form-styled agency PDFs (SSS R-3, PhilHealth RF-1, Pag-IBIG M1-1)
 * via StatutoryRemittancePdfRenderer, each logged through RecordPrint
 * (reprint-aware) exactly like the payslip and payroll-summary prints.
 *
 * Granted VIEW; each export is a read + an audit row. Mirrors the structure of
 * PayrollSummaryPanel / ThirteenthMonthPanel; the picker is a (year, month)
 * period picker.
 */
public class StatutoryRemittancePanel extends JPanel {

  private static final Color BRAND_DARK = new Color(0x0D1B2A);
  private static final Color MUTED = new Color(0x6B7682);
  private static final String FONT = "Segoe UI";

  private final IStatutoryRemittanceProcess process;
  private final StatutoryRemittancePdfRenderer renderer;

  private final RemittanceTableModel tableModel = new RemittanceTableModel();
  private final JTable table = new JTable(tableModel);
  private final JComboBox<String> periodPicker = new JComboBox<>();
  private final JLabel summaryLabel = new JLabel(" ");
  private JButton refreshBtn;
  private JButton sssBtn;
  private JButton phicBtn;
  private JButton hdmfBtn;

  private List<int[]> periods = new ArrayList<>();
  private List<StatutoryRemittanceRow> currentRows = new ArrayList<>();

  public StatutoryRemittancePanel(
    IStatutoryRemittanceProcess process,
    StatutoryRemittancePdfRenderer renderer
  ) {
    this.process = process;
    this.renderer = renderer;

    setLayout(new BorderLayout());
    setBackground(Color.WHITE);

    add(buildTop(), BorderLayout.NORTH);
    add(buildCenter(), BorderLayout.CENTER);
    add(buildBottom(), BorderLayout.SOUTH);

    loadPeriods();
    wireListeners();
    reload();
  }

  // ---- Layout ------------------------------------------------------------

  private JComponent buildTop() {
    JPanel top = new JPanel(new BorderLayout());
    top.setBackground(Color.WHITE);
    top.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

    JLabel title = new JLabel("Statutory Remittance");
    title.setFont(new Font(FONT, Font.BOLD, 20));
    title.setForeground(BRAND_DARK);

    JLabel sub = new JLabel(
      "Monthly SSS / PhilHealth / Pag-IBIG contributions (employee + employer)."
    );
    sub.setFont(new Font(FONT, Font.PLAIN, 12));
    sub.setForeground(MUTED);

    JPanel titleBox = new JPanel(new BorderLayout());
    titleBox.setBackground(Color.WHITE);
    titleBox.add(title, BorderLayout.NORTH);
    titleBox.add(sub, BorderLayout.SOUTH);

    JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    controls.setBackground(Color.WHITE);
    controls.add(new JLabel("Month:"));
    controls.add(periodPicker);
    refreshBtn = new JButton("Refresh");
    sssBtn = new JButton("Export SSS R-3");
    phicBtn = new JButton("Export PhilHealth RF-1");
    hdmfBtn = new JButton("Export Pag-IBIG M1-1");
    controls.add(refreshBtn);
    controls.add(sssBtn);
    controls.add(phicBtn);
    controls.add(hdmfBtn);

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
    for (int col : new int[] { 2, 3, 4, 5 }) {
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
    periodPicker.addActionListener(e -> reload());
    sssBtn.addActionListener(e -> exportForm(StatutoryRemittancePdfRenderer.AGENCY_SSS));
    phicBtn.addActionListener(e -> exportForm(StatutoryRemittancePdfRenderer.AGENCY_PHIC));
    hdmfBtn.addActionListener(e -> exportForm(StatutoryRemittancePdfRenderer.AGENCY_HDMF));
  }

  private void loadPeriods() {
    periods = process.GetAvailablePeriods();
    periodPicker.removeAllItems();
    for (int[] p : periods) {
      String monthName = Month.of(p[1]).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
      periodPicker.addItem(monthName + " " + p[0]);
    }
  }

  private int[] selectedPeriod() {
    int idx = periodPicker.getSelectedIndex();
    if (idx < 0 || idx >= periods.size()) {
      return null;
    }
    return periods.get(idx);
  }

  private void reload() {
    int[] period = selectedPeriod();
    boolean has = period != null;
    if (!has) {
      currentRows = new ArrayList<>();
      tableModel.setRows(currentRows);
      summaryLabel.setText("No remittance data available.");
      setExportsEnabled(false);
      return;
    }
    currentRows = process.GetForMonth(period[0], period[1]);
    tableModel.setRows(currentRows);

    double sss = 0, phic = 0, hdmf = 0;
    for (StatutoryRemittanceRow r : currentRows) {
      sss += r.GetSssTotal();
      phic += r.GetPhicTotal();
      hdmf += r.GetHdmfTotal();
    }
    DecimalFormat m = new DecimalFormat("#,##0.00");
    summaryLabel.setText(
      currentRows.size() + " employee(s)   -   SSS PHP " + m.format(sss) +
      "   |   PhilHealth PHP " + m.format(phic) +
      "   |   Pag-IBIG PHP " + m.format(hdmf)
    );
    setExportsEnabled(!currentRows.isEmpty());
  }

  private void setExportsEnabled(boolean on) {
    sssBtn.setEnabled(on);
    phicBtn.setEnabled(on);
    hdmfBtn.setEnabled(on);
  }

  private void exportForm(String agency) {
    int[] period = selectedPeriod();
    if (period == null || currentRows.isEmpty()) {
      return;
    }
    int year = period[0];
    int month = period[1];
    try {
      byte[] pdf;
      String reason;
      if (StatutoryRemittancePdfRenderer.AGENCY_SSS.equals(agency)) {
        pdf = renderer.RenderSssR3(year, month, currentRows);
        reason = "SSS R-3 remittance export";
      } else if (StatutoryRemittancePdfRenderer.AGENCY_PHIC.equals(agency)) {
        pdf = renderer.RenderPhilHealthRf1(year, month, currentRows);
        reason = "PhilHealth RF-1 remittance export";
      } else {
        pdf = renderer.RenderPagIbigM11(year, month, currentRows);
        reason = "Pag-IBIG M1-1 remittance export";
      }

      JFileChooser chooser = new JFileChooser();
      chooser.setSelectedFile(
        new File(StatutoryRemittancePdfRenderer.SuggestFileName(agency, year, month))
      );
      if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
        return;
      }
      File target = chooser.getSelectedFile();
      try (FileOutputStream fos = new FileOutputStream(target)) {
        fos.write(pdf);
      }

      boolean isReprint = process.RecordPrint(
        agency, year, month, Session.GetUsername(), reason
      );

      JOptionPane.showMessageDialog(
        this,
        (isReprint ? "Re-exported " : "Exported ") + agency + " for " +
        Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + year + ".",
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
        "Could not export the report: " + ex.getMessage(),
        "Export failed",
        JOptionPane.ERROR_MESSAGE
      );
    }
  }

  // ---- Table model -------------------------------------------------------

  private static final class RemittanceTableModel extends AbstractTableModel {

    private final String[] cols = {
      "Emp No", "Employee Full Name", "SSS Total", "PhilHealth Total", "Pag-IBIG Total", "Grand Total",
    };
    private final DecimalFormat money = new DecimalFormat("#,##0.00");
    private List<StatutoryRemittanceRow> rows = new ArrayList<>();

    void setRows(List<StatutoryRemittanceRow> rows) {
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
      StatutoryRemittanceRow row = rows.get(r);
      switch (c) {
        case 0:
          return row.GetEmployeeNo();
        case 1:
          return row.GetEmployeeFullName();
        case 2:
          return money.format(row.GetSssTotal());
        case 3:
          return money.format(row.GetPhicTotal());
        case 4:
          return money.format(row.GetHdmfTotal());
        case 5:
          return money.format(row.GetSssTotal() + row.GetPhicTotal() + row.GetHdmfTotal());
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