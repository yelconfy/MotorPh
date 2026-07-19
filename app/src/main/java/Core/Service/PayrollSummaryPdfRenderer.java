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

import Objects.models.PayrollSummaryRow;

/**
 * Pure Monthly Payroll Summary -> PDF rendering (OpenPDF 3.x, package org.openpdf).
 * No DB, no file I/O — returns the PDF bytes so the UI decides where they go,
 * exactly like PayslipPdfRenderer and DtrPdfRenderer.
 *
 * Landscape A4 (the report is 14 columns wide). Layout follows the official
 * MotorPH sample: company header, a period/info grid, the cross-employee grid
 * with grouped SSS / PhilHealth / Pag-IBIG / BIR header bands, and a shaded
 * TOTAL row. Totals are summed from the same rows, so the PDF can never drift
 * from the on-screen table.
 *
 * Shared scaffolding (brand palette, header band, info-grid cells) comes from
 * PdfSupport; only the grouped grid layout is local. Amounts print without the
 * peso sign: the base-14 PDF fonts have no glyph for U+20B1, so the info grid
 * notes "Philippine Peso (PHP)" once and the cells carry plain numbers.
 */
public final class PayrollSummaryPdfRenderer {

  private static final Color BRAND_DARK = PdfSupport.BRAND_DARK;
  private static final Color SHADE = PdfSupport.SHADE;
  private static final Color LINE = PdfSupport.LINE;

  private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");
  private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern(
    "MMM d, yyyy h:mm a"
  );

  // 14 columns: No, Name, Position, Dept, Gross, [SSS No, SSS], [Phil No, Phil],
  // [Pag No, Pag], [TIN, W/Tax], Net.
  private static final float[] WIDTHS = {
    5f, 14f, 12f, 9f, 9f, 9f, 8f, 9f, 8f, 9f, 8f, 9f, 8f, 9f
  };

