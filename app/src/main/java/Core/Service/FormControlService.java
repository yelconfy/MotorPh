package Core.Service;

import Core.Component.SmartField;
import java.awt.Component;
import java.awt.Container;

public class FormControlService {

  public boolean validate(Container container) {
    boolean allValid = true;

    for (Component c : container.getComponents()) {
      if (c instanceof SmartField smartField) {
        // Each smart element validates itself (format + mandatory) and shows
        // its own error styling. New element types just implement SmartField.
        boolean isValid = smartField.isContentValid();
        smartField.displayError(!isValid);
        if (!isValid) {
          allValid = false;
        }
      } else if (c instanceof Container nested) {
        if (!validate(nested)) {
          allValid = false;
        }
      }
    }
    return allValid;
  }
}