package Core.Service;

import Objects.models.Payslip;
import Objects.models.PayslipAllowanceLine;
import Objects.models.PayslipDeductionLine;
import Objects.models.PayslipDetail;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
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
 * Pure PayslipDetail -> PDF rendering (OpenPDF 3.x, package org.openpdf). No DB,
 * no file I/O — returns the PDF bytes so the UI decides where they go.
 *
 * Layout follows the official MotorPH payslip: company header, info grid, and
 * four sections (EARNINGS / BENEFITS / DEDUCTIONS / SUMMARY) with shaded totals.
 *
 * Renders entirely from the FROZEN snapshot — header totals + persisted line
 * items, never a recompute — so a reprint always matches the finalized slip.
 * Earnings are reorganised for display only (net pay is unchanged):
 *   Gross Income = GrossPay - TotalAllowances   (earnings, allowances excluded)
 *   Overtime     = Gross Income - BasicPay       (OT / premium residual)
 *   Benefits     = TotalAllowances
 *   Take Home    = NetPay
 * A "Lates / absences" residual (TotalDeductions - sum of deduction lines) is
 * kept so the itemised deductions reconcile to TOTAL DEDUCTIONS.
 *
 * Position, department, and the period-accurate Monthly/Daily rate are carried
 * on the header via PayrollDAO.GetById's JOINs (null/0 when absent).
 *
 * Amounts print as "PHP n": the base-14 PDF fonts have no glyph for U+20B1.
 */
public final class PayslipPdfRenderer {

  private static final Color BRAND_DARK = PdfSupport.BRAND_DARK;
  private static final Color MUTED = PdfSupport.MUTED;
  private static final Color SHADE = PdfSupport.SHADE;
  private static final Color LINE = PdfSupport.LINE;

  private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern(
    "MMM d, yyyy h:mm a"
  );
  private static final DateTimeFormatter D_SLASH = DateTimeFormatter.ofPattern(
    "M/d/yyyy"
  );
  private static final DateTimeFormatter D_ISO = DateTimeFormatter.ofPattern(
    "yyyy-MM-dd"
  );

  /** DB deduction names -> printed labels (official-payslip wording). */
  private static final Map<String, String> DED_LABELS = new LinkedHashMap<>();

  static {
    DED_LABELS.put("SSS", "Social Security System");
    DED_LABELS.put("PhilHealth", "Philhealth");
    DED_LABELS.put("Pag-IBIG", "Pag-Ibig");
    DED_LABELS.put("Withholding Tax", "Withholding Tax");
  }

