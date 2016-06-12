/*
 * The MIT License
 *
 * Copyright (c) 2013, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package com.cloudbees.jenkins.support;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

//import org.apache.tools.zip;
import net.sf.json.JSON;
import net.sf.json.JSONObject;
import org.apache.tools.zip.ZipEntry;
import org.apache.tools.zip.ZipFile;
import org.apache.tools.zip.ZipOutputStream;

public class BundleBrowser {
    private final Logger logger = Logger.getLogger(BundleBrowser.class.getName());
    private final String root = "./work/support";
    private List<File> zipFileList ;
    private static BundleBrowser bundleBrowser = new BundleBrowser();

    private BundleBrowser(){}

    public static BundleBrowser getBundleBrowser(){
        return bundleBrowser;
    }

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

    public List<ZipEntry> getZipContent(File file) throws IOException {
        ZipFile zipFile = new ZipFile(file);
        ArrayList<ZipEntry> zipFileList = new ArrayList<ZipEntry>();
        Enumeration<? extends ZipEntry> entries = zipFile.getEntries();

        while(entries.hasMoreElements()){
            ZipEntry entry = entries.nextElement();
            zipFileList.add(entry);
        }
        return zipFileList;
    }

    public List<File> getSelectedFiles(List<Integer> selectedFileIndices) throws IOException {
       List<File> list = new ArrayList<File>();
       // List<File> zipFileList = getZipFileList();
        for(Integer i : selectedFileIndices){
            list.add(zipFileList.get(i));
        }
        return  list;
    }

    public void deleteBundle(List<Integer>indices){
        for(Integer i : indices){
            zipFileList.get(i).delete();
            zipFileList.remove(i);
        }
    }

    public String getJsonFileList() throws IOException {
        String json="{'core' : { " +
                "'data' : [" +
                "";
      /*{ 'core' : {
            'data' : [
            { "id" : "ajson1", "parent" : "#", "text" : "Simple root node" },
            { "id" : "ajson2", "parent" : "#", "text" : "Root node 2" },
            { "id" : "ajson3", "parent" : "ajson2", "text" : "Child 1" },
            { "id" : "ajson4", "parent" : "ajson2", "text" : "Child 2" },
            ]
        } }*/

        JSONObject jsonObject = new JSONObject();
        Map map;
        ArrayList<Map> content = new ArrayList<Map>();
        getZipFileList();
        for(File file:zipFileList){
            map=new HashMap<String,String>();
            map.put("id",file.getName());
            map.put("parent","#");
            map.put("text",file.getName());
            content.add(map);

            /*List<ZipEntry> zipContent=getZipContent(new ZipFile(file));
            for(ZipEntry entry : zipContent){
                String array[] = entry.toString().split("/");
                map = new HashMap();
                map=new HashMap<String,String>();
                map.put("id",entry.getName());
                map.put("parent",file.getName());
                map.put("text",entry.getName());
                content.add(map);

            }
            content.add(map);*/

        }
        for(Map zipEntry:content){
            String entry = "{ \"id\" : \""+zipEntry.get("id")+ "\", " +
                    "\"parent\" : \""+zipEntry.get("parent")+"\", " +
                    "\"text\" : \""+zipEntry.get("text")+"\" },";
            json+=entry;
        }

        json+="]}}";

        return json;
    }

    public void doScheduledPurge() throws IOException {
        List<File> list = getZipFileList();
        int nDays = getPurgingAge();
        for(File file : list){
            Date lastModifiedDate = new Date(file.lastModified());
            Date currentDate = new Date(System.currentTimeMillis());
            long lastModifiedTime = lastModifiedDate.getTime()/(24*3600*1000);
            long currentTime = currentDate.getTime()/(24*3600*1000);

            if(currentTime - lastModifiedTime > nDays){
                file.delete();
            }
        }
    }

    public int getPurgingAge(){
        int numberOfDays = 0;
        String line = null;
        try {

            File file = new File("config.txt");

            if (file.createNewFile()){
                FileWriter fileWriter = new FileWriter(file);
                fileWriter.append("100");
                fileWriter.close();
                return 100;
            }else{
                FileReader fileReader = new FileReader(file);
                BufferedReader bufferedReader = new BufferedReader(fileReader);
                while((line = bufferedReader.readLine() )!= null){
                    numberOfDays = Integer.parseInt(line.trim());
                    return numberOfDays;
                }
            }
        } catch (IOException e) {
            logger.log(Level.FINE, e.getMessage(), e);
        }
        return numberOfDays;
    }
}
