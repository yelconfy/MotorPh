package Core.Enum;

import java.util.regex.Pattern;

public enum SmartFieldType {
  // Identity(isMandatory, inputMask, regexPattern)
  NAME(true, null, "^[A-Za-z\\s-]{2,50}$"),
  EMAIL(false, null, "^[A-Za-z0-9+_.-]+@(.+)$"),

  // Numeric types
  INTEGER(false, null, "^\\d+$"),
  MANDATORY_INTEGER(true, null, "^\\d+$"),

  // MotorPH Specifics
  EMPLOYEE_ID(false, "#####", "^\\d{5}$"), // Assuming a 5-digit ID
  SSS(true, "##-#######-#", "^\\d{2}-\\d{7}-\\d{1}$"),
  TIN(true, "###-###-###-###", "^\\d{3}-\\d{3}-\\d{3}-\\d{3}$"),
  PHILHEALTH(true, "##-#########-#", "^\\d{2}-\\d{9}-\\d{1}$"),
  PAGIBIG(true, "####-####-####", "^\\d{4}-\\d{4}-\\d{4}$"),

  // Financial & Professional
  BANK_ACCOUNT(true, null, "^\\d{10,16}$"), // Standard PH bank account length
  POSITION(true, null, "^[A-Za-z0-9\\s-]{2,30}$"),
  DEPARTMENT(true, null, "^[A-Za-z\\s]{2,30}$"),

  // Numeric & Contact
  CURRENCY(true, null, "^\\d{1,3}(,\\d{3})*(\\.\\d{2})?$"),
  PHONE(true, "####-###-####", "^\\+?[0-9\\s-]{7,15}$"),
  DATE(true, "MM/DD/YYYY", "^(0[1-9]|1[0-2])/(0[1-9]|[12][0-9]|3[01])/\\d{4}$"), // MM-DD-YYYY

  // Fallback
  GENERIC(false, null, ".*"),
  MANDATORY_GENERIC(true, null, ".+"); // For any field that just can't be empty

  private final boolean mandatory;
  private final String mask;
  private final String regex;

  SmartFieldType(boolean mandatory, String mask, String regex) {
    this.mandatory = mandatory;
    this.mask = mask;
    this.regex = regex;
  }

  public boolean isValid(String input) {
    if (input == null || input.trim().isEmpty()) {
      return !mandatory; // Search (GENERIC) returns true here because mandatory = false
    }

    String valueToValidate = input.trim();

    // If it's currency, strip PHP and commas so we can use a simpler, cleaner regex
    if (this == CURRENCY) {
      valueToValidate = valueToValidate
        .replace("PHP", "")
        .replace(",", "")
        .trim();
      // New Simple Regex: Starts with digits, optional dot and 2 decimals
      return Pattern.matches("^\\d+(\\.\\d{2})?$", valueToValidate);
    }

    if (regex != null) {
      return Pattern.matches(regex, valueToValidate);
    }

    return true;
  }

  public boolean isMandatory() {
    return mandatory;
  }

  public String getMask() {
    return mask;
  }
}
