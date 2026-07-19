package Core.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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

import Objects.models.Bir2316Row;

/**
 * Renders a single employee's BIR Form 2316 certificate (Certificate of
 * Compensation Payment / Tax Withheld) to PDF bytes. Pure: no DB, no file I/O.
 *
 * Portrait A4. Layout: header band, employer block (employer TIN/address are
 * not stored -> labelled blanks, see LIMITATION), employee block (name, TIN,
 * address), then the money summary in the 2316 order: gross, non-taxable/exempt
 * (contributions, 13th month & other benefits up to 90k, non-taxable
 * allowances), taxable compensation, tax due, tax withheld, and over/under.
 * Two signature blocks close the certificate.
 *
 * Shared brand palette + top header band from PdfSupport. Amounts print without
 * the peso glyph (base-14 fonts lack U+20B1); "PHP" noted once.
 *
 * LIMITATION: no company-level employer registration (employer TIN / RDO /
 * registered address) is stored (see BKL-22), so those render as blanks.
 * Form-styled certificate, not a pixel-exact BIR template.
 */
public final class Bir2316PdfRenderer {

  private static final Color BRAND_DARK = PdfSupport.BRAND_DARK;
  private static final Color MUTED = PdfSupport.MUTED;
  private static final Color SHADE = PdfSupport.SHADE;
  private static final Color LINE = PdfSupport.LINE;

  private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");
  private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern(
    "MMM d, yyyy h:mm a"
  );

  private static final String EMPLOYER_NAME = "MotorPH";

