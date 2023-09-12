package org.peyto.pdf.java.generator;

import org.peyto.pdf.java.generator.entity.JClass;
import org.peyto.pdf.java.generator.entity.JPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class FileCollector {

    private static final Logger log = LoggerFactory.getLogger(FileCollector.class);

    public static JPackage collectHTMLFiles(String rootFolderPath) {
        File rootFolder = new File(rootFolderPath);
        if (!rootFolder.exists() || !rootFolder.isDirectory()) {
            throw new RuntimeException("Invalid root folder path or the folder does not exist.");
        }
        JPackage rootPackage = new JPackage(rootFolder.getName());
        collectHTMLFilesRecursive(rootFolder, rootPackage);
        return rootPackage;
    }

    private static void collectHTMLFilesRecursive(File folder, JPackage parentPackage) {
        File[] files = folder.listFiles();

        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    JPackage jPackage = new JPackage(file.getName());
                    parentPackage.addChildPackage(jPackage);
                    // Recursively explore subfolders
                    collectHTMLFilesRecursive(file, jPackage);
                } else if (file.isFile() && file.getName().toLowerCase().endsWith(".html")) {
                    if (file.getName().toLowerCase().endsWith("index.html")) {
                        parentPackage.setHtmlFile(file);
                    } else {
                        // Add HTML files to the list
                        if (file.getName().endsWith(".java.html")) {
                            String javaClassName = file.getName().substring(0, file.getName().length() - 10);
                            JClass jClass = new JClass(javaClassName, file);
                            parentPackage.addChildClass(jClass);
                        } else if (file.getName().endsWith(".html")) {
                            // any other code file, e.g., js file, py file
                            String fileWithExtensionName = file.getName().substring(0, file.getName().length() - 5);
                            int lastExt = fileWithExtensionName.lastIndexOf(".");
                            if (lastExt != -1) {
                                String name = fileWithExtensionName.substring(0, lastExt - 1);
                                JClass jClass = new JClass(name, file);
                                parentPackage.addChildClass(jClass);
                            } else {
                                log.error("Unknown file without extension: {}", file.getAbsolutePath());
                            }
                        } else {
                            log.error("Unknown file without html suffix: {}", file.getAbsolutePath());
                        }
                    }
                }
            }
        }
    }


}
