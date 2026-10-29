package com.learnplatform.service;

import com.learnplatform.domain.Certificate;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Service;

/** Renders a certificate as a one-page landscape PDF with its QR code. */
@Service
public class CertificatePdfService {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    public byte[] render(Certificate c, byte[] qrPng) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth()));
            doc.addPage(page);
            PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDImageXObject qr = PDImageXObject.createFromByteArray(doc, qrPng, "qr");
            float w = page.getMediaBox().getWidth();
            float h = page.getMediaBox().getHeight();

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.setStrokingColor(0.49f, 0.36f, 1f);
                cs.setLineWidth(6);
                cs.addRect(24, 24, w - 48, h - 48);
                cs.stroke();
                cs.setStrokingColor(0.98f, 0.75f, 0.14f);
                cs.setLineWidth(1.5f);
                cs.addRect(36, 36, w - 72, h - 72);
                cs.stroke();

                center(cs, bold, 30, "CERTIFICATE OF COMPLETION", w, h - 120);
                center(cs, regular, 14, "This is to certify that", w, h - 170);
                center(cs, bold, 36, c.getLearnerName(), w, h - 225);
                center(cs, regular, 14, "has successfully completed", w, h - 270);
                center(cs, bold, 22, c.getTitle().replace("Certificate of Completion: ", ""), w, h - 310);
                center(cs, regular, 14, "with a score of " + c.getScore() + "%", w, h - 345);
                center(cs, regular, 12, "Issued by " + c.getIssuer(), w, h - 385);

                String issued = DATE.format(c.getCreatedAt().atZone(ZoneOffset.UTC));
                String until = DATE.format(c.getValidUntil().atZone(ZoneOffset.UTC));
                text(cs, regular, 11, "Issued: " + issued, 70, 90);
                text(cs, regular, 11, "Valid until: " + until, 70, 72);
                text(cs, regular, 11, "Certificate ID: " + c.getCode(), 70, 54);
                cs.drawImage(qr, w - 160, 50, 90, 90);
                text(cs, regular, 8, "Scan to verify", w - 147, 40);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        }
    }

    private static String safe(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : (s == null ? "" : s).toCharArray()) b.append(ch >= 32 && ch < 127 ? ch : '?');
        return b.toString();
    }

    private void center(PDPageContentStream cs, PDFont font, float size, String s, float pageW, float y) throws IOException {
        String t = safe(s);
        float tw = font.getStringWidth(t) / 1000 * size;
        text(cs, font, size, t, (pageW - tw) / 2, y);
    }

    private void text(PDPageContentStream cs, PDFont font, float size, String s, float x, float y) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(safe(s));
        cs.endText();
    }
}
