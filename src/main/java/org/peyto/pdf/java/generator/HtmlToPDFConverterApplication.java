package org.peyto.pdf.java.generator;

import org.peyto.pdf.java.generator.entity.JPackage;
import org.peyto.pdf.java.generator.entity.JavaProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class HtmlToPDFConverterApplication {

    private static final Logger log = LoggerFactory.getLogger(HtmlTransformer.class);

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: HtmlToPDFConverterApplication <inputFolderPath> [outputFileName without extension]");
            System.exit(1);
        }

        String folderPath = args[0];
        String outputFileName = args.length > 1 ? args[1] : "output";

        checkFolderExists(folderPath);
        process(folderPath, outputFileName, false);
    }

    private static void process(String folderPath, String outputFileName, boolean generateHtmlFile) {
        try {
            log.info("Starting processing [{}] to {}.pdf{}", folderPath, outputFileName, generateHtmlFile ? ", with html" : "");
            long startMillis = System.currentTimeMillis();
            JPackage files = FileCollector.collectHTMLFiles(folderPath);
            JavaProject parsedJavaProject = new JavaProject(files);

            log.info("Found total {} files in {} packages. Starting processing...", parsedJavaProject.getNumberOfClasses(), parsedJavaProject.getNumberOfPackages());

            // Perform HTML parsing and transformation to single document
            String outputDocumentHtml = HtmlTransformer.processHTMLFiles(parsedJavaProject);
            log.info("Html files processed and transformed in {} ms", delta(startMillis));

            long startGenerationMillis = System.currentTimeMillis();
            if (generateHtmlFile) {
                saveHtmlAsFile(outputFileName + ".html", outputDocumentHtml);
            }
            // create pdf file from html file
            PdfGenerator.generateFile(outputFileName + ".pdf", outputDocumentHtml);
            log.info("Pdf file {} generated in {} ms. {}", outputFileName + ".pdf", delta(startGenerationMillis),
                    generateHtmlFile ? "Html file " + outputFileName + ".html generated. " : "");
            log.info("Finished. App took total {} ms.", delta(startMillis));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static long delta(long startMillis) {
        return System.currentTimeMillis() - startMillis;
    }

    private static void checkFolderExists(String folderPath) {
        // Check if the specified folder exists
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            throw new RuntimeException("The specified folder does not exist " + folderPath);
        }
    }

    private static void saveHtmlAsFile(String fileName, String outerHtml) {
        try {
            FileOutputStream outputStream = new FileOutputStream(fileName);
            byte[] strToBytes = outerHtml.getBytes();
            outputStream.write(strToBytes);

            outputStream.close();
        } catch (Exception e) {
            log.error("Error writing html file", e);
        }
    }

}
