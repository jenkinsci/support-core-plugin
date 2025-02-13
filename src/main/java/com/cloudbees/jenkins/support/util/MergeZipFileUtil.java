package com.cloudbees.jenkins.support.util;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class MergeZipFileUtil {


    public static void mergeZipFiles(String zipFile1, String zipFile2, String outputZip) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(outputZip))) {
            addZipContentsToOutput(zipFile1, zos);
            addZipContentsToOutput(zipFile2, zos);
        }
    }

    private static void addZipContentsToOutput(String zipFile, ZipOutputStream zos) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                // Avoid duplicate file names by modifying if necessary
                String entryName = entry.getName();
                try {
                    zos.putNextEntry(new ZipEntry(entryName));
                }catch (ZipException e){
                    continue;
                }

                byte[] buffer = new byte[4096];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }

                zos.closeEntry();
                zis.closeEntry();
            }
        }
    }
}