  /** Renders one employee's 2316 certificate to PDF bytes. */
  public byte[] Render(Bir2316Row r) throws IOException {
    if (r == null) {
      throw new IOException("No 2316 data to render.");
    }
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
      PdfWriter.getInstance(doc, out);
      doc.open();

      doc.add(PdfSupport.header("BIR FORM 2316  -  CERTIFICATE OF COMPENSATION PAYMENT / TAX WITHHELD"));
      doc.add(partyBlock(r));
      doc.add(sectionTitle("SUMMARY OF COMPENSATION"));
      doc.add(moneyBlock(r));
      doc.add(signatures());
      doc.add(footer());

      doc.close();
      return out.toByteArray();
    } catch (DocumentException e) {
      throw new IOException("Failed to render 2316 PDF: " + e.getMessage(), e);
    }
  }

  /** Suggested filename, e.g. BIR2316_10001_2024.pdf */
  public static String SuggestFileName(long employeeNo, int year) {
    return "BIR2316_" + employeeNo + "_" + year + ".pdf";
  }

  // -------------------------------------------------------------------------
  // Employer + employee parties
  // -------------------------------------------------------------------------

  private static PdfPTable partyBlock(Bir2316Row r) {
    PdfPTable t = new PdfPTable(new float[] { 22f, 30f, 22f, 26f });
    t.setWidthPercentage(100);
    t.setSpacingAfter(10f);

    PdfSupport.infoLabel(t, "FOR THE YEAR");
    PdfSupport.infoValue(t, String.valueOf(r.GetPayYear()));
    PdfSupport.infoLabel(t, "GENERATED");
    PdfSupport.infoValue(t, LocalDateTime.now().format(STAMP));

    PdfSupport.infoLabel(t, "EMPLOYER");
    PdfSupport.infoValue(t, EMPLOYER_NAME);
    PdfSupport.infoLabel(t, "EMPLOYER TIN");
    PdfSupport.infoValue(t, "____________________"); // not stored (BKL-22)

    PdfSupport.infoLabel(t, "EMPLOYEE");
    PdfSupport.infoValue(t, orDash(r.GetEmployeeFullName()));
    PdfSupport.infoLabel(t, "EMPLOYEE TIN");
    PdfSupport.infoValue(t, orDash(r.GetTin()));

    PdfSupport.infoLabel(t, "POSITION");
    PdfSupport.infoValue(t, orDash(r.GetPosition()));
    PdfSupport.infoLabel(t, "EMPLOYEE NO.");
    PdfSupport.infoValue(t, String.valueOf(r.GetEmployeeNo()));

    PdfSupport.infoLabel(t, "ADDRESS");
    PdfPCell addr = new PdfPCell(
      new Phrase(
        orDash(r.GetRegisteredAddress()),
        FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, BRAND_DARK)
      )
    );
    addr.setColspan(3);
    addr.setBorder(0);
    addr.setPaddingBottom(4f);
    t.addCell(addr);
    return t;
  }

  // -------------------------------------------------------------------------
  // Money summary
  // -------------------------------------------------------------------------

  private static PdfPTable moneyBlock(Bir2316Row r) {
    PdfPTable t = new PdfPTable(new float[] { 72f, 28f });
    t.setWidthPercentage(100);
    t.setSpacingBefore(2f);

    line(t, "Gross Compensation Income", r.GetGrossCompensation(), false);

    subhead(t, "Less: Non-Taxable / Exempt");
    line(t, "   SSS Contribution", r.GetSssContribution(), false);
    line(t, "   PhilHealth Contribution", r.GetPhilHealthContribution(), false);
    line(t, "   Pag-IBIG Contribution", r.GetPagIbigContribution(), false);
    line(t, "   13th Month Pay & Other Benefits (exempt, max 90,000)", r.GetThirteenthMonthNonTaxable(), false);
    line(t, "   Non-Taxable / De Minimis Allowances", r.GetNonTaxableAllowances(), false);

    line(t, "Taxable 13th Month & Other Benefits (excess over 90,000)", r.GetThirteenthMonthTaxable(), false);

    line(t, "TAXABLE COMPENSATION INCOME", r.GetTaxableCompensation(), true);
    line(t, "Tax Due (annual, TRAIN)", r.GetTaxDue(), true);
    line(t, "Tax Withheld", r.GetTaxWithheld(), true);

    double ou = r.GetOverUnderWithheld();
    String ouLabel = ou >= 0
      ? "Over-Withheld (refund to employee)"
      : "Under-Withheld (collectible from employee)";
    line(t, ouLabel, Math.abs(ou), true);
    return t;
  }

  // -------------------------------------------------------------------------
  // Signatures
  // -------------------------------------------------------------------------

  private static PdfPTable signatures() {
    PdfPTable t = new PdfPTable(2);
    t.setWidthPercentage(100);
    t.setSpacingBefore(26f);

    t.addCell(sigCell("Employer / Authorized Agent\n(Signature over printed name)"));
    t.addCell(sigCell("Employee\n(Signature over printed name)"));
    return t;
  }

  private static PdfPCell sigCell(String label) {
    PdfPCell c = new PdfPCell();
    c.setBorder(0);
    c.setPaddingTop(20f);

    Paragraph rule = new Paragraph(
      "__________________________________",
      FontFactory.getFont(FontFactory.HELVETICA, 10, Font.NORMAL, BRAND_DARK)
    );
    Paragraph lbl = new Paragraph(
      label.replace("\n", "  "),
      FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, MUTED)
    );
    c.addElement(rule);
    c.addElement(lbl);
    return c;
  }

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  private static Paragraph sectionTitle(String text) {
    Paragraph p = new Paragraph(
      text,
      FontFactory.getFont(FontFactory.HELVETICA, 10, Font.BOLD, BRAND_DARK)
    );
    p.setSpacingBefore(6f);
    p.setSpacingAfter(4f);
    return p;
  }

  private static void line(PdfPTable t, String label, double amount, boolean strong) {
    Font f = FontFactory.getFont(
      FontFactory.HELVETICA, strong ? 9.5f : 9f,
      strong ? Font.BOLD : Font.NORMAL, BRAND_DARK
    );
    PdfPCell l = new PdfPCell(new Phrase(label, f));
    PdfPCell v = new PdfPCell(new Phrase(MONEY.format(amount), f));
    l.setBorderColor(LINE);
    v.setBorderColor(LINE);
    v.setHorizontalAlignment(Element.ALIGN_RIGHT);
    l.setPadding(4f);
    v.setPadding(4f);
    if (strong) {
      l.setBackgroundColor(SHADE);
      v.setBackgroundColor(SHADE);
    }
    t.addCell(l);
    t.addCell(v);
  }

  private static void subhead(PdfPTable t, String label) {
    PdfPCell c = new PdfPCell(
      new Phrase(
        label,
        FontFactory.getFont(FontFactory.HELVETICA, 9, Font.ITALIC, MUTED)
      )
    );
    c.setColspan(2);
    c.setBorderColor(LINE);
    c.setPadding(4f);
    t.addCell(c);
  }

  private static Paragraph footer() {
    Paragraph p = new Paragraph();
    p.setSpacingBefore(14f);
    p.add(
      new Phrase(
        "Tax due computed on annual TRAIN brackets; tax withheld is the actual " +
        "amount deducted. 13th month & other benefits exempt up to PHP 90,000. " +
        "Amounts in PHP. Form-styled certificate; verify against the official BIR " +
        "Form 2316 before issuance.",
        FontFactory.getFont(FontFactory.HELVETICA, 7.5f, Font.ITALIC, MUTED)
      )
    );
    return p;
  }

  private static String orDash(String s) {
    return (s == null || s.isEmpty()) ? "\u2014" : s;
  }
}