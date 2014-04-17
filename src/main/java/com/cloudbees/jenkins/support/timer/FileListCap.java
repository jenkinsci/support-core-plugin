package com.cloudbees.jenkins.support.timer;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashSet;

/**
 * @author stevenchristou
 *         Date: 4/2/14
 *         Time: 6:52 PM
 */
public class FileListCap {
    private final File folder;
    private final LinkedHashSet<File> files;
    private int size;

    public FileListCap(File folder, FilenameFilter filter, int size) {
        this.folder = folder;
        this.files = new LinkedHashSet<File>();
        this.size = size;

        File[] sortedFiles = folder.listFiles(filter);

        // Sorting in ascending order.
        Arrays.sort(sortedFiles, new Comparator<File>() {
            public int compare(File o1, File o2) {
                long l = o1.lastModified() - o2.lastModified();
                if (l<0)    return -1;
                if (l>0)    return 1;
                return 0;
            }
        });

        for (File f : sortedFiles)
            files.add(f);
    }

    public synchronized void add(File f) {
        if (size <= files.size()) {
            Iterator itr =files.iterator();
            while (size <= files.size()) {
                itr.remove();
            }
        }

        files.add(f);
    }

    public synchronized void touch(File f){
        files.remove(f);
        add(f);
    }
}
