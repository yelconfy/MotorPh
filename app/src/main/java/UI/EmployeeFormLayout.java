package UI;

import Core.Component.ComponentFactory;
import Core.Component.SmartComboBox;
import Core.Component.SmartField;
import Core.Component.SmartTextField;
import Core.Enum.SmartFieldType;
import Objects.enums.Status.EmploymentStatus;
import Objects.models.DepartmentInfo;
import Objects.models.EmpDetail;
import Objects.models.PositionInfo;
import Objects.models.WorkScheduleInfo;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.AbstractTableModel;

/**
 * Pure Swing construction for the Employee Management screen — the "layout"
 * half of the split, mirroring the existing UI.LoginFormLayout / Forms.LoginForm
 * pattern already in the codebase: this class only knows how to BUILD every
 * widget and exposes them via getters. It has zero knowledge of
 * IEmpMgmtProcess, RBAC, validation, or the Add/Edit/View state machine — all
 * of that lives in Forms.EmployeeManagementPanel (the "logic"/controller
 * half), which composes this class exactly the way LoginForm composes
 * LoginFormLayout.
 *
 * The only "smarts" kept here are the ones that are actually about
 * rendering, not business rules: cell renderers and display formatting
 * (nz/fmtDate/money/statusLabel) — none of it reads a process or makes a
 * decision, it only formats what it's handed. Those four are exposed as
 * public static utilities so the controller can reuse the same formatting
 * when it writes values back into these fields (populateForm), rather than
 * a second copy existing on the controller side.
 */
public class EmployeeFormLayout {

  // Brand tokens (match ShellFrame / PayrollPanel)
  private static final Color BRAND_DARK = new Color(0x0D1B2A);
  private static final Color BRAND_RED = new Color(0xE53935);
  private static final Color MUTED = new Color(0x6B7682);
  private static final String FONT = "Segoe UI";

  // ---- Master: search + table ---------------------------------------------
  private final JTextField searchField = new JTextField(18);
  private final JButton searchBtn = plainButton("Search");
  private final JButton refreshBtn = plainButton("Refresh");
  private final EmployeeTableModel tableModel = new EmployeeTableModel();
  private final JTable table = new JTable(tableModel);

  // ---- Detail: identity ----------------------------------------------------
  private final JLabel empIdValue = new JLabel("\u2014");
  private final SmartTextField lastNameField =
    ComponentFactory.createSmartField(SmartFieldType.NAME);
  private final SmartTextField firstNameField =
    ComponentFactory.createSmartField(SmartFieldType.NAME);
  private final SmartTextField birthdayField =
    ComponentFactory.createSmartField(SmartFieldType.DATE);
  private final SmartTextField emailField = ComponentFactory.createSmartField(
    SmartFieldType.EMAIL
  );
  private final SmartTextField phoneField = ComponentFactory.createSmartField(
    SmartFieldType.PHONE
  );

  // ---- Detail: address (street/barangay/house often blank in data -> optional)
  private final SmartTextField houseField = ComponentFactory.createSmartField(
    SmartFieldType.GENERIC
  );
  private final SmartTextField streetField = ComponentFactory.createSmartField(
    SmartFieldType.GENERIC
  );
  private final SmartTextField barangayField =
    ComponentFactory.createSmartField(SmartFieldType.GENERIC);
  private final SmartTextField cityField = ComponentFactory.createSmartField(
    SmartFieldType.MANDATORY_GENERIC
  );
  private final SmartTextField provinceField =
    ComponentFactory.createSmartField(SmartFieldType.MANDATORY_GENERIC);
  private final SmartTextField zipField = ComponentFactory.createSmartField(
    SmartFieldType.MANDATORY_INTEGER
  );

  // ---- Detail: statutory IDs -----------------------------------------------
  private final SmartTextField sssField = ComponentFactory.createSmartField(
    SmartFieldType.SSS
  );
  private final SmartTextField philHealthField =
    ComponentFactory.createSmartField(SmartFieldType.PHILHEALTH);
  private final SmartTextField tinField = ComponentFactory.createSmartField(
    SmartFieldType.TIN
  );
  private final SmartTextField pagIbigField =
    ComponentFactory.createSmartField(SmartFieldType.PAGIBIG);

