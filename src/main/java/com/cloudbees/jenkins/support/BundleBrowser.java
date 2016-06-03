package com.cloudbees.jenkins.support;

import java.io.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

//import org.apache.tools.zip;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipFile;
import org.apache.tools.zip.ZipOutputStream;
/*import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;*/

/**
 * Created by minudika on 5/29/16.
 */
public class BundleBrowser {
    private final String root = "./work/support";
    private List<File> zipFileList ;
    private ZipFile allInOneZipFile;

    public File[] getFileList() throws IOException {
        File rootFile = new File(root);
        return rootFile.listFiles();
    }
    public List<File> getZipFileList() throws IOException {
        List<File> zipFileList = new ArrayList<File>();
        for(File file: getFileList()){
            if(file!=null && file.getName().contains(".zip")){
                zipFileList.add(file);
            }
        }
        this.zipFileList = zipFileList;
        return zipFileList;
    }

    public List<ZipEntry> getZipContent(ZipFile zipFile){
        ArrayList<ZipEntry> zipFileList = new ArrayList<ZipEntry>();
        Enumeration<? extends ZipEntry> entries = zipFile.getEntries();

        while(entries.hasMoreElements()){
            ZipEntry entry = entries.nextElement();
            zipFileList.add(entry);
            System.out.println(entry.getName());
        }
        return zipFileList;
    }

    public List<File> getSelectedFiles(ArrayList<Integer> selectedFileIndices) throws IOException {
       List<File> list = new ArrayList<File>();
        List<File> zipFileList = getZipFileList();
        for(Integer i : selectedFileIndices){
            list.add(zipFileList.get(i));
        }
        return  list;
    }

    public void compressFiles(List<File>fileNames){
        byte[] buffer = new byte[1024*1024*10];
        try{
            String zipFile = "bundles.zip";
            FileOutputStream fos = new FileOutputStream(root+File.separator+zipFile);
            ZipOutputStream zos = new ZipOutputStream(fos);

            System.out.println("Output to Zip : " + zipFile);

            for(File file : fileNames){

                System.out.println("File Added : " + file);
                ZipEntry ze= new ZipEntry(file.getName());
                zos.putNextEntry(ze);

                FileInputStream in =
                        new FileInputStream(file);

                int len;
                while ((len = in.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }

                in.close();
            }

            zos.closeEntry();
            zos.close();

            System.out.println("Done");
        }catch(IOException ex){
            ex.printStackTrace();
        }
    }

   /* public static void main(String args[]) throws IOException {
        BundleBrowser bundleBrowser = new BundleBrowser();
        File[] fileList = bundleBrowser.getFileList();
        List<File> names = new ArrayList<File>();

        for (File file:fileList){
            if(file.getName().contains(".zip")){
               names.add(file);
            }
        }

        bundleBrowser.compressFiles(names);
    }*/
}
