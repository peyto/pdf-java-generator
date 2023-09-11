package org.peyto.pdf.java.generator;

import org.peyto.pdf.java.generator.entity.JClass;
import org.peyto.pdf.java.generator.entity.JPackage;

import java.io.File;

public class FileCollector {

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
                        } else {
                            System.err.println("Unknown file: " + file.getAbsolutePath());
                        }
                    }
                }
            }
        }
    }


}
