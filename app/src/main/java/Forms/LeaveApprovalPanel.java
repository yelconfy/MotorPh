package Forms;

import Interface.IApprovalProcess;
import Objects.enums.Status.RequestStatus;
import Objects.models.IAM.Session;
import Objects.models.LeaveRequest;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Font;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

/**
 * Leave Approvals (Phase 7a; audited 7c) — admin-facing review queue for PENDING
 * leave requests filed by the (separate) employee app into the shared DB.
 *
 * Select a row, Approve or Reject. Both stamp ActionedBy = Session.GetUserId()
 * and audit the change as Session.GetUsername(), via the process. The toolbar is
 * gated on the APPROVE permission for the LEAVE module.
 */
public class LeaveApprovalPanel extends JPanel {

  private static final Color BRAND_DARK = new Color(0x0D1B2A);
  private static final Color BRAND_RED = new Color(0xE53935);
  private static final Color MUTED = new Color(0x6B7682);
  private static final String FONT = "Segoe UI";

  private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter STAMP_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

  private final IApprovalProcess process;
  private final boolean canApprove;

  private final LeavePendingTableModel tableModel = new LeavePendingTableModel();
  private final JTable table = new JTable(tableModel);
  private final JLabel summaryLabel = new JLabel(" ");

  private JButton approveBtn;
  private JButton rejectBtn;
  private JButton refreshBtn;

  public LeaveApprovalPanel(IApprovalProcess process, List<String> permissions) {
    this.process = process;
    this.canApprove = permissions != null && permissions.contains("APPROVE");

    setLayout(new BorderLayout());
    setBackground(Color.WHITE);

    add(buildTop(), BorderLayout.NORTH);
    add(buildCenter(), BorderLayout.CENTER);
    add(buildBottom(), BorderLayout.SOUTH);

    wireListeners();
    reload();
  }

