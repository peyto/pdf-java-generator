package org.peyto.pdf.java.generator.entity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class JavaProject {

    private static final Logger log = LoggerFactory.getLogger(JavaProject.class);

    private final String basePackage;
    private final JPackage rootPackage;

    public JavaProject(JPackage rootPackage) {
        JPackage currentPackage = rootPackage;
        StringBuilder bpName = new StringBuilder();
        while (true) {
            if (currentPackage.getChildPackages().size() == 1 && currentPackage.getChildClasses().size() == 0) {
                if (currentPackage != rootPackage) {
                    // first package is usually folder name
                    bpName.append(currentPackage.getName()).append(".");
                }
                currentPackage = currentPackage.getChildPackages().get(0);
            } else {
                bpName.append(currentPackage.getName());
                this.rootPackage = currentPackage;
                break;
            }
        }
        if (bpName.length() == 0) {
            throw new RuntimeException("Something wrong, base package has no name");
        }
        this.basePackage = bpName.toString();
    }

    public String getBasePackage() {
        return basePackage;
    }

    public int getNumberOfPackages() {
        return getNumberOfPackages(rootPackage);
    }

    public int getNumberOfClasses() {
        return getNumberOfClasses(rootPackage);
    }

    public List<HtmlFileSupplier> getOrderedNodes() {
        List<HtmlFileSupplier> orderedNodes = new ArrayList<>();
        collectOrderedNodes(rootPackage, orderedNodes);
        return orderedNodes;
    }

    private int getNumberOfPackages(JPackage rootPackage) {
        int i = 1;
        for (JPackage childPackage : rootPackage.getChildPackages()) {
            i = i + getNumberOfPackages(childPackage);
        }
        return i;
    }

    private int getNumberOfClasses(JPackage rootPackage) {
        int i = rootPackage.getChildClasses().size();
        for (JPackage childPackage : rootPackage.getChildPackages()) {
            i = i + getNumberOfClasses(childPackage);
        }
        return i;
    }

    public void print() {
        log.info("JavaProject content:");
        for (String line : getTableOfContent()) {
            log.info(line);
        }
    }

    public List<String> getTableOfContent() {
        List<String> tableOfContent = new ArrayList<>();
        tableOfContent.add(basePackage);
        buildTableOfContent(rootPackage, 1, tableOfContent);
        return tableOfContent;
    }

    private void buildTableOfContent(JPackage jPackage, int indentLevel, List<String> tableOfContent) {
        StringBuilder indentation = new StringBuilder();
        for (int i = 0; i < indentLevel; i++) {
            indentation.append("    "); // 4 spaces per level
        }

        for (JPackage childPackage : jPackage.getChildPackages()) {
            tableOfContent.add(indentation + childPackage.getName());
            buildTableOfContent(childPackage, indentLevel + 1, tableOfContent);
        }
        for (JClass jClass : jPackage.getChildClasses()) {
            tableOfContent.add(indentation + "  " + jClass.getName());
        }
    }

    private void collectOrderedNodes(JPackage jPackage, List<HtmlFileSupplier> orderedNodes) {
        orderedNodes.add(jPackage);
        List<JPackage> childPackages = jPackage.getChildPackages();
        List<JClass> childClasses = jPackage.getChildClasses();
        childPackages.sort(Comparator.comparing(JPackage::getName));
        childClasses.sort(Comparator.comparing(JClass::getName));
        for (JPackage childPackage : childPackages) {
            collectOrderedNodes(childPackage, orderedNodes);
        }
        orderedNodes.addAll(childClasses);
    }
}
