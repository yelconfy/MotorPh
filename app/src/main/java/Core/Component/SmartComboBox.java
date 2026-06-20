package Core.Component;

import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.UIManager;
import javax.swing.border.Border;

/**
 * A JComboBox that participates in FormControlService validation via SmartField.
 *
 * Validity rule: a mandatory combo with no selection (getSelectedItem() == null,
 * i.e. selectedIndex == -1) is invalid; everything else is valid.
 *
 * For a mandatory combo, populate the items then call setSelectedIndex(-1) so the
 * user must make a deliberate choice (JComboBox otherwise auto-selects index 0).
 */
public class SmartComboBox<E> extends JComboBox<E> implements SmartField {

  private boolean mandatory = false;
  private final Border defaultBorder =
    getBorder() != null ? getBorder() : UIManager.getBorder("ComboBox.border");
  private final Border errorBorder = BorderFactory.createLineBorder(Color.RED, 1);

  public SmartComboBox() {
    super();
  }

  public void setMandatory(boolean mandatory) {
    this.mandatory = mandatory;
  }

  public boolean isMandatory() {
    return mandatory;
  }

  /** True when this combo satisfies its mandatory requirement. */
  public boolean isSelectionValid() {
    return !mandatory || getSelectedItem() != null;
  }

  @Override
  public boolean isContentValid() {
    return isSelectionValid();
  }

  @Override
  public void displayError(boolean hasError) {
    setBorder(hasError ? errorBorder : defaultBorder);
  }
}