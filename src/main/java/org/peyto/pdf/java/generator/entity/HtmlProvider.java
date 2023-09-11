package org.peyto.pdf.java.generator.entity;

import java.io.File;

public interface HtmlProvider {

    String getName();

    File getHtmlFile();

    SourceHtmlType sourceHtmlType();

    enum SourceHtmlType {
        PACKAGE,
        CLASS
    }
}
