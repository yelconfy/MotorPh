package Core.Component;

import java.awt.Color;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.text.DecimalFormat;

import javax.swing.BorderFactory;
import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;

import Core.Enum.SmartFieldType;

public class SmartTextField extends JTextField implements SmartField {

  private boolean isFormatting = false;
  private final Border defaultBorder = UIManager.getBorder("TextField.border");
  private final Border errorBorder = BorderFactory.createLineBorder(
    Color.RED,
    1
  );
  private SmartFieldType smartType = SmartFieldType.GENERIC;
  private final DecimalFormat currencyFormatter = new DecimalFormat(
    "PHP #,##0.00"
  );

  public SmartTextField() {
    super();
    // Apply the filter to block invalid characters LIVE
    ((AbstractDocument) this.getDocument()).setDocumentFilter(
      new SmartDocumentFilter()
    );

    // Formatting and Validation check when the user leaves the field
    this.addFocusListener(
      new FocusAdapter() {
        @Override
        public void focusLost(FocusEvent e) {
          applyFormatting();
          // Validate immediately on tab-out so user sees the red border early
          displayError(!smartType.isValid(getText()));
        }
      }
    );
  }

  private boolean isValidInput(String currentText, String incomingText) {
    if (incomingText == null || incomingText.isEmpty()) return true;

    // 1. Strict Numeric (IDs/Integer/Date)
    if (isStrictNumeric()) {
      return incomingText.matches("\\d+");
    }

    // 2. Currency
    if (smartType == SmartFieldType.CURRENCY) {
      if (!incomingText.matches("[0-9.]*")) return false;
      if (incomingText.contains(".") && currentText.contains(".")) return false;
      return true;
    }

    return true;
  }

  /**
   * Inner class to filter text input as it happens.
   */
  private class SmartDocumentFilter extends DocumentFilter {

    @Override
    public void replace(
      FilterBypass fb,
      int offset,
      int length,
      String text,
      AttributeSet attrs
    ) throws BadLocationException {
      if (isFormatting || text == null) {
        super.replace(fb, offset, length, text, attrs);
        return;
      }

      // Calculate what the total length WOULD be after this replacement
      int currentLength = fb.getDocument().getLength();
      int incomingLength = text.length();
      int totalLength = currentLength - length + incomingLength;

      // CHECK LENGTH LIMITS
      if (isStrictNumeric()) {
        int limit = getLimitForType();
        if (limit > 0 && totalLength > limit) {
          return; // BLOCK: exceeds character limit
        }
      }

      // Programmatic load check
      if (text.length() > 1) {
        super.replace(fb, offset, length, text, attrs);
        return;
      }

      if (
        isValidInput(
          fb.getDocument().getText(0, fb.getDocument().getLength()),
          text
        )
      ) {
        super.replace(fb, offset, length, text, attrs);
      }
    }

    // Helper to get limits based on type
    private int getLimitForType() {
      switch (smartType) {
        case SSS:
          return 10;
        case TIN:
          return 12;
        case PHILHEALTH:
          return 12;
        case PAGIBIG:
          return 12;
        case DATE:
          return 8; // MMDDYYYY
        case EMPLOYEE_ID:
          return 5; // MMDDYYYY
        default:
          return 0; // No limit
      }
    }

    // Remember to update insertString too for copy-paste actions
    @Override
    public void insertString(
      FilterBypass fb,
      int offset,
      String string,
      AttributeSet attr
    ) throws BadLocationException {
      int limit = getLimitForType();
      if (
        limit > 0 && (fb.getDocument().getLength() + string.length() > limit)
      ) {
        return;
      }
      if (
        isValidInput(
          fb.getDocument().getText(0, fb.getDocument().getLength()),
          string
        )
      ) {
        super.insertString(fb, offset, string, attr);
      }
    }
  }

  private boolean isStrictNumeric() {
    return (
      smartType == SmartFieldType.INTEGER ||
      smartType == SmartFieldType.SSS ||
      smartType == SmartFieldType.TIN ||
      smartType == SmartFieldType.PHILHEALTH ||
      smartType == SmartFieldType.PAGIBIG ||
      smartType == SmartFieldType.DATE
    ); // Keeps slashes out while typing
  }

