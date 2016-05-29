package com.cloudbees.jenkins.support;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by minudika on 5/29/16.
 */
public class BundleBrowser {
    private final String root = "./work/support";

    public File[] getFileList() throws IOException {
        File rootFile = new File(root);
        return rootFile.listFiles();
    }

    public List<ZipEntry> getZipContent(ZipFile zipFile){
        ArrayList<ZipEntry> zipFileList = new ArrayList<ZipEntry>();
        Enumeration<? extends ZipEntry> entries = zipFile.entries();

        while(entries.hasMoreElements()){
            ZipEntry entry = entries.nextElement();
            zipFileList.add(entry);
            System.out.println(entry.getName());
        }
        return zipFileList;
    }

    public static void main(String args[]) throws IOException {
        BundleBrowser bundleBrowser = new BundleBrowser();
        File[] fileList = bundleBrowser.getFileList();

        for (File file:fileList){
            if(file.getName().contains(".zip")){
                ZipFile zipFile = new ZipFile(file);
                bundleBrowser.getZipContent(zipFile);
                System.out.println("================================");
            }
        }
    }
}
