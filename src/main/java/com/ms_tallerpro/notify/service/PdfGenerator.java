package com.ms_tallerpro.notify.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.time.Instant;
import java.util.List;

/**
 * Generador minimo del PDF de presupuesto/orden de trabajo (q.cmd.quote, caso
 * seccion 8). Una sola pagina, texto plano: titulo + lineas clave-valor con los
 * datos que ya trae el comando. Sin tabla de repuestos detallada (eso requiere
 * que jobs mande el detalle linea por linea, no solo el conteo actual).
 */
public final class PdfGenerator {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm")
            .withZone(ZoneId.of("America/Santiago"));

    private PdfGenerator() {
    }

    public static byte[] generar(String titulo, List<String> lineas) {
        try (PDDocument doc = new PDDocument()) {
            PDPage pagina = new PDPage();
            doc.addPage(pagina);

            try (PDPageContentStream cs = new PDPageContentStream(doc, pagina)) {
                PDType1Font fuenteTitulo = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
                PDType1Font fuenteTexto = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
                float x = 60;
                float y = 760;

                cs.beginText();
                cs.setFont(fuenteTitulo, 16);
                cs.newLineAtOffset(x, y);
                cs.showText("TallerPro");
                cs.endText();
                y -= 26;

                cs.beginText();
                cs.setFont(fuenteTitulo, 13);
                cs.newLineAtOffset(x, y);
                cs.showText(titulo);
                cs.endText();
                y -= 22;

                cs.beginText();
                cs.setFont(fuenteTexto, 9);
                cs.newLineAtOffset(x, y);
                cs.showText("Generado: " + FECHA.format(Instant.now()));
                cs.endText();
                y -= 24;

                cs.setFont(fuenteTexto, 11);
                for (String linea : lineas) {
                    cs.beginText();
                    cs.newLineAtOffset(x, y);
                    cs.showText(linea);
                    cs.endText();
                    y -= 18;
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
