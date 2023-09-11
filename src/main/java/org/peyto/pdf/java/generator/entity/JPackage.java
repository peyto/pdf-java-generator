package org.peyto.pdf.java.generator.entity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class JPackage implements HtmlProvider {

    private final String name;
    private File htmlFile;

    private final List<JClass> childClasses = new ArrayList<>();
    private final List<JPackage> childPackages = new ArrayList<>();

    public JPackage(String name) {
        this.name = name;
    }

    @Override
    public String getName() {
        // TODO That's not correct
        return name;
    }

    @Override
    public File getHtmlFile() {
        return htmlFile;
    }

    @Override
    public SourceHtmlType sourceHtmlType() {
        return SourceHtmlType.PACKAGE;
    }

    public List<JClass> getChildClasses() {
        return childClasses;
    }

    public List<JPackage> getChildPackages() {
        return childPackages;
    }

    public void setHtmlFile(File htmlFile) {
        this.htmlFile = htmlFile;
    }

    public void addChildClass(JClass jClass) {
        childClasses.add(jClass);
    }

    public void addChildPackage(JPackage jPackage) {
        childPackages.add(jPackage);
    }
}