  /** Renders the summary for one month to PDF bytes. */
  public byte[] Render(
    int year,
    int month,
    String monthName,
    List<PayrollSummaryRow> rows
  ) throws IOException {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Document doc = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
      PdfWriter.getInstance(doc, out);
      doc.open();

      doc.add(PdfSupport.header("MONTHLY PAYROLL SUMMARY REPORT"));
      doc.add(infoGrid(year, monthName, rows != null ? rows.size() : 0));
      doc.add(grid(rows));
      doc.add(footer());

      doc.close();
      return out.toByteArray();
    } catch (DocumentException e) {
      throw new IOException(
        "Failed to render payroll summary PDF: " + e.getMessage(), e
      );
    }
  }

  /** Suggested download filename, e.g. PayrollSummary_2024-06.pdf */
  public static String SuggestFileName(int year, int month) {
    return "PayrollSummary_" + year + "-" + String.format("%02d", month) + ".pdf";
  }

  // -------------------------------------------------------------------------
  // Info grid
  // -------------------------------------------------------------------------

  private static PdfPTable infoGrid(int year, String monthName, int employees) {
    PdfPTable t = new PdfPTable(new float[] { 16f, 30f, 16f, 38f });
    t.setWidthPercentage(100);
    t.setSpacingAfter(10f);

    String period = (monthName != null ? monthName : "") + " " + year;

    PdfSupport.infoLabel(t, "PERIOD");
    PdfSupport.infoValue(t, period.trim());
    PdfSupport.infoLabel(t, "EMPLOYEES");
    PdfSupport.infoValue(t, String.valueOf(employees));

    PdfSupport.infoLabel(t, "GENERATED");
    PdfSupport.infoValue(t, LocalDateTime.now().format(STAMP));
    PdfSupport.infoLabel(t, "CURRENCY");
    PdfSupport.infoValue(t, "Philippine Peso (PHP)");
    return t;
  }

  // -------------------------------------------------------------------------
  // Grid (grouped two-tier header + body + TOTAL)
  // -------------------------------------------------------------------------

  private static PdfPTable grid(List<PayrollSummaryRow> rows) {
    PdfPTable t = new PdfPTable(WIDTHS);
    t.setWidthPercentage(100);
    t.setSpacingBefore(2f);
    t.setHeaderRows(2); // repeat both header rows if the table breaks a page

    // Row 1 — group bands (rowspan over the non-grouped columns, colspan 2 on bands)
    t.addCell(hCell("Employee No", 1, 2));
    t.addCell(hCell("Employee Full Name", 1, 2));
    t.addCell(hCell("Position", 1, 2));
    t.addCell(hCell("Department", 1, 2));
    t.addCell(hCell("Gross Income", 1, 2));
    t.addCell(hCell("SOCIAL SECURITY SYSTEM", 2, 1));
    t.addCell(hCell("PHILHEALTH", 2, 1));
    t.addCell(hCell("PAG-IBIG", 2, 1));
    t.addCell(hCell("BIR", 2, 1));
    t.addCell(hCell("Net Pay", 1, 2));

    // Row 2 — sub-columns under the four bands
    t.addCell(hCell("Social Security No.", 1, 1));
    t.addCell(hCell("Contribution", 1, 1));
    t.addCell(hCell("Philhealth No.", 1, 1));
    t.addCell(hCell("Contribution", 1, 1));
    t.addCell(hCell("Pag-ibig No.", 1, 1));
    t.addCell(hCell("Contribution", 1, 1));
    t.addCell(hCell("TIN", 1, 1));
    t.addCell(hCell("Withholding Tax", 1, 1));

    if (rows == null || rows.isEmpty()) {
      PdfPCell empty = new PdfPCell(
        new Phrase(
          "No payslips found for this period.",
          FontFactory.getFont(FontFactory.HELVETICA, 9, Font.ITALIC, BRAND_DARK)
        )
      );
      empty.setColspan(WIDTHS.length);
      empty.setHorizontalAlignment(Element.ALIGN_CENTER);
      empty.setPadding(10f);
      empty.setBorderColor(LINE);
      t.addCell(empty);
      return t;
    }

    double tGross = 0, tSss = 0, tPhil = 0, tPag = 0, tTax = 0, tNet = 0;
    for (PayrollSummaryRow r : rows) {
      bodyCell(t, String.valueOf(r.GetEmployeeNo()), Element.ALIGN_CENTER);
      bodyCell(t, orEmpty(r.GetEmployeeFullName()), Element.ALIGN_LEFT);
      bodyCell(t, orEmpty(r.GetPosition()), Element.ALIGN_LEFT);
      bodyCell(t, orEmpty(r.GetDepartment()), Element.ALIGN_LEFT);
      moneyCell(t, r.GetGrossIncome(), false);
      bodyCell(t, orEmpty(r.GetSocialSecurityNo()), Element.ALIGN_LEFT);
      moneyCell(t, r.GetSocialSecurityContribution(), false);
      bodyCell(t, orEmpty(r.GetPhilHealthNo()), Element.ALIGN_LEFT);
      moneyCell(t, r.GetPhilHealthContribution(), false);
      bodyCell(t, orEmpty(r.GetPagIbigNo()), Element.ALIGN_LEFT);
      moneyCell(t, r.GetPagIbigContribution(), false);
      bodyCell(t, orEmpty(r.GetTin()), Element.ALIGN_LEFT);
      moneyCell(t, r.GetWithholdingTax(), false);
      moneyCell(t, r.GetNetPay(), false);

      tGross += r.GetGrossIncome();
      tSss += r.GetSocialSecurityContribution();
      tPhil += r.GetPhilHealthContribution();
      tPag += r.GetPagIbigContribution();
      tTax += r.GetWithholdingTax();
      tNet += r.GetNetPay();
    }

    // TOTAL row — "TOTAL" spans the first four columns; sums under the money columns.
    PdfPCell totalLabel = new PdfPCell(
      new Phrase(
        "TOTAL",
        FontFactory.getFont(FontFactory.HELVETICA, 8.5f, Font.BOLD, BRAND_DARK)
      )
    );
    totalLabel.setColspan(4);
    totalLabel.setBackgroundColor(SHADE);
    totalLabel.setBorderColor(LINE);
    totalLabel.setPadding(5f);
    totalLabel.setHorizontalAlignment(Element.ALIGN_LEFT);
    t.addCell(totalLabel);

    moneyCell(t, tGross, true);
    emptyShade(t); // SSS No.
    moneyCell(t, tSss, true);
    emptyShade(t); // PhilHealth No.
    moneyCell(t, tPhil, true);
    emptyShade(t); // Pag-IBIG No.
    moneyCell(t, tPag, true);
    emptyShade(t); // TIN
    moneyCell(t, tTax, true);
    moneyCell(t, tNet, true);

    return t;
  }

  // -------------------------------------------------------------------------
  // Footer
  // -------------------------------------------------------------------------

  private static Paragraph footer() {
    Paragraph p = new Paragraph();
    p.setSpacingBefore(10f);
    p.add(
      new Phrase(
        "Generated by MotorPH ERP. Figures reflect finalized payroll only; "
          + "drafts are excluded.",
        FontFactory.getFont(FontFactory.HELVETICA, 7.5f, Font.ITALIC, PdfSupport.MUTED)
      )
    );
    return p;
  }

  // -------------------------------------------------------------------------
  // Grid cell helpers (layout-specific — stay local)
  // -------------------------------------------------------------------------

  private static PdfPCell hCell(String text, int colspan, int rowspan) {
    PdfPCell c = new PdfPCell(
      new Phrase(
        text,
        FontFactory.getFont(FontFactory.HELVETICA, 7.5f, Font.BOLD, Color.WHITE)
      )
    );
    c.setBackgroundColor(BRAND_DARK);
    c.setBorderColor(Color.WHITE);
    c.setHorizontalAlignment(Element.ALIGN_CENTER);
    c.setVerticalAlignment(Element.ALIGN_MIDDLE);
    c.setPadding(4f);
    if (colspan > 1) c.setColspan(colspan);
    if (rowspan > 1) c.setRowspan(rowspan);
    return c;
  }

  private static void bodyCell(PdfPTable t, String text, int align) {
    PdfPCell c = new PdfPCell(
      new Phrase(
        text,
        FontFactory.getFont(FontFactory.HELVETICA, 8f, Font.NORMAL, BRAND_DARK)
      )
    );
    c.setHorizontalAlignment(align);
    c.setVerticalAlignment(Element.ALIGN_MIDDLE);
    c.setBorderColor(LINE);
    c.setPadding(4f);
    t.addCell(c);
  }

  private static void moneyCell(PdfPTable t, double amount, boolean total) {
    PdfPCell c = new PdfPCell(
      new Phrase(
        MONEY.format(amount),
        FontFactory.getFont(
          FontFactory.HELVETICA, 8f, total ? Font.BOLD : Font.NORMAL, BRAND_DARK
        )
      )
    );
    c.setHorizontalAlignment(Element.ALIGN_RIGHT);
    c.setVerticalAlignment(Element.ALIGN_MIDDLE);
    c.setBorderColor(LINE);
    c.setPadding(4f);
    if (total) c.setBackgroundColor(SHADE);
    t.addCell(c);
  }

  private static void emptyShade(PdfPTable t) {
    PdfPCell c = new PdfPCell(new Phrase(""));
    c.setBackgroundColor(SHADE);
    c.setBorderColor(LINE);
    c.setPadding(4f);
    t.addCell(c);
  }

  private static String orEmpty(String s) {
    return (s != null) ? s : "";
  }
}