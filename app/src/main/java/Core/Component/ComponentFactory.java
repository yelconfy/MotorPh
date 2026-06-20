package Core.Component;

import Core.Enum.SmartFieldType;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.LayoutManager;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.border.Border;
import javax.swing.border.TitledBorder;

public class ComponentFactory {

  // -------------------------------------------------------------------------
  // Font constants — use these everywhere instead of hardcoding font names
  // -------------------------------------------------------------------------
  public static final Font TITLE_FONT = new Font("Segoe UI", Font.BOLD, 36);
  public static final Font HEADER_FONT = new Font("Segoe UI", Font.BOLD, 12);
  public static final Font LABEL_FONT = new Font("Segoe UI", Font.PLAIN, 12);
  public static final Font INPUT_FONT = new Font("Segoe UI", Font.PLAIN, 12);
  public static final Font BUTTON_FONT = new Font("Segoe UI", Font.PLAIN, 12);
  public static final Font TABLE_FONT = new Font("Segoe UI", Font.PLAIN, 12);

  // -------------------------------------------------------------------------
  // Labels
  // -------------------------------------------------------------------------

  public static JLabel createTitleLabel(String text) {
    JLabel label = new JLabel(text);
    label.setFont(TITLE_FONT);
    return label;
  }

  public static JLabel createHeaderLabel(String text) {
    JLabel label = new JLabel(text);
    label.setFont(HEADER_FONT);
    return label;
  }

  public static JLabel createLabel(String text) {
    JLabel label = new JLabel(text);
    label.setFont(LABEL_FONT);
    return label;
  }

  // Blank value label — used to display computed/read-only values next to a field label
  public static JLabel createValueLabel() {
    JLabel label = new JLabel(" ");
    label.setFont(LABEL_FONT);
    return label;
  }

  // -------------------------------------------------------------------------
  // Inputs
  // -------------------------------------------------------------------------

  public static JTextField createTextField() {
    JTextField field = new JTextField();
    field.setFont(INPUT_FONT);
    return field;
  }

  public static JPasswordField createPasswordField() {
    JPasswordField field = new JPasswordField();
    field.setFont(INPUT_FONT);
    return field;
  }

  // SmartTextField wired up with the given validation type
  public static SmartTextField createSmartField(SmartFieldType type) {
    SmartTextField field = new SmartTextField();
    field.setFieldType(type);
    field.setFont(INPUT_FONT);
    return field;
  }

  // SmartComboBox for validated dropdowns (Position/Department/Schedule/Status)
  public static <E> SmartComboBox<E> createSmartCombo(boolean mandatory) {
    SmartComboBox<E> combo = new SmartComboBox<>();
    combo.setMandatory(mandatory);
    combo.setFont(INPUT_FONT);
    return combo;
  }

  // -------------------------------------------------------------------------
  // Buttons
  // -------------------------------------------------------------------------

  public static JButton createButton(String text) {
    JButton button = new JButton(text);
    button.setFont(BUTTON_FONT);
    return button;
  }

  // -------------------------------------------------------------------------
  // Panels
  // -------------------------------------------------------------------------

  public static JPanel createPanel(LayoutManager layout) {
    return new JPanel(layout);
  }

  // Panel with uniform padding on all sides
  public static JPanel createPaddedPanel(LayoutManager layout, int padding) {
    JPanel panel = new JPanel(layout);
    panel.setBorder(
      BorderFactory.createEmptyBorder(padding, padding, padding, padding)
    );
    return panel;
  }

  // Panel with independent padding per side
  public static JPanel createPaddedPanel(
    LayoutManager layout,
    int top,
    int left,
    int bottom,
    int right
  ) {
    JPanel panel = new JPanel(layout);
    panel.setBorder(BorderFactory.createEmptyBorder(top, left, bottom, right));
    return panel;
  }

  // Panel with a visible titled border — used for section grouping (e.g. "DEDUCTIONS")
  public static JPanel createSectionPanel(String title, LayoutManager layout) {
    JPanel panel = new JPanel(layout);
    TitledBorder border = BorderFactory.createTitledBorder(
      BorderFactory.createEtchedBorder(),
      title
    );
    border.setTitleFont(HEADER_FONT);
    panel.setBorder(border);
    return panel;
  }

  // -------------------------------------------------------------------------
  // Tables
  // -------------------------------------------------------------------------

  // Non-editable single-selection table wrapped in a scroll pane
  public static JScrollPane createTablePane(
    JTable table,
    int width,
    int height
  ) {
    table.setFont(TABLE_FONT);
    table.setRowHeight(22);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setFillsViewportHeight(true);

    JScrollPane pane = new JScrollPane(table);
    pane.setPreferredSize(new Dimension(width, height));
    return pane;
  }

  // -------------------------------------------------------------------------
  // Borders (standalone — attach to any component)
  // -------------------------------------------------------------------------

  public static Border createOutlineBorder() {
    return BorderFactory.createLineBorder(java.awt.Color.BLACK, 2);
  }
}