  public void applyFormatting() {
    String text = getText().trim();
    if (text.isEmpty() || isFormatting) return;

    try {
      isFormatting = true;
      // Clean everything except numbers and decimals
      String raw = text.replaceAll("[^\\d]", "");

      switch (smartType) {
        case SSS: // 10 Digits (00-0000000-0)
          if (raw.length() == 10) {
            super.setText(
              raw.substring(0, 2) +
                "-" +
                raw.substring(2, 9) +
                "-" +
                raw.substring(9)
            );
          }
          break;
        case TIN: // 9 or 12 Digits
          if (raw.length() == 9) {
            super.setText(
              raw.substring(0, 3) +
                "-" +
                raw.substring(3, 6) +
                "-" +
                raw.substring(6)
            );
          } else if (raw.length() == 12) {
            super.setText(
              raw.substring(0, 3) +
                "-" +
                raw.substring(3, 6) +
                "-" +
                raw.substring(6, 9) +
                "-" +
                raw.substring(9)
            );
          }
          break;
        case PHILHEALTH: // 12 Digits (00-000000000-0)
          if (raw.length() == 12) {
            super.setText(
              raw.substring(0, 2) +
                "-" +
                raw.substring(2, 11) +
                "-" +
                raw.substring(11)
            );
          }
          break;
        case PAGIBIG: // 12 Digits (0000-0000-0000)
          if (raw.length() == 12) {
            super.setText(
              raw.substring(0, 4) +
                "-" +
                raw.substring(4, 8) +
                "-" +
                raw.substring(8)
            );
          }
          break;
        case DATE: // MM/DD/YYYY
          String dateRaw = text.replaceAll("[^\\d]", "");
          if (dateRaw.length() == 8) {
            super.setText(
              dateRaw.substring(0, 2) +
                "/" +
                dateRaw.substring(2, 4) +
                "/" +
                dateRaw.substring(4)
            );
          }
          break;
        case CURRENCY:
          String cleanVal = text.replaceAll("[^\\d.]", "");
          if (!cleanVal.isEmpty()) {
            super.setText(
              currencyFormatter.format(Double.parseDouble(cleanVal))
            );
          }
          break;
        default:
          break;
      }
    } catch (Exception ex) {
      // Log the error if needed: System.out.println("Format Error: " +
      // ex.getMessage());
    } finally {
      isFormatting = false;
    }
  }

  public void setFieldType(SmartFieldType type) {
    this.smartType = type;
    if (type.getMask() != null) {
      this.setToolTipText("Expected Format: " + type.getMask());
    }
  }

  public SmartFieldType getSmartType() {
    return this.smartType;
  }

  public void displayError(boolean hasError) {
    if (hasError) {
      this.setBorder(errorBorder);
      this.setBackground(new Color(255, 235, 235));
    } else {
      this.setBorder(defaultBorder);
      this.setBackground(UIManager.getColor("TextField.background"));
    }
  }

  @Override
  public void setText(String t) {
    super.setText(t);
    // Only format if we aren't ALREADY in the middle of a formatting call
    if (!isFormatting && smartType != Core.Enum.SmartFieldType.GENERIC) {
      applyFormatting();
    }
  }

  @Override
  public boolean isContentValid() {
    // Format first (adds dashes/PHP/commas), then validate by type.
    // SmartFieldType.isValid bakes in mandatory-ness (empty mandatory -> false).
    applyFormatting();
    return smartType.isValid(getText());
  }

  /**
   * Returns the field's value stripped of display formatting, for scraping into
   * a model. CURRENCY loses "PHP" and commas so it parses as a number; other
   * types keep their formatted form (statutory dashes / date slashes are exactly
   * what the DB stores / the caller parses).
   */
  public String getCleanValue() {
    String t = getText().trim();
    if (smartType == SmartFieldType.CURRENCY) {
      return t.replace("PHP", "").replace(",", "").trim();
    }
    return t;
  }
}
