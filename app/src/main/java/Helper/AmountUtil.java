package Helper;

import java.text.DecimalFormat;

public class AmountUtil {

  /**
   * Parses a formatted amount string (e.g. "1,234.56") into a double.
   *
   * @param input the formatted string
   * @return the double value
   */
  public static double ParseFormattedStringToDouble(String input) {
    if (input == null || input.trim().isEmpty()) return 0.0;

    try {
      String cleanValue = input.replaceAll("[^\\d.]", "");

      // Extra check: If the user only typed "." or the string is empty now
      if (cleanValue.isEmpty() || cleanValue.equals(".")) {
        return 0.0;
      }

      return Double.parseDouble(cleanValue);
    } catch (NumberFormatException e) {
      System.err.println("Failed to parse amount: " + input);
      return 0.0;
    }
  }

  /**
   * Formats a double amount to a string with commas and two decimal places.
   *
   * Example: 1234.56 => "1,234.56"
   *
   * @param amount the double amount
   * @return the formatted string
   */
  public static String FormatAmount(double amount) {
    DecimalFormat formatter = new DecimalFormat("#,##0.00");
    return formatter.format(amount);
  }
}
