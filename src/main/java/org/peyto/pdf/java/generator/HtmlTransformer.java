package org.peyto.pdf.java.generator;

import org.apache.commons.lang3.tuple.Pair;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.peyto.pdf.java.generator.entity.HtmlFileSupplier;
import org.peyto.pdf.java.generator.entity.JavaProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.peyto.pdf.java.generator.entity.HtmlFileSupplier.SourceHtmlType.CLASS;
import static org.peyto.pdf.java.generator.entity.HtmlFileSupplier.SourceHtmlType.PACKAGE;

public class HtmlTransformer {

    private static final Pattern CSS_PATTERN = Pattern.compile("\\.\\s*(.*?)\\s*\\{\\s*(.*?)\\s*\\}");

    private static final String PAGE_BREAK_DIV_TEMPLATE = "<div class=\"page-break\"></div> \n";

    public static String processHTMLFiles(JavaProject parsedJavaProject) throws IOException {
        Document outputDocument = generateBaseHtmlTemplate();

        List<Pair<String, HtmlFileSupplier.SourceHtmlType>> tableOfContent = new ArrayList<>();
        Map<String, String> cssCodeStyles = new HashMap<>();

        Element packageDiv = outputDocument.body().getElementById("package-hierarchy");
        for (HtmlFileSupplier htmlFileSupplier : parsedJavaProject.getOrderedNodes()) {
            Pair<String, Element> parsedFile = parseHtmlCodeFileAndTransform(parsedJavaProject.getBasePackage(), htmlFileSupplier, cssCodeStyles);
            if (htmlFileSupplier.sourceHtmlType() == PACKAGE) {
                tableOfContent.add(Pair.of(parsedFile.getLeft(), PACKAGE));
                packageDiv.append(PAGE_BREAK_DIV_TEMPLATE);
                packageDiv.append(parsedFile.getRight().html());
            } else {
                tableOfContent.add(Pair.of(parsedFile.getLeft(), CLASS));
                outputDocument.body().append(PAGE_BREAK_DIV_TEMPLATE);
                outputDocument.body().append(parsedFile.getRight().html());
            }
        }

        for (Map.Entry<String, String> style : cssCodeStyles.entrySet()) {
            outputDocument.head().appendElement("style").text("." + style.getKey() + " { " + style.getValue() + " }");
        }

        generateTableOfContent(outputDocument.body().getElementById("toc"), parsedJavaProject.getBasePackage(), tableOfContent);

        return outputDocument.outerHtml();
    }

    /**
     * <ul>Main logic of parsing file and transforming to unified format:</ul>
     * <li> make internal links</li>
     * <li> make common styles</li>
     *
     * @return Pair of (unique file id, parsed and transformed html document body)
     */
    private static Pair<String, Element> parseHtmlCodeFileAndTransform(String basePackage, HtmlFileSupplier htmlFileSupplier, Map<String, String> cssCodeStyles) {
        Document document = parseHTML(htmlFileSupplier.getHtmlFile());
        String currentPackageId;
        if (htmlFileSupplier.sourceHtmlType() == CLASS) {
            currentPackageId = makePackageLink(document);
            String fullUniqueName = createClassAnchor(htmlFileSupplier, document, currentPackageId);
            transformInternalLinks(currentPackageId, document, false);
            transformInternalCssCodeClasses(document, cssCodeStyles);
            return Pair.of(fullUniqueName, document.body());
        } else if (htmlFileSupplier.sourceHtmlType() == PACKAGE) {
            String packageName = document.getElementsByTag("title").get(0).text();
            currentPackageId = transformPackageContent(basePackage, document, packageName);
            transformInternalLinks(currentPackageId, document, true);
            return Pair.of(currentPackageId, document.body());
        } else {
            throw new RuntimeException("Unexpected source file: " + htmlFileSupplier.sourceHtmlType());
        }
    }

    private static String createClassAnchor(HtmlFileSupplier htmlFileSupplier, Document document, String currentPackageId) {
        String fullUniqueName = currentPackageId + "-" + htmlFileSupplier.getName();

        Element spanWithName = document.select("table center").first();
        if (spanWithName == null) {
            throw new RuntimeException("Wrong html structure");
        }
        spanWithName.html("<a name=\"" + fullUniqueName + "\">" + spanWithName.html() + "</a>");
        return fullUniqueName;
    }