  /** Renders the payslip to PDF bytes. */
  public byte[] Render(PayslipDetail detail) throws IOException {
    Payslip h = detail.GetHeader();
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      Document doc = new Document(PageSize.A4, 40, 40, 40, 40);
      PdfWriter.getInstance(doc, out);
      doc.open();

      doc.add(PdfSupport.header("EMPLOYEE PAYSLIP"));
      doc.add(infoGrid(h));
      doc.add(earnings(h));
      doc.add(benefits(detail));
      doc.add(deductions(detail));
      doc.add(summary(h));
      doc.add(footer(h));

      doc.close();
      return out.toByteArray();
    } catch (DocumentException e) {
      throw new IOException(
        "Failed to render payslip PDF: " + e.getMessage(),
        e
      );
    }
  }

  /** Suggested download filename, e.g. Payslip_10001_2024-06-16.pdf */
  public static String SuggestFileName(Payslip h) {
    String period = (h.GetPeriodStart() != null)
      ? h.GetPeriodStart().toString()
      : "period";
    return "Payslip_" + h.GetEmployeeId() + "_" + period + ".pdf";
  }

  // -------------------------------------------------------------------------
  // Header / title / info grid
  // -------------------------------------------------------------------------

  private static PdfPTable infoGrid(Payslip h) {
    PdfPTable t = new PdfPTable(new float[] { 20f, 26f, 22f, 32f });
    t.setWidthPercentage(100);
    t.setSpacingAfter(12f);

    String payslipNo =
      h.GetEmployeeId() +
      "-" +
      (h.GetPeriodEnd() != null ? h.GetPeriodEnd().format(D_ISO) : "");
    String posDept =
      orEmpty(h.GetPositionName()) +
      (notBlank(h.GetDepartmentName()) ? " / " + h.GetDepartmentName() : "");

    PdfSupport.infoLabel(t, "PAYSLIP NO");
    PdfSupport.infoValue(t, payslipNo);
    PdfSupport.infoLabel(t, "PERIOD START DATE");
    PdfSupport.infoValue(t, fmt(h.GetPeriodStart()));
    PdfSupport.infoLabel(t, "EMPLOYEE ID");
    PdfSupport.infoValue(t, String.valueOf(h.GetEmployeeId()));
    PdfSupport.infoLabel(t, "PERIOD END DATE");
    PdfSupport.infoValue(t, fmt(h.GetPeriodEnd()));
    PdfSupport.infoLabel(t, "EMPLOYEE NAME");
    PdfSupport.infoValue(t, nameLastFirst(h));
    PdfSupport.infoLabel(t, "EMPLOYEE POSITION/DEPARTMENT");
    PdfSupport.infoValue(t, posDept);
    return t;
  }

  // -------------------------------------------------------------------------
  // Sections
  // -------------------------------------------------------------------------

  private static PdfPTable earnings(Payslip h) {
    PdfPTable t = section("EARNINGS");
    double grossIncome = round2(h.GetGrossPay() - h.GetTotalAllowances());
    double overtime = round2(grossIncome - h.GetBasicPay());
    if (overtime < 0) overtime = 0;

    lineRow(t, "Monthly Salary", peso(h.GetMonthlyRate()));
    lineRow(t, "Daily Rate", peso(h.GetDailyRate()));
    lineRow(t, "Days Worked", trimNum(h.GetDaysWorked()));
    lineRow(t, "Overtime", peso(overtime));
    totalRow(t, "GROSS INCOME", peso(grossIncome));
    return t;
  }

  private static PdfPTable benefits(PayslipDetail d) {
    PdfPTable t = section("BENEFITS");
    if (d.GetAllowances() != null) {
      for (PayslipAllowanceLine a : d.GetAllowances()) {
        lineRow(t, a.GetAllowanceName(), peso(a.GetAmount()));
      }
    }
    totalRow(t, "TOTAL", peso(d.GetHeader().GetTotalAllowances()));
    return t;
  }

  private static PdfPTable deductions(PayslipDetail d) {
    Payslip h = d.GetHeader();
    PdfPTable t = section("DEDUCTIONS");

    double dedSum = 0;
    if (d.GetDeductions() != null) {
      for (PayslipDeductionLine de : d.GetDeductions()) {
        lineRow(
          t,
          DED_LABELS.getOrDefault(de.GetDeductionName(), de.GetDeductionName()),
          peso(de.GetAmount())
        );
        dedSum += de.GetAmount();
      }
    }
    double penalties = round2(h.GetTotalDeductions() - dedSum);
    if (penalties >= 0.01) {
      lineRow(t, "Lates / absences", peso(penalties));
    }
    totalRow(t, "TOTAL DEDUCTIONS", peso(h.GetTotalDeductions()));
    return t;
  }

  private static PdfPTable summary(Payslip h) {
    PdfPTable t = section("SUMMARY");
    double grossIncome = round2(h.GetGrossPay() - h.GetTotalAllowances());
    lineRow(t, "Gross Income", peso(grossIncome));
    lineRow(t, "Benefits", peso(h.GetTotalAllowances()));
    lineRow(t, "Deductions", peso(h.GetTotalDeductions()));
    takeHomeRow(t, "TAKE HOME PAY", peso(h.GetNetPay()));
    return t;
  }

  private static Paragraph footer(Payslip h) {
    String gen = (h.GetGeneratedDate() != null)
      ? h.GetGeneratedDate().format(DT)
      : "\u2014";
    Paragraph p = new Paragraph();
    p.setSpacingBefore(16f);
    p.add(
      new Phrase(
        "Days worked: " +
          trimNum(h.GetDaysWorked()) +
          "    Hours worked: " +
          trimNum(h.GetHoursWorked()) +
          "\n",
        FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, MUTED)
      )
    );
    p.add(
      new Phrase(
        "Generated " +
          gen +
          "  -  System-generated payslip; no signature required.",
        FontFactory.getFont(FontFactory.HELVETICA, 7.5f, Font.ITALIC, MUTED)
      )
    );
    return p;
  }

  // -------------------------------------------------------------------------
  // Cell / format helpers
  // -------------------------------------------------------------------------

  private static PdfPTable section(String title) {
    PdfPTable t = new PdfPTable(new float[] { 62f, 38f });
    t.setWidthPercentage(100);
    t.setSpacingBefore(2f);
    t.setSpacingAfter(8f);
    PdfPCell bar = new PdfPCell(
      new Phrase(
        title,
        FontFactory.getFont(FontFactory.HELVETICA, 9, Font.BOLD, Color.WHITE)
      )
    );
    bar.setColspan(2);
    bar.setBackgroundColor(BRAND_DARK);
    bar.setBorder(PdfPCell.NO_BORDER);
    bar.setPadding(6f);
    t.addCell(bar);
    return t;
  }

  private static void lineRow(PdfPTable t, String label, String value) {
    Font f = FontFactory.getFont(
      FontFactory.HELVETICA,
      9,
      Font.NORMAL,
      BRAND_DARK
    );
    t.addCell(rowCell(label, f, Element.ALIGN_LEFT, null));
    t.addCell(rowCell(value, f, Element.ALIGN_RIGHT, null));
  }

  private static void totalRow(PdfPTable t, String label, String value) {
    Font f = FontFactory.getFont(
      FontFactory.HELVETICA,
      9,
      Font.BOLD,
      BRAND_DARK
    );
    t.addCell(rowCell(label, f, Element.ALIGN_LEFT, SHADE));
    t.addCell(rowCell(value, f, Element.ALIGN_RIGHT, SHADE));
  }

  private static void takeHomeRow(PdfPTable t, String label, String value) {
    Font f = FontFactory.getFont(
      FontFactory.HELVETICA,
      11,
      Font.BOLD | Font.UNDERLINE,
      BRAND_DARK
    );
    PdfPCell l = new PdfPCell(new Phrase(label, f));
    l.setBackgroundColor(SHADE);
    l.setBorder(PdfPCell.NO_BORDER);
    l.setPadding(6f);
    PdfPCell a = new PdfPCell(new Phrase(value, f));
    a.setBackgroundColor(SHADE);
    a.setBorder(PdfPCell.NO_BORDER);
    a.setPadding(6f);
    a.setHorizontalAlignment(Element.ALIGN_RIGHT);
    t.addCell(l);
    t.addCell(a);
  }

  private static PdfPCell rowCell(String text, Font f, int align, Color bg) {
    PdfPCell c = new PdfPCell(new Phrase(text, f));
    c.setHorizontalAlignment(align);
    c.setBorder(PdfPCell.BOTTOM);
    c.setBorderColor(LINE);
    c.setPadding(5f);
    if (bg != null) c.setBackgroundColor(bg);
    return c;
  }

  private static String peso(double v) {
    NumberFormat nf = NumberFormat.getNumberInstance(Locale.US);
    nf.setMinimumFractionDigits(2);
    nf.setMaximumFractionDigits(2);
    return "PHP " + nf.format(v);
  }

  private static String trimNum(double v) {
    return (v == Math.floor(v))
      ? String.valueOf((long) v)
      : String.valueOf(round2(v));
  }

  private static double round2(double v) {
    return Math.round(v * 100.0) / 100.0;
  }

  private static String fmt(LocalDate d) {
    return d != null ? d.format(D_SLASH) : "";
  }

  private static String orEmpty(String s) {
    return s == null ? "" : s;
  }

  private static boolean notBlank(String s) {
    return s != null && !s.isBlank();
  }

  private static String nameLastFirst(Payslip h) {
    String last = h.GetEmployeeLastName();
    String first = h.GetEmployeeFirstName();
    if (last != null && first != null) return last + ", " + first;
    String full = h.GetEmployeeFullName();
    return full == null ? "" : full.trim();
  }
}
