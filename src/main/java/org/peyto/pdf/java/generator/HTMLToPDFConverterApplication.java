package org.peyto.pdf.java.generator;

import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.peyto.pdf.java.generator.entity.HtmlProvider;
import org.peyto.pdf.java.generator.entity.HtmlProvider.SourceHtmlType;
import org.peyto.pdf.java.generator.entity.JPackage;
import org.peyto.pdf.java.generator.entity.JavaProject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.peyto.pdf.java.generator.entity.HtmlProvider.SourceHtmlType.CLASS;
import static org.peyto.pdf.java.generator.entity.HtmlProvider.SourceHtmlType.PACKAGE;

public class HTMLToPDFConverterApplication {

    private static final Pattern CSS_PATTERN = Pattern.compile("\\.\\s*(.*?)\\s*\\{\\s*(.*?)\\s*\\}");

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: HTMLToPDFConverter <folderPath> [outputFileName]");
            System.exit(1);
        }

        String folderPath = args[0];
        String outputFileName = args.length > 1 ? args[1] : "output.pdf";

        // Check if the specified folder exists
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            System.out.println("The specified folder does not exist.");
            System.exit(1);
        }

        // Perform HTML parsing and PDF generation
        try {
            processHTMLFiles(folderPath, outputFileName);
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void processHTMLFiles(String folderPath, String outputFileName) throws IOException {
        JPackage files = FileCollector.collectHTMLFiles(folderPath);
        JavaProject parsedJavaProject = new JavaProject(files);

        Document outputDocument = generateBaseHtmlTemplate();

        String pageBreakDiv = "<div class=\"page-break\"></div> \n";

        List<Pair<String, SourceHtmlType>> tableOfContent = new ArrayList<>();
        Map<String, String> cssCodeStyles = new HashMap<>();

        Element packageDiv = outputDocument.body().getElementById("package-hierarchy");
        for (HtmlProvider htmlProvider : parsedJavaProject.getOrderedNodes()) {
            Pair<String, Document> parsedClass = parseJavaFile(parsedJavaProject.getBasePackage(), htmlProvider, cssCodeStyles);
            if (htmlProvider.sourceHtmlType() == PACKAGE) {
                tableOfContent.add(Pair.of(parsedClass.getLeft(), PACKAGE));
                packageDiv.append(pageBreakDiv);
                packageDiv.append(parsedClass.getRight().body().html());
            } else {
                tableOfContent.add(Pair.of(parsedClass.getLeft(), CLASS));
                outputDocument.body().append(pageBreakDiv);
                outputDocument.body().append(parsedClass.getRight().body().html());
            }
        }

        for (Map.Entry<String, String> style : cssCodeStyles.entrySet()) {
            outputDocument.head().appendElement("style").text("."+style.getKey() + " { " + style.getValue() + " }");
        }

        generateTableOfContent(outputDocument, parsedJavaProject.getBasePackage(), tableOfContent);

        // saveHtmlAsFile("a.html", outputDocument.outerHtml());
        PdfGenerator.generateFile(outputFileName, outputDocument.outerHtml());
    }

    private static void generateTableOfContent(Document outputDocument, String basePackage, List<Pair<String, SourceHtmlType>> tableOfContent) {
        Element tocDiv = outputDocument.body().getElementById("toc");
        for (Pair<String, SourceHtmlType> entry : tableOfContent) {
            String uniqueId = entry.getKey();
            boolean pckg = entry.getValue() == PACKAGE;
            String displayName = uniqueId.replace("-", ".");
            displayName = displayName.replace(basePackage, "");
            if (displayName.startsWith(".")) {
                displayName = displayName.substring(1);
            }
            if (displayName.isEmpty()) {
                tocDiv.append("<br><a name=\"toc\" href=\"#" + uniqueId.trim() + "\"><b>" + basePackage + "</b></a>");
            } else {
                displayName = removeParentPackagesWithIntent(displayName);
                tocDiv.append("<br><a href=\"#" + uniqueId.trim() + "\">" + (pckg ? "<b>" : "") + displayName + (pckg ? "</b>" : "") + "</a>");
            }
        }
    }

    private static String removeParentPackagesWithIntent(String inputString) {
        String intentStr = "&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;";
        StringBuilder result = new StringBuilder(intentStr);
        for (int i = 0; i < inputString.length(); i++) {
            if (inputString.charAt(i) == '.') {
                result.append("&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;");
            }
        }
        int last = inputString.lastIndexOf(".");
        if (last != -1) {
            result.append(inputString.substring(last + 1));
        } else {
            result.append(inputString);
        }
        return result.toString();
    }

    private static Pair<String, Document> parseJavaFile(String basePackage, HtmlProvider htmlProvider, Map<String, String> cssCodeStyles) {
        Document document = parseHTML(htmlProvider.getHtmlFile());
        String currentPackageId;
        if (htmlProvider.sourceHtmlType() == CLASS) {
            currentPackageId = makePackageLink(document);
            String fullUniqueName = currentPackageId + "-" + htmlProvider.getName();

            Element spanWithName = document.select("table center").first();
            if (spanWithName == null) {
                throw new RuntimeException("Wrong html structure");
            }
            spanWithName.html("<a name=\"" + fullUniqueName + "\">" + spanWithName.html() + "</a>");

            changeInternalLinks(currentPackageId, document);
            changeInternalCssCodeClasses(document, cssCodeStyles);
            return Pair.of(fullUniqueName, document);
        } else {
            String packageName = document.getElementsByTag("title").get(0).text();
            currentPackageId = packageUniqueIdFromName(packageName);
            Pair<String, String> parentPackageAndCurrentPackage = calculateParentPackageName(packageName);

            String parentPackageId = parentPackageAndCurrentPackage.getLeft() == null || parentPackageAndCurrentPackage.getLeft().length() < basePackage.length() ?
                    "toc" :
                    packageUniqueIdFromName(parentPackageAndCurrentPackage.getLeft());
            document.body().prepend("<a " +
                    "name=\"" + currentPackageId + "\" " +
                    "href=\"#" + parentPackageId + "\">" +
                    "<span>" + parentPackageAndCurrentPackage.getLeft() + "</span></a>." + parentPackageAndCurrentPackage.getRight() + "<br><br>");
            Elements allLinks = document.body().getElementsByTag("a");
            List<Element> links = allLinks.stream().filter(element -> element.hasAttr("href")).collect(Collectors.toList());
            for (Element aLinkElement : links) {
                String linkText = aLinkElement.text();
                aLinkElement.html("<span>&nbsp;&nbsp;&nbsp;&nbsp;" + linkText + "</span>");
            }
            changeInternalLinks(currentPackageId, document);
            return Pair.of(currentPackageId, document);
        }
    }

    private static void changeInternalCssCodeClasses(Document document, Map<String, String> cssCodeStyles) {
        // s0 -> color, s1 -> color
        Map<String, String> internalParsedClasses = parseStyles(document);
        Map<String, String> cssCodeStylesInverted = new HashMap<>();
        cssCodeStyles.forEach((key, value) -> cssCodeStylesInverted.put(value, key));
        Map<String, String> oldClassToNewClassMapping = new HashMap<>();
        internalParsedClasses.forEach((classNameInParsedFile, classStyle) -> {
            String newClassName = cssCodeStylesInverted.get(classStyle);
            if (newClassName == null) {
                String newUniqueClassName = getNewUniqueClassName(cssCodeStyles.keySet(), classNameInParsedFile);
                cssCodeStyles.put(newUniqueClassName, classStyle);
            } else {
                if (!newClassName.equals(classNameInParsedFile)) {
                    oldClassToNewClassMapping.put(classNameInParsedFile, cssCodeStylesInverted.get(classStyle));
                }
            }
        });

        document.getElementsByTag("span")
                .forEach(element -> {
                    String oldClass = element.attr("class");
                    if (oldClassToNewClassMapping.containsKey(oldClass)) {
                        element.attr("class", oldClassToNewClassMapping.get(oldClass));
                    }
                });
    }

    private static String getNewUniqueClassName(Set<String> existingKeys, String desiredKey) {
        if (!existingKeys.contains(desiredKey)) {
            return desiredKey;
        }
        String generated;
        int i = 0;
        do {
            generated = "g" + i++;
        } while (existingKeys.contains(generated));
        return generated;
    }

    private static Map<String, String> parseStyles(Document document) {
        Map<String, String> internalParsedClasses = new HashMap<>();
        for (Element styleEl : document.select("head style")) {
            String cssString = styleEl.html();
            Matcher matcher = CSS_PATTERN.matcher(cssString);
            while (matcher.find()) {
                String selector = matcher.group(1);
                String style = matcher.group(2);
                internalParsedClasses.put(selector, style);
            }
        }
        return internalParsedClasses;
    }

    private static Pair<String, String> calculateParentPackageName(String packageName) {
        int i = packageName.lastIndexOf(".");
        if (i != -1) {
            return Pair.of(packageName.substring(0, i), packageName.substring(i + 1));
        } else {
            return Pair.of(null, packageName);
        }
    }

    private static void changeInternalLinks(String currentPackage, Document document) {
        Elements allLinks = document.body().getElementsByTag("a");
        List<Element> links = allLinks.stream().filter(element -> element.hasAttr("href")).collect(Collectors.toList());
        for (Element aLinkElement : links) {
            String oldFileLink = aLinkElement.attr("href");
            Pair<String, SourceHtmlType> newUniqueLink = createNewUniqueLink(currentPackage, oldFileLink);
            if (newUniqueLink.getRight() == PACKAGE) {
                aLinkElement.attr("style", "font-weight: bold");
            }
            aLinkElement.attr("href", newUniqueLink.getLeft());
        }
    }

    private static Pair<String, SourceHtmlType> createNewUniqueLink(String currentPackage, String oldFileLink) {
        if (oldFileLink.startsWith("#")) {
            // It's already internal link, nothing to do here
            return Pair.of(oldFileLink, null);
        }
        SourceHtmlType sourceHtmlType = null;
        if (oldFileLink.endsWith(".java.html")) {
            oldFileLink = oldFileLink.substring(0, oldFileLink.length() - 10);
            sourceHtmlType = CLASS;
        } else if (oldFileLink.endsWith("/index.html")) {
            oldFileLink = oldFileLink.substring(0, oldFileLink.length() - 11);
            sourceHtmlType = PACKAGE;
        }

        String[] packageParts = currentPackage.split("-");
        String[] linkParts = oldFileLink.split("/");

        String[] fullPath = new String[packageParts.length + linkParts.length];
        System.arraycopy(packageParts, 0, fullPath, 0, packageParts.length);
        System.arraycopy(linkParts, 0, fullPath, packageParts.length, linkParts.length);

        int totalLength = fullPath.length;
        int i = 0;
        while (i < totalLength - 1) {
            if ("..".equals(fullPath[i + 1])) {
                if (0 < totalLength - i - 2) {
                    System.arraycopy(fullPath, i + 2, fullPath, i, totalLength - i - 2);
                    totalLength = totalLength - 2;
                    i = i - 1;
                    if (i < 0) {
                        // There is nothing left from first array, time to check just second
                        i = 0;
                    }
                }
            } else {
                i++;
            }
        }

        StringBuilder str = new StringBuilder("#");
        for (int j = 0; j < totalLength; j++) {
            str.append(fullPath[j]);
            str.append("-");
        }
        str.setLength(str.length() - 1);
        return Pair.of(str.toString(), sourceHtmlType);
    }

    private static String makePackageLink(Document document) {
        Elements preTag = document.body().getElementsByTag("pre");
        if (preTag.size() == 1) {
            Element element = preTag.get(0);
            if (element.childrenSize() >= 3) {
                Element packageNameSpan = element.child(2);
                String packageName = packageNameSpan.text();
                String uniquePackageId = packageUniqueIdFromName(packageName);
                packageNameSpan.html("<a href=\"#" + uniquePackageId + "\">" + packageName + "</a>");
                return uniquePackageId;
            }
        }
        return null;
    }

    private static String packageUniqueIdFromName(String packageName) {
        if (packageName == null || packageName.length() < 2) {
            return null;
        }
        if (packageName.endsWith(";")) {
            packageName = packageName.substring(0, packageName.length() - 1);
        }
        packageName = packageName.replace(".", "-");
        return packageName;
    }

    private static Document generateBaseHtmlTemplate() {
        String cssContent1 = ".page-break { " +
                "page-break-before: always; } \n";
        String cssContent2 = "@page {\n" +
                "            size: A4; /* Define the page size */\n" +
                "            margin: 30pt 30pt 50pt 30pt; /* Define the page margins */\n" +
                "            @bottom-center {\n" +
                "                content: counter(page)\n" +
                "            }\n" +
                "        }";
        String cssContent3 = "a, a:visited { text-decoration: none;\n" +
                "color: blue;\n" +
                "  } ";

        Document outputDocument = Jsoup.parse(
                "<html>" +
                        "<head></head>" +
                        "<body>" +
                        "<div id=\"toc\"></div>" +
                        "<div id=\"package-hierarchy\"></div>" +
                        "</body>" +
                        "</html>");
        outputDocument.head().appendElement("style").text(cssContent1);
        outputDocument.head().appendElement("style").text(cssContent2);
        outputDocument.head().appendElement("style").text(cssContent3);

        return outputDocument;
    }

    private static Document parseHTML(File htmlFile) {
        // Parse the HTML content using Jsoup
        try {
            return Jsoup.parse(htmlFile, StandardCharsets.UTF_8.displayName());
        } catch (IOException e) {
            throw new RuntimeException("Unable to parse html file", e);
        }
    }

    private static void saveHtmlAsFile(String fileName, String outerHtml) {
        try {
            FileOutputStream outputStream = new FileOutputStream(fileName);
            byte[] strToBytes = outerHtml.getBytes();
            outputStream.write(strToBytes);

            outputStream.close();
        } catch (Exception e) {
            System.err.println("Error wring html file");
            e.printStackTrace();
        }
    }

}
