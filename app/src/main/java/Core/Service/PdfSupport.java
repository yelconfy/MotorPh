package Core.Service;

import java.awt.Color;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;

/**
 * Shared PDF building blocks for the document renderers (PayslipPdfRenderer,
 * DtrPdfRenderer, PayrollSummaryPdfRenderer). Holds the MotorPH brand palette,
 * the company header band, and the info-grid label/value cells — the pieces
 * that were byte-for-byte identical across all three renderers.
 *
 * Layout that genuinely differs per document (the payslip earnings sections,
 * the DTR daily grid, the summary's grouped statutory bands) stays in each
 * renderer; only the verbatim-shared scaffolding lives here, so changing the
 * brand colour or the header once updates every PDF.
 *
 * OpenPDF 3.x (package org.openpdf). No money is formatted here — the base-14
 * fonts have no U+20B1 glyph, so each renderer formats its own numbers.
 */
public final class PdfSupport {

  private PdfSupport() {}

  // Brand palette — single source of truth for all renderers.
  public static final Color BRAND_DARK = new Color(0x0D1B2A);
  public static final Color BRAND_RED = new Color(0xE53935);
  public static final Color MUTED = new Color(0x6B7682);
  public static final Color SHADE = new Color(0xEFEFEF);
  public static final Color LINE = new Color(0xDDDDDD);

  /**
   * Company header band: "MotorPH" in brand red over an underlined document
   * title (e.g. "EMPLOYEE PAYSLIP", "DAILY TIME RECORD").
   */
  public static PdfPTable header(String title) {
    PdfPTable t = new PdfPTable(1);
    t.setWidthPercentage(100);
    t.setSpacingAfter(4f);

    PdfPCell c = new PdfPCell();
    c.setBorder(PdfPCell.NO_BORDER);
    c.setHorizontalAlignment(Element.ALIGN_LEFT);
    c.addElement(
      new Paragraph(
        "MotorPH",
        FontFactory.getFont(FontFactory.HELVETICA, 20, Font.BOLD, BRAND_RED)
      )
    );
    c.addElement(
      new Paragraph(
        title,
        FontFactory.getFont(
          FontFactory.HELVETICA, 14, Font.BOLD | Font.UNDERLINE, BRAND_DARK
        )
      )
    );
    t.addCell(c);
    return t;
  }

  /** Dark label cell for an info grid. */
  public static void infoLabel(PdfPTable t, String text) {
    PdfPCell c = new PdfPCell(
      new Phrase(
        text,
        FontFactory.getFont(FontFactory.HELVETICA, 8, Font.BOLD, Color.WHITE)
      )
    );
    c.setBackgroundColor(BRAND_DARK);
    c.setBorderColor(Color.WHITE);
    c.setPadding(5f);
    c.setVerticalAlignment(Element.ALIGN_MIDDLE);
    t.addCell(c);
  }

  /** Plain value cell for an info grid. */
  public static void infoValue(PdfPTable t, String text) {
    PdfPCell c = new PdfPCell(
      new Phrase(
        text,
        FontFactory.getFont(FontFactory.HELVETICA, 8.5f, Font.NORMAL, BRAND_DARK)
      )
    );
    c.setBorderColor(LINE);
    c.setPadding(5f);
    c.setVerticalAlignment(Element.ALIGN_MIDDLE);
    t.addCell(c);
  }
}