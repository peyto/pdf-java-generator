package org.peyto.pdf.java.generator.entity;

import java.io.File;

public class JClass implements HtmlFileSupplier {

    private final String name;
    private File htmlFile;

    public JClass(String name, File htmlFile) {
        this.name = name;
        this.htmlFile = htmlFile;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public File getHtmlFile() {
        return htmlFile;
    }

    @Override
    public SourceHtmlType sourceHtmlType() {
        return SourceHtmlType.CLASS;
    }
}
