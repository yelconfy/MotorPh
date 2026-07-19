package Core.Service;

import Core.Service.AttendanceCalculator.Summary;
import Objects.models.DailyAttendanceRecord;
import Objects.models.EmpDetail;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;

/**
 * Pure Daily Time Record -> PDF rendering (OpenPDF 3.x, package org.openpdf).
 * No DB, no file I/O — returns the PDF bytes so the UI decides where they go,
 * exactly like PayslipPdfRenderer.
 *
 * The DTR is a READ-ONLY view of the records the AttendanceCalculator already
 * produced for the Timekeeping grid (same numbers Payroll uses). Layout: company
 * header, employee/period info grid, one row per attendance day, then a summary
 * block of the period roll-up.
 *
 * Overtime shown is RAW (DTR truth) — the same figure the Timekeeping screen
 * shows — not the approved/paid figure, which lives on the payslip.
 */
public final class DtrPdfRenderer {

  private static final Color BRAND_DARK = PdfSupport.BRAND_DARK;
  private static final Color MUTED = PdfSupport.MUTED;
  private static final Color SHADE = PdfSupport.SHADE;
  private static final Color LINE = PdfSupport.LINE;

  private static final DateTimeFormatter D_ISO = DateTimeFormatter.ofPattern(
    "yyyy-MM-dd"
  );
  private static final DateTimeFormatter D_SLASH = DateTimeFormatter.ofPattern(
    "M/d/yyyy"
  );
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern(
    "HH:mm"
  );
  private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern(
    "MMM d, yyyy h:mm a"
  );

