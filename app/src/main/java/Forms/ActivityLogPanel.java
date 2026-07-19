package Forms;

import Interface.IActivityLogProcess;
import Objects.models.SystemActivity;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

/**
 * Activity Log (Phase 7c) — read-only view of the merged audit + access timeline
 * (vw_SystemActivity). This is where the audit writes from punch corrections and
 * approval actions become visible, alongside login successes/failures.
 *
 * Newest-first, capped at a selectable row limit. HR-granted (VIEW only).
 */
public class ActivityLogPanel extends JPanel {

  private static final Color BRAND_DARK = new Color(0x0D1B2A);
  private static final Color MUTED = new Color(0x6B7682);
  private static final String FONT = "Segoe UI";

  private static final Integer[] LIMITS = { 100, 200, 500, 1000 };

  private final IActivityLogProcess process;

  private final ActivityTableModel tableModel = new ActivityTableModel();
  private final JTable table = new JTable(tableModel);
  private final JComboBox<Integer> limitPicker = new JComboBox<>(LIMITS);
  private final JLabel summaryLabel = new JLabel(" ");
  private JButton refreshBtn;

  public ActivityLogPanel(IActivityLogProcess process) {
    this.process = process;

    setLayout(new BorderLayout());
    setBackground(Color.WHITE);

    add(buildTop(), BorderLayout.NORTH);
    add(buildCenter(), BorderLayout.CENTER);
    add(buildBottom(), BorderLayout.SOUTH);

    limitPicker.setSelectedItem(200);
    wireListeners();
    reload();
  }

  private JComponent buildTop() {
    JPanel top = new JPanel(new BorderLayout());
    top.setBackground(Color.WHITE);
    top.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

    JLabel title = new JLabel("Activity Log");
    title.setFont(new Font(FONT, Font.BOLD, 20));
    title.setForeground(BRAND_DARK);

    JLabel sub = new JLabel(
      "Audit and access trail: data changes (approvals, punch corrections) and sign-in events."
    );
    sub.setFont(new Font(FONT, Font.PLAIN, 12));
    sub.setForeground(MUTED);

    JPanel heads = new JPanel(new BorderLayout());
    heads.setBackground(Color.WHITE);
    heads.add(title, BorderLayout.NORTH);
    heads.add(sub, BorderLayout.SOUTH);

    JPanel filters = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
    filters.setBackground(Color.WHITE);
    limitPicker.setFont(new Font(FONT, Font.PLAIN, 13));
    refreshBtn = brandButton("Refresh");
    filters.add(new JLabel("Show latest:"));
    filters.add(limitPicker);
    filters.add(refreshBtn);

    JPanel wrap = new JPanel(new BorderLayout());
    wrap.setBackground(Color.WHITE);
    wrap.add(heads, BorderLayout.NORTH);
    wrap.add(filters, BorderLayout.SOUTH);

    top.add(wrap, BorderLayout.CENTER);
    return top;
  }

  private JComponent buildCenter() {
    table.setFont(new Font(FONT, Font.PLAIN, 13));
    table.setRowHeight(24);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setFillsViewportHeight(true);
    table.getTableHeader().setFont(new Font(FONT, Font.BOLD, 12));

    JScrollPane scroll = new JScrollPane(table);
    scroll.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 16));
    return scroll;
  }

  private JComponent buildBottom() {
    JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 8));
    bar.setBackground(new Color(0xECEDEF));
    bar.setBorder(
      BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xD8DBDF)),
        BorderFactory.createEmptyBorder(4, 16, 4, 16)
      )
    );
    summaryLabel.setFont(new Font(FONT, Font.PLAIN, 12));
    summaryLabel.setForeground(BRAND_DARK);
    bar.add(summaryLabel);
    return bar;
  }

  private void wireListeners() {
    refreshBtn.addActionListener(e -> reload());
    limitPicker.addActionListener(e -> reload());
  }

  private void reload() {
    setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    try {
      int limit = (Integer) limitPicker.getSelectedItem();
      List<SystemActivity> rows = process.GetRecentActivity(limit);
      tableModel.setData(rows);
      summaryLabel.setText(
        rows.size() + (rows.size() == 1 ? " event" : " events") + " (newest first)"
      );
    } finally {
      setCursor(Cursor.getDefaultCursor());
    }
  }

  private JButton brandButton(String text) {
    JButton b = new JButton(text);
    b.setFont(new Font(FONT, Font.BOLD, 12));
    b.setFocusPainted(false);
    b.setBackground(BRAND_DARK);
    b.setForeground(Color.WHITE);
    b.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return b;
  }

  private static final class ActivityTableModel extends AbstractTableModel {

    private static final DateTimeFormatter STAMP =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String[] cols = { "Time", "Source", "User", "Detail" };
    private List<SystemActivity> rows = new ArrayList<>();

    void setData(List<SystemActivity> r) {
      this.rows = (r != null) ? r : new ArrayList<>();
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
    public boolean isCellEditable(int r, int c) {
      return false;
    }

    @Override
    public Object getValueAt(int r, int c) {
      SystemActivity a = rows.get(r);
      return switch (c) {
        case 0 -> a.GetEventTime() != null ? a.GetEventTime().format(STAMP) : "";
        case 1 -> a.GetSource();
        case 2 -> a.GetUsername();
        case 3 -> a.GetDetail();
        default -> null;
      };
    }
  }
}