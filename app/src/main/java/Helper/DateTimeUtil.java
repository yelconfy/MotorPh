package Helper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class DateTimeUtil {

  // Standard formatters
  private static final DateTimeFormatter DATE_FORMATTER =
    DateTimeFormatter.ofPattern("M/d/yyyy");
  private static final DateTimeFormatter TIME_FORMATTER =
    DateTimeFormatter.ofPattern("H:mm");

  /**
   * Parses a string in MM/dd/yyyy format to LocalDate.
   */
  public static LocalDate parseDate(String dateStr) {
    return LocalDate.parse(dateStr, DATE_FORMATTER);
  }

  /**
   * Parses a string in HH:mm (24-hour) format to LocalTime.
   */
  public static LocalTime parseTime(String timeStr) {
    return LocalTime.parse(timeStr, TIME_FORMATTER);
  }

  /**
   * Formats a LocalDate to MM/dd/yyyy.
   */
  public static String formatDate(LocalDate date) {
    return date.format(DATE_FORMATTER);
  }

  /**
   * Formats a LocalTime to HH:mm (24-hour).
   */
  public static String formatTime(LocalTime time) {
    return time.format(TIME_FORMATTER);
  }
}
