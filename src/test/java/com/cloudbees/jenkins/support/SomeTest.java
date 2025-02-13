package com.cloudbees.jenkins.support;

import org.junit.Test;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class SomeTest {

    @Test
    public void mergeBundles() throws Exception {
        mergeZipFiles(
                "/var/folders/sp/6gfk22_90x775nnfkrgsbn200000gn/T/support-bundle/18d2da40-7441-4b2f-af2b-b49e7257ca98/asupport_2025-02-13_06.15.11.zip",
                "/var/folders/sp/6gfk22_90x775nnfkrgsbn200000gn/T/support-bundle/18d2da40-7441-4b2f-af2b-b49e7257ca98/ssupport_2025-02-13_06.15.11.zip",
                "/var/folders/sp/6gfk22_90x775nnfkrgsbn200000gn/T/support-bundle/18d2da40-7441-4b2f-af2b-b49e7257ca98/shit.zip"
        );
    }

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
                zos.putNextEntry(new ZipEntry(entryName));

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
