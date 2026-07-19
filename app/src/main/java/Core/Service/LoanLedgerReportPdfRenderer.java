package Core.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.time.LocalDate;
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

import Objects.models.LoanLedgerRow;

/**
 * Renders the Loan Ledger Report to PDF bytes. Pure: no DB, no file I/O.
 * Landscape A4 (10 columns): header band, filter/info grid, loan grid with a
 * TOTAL row (payable / paid / outstanding). Shared brand palette + header from
 * PdfSupport. Amounts print without the peso glyph; "PHP" noted once.
 */
public final class LoanLedgerReportPdfRenderer {

  private static final Color BRAND_DARK = PdfSupport.BRAND_DARK;
  private static final Color MUTED = PdfSupport.MUTED;
  private static final Color SHADE = PdfSupport.SHADE;
  private static final Color LINE = PdfSupport.LINE;

  private static final DecimalFormat MONEY = new DecimalFormat("#,##0.00");
  private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("MMM d, yyyy h:mm a");
  private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

  // No, Name, Dept, Loan Type, Principal, Total Payable, Paid, Outstanding, Terms, Status
  private static final float[] WIDTHS = { 6f, 18f, 12f, 12f, 10f, 11f, 10f, 11f, 5f, 8f };

  public byte[] Render(String scopeLabel, List<LoanLedgerRow> rows) throws IOException {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Document doc = new Document(PageSize.A4.rotate(), 30, 30, 30, 30);
      PdfWriter.getInstance(doc, out);
      doc.open();
      doc.add(PdfSupport.header("LOAN LEDGER REPORT"));
      doc.add(infoGrid(scopeLabel, rows != null ? rows.size() : 0));
      doc.add(grid(rows));
      doc.add(footer());
      doc.close();
      return out.toByteArray();
    } catch (DocumentException e) {
      throw new IOException("Failed to render loan ledger PDF: " + e.getMessage(), e);
    }
  }

  public static String SuggestFileName(String scopeLabel) {
    String s = (scopeLabel == null ? "All" : scopeLabel).replace(" ", "");
    return "LoanLedger_" + s + ".pdf";
  }

  private static PdfPTable infoGrid(String scopeLabel, int rowCount) {
    PdfPTable t = new PdfPTable(new float[] { 14f, 26f, 16f, 44f });
    t.setWidthPercentage(100);
    t.setSpacingAfter(10f);
    PdfSupport.infoLabel(t, "STATUS FILTER");
    PdfSupport.infoValue(t, scopeLabel == null ? "All" : scopeLabel);
    PdfSupport.infoLabel(t, "LOANS");
    PdfSupport.infoValue(t, String.valueOf(rowCount));
    PdfSupport.infoLabel(t, "GENERATED");
    PdfSupport.infoValue(t, LocalDateTime.now().format(STAMP));
    PdfSupport.infoLabel(t, "CURRENCY");
    PdfSupport.infoValue(t, "Philippine Peso (PHP)");
    return t;
  }

  private static PdfPTable grid(List<LoanLedgerRow> rows) {
    PdfPTable t = new PdfPTable(WIDTHS);
    t.setWidthPercentage(100);
    t.setSpacingBefore(2f);
    t.setHeaderRows(1);

    headCell(t, "No.", Element.ALIGN_LEFT);
    headCell(t, "Employee Full Name", Element.ALIGN_LEFT);
    headCell(t, "Department", Element.ALIGN_LEFT);
    headCell(t, "Loan Type", Element.ALIGN_LEFT);
    headCell(t, "Principal", Element.ALIGN_RIGHT);
    headCell(t, "Total Payable", Element.ALIGN_RIGHT);
    headCell(t, "Paid", Element.ALIGN_RIGHT);
    headCell(t, "Outstanding", Element.ALIGN_RIGHT);
    headCell(t, "Terms", Element.ALIGN_CENTER);
    headCell(t, "Status", Element.ALIGN_LEFT);

    if (rows == null || rows.isEmpty()) {
      PdfPCell empty = new PdfPCell(new Phrase("No loans match this filter.",
        FontFactory.getFont(FontFactory.HELVETICA, 9, Font.ITALIC, MUTED)));
      empty.setColspan(WIDTHS.length);
      empty.setPadding(8f);
      empty.setHorizontalAlignment(Element.ALIGN_CENTER);
      empty.setBorderColor(LINE);
      t.addCell(empty);
      return t;
    }

    double tPay = 0, tPaid = 0, tOut = 0;
    for (LoanLedgerRow r : rows) {
      bodyCell(t, String.valueOf(r.GetEmployeeNo()), Element.ALIGN_LEFT);
      bodyCell(t, orEmpty(r.GetEmployeeFullName()), Element.ALIGN_LEFT);
      bodyCell(t, orEmpty(r.GetDepartment()), Element.ALIGN_LEFT);
      bodyCell(t, orEmpty(r.GetLoanType()), Element.ALIGN_LEFT);
      bodyCell(t, MONEY.format(r.GetPrincipal()), Element.ALIGN_RIGHT);
      bodyCell(t, MONEY.format(r.GetTotalPayable()), Element.ALIGN_RIGHT);
      bodyCell(t, MONEY.format(r.GetAmountPaid()), Element.ALIGN_RIGHT);
      bodyCell(t, MONEY.format(r.GetOutstandingBalance()), Element.ALIGN_RIGHT);
      bodyCell(t, String.valueOf(r.GetTerms()), Element.ALIGN_CENTER);
      bodyCell(t, orEmpty(r.GetStatusLabel()), Element.ALIGN_LEFT);
      tPay += r.GetTotalPayable();
      tPaid += r.GetAmountPaid();
      tOut += r.GetOutstandingBalance();
    }
    totalCell(t, "TOTAL", Element.ALIGN_LEFT, 5);
    totalCell(t, MONEY.format(tPay), Element.ALIGN_RIGHT, 1);
    totalCell(t, MONEY.format(tPaid), Element.ALIGN_RIGHT, 1);
    totalCell(t, MONEY.format(tOut), Element.ALIGN_RIGHT, 1);
    totalCell(t, "", Element.ALIGN_CENTER, 2);
    return t;
  }

  private static Paragraph footer() {
    Paragraph p = new Paragraph();
    p.setSpacingBefore(12f);
    p.add(new Phrase(
      "Outstanding = total payable minus loan-tagged payroll deductions. " +
      "Amounts in PHP. System-generated report.",
      FontFactory.getFont(FontFactory.HELVETICA, 7.5f, Font.ITALIC, MUTED)));
    return p;
  }

  private static void headCell(PdfPTable t, String text, int align) {
    PdfPCell c = new PdfPCell(new Phrase(text,
      FontFactory.getFont(FontFactory.HELVETICA, 7.5f, Font.BOLD, Color.WHITE)));
    c.setBackgroundColor(BRAND_DARK);
    c.setBorderColor(Color.WHITE);
    c.setPadding(4f);
    c.setHorizontalAlignment(align);
    t.addCell(c);
  }

  private static void bodyCell(PdfPTable t, String text, int align) {
    PdfPCell c = new PdfPCell(new Phrase(text,
      FontFactory.getFont(FontFactory.HELVETICA, 8f, Font.NORMAL, BRAND_DARK)));
    c.setHorizontalAlignment(align);
    c.setBorderColor(LINE);
    c.setPadding(3.5f);
    t.addCell(c);
  }

  private static void totalCell(PdfPTable t, String text, int align, int colspan) {
    PdfPCell c = new PdfPCell(new Phrase(text,
      FontFactory.getFont(FontFactory.HELVETICA, 8.5f, Font.BOLD, BRAND_DARK)));
    c.setColspan(colspan);
    c.setBackgroundColor(SHADE);
    c.setHorizontalAlignment(align);
    c.setBorderColor(LINE);
    c.setPadding(4f);
    t.addCell(c);
  }

  private static String orEmpty(String s) { return s == null ? "" : s; }
}