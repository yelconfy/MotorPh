package Objects.enums;

import Helper.KeyValue;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.Color;
import java.text.MessageFormat;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.function.Predicate;
import javax.swing.Icon;

public class Constants {

  // <editor-fold defaultstate="collapsed" desc="File">
  public enum File {
    EmpDetailsCSV(
      "EmpDetailsCSV.csv",
      "\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,\"%s\"%n",
      "Employee #,Last Name,First Name,Birthday,Email,Phone Number,House/Block/Lot #,Street,Barangay,City/Municipality,Province,ZIP Code,SSS #,Philhealth #,TIN #,Pag-ibig #,Employment Status,Position,Immediate Supervisor,Date Hired,Basic Salary,Rice Subsidy,Phone Allowance,Clothing Allowance,Gross Semi-monthly Rate,Hourly Rate,Status"
    ),
    CredCSV(
      "CredCSV.csv",
      "%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%s%n", // edit t
      ""
    ),
    EmpAttendanceRecordCSV(
      "EmpAttendanceRecordCSV.csv",
      "%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%s,%.2f,%.2f,%.2f,%.2f,%.2f,%.2f,%s%n", // edit t
      ""
    );

    private static final String runtimeRootPath =
      System.getProperty("user.dir") + "/files";

    private String fileName;
    private String columnFormat;
    private String header;

    File(String fileName, String columnFormat, String header) {
      this.fileName = fileName;
      this.columnFormat = columnFormat;
      this.header = header;
    }

    public String GetFileName() {
      return fileName;
    }

    public String GetColumnFormat() {
      return columnFormat;
    }

    public String GetHeader() {
      return header;
    }

    public static String GetFilePath(String fileName) {
      return runtimeRootPath + "/" + fileName;
    }
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="ScreenState">
  public enum ScreenState {
    DEFAULT(0, "Default"),
    ADD(1, "Add"),
    EDIT(2, "Edit");

    private int key;
    private String value;

    ScreenState(int key, String value) {
      this.key = key;
      this.value = value;
    }

    public int GetKey() {
      return this.key;
    }

    public String GetValue() {
      return this.value;
    }
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="ShiftTime">
  public enum ShiftTime {
    START(LocalTime.of(8, 30)),
    END(LocalTime.of(17, 30)),
    LUNCH_BREAK_DURATION(Duration.ofHours(1)),
    SHIFT_LENGTH(Duration.ofHours(8));

    private final Object value;

    ShiftTime(Object value) {
      this.value = value;
    }

    public LocalTime GetTime() {
      return (LocalTime) value;
    }

    public Duration GetDuration() {
      return (Duration) value;
    }

    public Duration GetShiftLength() {
      return (Duration) value;
    }
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="Months">
  public enum Months {
    JANUARY(1, "January"),
    FEBRUARY(2, "February"),
    MARCH(3, "March"),
    APRIL(4, "April"),
    MAY(5, "May"),
    JUNE(6, "June"),
    JULY(7, "July"),
    AUGUST(8, "August"),
    SEPTEMBER(9, "September"),
    OCTOBER(10, "October"),
    NOVEMBER(11, "November"),
    DECEMBER(12, "December");

    private final int value;
    private final String name;

    Months(int value, String name) {
      this.value = value;
      this.name = name;
    }

    public int GetValue() {
      return value;
    }