  /** Renders the DTR to PDF bytes. */
  public byte[] Render(
    EmpDetail emp,
    List<DailyAttendanceRecord> records,
    LocalDate from,
    LocalDate to,
    Summary summary
  ) throws IOException {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
      PdfWriter.getInstance(doc, out);
      doc.open();

      doc.add(PdfSupport.header("DAILY TIME RECORD"));
      doc.add(infoGrid(emp, from, to));
      doc.add(grid(records));
      doc.add(summaryBlock(summary));
      doc.add(footer());

      doc.close();
      return out.toByteArray();
    } catch (DocumentException e) {
      throw new IOException("Failed to render DTR PDF: " + e.getMessage(), e);
    }
  }

  /** Suggested download filename, e.g. DTR_10001_2024-06-01_2024-06-30.pdf */
  public static String SuggestFileName(
    EmpDetail emp,
    LocalDate from,
    LocalDate to
  ) {
    return (
      "DTR_" +
      emp.GetEmployeeId() +
      "_" +
      (from != null ? from.format(D_ISO) : "from") +
      "_" +
      (to != null ? to.format(D_ISO) : "to") +
      ".pdf"
    );
  }

  // -------------------------------------------------------------------------
  // Header / info grid
  // -------------------------------------------------------------------------

  private static PdfPTable infoGrid(
    EmpDetail emp,
    LocalDate from,
    LocalDate to
  ) {
    PdfPTable t = new PdfPTable(new float[] { 22f, 28f, 22f, 28f });
    t.setWidthPercentage(100);
    t.setSpacingAfter(12f);

    String posDept = orEmpty(positionName(emp));
    String dept = departmentName(emp);
    if (notBlank(dept)) {
      posDept = posDept.isEmpty() ? dept : posDept + " / " + dept;
    }

    PdfSupport.infoLabel(t, "EMPLOYEE ID");
    PdfSupport.infoValue(t, String.valueOf(emp.GetEmployeeId()));
    PdfSupport.infoLabel(t, "PERIOD START");
    PdfSupport.infoValue(t, fmt(from));

    PdfSupport.infoLabel(t, "EMPLOYEE NAME");
    PdfSupport.infoValue(t, orEmpty(emp.GetFullName()));
    PdfSupport.infoLabel(t, "PERIOD END");
    PdfSupport.infoValue(t, fmt(to));

    PdfSupport.infoLabel(t, "POSITION / DEPT");
    PdfSupport.infoValue(t, posDept);
    PdfSupport.infoLabel(t, "GENERATED");
    PdfSupport.infoValue(t, java.time.LocalDateTime.now().format(STAMP));
    return t;
  }

  // -------------------------------------------------------------------------
  // Daily grid
  // -------------------------------------------------------------------------

  private static PdfPTable grid(List<DailyAttendanceRecord> records) {
    PdfPTable t = new PdfPTable(
      new float[] { 16f, 9f, 12f, 12f, 17f, 11f, 12f, 11f }
    );
    t.setWidthPercentage(100);
    t.setSpacingAfter(12f);

    headCell(t, "Date");
    headCell(t, "Day");
    headCell(t, "In");
    headCell(t, "Out");
    headCell(t, "Status");
    headCell(t, "Late");
    headCell(t, "Worked");
    headCell(t, "OT");

    if (records == null || records.isEmpty()) {
      PdfPCell empty = new PdfPCell(
        new Phrase(
          "No attendance records in this period.",
          FontFactory.getFont(FontFactory.HELVETICA, 9, Font.ITALIC, MUTED)
        )
      );
      empty.setColspan(8);
      empty.setPadding(8f);
      empty.setHorizontalAlignment(Element.ALIGN_CENTER);
      empty.setBorderColor(LINE);
      t.addCell(empty);
      return t;
    }

    for (DailyAttendanceRecord r : records) {
      LocalDate d = r.GetDate();
      bodyCell(t, d != null ? d.format(D_ISO) : "", Element.ALIGN_LEFT);
      bodyCell(t, d != null ? dayAbbrev(d) : "", Element.ALIGN_CENTER);
      bodyCell(t, time(r.GetTimeIn()), Element.ALIGN_CENTER);
      bodyCell(t, time(r.GetTimeOut()), Element.ALIGN_CENTER);
      bodyCell(t, statusLabel(r), Element.ALIGN_LEFT);
      bodyCell(
        t,
        r.GetLateMinutes() > 0 ? String.valueOf(r.GetLateMinutes()) : "\u2014",
        Element.ALIGN_RIGHT
      );
      bodyCell(t, hm(r.GetRegularMinutes()), Element.ALIGN_RIGHT);
      bodyCell(
        t,
        r.GetOvertimeMinutes() > 0 ? hm(r.GetOvertimeMinutes()) : "\u2014",
        Element.ALIGN_RIGHT
      );
    }
    return t;
  }

  // -------------------------------------------------------------------------
  // Summary block
  // -------------------------------------------------------------------------

  private static PdfPTable summaryBlock(Summary s) {
    PdfPTable t = new PdfPTable(new float[] { 62f, 38f });
    t.setWidthPercentage(100);
    t.setSpacingBefore(2f);

    PdfPCell bar = new PdfPCell(
      new Phrase(
        "PERIOD SUMMARY",
        FontFactory.getFont(FontFactory.HELVETICA, 9, Font.BOLD, Color.WHITE)
      )
    );
    bar.setColspan(2);
    bar.setBackgroundColor(BRAND_DARK);
    bar.setBorder(PdfPCell.NO_BORDER);
    bar.setPadding(6f);
    t.addCell(bar);

    if (s != null) {
      line(t, "Days worked", String.valueOf(s.GetDaysWorked()));
      line(t, "On time", String.valueOf(s.GetOnTimeDays()));
      line(t, "Late", String.valueOf(s.GetLateDays()));
      line(t, "Incomplete", String.valueOf(s.GetIncompleteDays()));
      line(t, "Absent", String.valueOf(s.GetAbsentDays()));
      line(t, "Total late (minutes)", String.valueOf(s.GetTotalLateMinutes()));
      line(t, "Worked hours", String.valueOf(s.GetWorkedHours()));
      total(t, "Overtime hours (raw)", String.valueOf(s.GetOvertimeHours()));
    }
    return t;
  }

  private static Paragraph footer() {
    Paragraph p = new Paragraph();
    p.setSpacingBefore(16f);
    p.add(
      new Phrase(
        "System-generated Daily Time Record. Overtime shown is raw (worked); " +
          "only approved overtime is paid on the payslip.",
        FontFactory.getFont(FontFactory.HELVETICA, 7.5f, Font.ITALIC, MUTED)
      )
    );
    return p;
  }

  // -------------------------------------------------------------------------
  // Cell / format helpers
  // -------------------------------------------------------------------------

  private static void headCell(PdfPTable t, String text) {
    PdfPCell c = new PdfPCell(
      new Phrase(
        text,
        FontFactory.getFont(FontFactory.HELVETICA, 8, Font.BOLD, Color.WHITE)
      )
    );
    c.setBackgroundColor(BRAND_DARK);
    c.setBorderColor(Color.WHITE);
    c.setPadding(5f);
    c.setHorizontalAlignment(Element.ALIGN_CENTER);
    t.addCell(c);
  }

  private static void bodyCell(PdfPTable t, String text, int align) {
    PdfPCell c = new PdfPCell(
      new Phrase(
        text,
        FontFactory.getFont(
          FontFactory.HELVETICA,
          8.5f,
          Font.NORMAL,
          BRAND_DARK
        )
      )
    );
    c.setHorizontalAlignment(align);
    c.setBorderColor(LINE);
    c.setPadding(4f);
    t.addCell(c);
  }

  private static void line(PdfPTable t, String label, String value) {
    Font f = FontFactory.getFont(
      FontFactory.HELVETICA,
      9,
      Font.NORMAL,
      BRAND_DARK
    );
    t.addCell(cell(label, f, Element.ALIGN_LEFT, null));
    t.addCell(cell(value, f, Element.ALIGN_RIGHT, null));
  }

  private static void total(PdfPTable t, String label, String value) {
    Font f = FontFactory.getFont(
      FontFactory.HELVETICA,
      9,
      Font.BOLD,
      BRAND_DARK
    );
    t.addCell(cell(label, f, Element.ALIGN_LEFT, SHADE));
    t.addCell(cell(value, f, Element.ALIGN_RIGHT, SHADE));
  }

  private static PdfPCell cell(String text, Font f, int align, Color bg) {
    PdfPCell c = new PdfPCell(new Phrase(text, f));
    c.setHorizontalAlignment(align);
    c.setBorder(PdfPCell.BOTTOM);
    c.setBorderColor(LINE);
    c.setPadding(5f);
    if (bg != null) c.setBackgroundColor(bg);
    return c;
  }

  private static String statusLabel(DailyAttendanceRecord r) {
    return (r.GetStatus() != null) ? r.GetStatus().GetLabel() : "";
  }

  private static String dayAbbrev(LocalDate d) {
    return d.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.US);
  }

  private static String time(LocalTime t) {
    return (t != null) ? t.format(TIME) : "\u2014";
  }

  /** Formats a minute count as H:MM (e.g. 485 -> "8:05"). */
  private static String hm(long minutes) {
    if (minutes <= 0) return "0:00";
    return (minutes / 60) + ":" + String.format("%02d", minutes % 60);
  }

  private static String fmt(LocalDate d) {
    return d != null ? d.format(D_SLASH) : "";
  }

  private static String positionName(EmpDetail emp) {
    return (emp.GetPosition() != null)
      ? emp.GetPosition().GetPositionName()
      : null;
  }

  private static String departmentName(EmpDetail emp) {
    return (emp.GetDepartment() != null)
      ? emp.GetDepartment().GetDepartmentName()
      : null;
  }

  private static String orEmpty(String s) {
    return s == null ? "" : s;
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }
}