  // ---- Detail: assignment ---------------------------------------------------
  private final SmartComboBox<PositionInfo> positionCombo =
    ComponentFactory.createSmartCombo(true);
  private final SmartComboBox<DepartmentInfo> departmentCombo =
    ComponentFactory.createSmartCombo(true);
  private final SmartComboBox<WorkScheduleInfo> scheduleCombo =
    ComponentFactory.createSmartCombo(true);
  private final SmartComboBox<EmploymentStatus> statusCombo =
    ComponentFactory.createSmartCombo(true);
  private final SmartTextField dateHiredField =
    ComponentFactory.createSmartField(SmartFieldType.DATE);

  // ---- Detail: compensation + allowances ------------------------------------
  private final SmartTextField basicSalaryField =
    ComponentFactory.createSmartField(SmartFieldType.CURRENCY);
  private final SmartTextField hourlyRateField =
    ComponentFactory.createSmartField(SmartFieldType.CURRENCY);
  private final SmartTextField riceField = ComponentFactory.createSmartField(
    SmartFieldType.CURRENCY
  );
  private final SmartTextField phoneAllowanceField =
    ComponentFactory.createSmartField(SmartFieldType.CURRENCY);
  private final SmartTextField clothingField =
    ComponentFactory.createSmartField(SmartFieldType.CURRENCY);

  // ---- Toolbar ---------------------------------------------------------------
  private final JButton addBtn = plainButton("Add New");
  private final JButton editBtn = plainButton("Edit");
  private final JButton deleteBtn = dangerButton("Delete");
  private final JButton acceptBtn = brandButton("Accept");
  private final JButton cancelBtn = plainButton("Cancel");

  // Every field the Add/Edit state machine enables & disables together.
  // hourlyRateField is deliberately excluded — it's derived, never typed.
  private final List<JComponent> editableInputs = new ArrayList<>();

  public EmployeeFormLayout() {
    collectEditableInputs();
  }

  /**
   * Assembles the whole screen and returns the root component. Called once by
   * the controller's constructor, mirroring LoginFormLayout.build().
   */
  public JComponent build() {
    JSplitPane split = new JSplitPane(
      JSplitPane.VERTICAL_SPLIT,
      buildMaster(),
      buildDetail()
    );
    split.setResizeWeight(0.42);
    split.setEnabled(false); // user can't drag the divider
    split.setDividerSize(1); // thin separator, no grab handle
    split.setBorder(BorderFactory.createEmptyBorder());
    hourlyRateField.setEditable(false); // derived from basic salary, not typed

    // Self-contained combo setup — EmploymentStatus.values() and the two
    // renderers need no process data, so they're wired here rather than
    // waiting on the controller's loadDropdowns().
    for (EmploymentStatus st : EmploymentStatus.values()) {
      statusCombo.addItem(st);
    }
    setRenderer(scheduleCombo, WorkScheduleInfo::GetScheduleName);
    setRenderer(statusCombo, EmployeeFormLayout::statusLabel);
    positionCombo.setSelectedIndex(-1);
    departmentCombo.setSelectedIndex(-1);
    scheduleCombo.setSelectedIndex(-1);
    statusCombo.setSelectedIndex(-1);

    return split;
  }

  // =========================================================================
  // Master (top)
  // =========================================================================
  private JComponent buildMaster() {
    JPanel master = new JPanel(new BorderLayout());
    master.setBackground(Color.WHITE);
    master.setBorder(BorderFactory.createEmptyBorder(16, 16, 8, 16));

    JLabel title = new JLabel("Employee Management");
    title.setFont(new Font(FONT, Font.BOLD, 20));
    title.setForeground(BRAND_DARK);

    JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
    searchRow.setBackground(Color.WHITE);
    searchField.setFont(new Font(FONT, Font.PLAIN, 13));
    searchField.setToolTipText("Employee #, name, or position");
    searchRow.add(new JLabel("Search:"));
    searchRow.add(searchField);
    searchRow.add(searchBtn);
    searchRow.add(refreshBtn);

    JPanel header = new JPanel(new BorderLayout());
    header.setBackground(Color.WHITE);
    header.setBorder(BorderFactory.createEmptyBorder(0, 0, 12, 0)); // gap above the grid
    header.add(title, BorderLayout.WEST);
    header.add(searchRow, BorderLayout.EAST);

    table.setFont(new Font(FONT, Font.PLAIN, 13));
    table.setRowHeight(24);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setFillsViewportHeight(true);
    table.getTableHeader().setFont(new Font(FONT, Font.BOLD, 12));

    master.add(header, BorderLayout.NORTH);
    master.add(new JScrollPane(table), BorderLayout.CENTER);
    return master;
  }