    public String GetName() {
      return name;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="Holidays">
  public enum Holidays {
    NEW_YEAR(
      new KeyValue<>(1, "New Year's Day"),
      date ->
        date.getMonthValue() == Months.JANUARY.GetValue() &&
        date.getDayOfMonth() == 1
    ),
    MAUNDY_THURSDAY(
      new KeyValue<>(2, "Maundy Thursday"),
      Holidays::IsMaundyThursday
    ),
    GOOD_FRIDAY(new KeyValue<>(3, "Good Friday"), Holidays::IsGoodFriday),
    INDEPENDENCE_DAY(
      new KeyValue<>(4, "Independence Day"),
      date ->
        date.getMonthValue() == Months.JUNE.GetValue() &&
        date.getDayOfMonth() == 12
    ),
    NATIONAL_HEROES_DAY(
      new KeyValue<>(5, "National Heroes Day"),
      Holidays::IsNationalHeroesDay
    ),
    BONIFACIO_DAY(
      new KeyValue<>(6, "Bonifacio Day"),
      date ->
        date.getMonthValue() == Months.NOVEMBER.GetValue() &&
        date.getDayOfMonth() == 30
    ),
    CHRISTMAS_DAY(
      new KeyValue<>(7, "Christmas Day"),
      date ->
        date.getMonthValue() == Months.DECEMBER.GetValue() &&
        date.getDayOfMonth() == 25
    ),
    RIZAL_DAY(
      new KeyValue<>(8, "Rizal Day"),
      date ->
        date.getMonthValue() == Months.DECEMBER.GetValue() &&
        date.getDayOfMonth() == 30
    );

    private final KeyValue<Integer, String> entry;
    private final Predicate<LocalDate> matcher;

    Holidays(KeyValue<Integer, String> entry, Predicate<LocalDate> matcher) {
      this.entry = entry;
      this.matcher = matcher;
    }

    public int GetKey() {
      return entry.getKey();
    }

    public String GetValue() {
      return entry.getValue();
    }

    public boolean Matches(LocalDate date) {
      return matcher.test(date);
    }

    public static boolean IsHoliday(LocalDate date) {
      for (Holidays holiday : Holidays.values()) {
        if (holiday.Matches(date)) {
          return true;
        }
      }
      return false;
    }

    // Movable rules:
    private static boolean IsMaundyThursday(LocalDate date) {
      LocalDate easter = ComputeEasterSunday(date.getYear());
      return date.equals(easter.minusDays(3));
    }

    private static boolean IsGoodFriday(LocalDate date) {
      LocalDate easter = ComputeEasterSunday(date.getYear());
      return date.equals(easter.minusDays(2));
    }

    private static boolean IsNationalHeroesDay(LocalDate date) {
      return (
        date.getMonthValue() == 8 &&
        date.getDayOfWeek() == DayOfWeek.MONDAY &&
        date.getDayOfMonth() > 24
      );
    }

    private static LocalDate ComputeEasterSunday(int year) {
      int a = year % 19;
      int b = year / 100;
      int c = year % 100;
      int d = b / 4;
      int e = b % 4;
      int f = (b + 8) / 25;
      int g = (b - f + 1) / 3;
      int h = (19 * a + b - d - g + 15) % 30;
      int i = c / 4;
      int k = c % 4;
      int l = (32 + 2 * e + 2 * i - h - k) % 7;
      int m = (a + 11 * h + 22 * l) / 451;
      int month = (h + l - 7 * m + 114) / 31;
      int day = ((h + l - 7 * m + 114) % 31) + 1;
      return LocalDate.of(year, month, day);
    }
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="OvertimeRateMultiplier">
  public enum OvertimeRateMultiplier {
    REGULAR_OT(1.25),
    WEEKEND_OT(1.5),
    HOLIDAY_OT(2.0);

    private final double multiplier;

    OvertimeRateMultiplier(double multiplier) {
      this.multiplier = multiplier;
    }

    public double getMultiplier() {
      return multiplier;
    }
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="PremiumRateMultiplier">

  public enum PremiumRateMultiplier {
    REST_DAY(1.30), // worked rest day / weekend
    SPECIAL_HOLIDAY(1.30), // worked special non-working day
    REGULAR_HOLIDAY(2.00); // worked regular holiday

    // future granularity: SPECIAL_HOLIDAY(1.30), REST_DAY_HOLIDAY(2.60), NIGHT_DIFF(1.10)
    private final double multiplier;

    PremiumRateMultiplier(double m) {
      this.multiplier = m;
    }

    public double getMultiplier() {
      return multiplier;
    }
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="SSSBracket">
  public enum SSSBracket {
    BELOW_3250(0, 3249.99, 135.00),
    RANGE_3250_3750(3250, 3749.99, 157.50),
    RANGE_3750_4250(3750, 4249.99, 180.00),
    RANGE_4250_4750(4250, 4749.99, 202.50),
    RANGE_4750_5250(4750, 5249.99, 225.00),
    RANGE_5250_5750(5250, 5749.99, 247.50),
    RANGE_5750_6250(5750, 6249.99, 270.00),
    RANGE_6250_6750(6250, 6749.99, 292.50),
    RANGE_6750_7250(6750, 7249.99, 315.00),
    RANGE_7250_7750(7250, 7749.99, 337.50),
    RANGE_7750_8250(7750, 8249.99, 360.00),
    RANGE_8250_8750(8250, 8749.99, 382.50),
    RANGE_8750_9250(8750, 9249.99, 405.00),
    RANGE_9250_9750(9250, 9749.99, 427.50),
    RANGE_9750_10250(9750, 10249.99, 450.00),
    RANGE_10250_10750(10250, 10749.99, 472.50),
    RANGE_10750_11250(10750, 11249.99, 495.00),
    RANGE_11250_11750(11250, 11749.99, 517.50),
    RANGE_11750_12250(11750, 12249.99, 540.00),
    RANGE_12250_12750(12250, 12749.99, 562.50),
    RANGE_12750_13250(12750, 13249.99, 585.00),
    RANGE_13250_13750(13250, 13749.99, 607.50),
    RANGE_13750_14250(13750, 14249.99, 630.00),
    RANGE_14250_14750(14250, 14749.99, 652.50),
    RANGE_14750_15250(14750, 15249.99, 675.00),
    RANGE_15250_15750(15250, 15749.99, 697.50),
    RANGE_15750_16250(15750, 16249.99, 720.00),
    RANGE_16250_16750(16250, 16749.99, 742.50),
    RANGE_16750_17250(16750, 17249.99, 765.00),
    RANGE_17250_17750(17250, 17749.99, 787.50),
    RANGE_17750_18250(17750, 18249.99, 810.00),
    RANGE_18250_18750(18250, 18749.99, 832.50),
    RANGE_18750_19250(18750, 19249.99, 855.00),
    RANGE_19250_19750(19250, 19749.99, 877.50),
    RANGE_19750_20250(19750, 20249.99, 900.00),
    RANGE_20250_20750(20250, 20749.99, 922.50),
    RANGE_20750_21250(20750, 21249.99, 945.00),
    RANGE_21250_21750(21250, 21749.99, 967.50),
    RANGE_21750_22250(21750, 22249.99, 990.00),
    RANGE_22250_22750(22250, 22749.99, 1012.50),
    RANGE_22750_23250(22750, 23249.99, 1035.00),
    RANGE_23250_23750(23250, 23749.99, 1057.50),
    RANGE_23750_24250(23750, 24249.99, 1080.00),
    RANGE_24250_24750(24250, 24749.99, 1102.50),
    ABOVE_24750(24750, Double.MAX_VALUE, 1125.00);

    private final double min;
    private final double max;
    private final double contribution;

    SSSBracket(double min, double max, double contribution) {
      this.min = min;
      this.max = max;
      this.contribution = contribution;
    }

    public double GetMin() {
      return min;
    }

    public double GetMax() {
      return max;
    }

    public double GetContribution() {
      return contribution;
    }
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="PhilHealthBracket">
  public enum PhilHealthBracket {
    BELOW_10K(0.00, 10_000.00, 0.03),
    BETWEEN_10K_TO_60K(10_000.01, 59_999.99, 0.03),
    ABOVE_60K(60_000.00, Double.MAX_VALUE, 0.03);

    private final double min;
    private final double max;
    private final double rate;

    PhilHealthBracket(double min, double max, double rate) {
      this.min = min;
      this.max = max;
      this.rate = rate;
    }

    public double GetMin() {
      return min;
    }

    public double GetMax() {
      return max;
    }

    public double GetRate() {
      return rate;
    }
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="PagIbigBracket">
  public enum PagIbigBracket {
    UP_TO_1500(1_000.00, 1_500.00, 0.01, 0.02),
    ABOVE_1500(1_500.01, Double.MAX_VALUE, 0.02, 0.02);

    private final double min;
    private final double max;
    private final double employeeRate;
    private final double employerRate;

    PagIbigBracket(
      double min,
      double max,
      double employeeRate,
      double employerRate
    ) {
      this.min = min;
      this.max = max;
      this.employeeRate = employeeRate;
      this.employerRate = employerRate;
    }

    public double getMin() {
      return min;
    }

    public double getMax() {
      return max;
    }

    public double getEmployeeRate() {
      return employeeRate;
    }

    public double getEmployerRate() {
      return employerRate;
    }
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="WithholdingTaxBracket">
  public enum WithholdingTaxBracket {
    EXEMPT(0, 20_832, 0, 0),
    BRACKET_1(20_833, 33_332, 0, 0.20),
    BRACKET_2(33_333, 66_666, 2_500, 0.25),
    BRACKET_3(66_667, 166_666, 10_833, 0.30),
    BRACKET_4(166_667, 666_666, 40_833.33, 0.32),
    BRACKET_5(666_667, Double.MAX_VALUE, 200_833.33, 0.35);

    private final double min;
    private final double max;
    private final double baseTax;
    private final double rate;

    WithholdingTaxBracket(double min, double max, double baseTax, double rate) {
      this.min = min;
      this.max = max;
      this.baseTax = baseTax;
      this.rate = rate;
    }

    public double GetMin() {
      return min;
    }

    public double GetMax() {
      return max;
    }

    public double GetBaseTax() {
      return baseTax;
    }

    public double GetRate() {
      return rate;
    }
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="EmpStatus">
  public enum EmpStatus {
    INTERN("Intern"),
    PROBI("Probationary"),
    REGULAR("Regular");

    private String empStatus;

    EmpStatus(String empStatus) {
      this.empStatus = empStatus;
    }

    @Override
    public String toString() {
      return empStatus;
    }
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="DeptCode">
  public enum DeptCode {
    PR("PR"),
    TK("TK"),
    HR("HR");

    private String deptCode;

    DeptCode(String deptCode) {
      this.deptCode = deptCode;
    }

    public String GetDeptCode() {
      return this.deptCode;
    }
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="ModuleIcons">

  /**
   * Resolves a navigation icon for a module by <b>convention, not a hard-coded
   * table</b>: the icon for module code {@code XYZ} is the SVG at
   * {@code Assets/Icons/xyz.svg} (code lower-cased). Adding a new module needs
   * no change here — just drop a matching SVG into that folder.
   *
   * Resolution order, so the UI never shows a broken (red-square) icon:
   *   1. {@code Assets/Icons/<code>.svg}      — the module's own icon
   *   2. {@code Assets/Icons/_default.svg}    — generic fallback glyph
   *   3. a blank transparent icon             — last resort, keeps layout stable
   *
   * Each path is tried on the classpath first (works from the packaged jar),
   * then in the dev source tree (works when run straight from the IDE).
   */
  public static final class ModuleIcons {

    private static final String CLASSPATH_DIR = "Assets/Icons/";
    private static final String DEV_TREE_DIR =
      "app/src/main/resources/Assets/Icons/";
    private static final String DEFAULT_NAME = "_default";

    // Cache keyed by code|color|size so repeated nav rebuilds don't re-decode SVGs.
    private static final java.util.Map<String, Icon> CACHE =
      new java.util.concurrent.ConcurrentHashMap<>();

    private ModuleIcons() {}

    public static Icon For(String moduleCode, Color color, int sizePx) {
      String code = moduleCode == null ? "" : moduleCode.trim().toLowerCase();
      String key = code + "|" + color.getRGB() + "|" + sizePx;
      return CACHE.computeIfAbsent(key, k -> resolve(code, color, sizePx));
    }

    private static Icon resolve(String code, Color color, int sizePx) {
      FlatSVGIcon icon = load(code, sizePx);
      if (icon == null) {
        icon = load(DEFAULT_NAME, sizePx);
      }
      if (icon == null) {
        return new BlankIcon(sizePx);
      }
      return icon.setColorFilter(
        new FlatSVGIcon.ColorFilter().add(Color.BLACK, color)
      );
    }

    /**
     * Loads {@code <name>.svg}: classpath first (packaged jar), then the dev
     * source tree (running from the IDE). Returns {@code null} if neither has
     * it, so the caller can fall back instead of getting FlatSVGIcon's
     * red-square "not found" placeholder.
     *
     * Uses java.io.File fully-qualified on purpose: Constants already declares
     * a nested File enum (the legacy CSV one above), which shadows any plain
     * "File" reference in this whole class — only qualifying the real type
     * works, an import can't fix that.
     */
    private static FlatSVGIcon load(String name, int sizePx) {
      String fileName = name + ".svg";
      FlatSVGIcon icon = new FlatSVGIcon(
        CLASSPATH_DIR + fileName,
        sizePx,
        sizePx
      );
      if (icon.hasFound()) {
        return icon;
      }
      java.io.File devFile = new java.io.File(DEV_TREE_DIR + fileName);
      return devFile.exists()
        ? new FlatSVGIcon(devFile).derive(sizePx, sizePx)
        : null;
    }
  }

  /** Transparent square icon — the absolute last-resort so layout stays stable. */
  private static final class BlankIcon implements Icon {

    private final int size;

    private BlankIcon(int size) {
      this.size = size;
    }

    @Override
    public void paintIcon(java.awt.Component c, java.awt.Graphics g, int x, int y) {
      // intentionally blank
    }

    @Override
    public int getIconWidth() {
      return size;
    }

    @Override
    public int getIconHeight() {
      return size;
    }
  }

  // </editor-fold>
}
