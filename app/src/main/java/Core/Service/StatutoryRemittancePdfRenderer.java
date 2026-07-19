package Core.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

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

import Objects.models.StatutoryRemittanceRow;

/**
 * Renders the three Philippine government remittance reports to PDF bytes,
 * each styled after its official form:
 *
 *   RenderSssR3        -> SSS R-3  (Contribution Collection List)
 *   RenderPhilHealthRf1-> PhilHealth RF-1 (Employer Remittance Report)
 *   RenderPagIbigM11   -> Pag-IBIG M1-1 (Monthly Remittance)
 *
 * All three read one month of vw_StatutoryRemittance rows and lay out the same
 * shape: an employer header block, then an employee list with that agency's ID
 * plus employee share / employer share / total, then a TOTAL row. Shared brand
 * palette and the top header band come from PdfSupport; the employer block and
 * grid are local and shared across the three forms.
 *
 * LIMITATION: the system stores no company-level employer registration numbers
 * (employer SSS / PhilHealth / HDMF numbers), so those header fields render as
 * labelled blanks to be filled in on the printed form. Employer name is MotorPH.
 *
 * These are FORM-STYLED reports, not pixel-exact reproductions of the official
 * government templates. Amounts print without the peso glyph (base-14 PDF fonts
 * lack U+20B1); "PHP" is noted once in the header.
 */
public final class StatutoryRemittancePdfRenderer {

  private static final Color BRAND_DARK = PdfSupport.BRAND_DARK;
  private static final Color MUTED = PdfSupport.MUTED;
  private static final Color SHADE = PdfSupport.SHADE;
  private static final Color LINE = PdfSupport.LINE;

  private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");
  private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern(
    "MMM d, yyyy h:mm a"
  );

  private static final String EMPLOYER_NAME = "MotorPH";

  // seq, employee name, agency ID, EE, ER, Total
  private static final float[] WIDTHS = { 7f, 30f, 21f, 14f, 14f, 14f };

  // Agency keys for audit + filenames.
  public static final String AGENCY_SSS = "SSS-R3";
  public static final String AGENCY_PHIC = "PHIC-RF1";
  public static final String AGENCY_HDMF = "HDMF-M11";

  // -------------------------------------------------------------------------
  // Public render methods (one per agency form)
  // -------------------------------------------------------------------------

  public byte[] RenderSssR3(int year, int month, List<StatutoryRemittanceRow> rows)
    throws IOException {
    return render(
      "SSS FORM R-3  -  CONTRIBUTION COLLECTION LIST",
      "Employer SSS No.",
      "SSS No.",
      year, month, rows,
      StatutoryRemittanceRow::GetSssNo,
      StatutoryRemittanceRow::GetSssEmployeeShare,
      StatutoryRemittanceRow::GetSssEmployerShare,
      StatutoryRemittanceRow::GetSssTotal
    );
  }

  public byte[] RenderPhilHealthRf1(int year, int month, List<StatutoryRemittanceRow> rows)
    throws IOException {
    return render(
      "PHILHEALTH RF-1  -  EMPLOYER REMITTANCE REPORT",
      "Employer PhilHealth No.",
      "PhilHealth No.",
      year, month, rows,
      StatutoryRemittanceRow::GetPhilHealthNo,
      StatutoryRemittanceRow::GetPhicEmployeeShare,
      StatutoryRemittanceRow::GetPhicEmployerShare,
      StatutoryRemittanceRow::GetPhicTotal
    );
  }

  public byte[] RenderPagIbigM11(int year, int month, List<StatutoryRemittanceRow> rows)
    throws IOException {
    return render(
      "PAG-IBIG M1-1  -  MONTHLY REMITTANCE",
      "Employer Pag-IBIG No.",
      "Pag-IBIG MID",
      year, month, rows,
      StatutoryRemittanceRow::GetPagIbigNo,
      StatutoryRemittanceRow::GetHdmfEmployeeShare,
      StatutoryRemittanceRow::GetHdmfEmployerShare,
      StatutoryRemittanceRow::GetHdmfTotal
    );
  }

  /** Suggested filename, e.g. SSS-R3_2024-06.pdf */
  public static String SuggestFileName(String agency, int year, int month) {
    return agency + "_" + year + "-" + String.format("%02d", month) + ".pdf";
  }

  // -------------------------------------------------------------------------
  // Shared rendering
  // -------------------------------------------------------------------------