  private JComponent buildTop() {
    JPanel top = new JPanel(new BorderLayout());
    top.setBackground(Color.WHITE);
    top.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

    JLabel title = new JLabel("Leave Approvals");
    title.setFont(new Font(FONT, Font.BOLD, 20));
    title.setForeground(BRAND_DARK);

    JLabel sub = new JLabel(
      "Review pending leave requests. Approving a paid leave credits the day at payroll time."
    );
    sub.setFont(new Font(FONT, Font.PLAIN, 12));
    sub.setForeground(MUTED);

    JPanel heads = new JPanel(new BorderLayout());
    heads.setBackground(Color.WHITE);
    heads.add(title, BorderLayout.NORTH);
    heads.add(sub, BorderLayout.SOUTH);

    top.add(heads, BorderLayout.WEST);
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
    JPanel bar = new JPanel(new BorderLayout());
    bar.setBackground(new Color(0xECEDEF));
    bar.setBorder(
      BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xD8DBDF)),
        BorderFactory.createEmptyBorder(6, 16, 6, 16)
      )
    );

    summaryLabel.setFont(new Font(FONT, Font.PLAIN, 12));
    summaryLabel.setForeground(BRAND_DARK);

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    actions.setOpaque(false);
    refreshBtn = plainButton("Refresh");
    approveBtn = brandButton("Approve");
    rejectBtn = dangerButton("Reject");
    actions.add(refreshBtn);
    actions.add(rejectBtn);
    actions.add(approveBtn);

    bar.add(summaryLabel, BorderLayout.WEST);
    bar.add(actions, BorderLayout.EAST);
    return bar;
  }

  private void wireListeners() {
    refreshBtn.addActionListener(e -> reload());
    approveBtn.addActionListener(e -> act(RequestStatus.APPROVED));
    rejectBtn.addActionListener(e -> act(RequestStatus.REJECTED));
    table.getSelectionModel().addListSelectionListener(e -> syncButtons());
  }

  private void reload() {
    List<LeaveRequest> pending = process.GetPendingLeave();
    Map<Long, String> names = process.GetEmployeeDirectory();
    tableModel.setData(pending, names);
    summaryLabel.setText(
      pending.size() +
      " pending leave request" +
      (pending.size() == 1 ? "" : "s") +
      (canApprove ? "" : "   \u2014 view only (no approval permission)")
    );
    syncButtons();
  }

  private void syncButtons() {
    boolean hasSelection = table.getSelectedRow() >= 0;
    approveBtn.setEnabled(canApprove && hasSelection);
    rejectBtn.setEnabled(canApprove && hasSelection);
  }

  private void act(RequestStatus decision) {
    int viewRow = table.getSelectedRow();
    if (viewRow < 0) {
      return;
    }
    int modelRow = table.convertRowIndexToModel(viewRow);
    LeaveRequest req = tableModel.getAt(modelRow);

    String verb = decision == RequestStatus.APPROVED ? "Approve" : "Reject";
    String past = decision == RequestStatus.APPROVED ? "approved" : "rejected";

    int choice = JOptionPane.showConfirmDialog(
      this,
      verb + " leave request #" + req.GetLeaveRequestId() + " for " +
      tableModel.nameFor(req.GetEmployeeId()) + "?",
      verb + " leave",
      JOptionPane.YES_NO_OPTION,
      JOptionPane.QUESTION_MESSAGE
    );
    if (choice != JOptionPane.YES_OPTION) {
      return;
    }

    boolean ok = process.ActionLeave(
      req.GetLeaveRequestId(),
      decision,
      Session.GetUserId(),
      Session.GetUsername()
    );
    if (ok) {
      JOptionPane.showMessageDialog(this, "Leave request " + past + ".", "Done", JOptionPane.INFORMATION_MESSAGE);
      reload();
    } else {
      JOptionPane.showMessageDialog(
        this,
        "Could not " + verb.toLowerCase() + " the request. Please try again.",
        "Error",
        JOptionPane.ERROR_MESSAGE
      );
    }
  }

  private JButton brandButton(String text) {
    JButton b = baseButton(text);
    b.setBackground(BRAND_DARK);
    b.setForeground(Color.WHITE);
    return b;
  }

  private JButton dangerButton(String text) {
    JButton b = baseButton(text);
    b.setBackground(BRAND_RED);
    b.setForeground(Color.WHITE);
    return b;
  }

  private JButton plainButton(String text) {
    return baseButton(text);
  }

  private JButton baseButton(String text) {
    JButton b = new JButton(text);
    b.setFont(new Font(FONT, Font.BOLD, 12));
    b.setFocusPainted(false);
    b.setBackground(new Color(0xE3E6EA));
    b.setForeground(BRAND_DARK);
    b.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return b;
  }

  private static final class LeavePendingTableModel extends AbstractTableModel {

    private final String[] cols = {
      "Emp #", "Employee", "Leave Type", "Start", "End", "Days", "Filed", "Reason",
    };

    private List<LeaveRequest> rows = new ArrayList<>();
    private Map<Long, String> names = new HashMap<>();

    void setData(List<LeaveRequest> r, Map<Long, String> n) {
      this.rows = (r != null) ? r : new ArrayList<>();
      this.names = (n != null) ? n : new HashMap<>();
      fireTableDataChanged();
    }

    LeaveRequest getAt(int row) {
      return rows.get(row);
    }

    String nameFor(long employeeId) {
      String n = names.get(employeeId);
      return n != null ? n : "Employee #" + employeeId;
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
      LeaveRequest req = rows.get(r);
      return switch (c) {
        case 0 -> req.GetEmployeeId();
        case 1 -> nameFor(req.GetEmployeeId());
        case 2 -> req.GetLeaveTypeName() != null ? req.GetLeaveTypeName() : "\u2014";
        case 3 -> req.GetStartDate() != null ? req.GetStartDate().format(DATE_FMT) : "";
        case 4 -> req.GetEndDate() != null ? req.GetEndDate().format(DATE_FMT) : "";
        case 5 -> req.GetNumberOfDays();
        case 6 -> req.GetDateFiled() != null ? req.GetDateFiled().format(STAMP_FMT) : "";
        case 7 -> req.GetReason() != null ? req.GetReason() : "";
        default -> null;
      };
    }
  }
}