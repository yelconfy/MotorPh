package Forms;

import Core.Component.ComponentFactory;
import Core.Component.SmartTextField;
import Core.Service.FormControlService;
import Interface.IMaintenanceProcess;
import Objects.results.SaveResult;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

/**
 * Generic master-detail CRUD screen for a reference-data maintenance module
 * (BKL-26) — extracted from PositionMaintenancePanel, which was ~250 lines
 * of table model, master/detail layout, Mode state machine, RBAC button
 * hiding, and DeleteOutcome handling that would otherwise have been retyped
 * for Departments, Leave Type, Allowance Type, Deduction Type, and Work
 * Schedule.
 *
 * Everything screen-specific lives in the MaintenanceDescriptor<T> passed in
 * at construction (title, table columns, form fields, id/label extraction,
 * a blank-instance factory for Add, and the handful of dialog strings that
 * need exact wording). This class is the mechanics only: select a row -> the
 * form populates (VIEW, read-only) -> Edit enables it -> Accept validates via
 * FormControlService and persists -> back to VIEW. Add New blanks the form
 * into ADD mode; Delete removes, guarded against IN_USE the same way Positions
 * always was.
 *
 * Talks only to IMaintenanceProcess<T> (no raw DAOs). RBAC: buttons the role
 * isn't granted are hidden, identical to every other maintenance/CRUD panel
 * in the app.
 *
 * POSITIONS is the retrofit proof (see ReferenceModules) — same behavior,
 * same dialogs, zero bespoke Panel class. Departments and the next four flat
 * reference tables (BKL-01 stage 3a/3b) add only a DAO + a thin process + a
 * descriptor, no new Panel.
 */
public class ReferenceMaintenancePanel<T> extends JPanel {

  private static final Color BRAND_DARK = new Color(0x0D1B2A);
  private static final Color BRAND_RED = new Color(0xE53935);
  private static final Color MUTED = new Color(0x6B7682);
  private static final String FONT = "Segoe UI";

  private enum Mode {
    VIEW,
    ADD,
    EDIT,
  }

  private final IMaintenanceProcess<T> process;
  private final FormControlService validator;
  private final MaintenanceDescriptor<T> descriptor;
  private final boolean canAdd;
  private final boolean canEdit;
  private final boolean canDelete;

  // ---- Master: table -----------------------------------------------------
  private final MaintenanceTableModel tableModel;
  private final JTable table;

  // ---- Detail: descriptor-driven fields -----------------------------------
  private final JLabel idValue = new JLabel("\u2014");
  private final Map<String, FieldBinding<T>> bindings = new LinkedHashMap<>();
  private final JPanel formPanel = new JPanel(new GridBagLayout());

  // ---- Toolbar -------------------------------------------------------------
  private final JButton addBtn = plainButton("Add New");
  private final JButton editBtn = plainButton("Edit");
  private final JButton deleteBtn = dangerButton("Delete");
  private final JButton acceptBtn = brandButton("Accept");
  private final JButton cancelBtn = plainButton("Cancel");

  // ---- State ---------------------------------------------------------------
  private List<T> rows = new ArrayList<>();
  private T current;
  private Mode mode = Mode.VIEW;

  public ReferenceMaintenancePanel(
    IMaintenanceProcess<T> process,
    FormControlService validator,
    List<String> permissions,
    MaintenanceDescriptor<T> descriptor
  ) {
    this.process = process;
    this.validator = validator;
    this.descriptor = descriptor;
    this.canAdd = permissions != null && permissions.contains("ADD");
    this.canEdit = permissions != null && permissions.contains("EDIT");
    this.canDelete = permissions != null && permissions.contains("DELETE");

    this.tableModel = new MaintenanceTableModel();
    this.table = new JTable(tableModel);

    setLayout(new BorderLayout());
    setBackground(Color.WHITE);
    setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

    add(buildHeader(), BorderLayout.NORTH);
    add(buildMaster(), BorderLayout.CENTER);
    add(buildDetail(), BorderLayout.SOUTH);

    wireActions();
    reload();
    setMode(Mode.VIEW);
  }

  // =========================================================================
  // UI construction
  // =========================================================================

