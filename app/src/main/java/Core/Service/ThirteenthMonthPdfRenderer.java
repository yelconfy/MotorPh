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

import Objects.models.ThirteenthMonthRow;

/**
 * Pure 13th Month Pay report -> PDF rendering (OpenPDF 3.x, package org.openpdf).
 * No DB, no file I/O — returns the PDF bytes so the UI decides where they go,
 * exactly like PayslipPdfRenderer, DtrPdfRenderer and PayrollSummaryPdfRenderer.
 *
 * Portrait A4 (the report is 7 columns wide). Layout: company header, a
 * year/info grid, the cross-employee grid, and a shaded TOTAL row. Totals are
 * summed from the same rows, so the PDF can never drift from the on-screen
 * table.
 *
 * Shared scaffolding (brand palette, header band, info-grid cells) comes from
 * PdfSupport; only the grid layout is local. Amounts print without the peso
 * sign: the base-14 PDF fonts have no glyph for U+20B1, so the info grid notes
 * "Philippine Peso (PHP)" once and the cells carry plain numbers.
 *
 * Basis (PD 851): total BASIC salary earned in the year / 12. The "Basic
 * Earned" and "Cutoffs" columns make the division auditable per employee.
 */
public final class ThirteenthMonthPdfRenderer {

  private static final Color BRAND_DARK = PdfSupport.BRAND_DARK;
  private static final Color MUTED = PdfSupport.MUTED;
  private static final Color SHADE = PdfSupport.SHADE;
  private static final Color LINE = PdfSupport.LINE;

  private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");
  private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern(
    "MMM d, yyyy h:mm a"
  );

  // 7 columns: No, Name, Position, Dept, Basic Earned, Cutoffs, 13th Month Pay.
  private static final float[] WIDTHS = {
    8f, 24f, 18f, 14f, 15f, 8f, 15f
  };

