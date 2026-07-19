package Core.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

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

import Objects.models.LeaveBalanceRow;

/**
 * Renders the Leave Balance Report to PDF bytes. Pure: no DB, no file I/O.
 * Portrait A4: header band, year/info grid, then an employee x leave-type grid
 * of entitled / used / remaining days. Shared brand palette + header from
 * PdfSupport. Days are counts (2 dp for half-days).
 */
public final class LeaveBalanceReportPdfRenderer {

  private static final Color BRAND_DARK = PdfSupport.BRAND_DARK;
  private static final Color MUTED = PdfSupport.MUTED;
  private static final Color LINE = PdfSupport.LINE;

  private static final DecimalFormat DAYS = new DecimalFormat("#,##0.##");
  private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");

  // Emp No, Name, Dept, Leave Type, Entitled, Used, Remaining
  private static final float[] WIDTHS = { 9f, 26f, 18f, 21f, 9f, 8f, 9f };

  public byte[] Render(int year, List<LeaveBalanceRow> rows) throws IOException {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
      PdfWriter.getInstance(doc, out);
      doc.open();
      doc.add(PdfSupport.header("LEAVE BALANCE REPORT"));
      doc.add(infoGrid(year, rows != null ? rows.size() : 0));
      doc.add(grid(rows));
      doc.add(footer());
      doc.close();
      return out.toByteArray();
    } catch (DocumentException e) {
      throw new IOException("Failed to render leave balance PDF: " + e.getMessage(), e);
    }
  }

  public static String SuggestFileName(int year) {
    return "LeaveBalance_" + year + ".pdf";
  }

  private static PdfPTable infoGrid(int year, int rowCount) {
    PdfPTable t = new PdfPTable(new float[] { 16f, 30f, 20f, 34f });
    t.setWidthPercentage(100);
    t.setSpacingAfter(10f);
    PdfSupport.infoLabel(t, "YEAR");
    PdfSupport.infoValue(t, String.valueOf(year));
    PdfSupport.infoLabel(t, "ROWS");
    PdfSupport.infoValue(t, String.valueOf(rowCount));
    PdfSupport.infoLabel(t, "GENERATED");
    PdfSupport.infoValue(t, LocalDateTime.now().format(STAMP));
    PdfSupport.infoLabel(t, "UNIT");
    PdfSupport.infoValue(t, "Days");
    return t;
  }

  private static PdfPTable grid(List<LeaveBalanceRow> rows) {
    PdfPTable t = new PdfPTable(WIDTHS);
    t.setWidthPercentage(100);
    t.setSpacingBefore(2f);
    t.setHeaderRows(1);

    headCell(t, "Emp No", Element.ALIGN_LEFT);
    headCell(t, "Employee Full Name", Element.ALIGN_LEFT);
    headCell(t, "Department", Element.ALIGN_LEFT);
    headCell(t, "Leave Type", Element.ALIGN_LEFT);
    headCell(t, "Entitled", Element.ALIGN_RIGHT);
    headCell(t, "Used", Element.ALIGN_RIGHT);
    headCell(t, "Remaining", Element.ALIGN_RIGHT);

    if (rows == null || rows.isEmpty()) {
      PdfPCell empty = new PdfPCell(
        new Phrase("No leave entitlement records for this year.",
          FontFactory.getFont(FontFactory.HELVETICA, 9, Font.ITALIC, MUTED)));
      empty.setColspan(WIDTHS.length);
      empty.setPadding(8f);
      empty.setHorizontalAlignment(Element.ALIGN_CENTER);
      empty.setBorderColor(LINE);
      t.addCell(empty);
      return t;
    }

    for (LeaveBalanceRow r : rows) {
      bodyCell(t, String.valueOf(r.GetEmployeeNo()), Element.ALIGN_LEFT);
      bodyCell(t, orEmpty(r.GetEmployeeFullName()), Element.ALIGN_LEFT);
      bodyCell(t, orEmpty(r.GetDepartment()), Element.ALIGN_LEFT);
      bodyCell(t, orEmpty(r.GetLeaveType()), Element.ALIGN_LEFT);
      bodyCell(t, DAYS.format(r.GetEntitledDays()), Element.ALIGN_RIGHT);
      bodyCell(t, DAYS.format(r.GetUsedDays()), Element.ALIGN_RIGHT);
      bodyCell(t, DAYS.format(r.GetRemainingDays()), Element.ALIGN_RIGHT);
    }
    return t;
  }

  private static Paragraph footer() {
    Paragraph p = new Paragraph();
    p.setSpacingBefore(14f);
    p.add(new Phrase(
      "Remaining = entitled (base + carried over) minus approved leave days. " +
      "System-generated report.",
      FontFactory.getFont(FontFactory.HELVETICA, 7.5f, Font.ITALIC, MUTED)));
    return p;
  }

  private static void headCell(PdfPTable t, String text, int align) {
    PdfPCell c = new PdfPCell(new Phrase(text,
      FontFactory.getFont(FontFactory.HELVETICA, 8, Font.BOLD, Color.WHITE)));
    c.setBackgroundColor(BRAND_DARK);
    c.setBorderColor(Color.WHITE);
    c.setPadding(5f);
    c.setHorizontalAlignment(align);
    t.addCell(c);
  }

  private static void bodyCell(PdfPTable t, String text, int align) {
    PdfPCell c = new PdfPCell(new Phrase(text,
      FontFactory.getFont(FontFactory.HELVETICA, 8.5f, Font.NORMAL, BRAND_DARK)));
    c.setHorizontalAlignment(align);
    c.setBorderColor(LINE);
    c.setPadding(4f);
    t.addCell(c);
  }

  private static String orEmpty(String s) { return s == null ? "" : s; }
}