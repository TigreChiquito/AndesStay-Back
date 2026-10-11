package cl.tigrechiquito.ms_andesstay_notify.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Arma el PDF del voucher con OpenPDF. Solo presentación: recibe los datos ya
 * resueltos (VoucherData) y devuelve los bytes del documento.
 */
@Component
public class VoucherPdfGenerator {

    private static final Color BRAND = new Color(31, 78, 121);
    private static final Color MUTED = new Color(110, 110, 110);
    private static final Color ROW_BG = new Color(242, 245, 249);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final ZoneId ZONE = ZoneId.of("America/Santiago");

    /** Datos del voucher. unitName, location, pricePerNight y total pueden venir null. */
    public record VoucherData(
            String code,
            Long reservationId,
            String guestName,
            Long unitId,
            String unitName,
            String location,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            long nights,
            BigDecimal pricePerNight,
            BigDecimal total
    ) {
    }

    public byte[] generate(VoucherData data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        PdfWriter.getInstance(document, out);
        document.open();

        Font brand = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 26, BRAND);
        Font subtitle = FontFactory.getFont(FontFactory.HELVETICA, 14, MUTED);
        Font codeFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, Color.BLACK);
        Font label = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, Color.DARK_GRAY);
        Font value = FontFactory.getFont(FontFactory.HELVETICA, 11, Color.BLACK);
        Font totalFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 13, BRAND);
        Font note = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 10, MUTED);

        document.add(new Paragraph("AndesStay", brand));
        document.add(new Paragraph("Voucher de reserva", subtitle));

        Paragraph code = new Paragraph("Código: " + data.code(), codeFont);
        code.setSpacingBefore(18);
        document.add(code);
        document.add(new Paragraph("Emitido el " + LocalDate.now(ZONE).format(DATE), note));

        PdfPTable table = new PdfPTable(new float[] {1.2f, 2f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(20);

        row(table, "Huésped", orDash(data.guestName()), label, value);
        row(table, "N° de reserva", "#" + data.reservationId(), label, value);
        row(table, "Unidad", data.unitName() != null
                ? data.unitName() + " (#" + data.unitId() + ")"
                : "Unidad #" + data.unitId(), label, value);
        if (data.location() != null) {
            row(table, "Ubicación", data.location(), label, value);
        }
        row(table, "Check-in", fmt(data.checkInDate()), label, value);
        row(table, "Check-out", fmt(data.checkOutDate()), label, value);
        row(table, "Noches", String.valueOf(data.nights()), label, value);
        if (data.pricePerNight() != null) {
            row(table, "Tarifa por noche", clp(data.pricePerNight()), label, value);
        }
        if (data.total() != null) {
            row(table, "Total", clp(data.total()), label, totalFont);
        }
        document.add(table);

        Paragraph footer = new Paragraph(
                "Presenta este voucher al momento del check-in. Reserva confirmada por AndesStay.", note);
        footer.setSpacingBefore(30);
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);

        document.close();
        return out.toByteArray();
    }

    private static void row(PdfPTable table, String label, String value, Font labelFont, Font valueFont) {
        PdfPCell left = new PdfPCell(new Phrase(label, labelFont));
        PdfPCell right = new PdfPCell(new Phrase(value, valueFont));
        for (PdfPCell cell : new PdfPCell[] {left, right}) {
            cell.setPadding(8);
            cell.setBorder(Rectangle.BOTTOM);
            cell.setBorderColor(Color.LIGHT_GRAY);
        }
        left.setBackgroundColor(ROW_BG);
        table.addCell(left);
        table.addCell(right);
    }

    private static String fmt(LocalDate date) {
        return date != null ? date.format(DATE) : "—";
    }

    private static String orDash(String text) {
        return text != null && !text.isBlank() ? text : "—";
    }

    private static String clp(BigDecimal amount) {
        NumberFormat format = NumberFormat.getIntegerInstance(Locale.forLanguageTag("es-CL"));
        return "$" + format.format(amount) + " CLP";
    }
}