  private JPanel buildHeader() {
    JPanel p = new JPanel(new BorderLayout());
    p.setOpaque(false);
    p.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0));

    JLabel title = new JLabel(descriptor.GetTitle());
    title.setFont(new Font(FONT, Font.BOLD, 20));
    title.setForeground(BRAND_DARK);

    JLabel subtitle = new JLabel(descriptor.GetSubtitle());
    subtitle.setFont(new Font(FONT, Font.PLAIN, 12));
    subtitle.setForeground(MUTED);

    JPanel text = new JPanel();
    text.setOpaque(false);
    text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
    title.setAlignmentX(LEFT_ALIGNMENT);
    subtitle.setAlignmentX(LEFT_ALIGNMENT);
    text.add(title);
    text.add(subtitle);

    p.add(text, BorderLayout.WEST);
    return p;
  }

  private JScrollPane buildMaster() {
    table.setFont(new Font(FONT, Font.PLAIN, 12));
    table.setRowHeight(26);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.getTableHeader().setReorderingAllowed(false);
    table
      .getSelectionModel()
      .addListSelectionListener(e -> {
        if (!e.getValueIsAdjusting()) {
          onRowSelected();
        }
      });

    JScrollPane sp = new JScrollPane(table);
    sp.setBorder(BorderFactory.createLineBorder(new Color(0xE3E6EA)));
    return sp;
  }

  private JPanel buildDetail() {
    JPanel wrapper = new JPanel(new BorderLayout());
    wrapper.setOpaque(false);
    wrapper.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));

    formPanel.setOpaque(false);
    GridBagConstraints gc = new GridBagConstraints();
    gc.insets = new Insets(4, 4, 4, 8);
    gc.anchor = GridBagConstraints.WEST;

    int gy = 0;

    gc.gridx = 0;
    gc.gridy = gy;
    gc.fill = GridBagConstraints.NONE;
    gc.weightx = 0;
    formPanel.add(label(capitalize(descriptor.GetItemLabel()) + " ID"), gc);
    gc.gridx = 1;
    idValue.setFont(new Font(FONT, Font.PLAIN, 12));
    idValue.setForeground(MUTED);
    formPanel.add(idValue, gc);
    gy++;

    for (Map.Entry<String, FieldDescriptor<T>> entry : descriptor
      .GetFields()
      .entrySet()) {
      FieldDescriptor<T> fd = entry.getValue();
      FieldBinding<T> binding = FieldBinding.From(fd);
      binding.GetComponent().setPreferredSize(new Dimension(260, 26));
      bindings.put(entry.getKey(), binding);

      gc.gridx = 0;
      gc.gridy = gy;
      gc.fill = GridBagConstraints.NONE;
      gc.weightx = 0;
      formPanel.add(label(fd.GetLabel()), gc);

      gc.gridx = 1;
      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.weightx = 1.0;
      formPanel.add(binding.GetComponent(), gc);
      gy++;
    }

    JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    toolbar.setOpaque(false);
    toolbar.add(addBtn);
    toolbar.add(editBtn);
    toolbar.add(deleteBtn);
    toolbar.add(cancelBtn);
    toolbar.add(acceptBtn);

    wrapper.add(formPanel, BorderLayout.CENTER);
    wrapper.add(toolbar, BorderLayout.SOUTH);

    // RBAC: hide actions the role isn't granted.
    addBtn.setVisible(canAdd);
    editBtn.setVisible(canEdit);
    deleteBtn.setVisible(canDelete);

    return wrapper;
  }

  // =========================================================================
  // Actions / state machine
  // =========================================================================

  private void wireActions() {
    addBtn.addActionListener(e -> beginAdd());
    editBtn.addActionListener(e -> beginEdit());
    deleteBtn.addActionListener(e -> doDelete());
    cancelBtn.addActionListener(e -> cancelEdit());
    acceptBtn.addActionListener(e -> doSave());
  }

  private void reload() {
    Long keepId = (current != null)
      ? descriptor.GetIdFn().apply(current)
      : null;
    rows = process.GetAll();
    tableModel.setData(rows);

    if (rows.isEmpty()) {
      current = null;
      clearForm();
      return;
    }

    int selectRow = 0;
    if (keepId != null) {
      for (int i = 0; i < rows.size(); i++) {
        if (descriptor.GetIdFn().apply(rows.get(i)).equals(keepId)) {
          selectRow = i;
          break;
        }
      }
    }
    table.setRowSelectionInterval(selectRow, selectRow);
  }

  private void onRowSelected() {
    if (mode != Mode.VIEW) {
      return; // don't clobber an in-progress add/edit
    }
    int r = table.getSelectedRow();
    if (r < 0 || r >= rows.size()) {
      current = null;
      clearForm();
      return;
    }
    current = rows.get(r);
    idValue.setText(String.valueOf(descriptor.GetIdFn().apply(current)));
    for (FieldBinding<T> b : bindings.values()) {
      b.PopulateFrom(current);
    }
  }

  private void beginAdd() {
    current = null;
    idValue.setText("(new)");
    for (FieldBinding<T> b : bindings.values()) {
      b.Clear();
    }
    setMode(Mode.ADD);
    if (!bindings.isEmpty()) {
      bindings.values().iterator().next().GetComponent().requestFocusInWindow();
    }
  }

  private void beginEdit() {
    if (current == null) {
      JOptionPane.showMessageDialog(
        this,
        "Select a " + descriptor.GetItemLabel() + " to edit first.",
        "Nothing selected",
        JOptionPane.INFORMATION_MESSAGE
      );
      return;
    }
    if (descriptor.IsProtected(current)) {
      JOptionPane.showMessageDialog(
        this,
        descriptor.GetProtectedMessage(),
        "Protected " + descriptor.GetItemLabel(),
        JOptionPane.INFORMATION_MESSAGE
      );
      return;
    }
    setMode(Mode.EDIT);
    if (!bindings.isEmpty()) {
      bindings.values().iterator().next().GetComponent().requestFocusInWindow();
    }
  }

  private void cancelEdit() {
    setMode(Mode.VIEW);
    onRowSelected(); // restore the selected row's values
  }

  private void doSave() {
    if (!validator.validate(formPanel)) {
      return; // SmartField shows its own error styling
    }

    SaveResult<Void> result;
    if (mode == Mode.ADD) {
      T item = descriptor.GetFactory().get();
      applyFieldsTo(item);
      result = process.Add(item);
    } else if (mode == Mode.EDIT && current != null) {
      applyFieldsTo(current);
      result = process.Update(current);
    } else {
      return;
    }

    switch (result.GetOutcome()) {
      case SUCCESS -> {
        setMode(Mode.VIEW);
        reload();
      }
      case VALIDATION_FAILED, FAILED, IN_USE -> JOptionPane.showMessageDialog(
        this,
        result.GetMessage() != null
          ? result.GetMessage()
          : descriptor.GetSaveFailedMessage(),
        "Save failed",
        JOptionPane.ERROR_MESSAGE
      );
    }
  }

  private void applyFieldsTo(T item) {
    for (FieldBinding<T> b : bindings.values()) {
      b.ApplyTo(item);
    }
  }

  private void doDelete() {
    if (current == null) {
      JOptionPane.showMessageDialog(
        this,
        "Select a " + descriptor.GetItemLabel() + " to delete first.",
        "Nothing selected",
        JOptionPane.INFORMATION_MESSAGE
      );
      return;
    }
    if (descriptor.IsProtected(current)) {
      JOptionPane.showMessageDialog(
        this,
        descriptor.GetProtectedMessage(),
        "Protected " + descriptor.GetItemLabel(),
        JOptionPane.INFORMATION_MESSAGE
      );
      return;
    }

    String label = descriptor.GetLabelFn().apply(current);
    int choice = JOptionPane.showConfirmDialog(
      this,
      "Delete " + descriptor.GetItemLabel() + " \"" + label + "\"?",
      "Confirm delete",
      JOptionPane.YES_NO_OPTION,
      JOptionPane.WARNING_MESSAGE
    );
    if (choice != JOptionPane.YES_OPTION) {
      return;
    }

    long id = descriptor.GetIdFn().apply(current);
    SaveResult<Void> result = process.Delete(id);
    switch (result.GetOutcome()) {
      case SUCCESS -> {
        current = null;
        reload();
      }
      case IN_USE -> JOptionPane.showMessageDialog(
        this,
        // Prefer the process's specific reason; fall back to the descriptor's.
        result.GetMessage() != null
          ? result.GetMessage()
          : descriptor.GetInUseMessage(),
        capitalize(descriptor.GetItemLabel()) + " in use",
        JOptionPane.WARNING_MESSAGE
      );
      case VALIDATION_FAILED, FAILED -> JOptionPane.showMessageDialog(
        this,
        result.GetMessage() != null
          ? result.GetMessage()
          : "Could not delete the " +
            descriptor.GetItemLabel() +
            ". Please try again.",
        "Delete failed",
        JOptionPane.ERROR_MESSAGE
      );
    }
  }

  private void setMode(Mode m) {
    this.mode = m;
    boolean editing = (m == Mode.ADD || m == Mode.EDIT);

    for (FieldBinding<T> b : bindings.values()) {
      b.SetEditable(editing);
    }

    // In an edit/add, only Accept/Cancel are live; otherwise the CRUD entry points.
    addBtn.setVisible(canAdd && !editing);
    editBtn.setVisible(canEdit && !editing);
    deleteBtn.setVisible(canDelete && !editing);
    acceptBtn.setVisible(editing);
    cancelBtn.setVisible(editing);

    table.setEnabled(!editing);
  }

  private void clearForm() {
    idValue.setText("\u2014");
    for (FieldBinding<T> b : bindings.values()) {
      b.Clear();
    }
  }

  // =========================================================================
  // Small helpers (same local button style every other maintenance panel uses)
  // =========================================================================

  private static String capitalize(String s) {
    if (s == null || s.isEmpty()) return s;
    return s.substring(0, 1).toUpperCase() + s.substring(1);
  }

  private JLabel label(String text) {
    JLabel l = new JLabel(text);
    l.setFont(new Font(FONT, Font.BOLD, 12));
    l.setForeground(BRAND_DARK);
    return l;
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

  // =========================================================================
  // Table model — columns come from the descriptor
  // =========================================================================
  private final class MaintenanceTableModel extends AbstractTableModel {

    private List<T> data = new ArrayList<>();

    void setData(List<T> d) {
      this.data = (d != null) ? d : new ArrayList<>();
      fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
      return data.size();
    }

    @Override
    public int getColumnCount() {
      return descriptor.GetColumns().size();
    }

    @Override
    public String getColumnName(int c) {
      return descriptor.GetColumns().get(c).GetHeader();
    }

    @Override
    public boolean isCellEditable(int r, int c) {
      return false;
    }

    @Override
    public Object getValueAt(int r, int c) {
      return descriptor.GetColumns().get(c).GetValueFn().apply(data.get(r));
    }
  }
}