  private byte[] render(
    String formTitle,
    String employerRegLabel,
    String idColumnLabel,
    int year,
    int month,
    List<StatutoryRemittanceRow> rows,
    Function<StatutoryRemittanceRow, String> idExtractor,
    Function<StatutoryRemittanceRow, Double> eeExtractor,
    Function<StatutoryRemittanceRow, Double> erExtractor,
    Function<StatutoryRemittanceRow, Double> totalExtractor
  ) throws IOException {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Document doc = new Document(PageSize.A4, 36, 36, 36, 36);
      PdfWriter.getInstance(doc, out);
      doc.open();

      doc.add(PdfSupport.header(formTitle));
      doc.add(employerBlock(employerRegLabel, year, month, rows != null ? rows.size() : 0));
      doc.add(grid(idColumnLabel, rows, idExtractor, eeExtractor, erExtractor, totalExtractor));
      doc.add(footer());

      doc.close();
      return out.toByteArray();
    } catch (DocumentException e) {
      throw new IOException("Failed to render remittance PDF: " + e.getMessage(), e);
    }
  }

  private static PdfPTable employerBlock(
    String employerRegLabel,
    int year,
    int month,
    int employees
  ) {
    String monthName = java.time.Month
      .of(month)
      .getDisplayName(TextStyle.FULL, Locale.ENGLISH);

    PdfPTable t = new PdfPTable(new float[] { 20f, 34f, 20f, 26f });
    t.setWidthPercentage(100);
    t.setSpacingAfter(10f);

    PdfSupport.infoLabel(t, "EMPLOYER");
    PdfSupport.infoValue(t, EMPLOYER_NAME);
    PdfSupport.infoLabel(t, "APPLICABLE PERIOD");
    PdfSupport.infoValue(t, monthName + " " + year);

    PdfSupport.infoLabel(t, employerRegLabel.toUpperCase());
    PdfSupport.infoValue(t, "____________________"); // not stored; fill on print
    PdfSupport.infoLabel(t, "EMPLOYEES");
    PdfSupport.infoValue(t, String.valueOf(employees));

    PdfSupport.infoLabel(t, "GENERATED");
    PdfSupport.infoValue(t, LocalDateTime.now().format(STAMP));
    PdfSupport.infoLabel(t, "CURRENCY");
    PdfSupport.infoValue(t, "Philippine Peso (PHP)");
    return t;
  }

  private static PdfPTable grid(
    String idColumnLabel,
    List<StatutoryRemittanceRow> rows,
    Function<StatutoryRemittanceRow, String> idExtractor,
    Function<StatutoryRemittanceRow, Double> eeExtractor,
    Function<StatutoryRemittanceRow, Double> erExtractor,
    Function<StatutoryRemittanceRow, Double> totalExtractor
  ) {
    PdfPTable t = new PdfPTable(WIDTHS);
    t.setWidthPercentage(100);
    t.setSpacingBefore(2f);
    t.setHeaderRows(1);

    headCell(t, "No.", Element.ALIGN_CENTER);
    headCell(t, "Employee Full Name", Element.ALIGN_LEFT);
    headCell(t, idColumnLabel, Element.ALIGN_LEFT);
    headCell(t, "Employee Share", Element.ALIGN_RIGHT);
    headCell(t, "Employer Share", Element.ALIGN_RIGHT);
    headCell(t, "Total", Element.ALIGN_RIGHT);

    if (rows == null || rows.isEmpty()) {
      PdfPCell empty = new PdfPCell(
        new Phrase(
          "No finalized or paid contributions for this month.",
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

    double tEE = 0, tER = 0, tTot = 0;
    int seq = 1;
    for (StatutoryRemittanceRow r : rows) {
      double ee = eeExtractor.apply(r);
      double er = erExtractor.apply(r);
      double tot = totalExtractor.apply(r);

      bodyCell(t, String.valueOf(seq++), Element.ALIGN_CENTER);
      bodyCell(t, orEmpty(r.GetEmployeeFullName()), Element.ALIGN_LEFT);
      bodyCell(t, orDash(idExtractor.apply(r)), Element.ALIGN_LEFT);
      bodyCell(t, MONEY.format(ee), Element.ALIGN_RIGHT);
      bodyCell(t, MONEY.format(er), Element.ALIGN_RIGHT);
      bodyCell(t, MONEY.format(tot), Element.ALIGN_RIGHT);

      tEE += ee;
      tER += er;
      tTot += tot;
    }

    totalCell(t, "TOTAL", Element.ALIGN_LEFT, 3);
    totalCell(t, MONEY.format(tEE), Element.ALIGN_RIGHT, 1);
    totalCell(t, MONEY.format(tER), Element.ALIGN_RIGHT, 1);
    totalCell(t, MONEY.format(tTot), Element.ALIGN_RIGHT, 1);
    return t;
  }

  private static Paragraph footer() {
    Paragraph p = new Paragraph();
    p.setSpacingBefore(14f);
    p.add(
      new Phrase(
        "Employer share re-derived from statutory tables (SSS bracketed on " +
        "monthly basic; PhilHealth 50/50; Pag-IBIG 2%/2%). Amounts in PHP. " +
        "Form-styled report for internal remittance preparation; verify against " +
        "the official agency form before filing.",
        FontFactory.getFont(FontFactory.HELVETICA, 7.5f, Font.ITALIC, MUTED)
      )
    );
    return p;
  }

  // -------------------------------------------------------------------------
  // Cell helpers
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

  private static String orDash(String s) {
    return (s == null || s.isEmpty()) ? "\u2014" : s;
  }
}