  // =========================================================================
  // Detail (bottom): form + toolbar
  // =========================================================================
  private JComponent buildDetail() {
    JPanel form = new JPanel();
    form.setBackground(Color.WHITE);
    form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
    form.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));

    JPanel personal = section("Personal");
    field(personal, "Employee #", empIdValue);
    field(personal, "Last name", lastNameField);
    field(personal, "First name", firstNameField);
    field(personal, "Birthday (MM/DD/YYYY)", birthdayField);
    field(personal, "Email", emailField);
    field(personal, "Phone", phoneField);

    JPanel address = section("Address");
    field(address, "House/Block/Lot", houseField);
    field(address, "Street", streetField);
    field(address, "Barangay", barangayField);
    field(address, "City / Municipality", cityField);
    field(address, "Province", provinceField);
    field(address, "ZIP code", zipField);

    JPanel statutory = section("Statutory IDs");
    field(statutory, "SSS", sssField);
    field(statutory, "PhilHealth", philHealthField);
    field(statutory, "TIN", tinField);
    field(statutory, "Pag-IBIG", pagIbigField);

    JPanel assignment = section("Assignment");
    field(assignment, "Position", positionCombo);
    field(assignment, "Department", departmentCombo);
    field(assignment, "Work schedule", scheduleCombo);
    field(assignment, "Employment status", statusCombo);
    field(assignment, "Date hired (MM/DD/YYYY)", dateHiredField);

    JPanel pay = section("Compensation & Allowances");
    field(pay, "Basic salary", basicSalaryField);
    field(pay, "Hourly rate (auto)", hourlyRateField);
    field(pay, "Rice subsidy", riceField);
    field(pay, "Phone allowance", phoneAllowanceField);
    field(pay, "Clothing allowance", clothingField);

    form.add(personal);
    form.add(Box.createVerticalStrut(8));
    form.add(address);
    form.add(Box.createVerticalStrut(8));
    form.add(statutory);
    form.add(Box.createVerticalStrut(8));
    form.add(assignment);
    form.add(Box.createVerticalStrut(8));
    form.add(pay);

    JScrollPane formScroll = new JScrollPane(form);
    formScroll.setBorder(BorderFactory.createEmptyBorder());
    formScroll.getVerticalScrollBar().setUnitIncrement(16);

    JPanel detail = new JPanel(new BorderLayout());
    detail.setBackground(Color.WHITE);
    detail.add(formScroll, BorderLayout.CENTER);
    detail.add(buildToolbar(), BorderLayout.SOUTH);
    return detail;
  }

  private JComponent buildToolbar() {
    JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
    bar.setBackground(Color.WHITE);
    bar.setBorder(
      BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(0xE3E6EA))
    );
    bar.add(addBtn);
    bar.add(editBtn);
    bar.add(deleteBtn);
    bar.add(cancelBtn);
    bar.add(acceptBtn);
    return bar;
  }

  // =========================================================================
  // Section / field helpers (GridBag rows; row counter via client property)
  // =========================================================================
  private JPanel section(String title) {
    JPanel p = new JPanel(new GridBagLayout());
    p.setOpaque(false);
    p.setBorder(BorderFactory.createTitledBorder(title));
    p.putClientProperty("gy", 0);
    return p;
  }

  private void field(JPanel section, String label, JComponent comp) {
    int gy = (int) section.getClientProperty("gy");
    GridBagConstraints g = new GridBagConstraints();
    g.insets = new Insets(3, 6, 3, 6);
    g.gridx = 0;
    g.gridy = gy;
    g.anchor = GridBagConstraints.WEST;
    boolean required = isMandatory(comp) && comp != hourlyRateField; // hourly is auto-derived
    JLabel l = new JLabel(
      required
        ? "<html>" + label + " <font color='#E53935'>*</font></html>"
        : label
    );
    l.setFont(new Font(FONT, Font.PLAIN, 12));
    l.setForeground(MUTED);
    section.add(l, g);

    g.gridx = 1;
    g.weightx = 1;
    g.fill = GridBagConstraints.HORIZONTAL;
    if (comp instanceof JTextField || comp instanceof JComboBox) {
      comp.setPreferredSize(new Dimension(260, 26));
    }
    section.add(comp, g);
    section.putClientProperty("gy", gy + 1);
  }

  private boolean isMandatory(JComponent comp) {
    if (comp instanceof SmartTextField tf) return tf
      .getSmartType()
      .isMandatory();
    if (comp instanceof SmartComboBox<?> cb) return cb.isMandatory();
    return false;
  }

  // =========================================================================
  // State-machine support (pure widget operations, no domain/business meaning)
  // =========================================================================

  /** Enables/disables every field collected in editableInputs. */
  public void setInputsEnabled(boolean enabled) {
    for (JComponent c : editableInputs) {
      c.setEnabled(enabled);
    }
  }

  /** Clears the red-border validation flag on every SmartField input. */
  public void clearFieldErrors() {
    for (JComponent c : editableInputs) {
      if (c instanceof SmartField sf) {
        sf.displayError(false);
      }
    }
  }

  /** Blanks every field, including the derived hourly-rate display. */
  public void clearForm() {
    empIdValue.setText("\u2014");
    for (JComponent c : editableInputs) {
      if (c instanceof JTextField tf) {
        tf.setText("");
      } else if (c instanceof JComboBox<?> combo) {
        combo.setSelectedIndex(-1);
      }
    }
    hourlyRateField.setText(""); // derived field, not in editableInputs -> clear explicitly
  }

  private void collectEditableInputs() {
    editableInputs.add(lastNameField);
    editableInputs.add(firstNameField);
    editableInputs.add(birthdayField);
    editableInputs.add(emailField);
    editableInputs.add(phoneField);
    editableInputs.add(houseField);
    editableInputs.add(streetField);
    editableInputs.add(barangayField);
    editableInputs.add(cityField);
    editableInputs.add(provinceField);
    editableInputs.add(zipField);
    editableInputs.add(sssField);
    editableInputs.add(philHealthField);
    editableInputs.add(tinField);
    editableInputs.add(pagIbigField);
    editableInputs.add(positionCombo);
    editableInputs.add(departmentCombo);
    editableInputs.add(scheduleCombo);
    editableInputs.add(statusCombo);
    editableInputs.add(dateHiredField);
    editableInputs.add(basicSalaryField);
    editableInputs.add(riceField);
    editableInputs.add(phoneAllowanceField);
    editableInputs.add(clothingField);
  }

  // =========================================================================
  // Buttons
  // =========================================================================
  private static JButton brandButton(String text) {
    // Accept — primary confirm
    JButton b = baseButton(text);
    b.setBackground(BRAND_DARK);
    b.setForeground(Color.WHITE);
    return b;
  }

  private static JButton dangerButton(String text) {
    // Delete — destructive
    JButton b = baseButton(text);
    b.setBackground(BRAND_RED);
    b.setForeground(Color.WHITE);
    return b;
  }

  private static JButton plainButton(String text) {
    return baseButton(text);
  }

  private static JButton baseButton(String text) {
    JButton b = new JButton(text);
    b.setFont(new Font(FONT, Font.BOLD, 12));
    b.setFocusPainted(false);
    b.setBackground(new Color(0xE3E6EA)); // neutral flat chip
    b.setForeground(BRAND_DARK);
    b.setBorder(BorderFactory.createEmptyBorder(6, 16, 6, 16));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return b;
  }

  // =========================================================================
  // Display formatting (pure — no process, no business rule). Public so the
  // controller can reuse the same formatting when writing values back in.
  // =========================================================================
  public static String nz(String s) {
    return s == null ? "" : s;
  }

  // Feed the masked DATE field bare digits (MMddyyyy): its filter caps input at
  // 8 digits, so a pre-slashed "MM/dd/yyyy" (10 chars) gets rejected on setText.
  // The field re-adds the slashes via applyFormatting once the digits land.
  public static String fmtDate(java.time.LocalDate d) {
    return d != null ? d.format(DateTimeFormatter.ofPattern("MMddyyyy")) : "";
  }

  public static String money(double v) {
    // Plain number; the CURRENCY SmartTextField formats to "PHP #,##0.00".
    return String.valueOf(v);
  }

  public static String statusLabel(EmploymentStatus s) {
    if (s == null) {
      return "";
    }
    return switch (s) {
      case PROBATIONARY -> "Probationary";
      case REGULAR -> "Regular";
      case TERMINATED -> "Terminated";
    };
  }

  private static <T> void setRenderer(
    JComboBox<T> combo,
    Function<T, String> fmt
  ) {
    combo.setRenderer(
      new DefaultListCellRenderer() {
        @Override
        @SuppressWarnings("unchecked")
        public Component getListCellRendererComponent(
          JList<?> list,
          Object value,
          int index,
          boolean isSelected,
          boolean cellHasFocus
        ) {
          super.getListCellRendererComponent(
            list,
            value,
            index,
            isSelected,
            cellHasFocus
          );
          setText(value != null ? fmt.apply((T) value) : "");
          return this;
        }
      }
    );
  }

  // =========================================================================
  // Getters — every component the controller wires listeners to or
  // reads/writes during scrapeForm/populateForm.
  // =========================================================================
  public JTextField getSearchField() {
    return searchField;
  }

  public JButton getSearchBtn() {
    return searchBtn;
  }

  public JButton getRefreshBtn() {
    return refreshBtn;
  }

  public JTable getTable() {
    return table;
  }

  public EmployeeTableModel getTableModel() {
    return tableModel;
  }

  public JLabel getEmpIdValue() {
    return empIdValue;
  }

  public SmartTextField getLastNameField() {
    return lastNameField;
  }

  public SmartTextField getFirstNameField() {
    return firstNameField;
  }

  public SmartTextField getBirthdayField() {
    return birthdayField;
  }

  public SmartTextField getEmailField() {
    return emailField;
  }

  public SmartTextField getPhoneField() {
    return phoneField;
  }

  public SmartTextField getHouseField() {
    return houseField;
  }

  public SmartTextField getStreetField() {
    return streetField;
  }

  public SmartTextField getBarangayField() {
    return barangayField;
  }

  public SmartTextField getCityField() {
    return cityField;
  }

  public SmartTextField getProvinceField() {
    return provinceField;
  }

  public SmartTextField getZipField() {
    return zipField;
  }

  public SmartTextField getSssField() {
    return sssField;
  }

  public SmartTextField getPhilHealthField() {
    return philHealthField;
  }

  public SmartTextField getTinField() {
    return tinField;
  }

  public SmartTextField getPagIbigField() {
    return pagIbigField;
  }

  public SmartComboBox<PositionInfo> getPositionCombo() {
    return positionCombo;
  }

  public SmartComboBox<DepartmentInfo> getDepartmentCombo() {
    return departmentCombo;
  }

  public SmartComboBox<WorkScheduleInfo> getScheduleCombo() {
    return scheduleCombo;
  }

  public SmartComboBox<EmploymentStatus> getStatusCombo() {
    return statusCombo;
  }

  public SmartTextField getDateHiredField() {
    return dateHiredField;
  }

  public SmartTextField getBasicSalaryField() {
    return basicSalaryField;
  }

  public SmartTextField getHourlyRateField() {
    return hourlyRateField;
  }

  public SmartTextField getRiceField() {
    return riceField;
  }

  public SmartTextField getPhoneAllowanceField() {
    return phoneAllowanceField;
  }

  public SmartTextField getClothingField() {
    return clothingField;
  }

  public JButton getAddBtn() {
    return addBtn;
  }

  public JButton getEditBtn() {
    return editBtn;
  }

  public JButton getDeleteBtn() {
    return deleteBtn;
  }

  public JButton getAcceptBtn() {
    return acceptBtn;
  }

  public JButton getCancelBtn() {
    return cancelBtn;
  }

  // =========================================================================
  // Table model — Emp #, Name, Position, Department, Status
  // =========================================================================
  public final class EmployeeTableModel extends AbstractTableModel {

    private final String[] cols = {
      "Emp #",
      "Name",
      "Position",
      "Department",
      "Status",
    };
    private List<EmpDetail> data = new ArrayList<>();

    public void setData(List<EmpDetail> d) {
      this.data = (d != null) ? d : new ArrayList<>();
      fireTableDataChanged();
    }

    @Override
    public int getRowCount() {
      return data.size();
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
      EmpDetail e = data.get(r);
      return switch (c) {
        case 0 -> e.GetEmployeeId();
        case 1 -> nz(e.GetFullName());
        case 2 -> e.GetPosition() != null
          ? nz(e.GetPosition().GetPositionName())
          : "\u2014";
        case 3 -> e.GetDepartment() != null
          ? nz(e.GetDepartment().GetDepartmentName())
          : "\u2014";
        case 4 -> statusLabel(e.GetEmpStatus());
        default -> null;
      };
    }
  }
}