    private static String transformPackageContent(String basePackage, Document document, String packageName) {
        Pair<String, String> parentPackageAndCurrentPackageNames = splitParentPackageAndCurrent(packageName);
        String currentPackageId = packageUniqueIdFromName(packageName);

        String parentPackageId = parentPackageAndCurrentPackageNames.getLeft() == null || parentPackageAndCurrentPackageNames.getLeft().length() < basePackage.length() ?
                "toc" :
                packageUniqueIdFromName(parentPackageAndCurrentPackageNames.getLeft());
        document.body().prepend("<a " +
                "name=\"" + currentPackageId + "\" " +
                "href=\"#" + parentPackageId + "\">" +
                "<span>" + parentPackageAndCurrentPackageNames.getLeft() + "</span></a>." + parentPackageAndCurrentPackageNames.getRight() + "<br><br>");
        Elements allLinks = document.body().getElementsByTag("a");
        List<Element> links = allLinks.stream().filter(element -> element.hasAttr("href")).collect(Collectors.toList());
        for (Element aLinkElement : links) {
            String linkText = aLinkElement.text();
            aLinkElement.html("<span>&nbsp;&nbsp;&nbsp;&nbsp;" + linkText + "</span>");
        }
        return currentPackageId;
    }

    private static void transformInternalLinks(String currentPackage, Document document, boolean highlightPackages) {
        Elements allLinks = document.body().getElementsByTag("a");
        List<Element> links = allLinks.stream().filter(element -> element.hasAttr("href")).collect(Collectors.toList());
        for (Element aLinkElement : links) {
            String oldFileLink = aLinkElement.attr("href");
            Pair<String, HtmlFileSupplier.SourceHtmlType> newUniqueLink = createNewUniqueLink(currentPackage, oldFileLink);
            if (highlightPackages && newUniqueLink.getRight() == PACKAGE) {
                aLinkElement.attr("style", "font-weight: bold");
            }
            aLinkElement.attr("href", newUniqueLink.getLeft());
        }
    }

    private static void transformInternalCssCodeClasses(Document document, Map<String, String> cssCodeStyles) {
        // s0 -> color, s1 -> color
        Map<String, String> internalParsedClasses = parseExistingStyles(document);
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

    private static void generateTableOfContent(Element tocDiv, String basePackage, List<Pair<String, HtmlFileSupplier.SourceHtmlType>> tableOfContent) {
        for (Pair<String, HtmlFileSupplier.SourceHtmlType> entry : tableOfContent) {
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

    private static Map<String, String> parseExistingStyles(Document document) {
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

    private static Pair<String, String> splitParentPackageAndCurrent(String packageName) {
        int i = packageName.lastIndexOf(".");
        if (i != -1) {
            return Pair.of(packageName.substring(0, i), packageName.substring(i + 1));
        } else {
            return Pair.of(null, packageName);
        }
    }

    private static Pair<String, HtmlFileSupplier.SourceHtmlType> createNewUniqueLink(String currentPackage, String oldFileLink) {
        if (oldFileLink.startsWith("#") || oldFileLink.startsWith("http")) {
            // It's already internal link, nothing to do here
            return Pair.of(oldFileLink, null);
        }
        HtmlFileSupplier.SourceHtmlType sourceHtmlType = null;
        if (oldFileLink.endsWith("/index.html")) {
            oldFileLink = oldFileLink.substring(0, oldFileLink.length() - 11);
            sourceHtmlType = PACKAGE;
        } else if (oldFileLink.endsWith(".java.html")) {
            oldFileLink = oldFileLink.substring(0, oldFileLink.length() - 10);
            sourceHtmlType = CLASS;
        } else if (oldFileLink.endsWith(".html")) {
            oldFileLink = oldFileLink.substring(0, oldFileLink.length() - 5);
            int lastExt = oldFileLink.lastIndexOf(".");
            if (lastExt != -1) {
                oldFileLink = oldFileLink.substring(0, lastExt - 1);
                sourceHtmlType = CLASS;
            }
        }
        if (sourceHtmlType == null) {
            // We are not prepared for such file type, let's not try to parse it
            return Pair.of(oldFileLink, null);
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

        // It's internal link now
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
}
