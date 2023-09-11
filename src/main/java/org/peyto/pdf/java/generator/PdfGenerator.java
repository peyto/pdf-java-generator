package org.peyto.pdf.java.generator;

import com.itextpdf.html2pdf.ConverterProperties;
import com.itextpdf.html2pdf.HtmlConverter;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;

import java.nio.charset.StandardCharsets;

public class PdfGenerator {

    public static void generateFile(String outputFileName, String fullHtmlContent) {
        try {
            PdfDocument pdfDocument = new PdfDocument(new PdfWriter(outputFileName));
            ConverterProperties converterProperties = new ConverterProperties();
            converterProperties.setCharset(StandardCharsets.UTF_8.name());
            // Convert HTML to PDF
            HtmlConverter.convertToPdf(fullHtmlContent, pdfDocument, converterProperties);
            pdfDocument.close();
        } catch (Exception e) {
            throw new RuntimeException("Error generating pdf file", e);
        }
    }
}