  /** Renders the 13th-month report for one year to PDF bytes. */
  public byte[] Render(int year, List<ThirteenthMonthRow> rows)
    throws IOException {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
      PdfWriter.getInstance(doc, out);
      doc.open();

      doc.add(PdfSupport.header("13TH MONTH PAY REPORT"));
      doc.add(infoGrid(year, rows != null ? rows.size() : 0));
      doc.add(grid(rows));
      doc.add(footer());

      doc.close();
      return out.toByteArray();
    } catch (DocumentException e) {
      throw new IOException(
        "Failed to render 13th month PDF: " + e.getMessage(), e
      );
    }
  }

  /** Suggested download filename, e.g. ThirteenthMonth_2024.pdf */
  public static String SuggestFileName(int year) {
    return "ThirteenthMonth_" + year + ".pdf";
  }

  // -------------------------------------------------------------------------
  // Info grid
  // -------------------------------------------------------------------------

  private static PdfPTable infoGrid(int year, int employees) {
    PdfPTable t = new PdfPTable(new float[] { 16f, 30f, 16f, 38f });
    t.setWidthPercentage(100);
    t.setSpacingAfter(10f);

    PdfSupport.infoLabel(t, "PAY YEAR");
    PdfSupport.infoValue(t, String.valueOf(year));
    PdfSupport.infoLabel(t, "EMPLOYEES");
    PdfSupport.infoValue(t, String.valueOf(employees));

    PdfSupport.infoLabel(t, "GENERATED");
    PdfSupport.infoValue(t, LocalDateTime.now().format(STAMP));
    PdfSupport.infoLabel(t, "CURRENCY");
    PdfSupport.infoValue(t, "Philippine Peso (PHP)");
    return t;
  }

  // -------------------------------------------------------------------------
  // Grid (header + body + TOTAL)
  // -------------------------------------------------------------------------

  private static PdfPTable grid(List<ThirteenthMonthRow> rows) {
    PdfPTable t = new PdfPTable(WIDTHS);
    t.setWidthPercentage(100);
    t.setSpacingBefore(2f);
    t.setHeaderRows(1); // repeat the header row if the table breaks a page

    headCell(t, "Emp No", Element.ALIGN_LEFT);
    headCell(t, "Employee Full Name", Element.ALIGN_LEFT);
    headCell(t, "Position", Element.ALIGN_LEFT);
    headCell(t, "Department", Element.ALIGN_LEFT);
    headCell(t, "Basic Earned", Element.ALIGN_RIGHT);
    headCell(t, "Cutoffs", Element.ALIGN_CENTER);
    headCell(t, "13th Month Pay", Element.ALIGN_RIGHT);

    if (rows == null || rows.isEmpty()) {
      PdfPCell empty = new PdfPCell(
        new Phrase(
          "No finalized or paid payslips for this year.",
          FontFactory.getFont(FontFactory.HELVETICA, 9, Font.ITALIC, MUTED)
        )
      );
      empty.setColspan(WIDTHS.length);
      empty.setPadding(8f);
      empty.setHorizontalAlignment(Element.ALIGN_CENTER);
      empty.setBorderColor(LINE);
      t.addCell(empty);
      return t;
    }

    double totalBasic = 0d;
    double totalThirteenth = 0d;

    for (ThirteenthMonthRow r : rows) {
      bodyCell(t, String.valueOf(r.GetEmployeeNo()), Element.ALIGN_LEFT);
      bodyCell(t, orEmpty(r.GetEmployeeFullName()), Element.ALIGN_LEFT);
      bodyCell(t, orEmpty(r.GetPosition()), Element.ALIGN_LEFT);
      bodyCell(t, orEmpty(r.GetDepartment()), Element.ALIGN_LEFT);
      bodyCell(t, MONEY.format(r.GetTotalBasicEarned()), Element.ALIGN_RIGHT);
      bodyCell(t, String.valueOf(r.GetPayslipsIncluded()), Element.ALIGN_CENTER);
      bodyCell(t, MONEY.format(r.GetThirteenthMonthPay()), Element.ALIGN_RIGHT);

      totalBasic += r.GetTotalBasicEarned();
      totalThirteenth += r.GetThirteenthMonthPay();
    }

    totalCell(t, "TOTAL", Element.ALIGN_LEFT, 4);
    totalCell(t, MONEY.format(totalBasic), Element.ALIGN_RIGHT, 1);
    totalCell(t, "", Element.ALIGN_CENTER, 1);
    totalCell(t, MONEY.format(totalThirteenth), Element.ALIGN_RIGHT, 1);
    return t;
  }

  // -------------------------------------------------------------------------
  // Footer
  // -------------------------------------------------------------------------

  private static Paragraph footer() {
    Paragraph p = new Paragraph();
    p.setSpacingBefore(14f);
    p.add(
      new Phrase(
        "Basis: Presidential Decree 851 - total basic salary earned in the " +
        "calendar year divided by 12. Amounts in Philippine Peso (PHP). " +
        "System-generated report.",
        FontFactory.getFont(FontFactory.HELVETICA, 7.5f, Font.ITALIC, MUTED)
      )
    );
    return p;
  }

  // -------------------------------------------------------------------------
  // Cell helpers (grid-local; shared brand/header/info cells live in PdfSupport)
  // -------------------------------------------------------------------------

  private static void headCell(PdfPTable t, String text, int align) {
    PdfPCell c = new PdfPCell(
      new Phrase(
        text,
        FontFactory.getFont(FontFactory.HELVETICA, 8, Font.BOLD, Color.WHITE)
      )
    );
    c.setBackgroundColor(BRAND_DARK);
    c.setBorderColor(Color.WHITE);
    c.setPadding(5f);
    c.setHorizontalAlignment(align);
    t.addCell(c);
  }

  private static void bodyCell(PdfPTable t, String text, int align) {
    PdfPCell c = new PdfPCell(
      new Phrase(
        text,
        FontFactory.getFont(FontFactory.HELVETICA, 8.5f, Font.NORMAL, BRAND_DARK)
      )
    );
    c.setHorizontalAlignment(align);
    c.setBorderColor(LINE);
    c.setPadding(4f);
    t.addCell(c);
  }

  private static void totalCell(PdfPTable t, String text, int align, int colspan) {
    PdfPCell c = new PdfPCell(
      new Phrase(
        text,
        FontFactory.getFont(FontFactory.HELVETICA, 9, Font.BOLD, BRAND_DARK)
      )
    );
    c.setColspan(colspan);
    c.setBackgroundColor(SHADE);
    c.setHorizontalAlignment(align);
    c.setBorderColor(LINE);
    c.setPadding(5f);
    t.addCell(c);
  }

  private static String orEmpty(String s) {
    return s == null ? "" : s;
  }
}