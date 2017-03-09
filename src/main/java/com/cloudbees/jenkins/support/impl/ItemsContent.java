package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.model.Items;
import com.cloudbees.jenkins.support.util.Helper;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.model.Job;
import jenkins.model.Jenkins;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

class ItemsContent {
    Items generate() {
        Items items = new Items();
        final Jenkins jenkins = Helper.getActiveInstance();
        // RunMap.createDirectoryFilter protected, so must do it by hand:
        DateFormat BUILD_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        for (Item i : jenkins.getAllItems()) {
            String key = i.getClass().getName();
            Integer cnt = items.getContainerCounts().get(key);
            items.getContainerCounts().put(key, cnt == null ? 1 : cnt + 1);
            if (i instanceof Job) {
                Job<?,?> j = (Job) i;
                // too expensive: int builds = j.getBuilds().size();
                int builds = 0;
                // protected access: File buildDir = j.getBuildDir();
                File buildDir = jenkins.getBuildDirFor(j);
                boolean newFormat = new File(buildDir, "legacyIds").isFile(); // JENKINS-24380
                File[] buildDirs = buildDir.listFiles();
                if (buildDirs != null) {
                    for (File d : buildDirs) {
                        String name = d.getName();
                        if (newFormat) {
                            try {
                                Integer.parseInt(name);
                                if (d.isDirectory()) {
                                    builds++;
                                }
                            } catch (NumberFormatException x) {
                                // something else
                            }
                        } else /* legacy format */if (mayBeDate(name)) {
                            // check for real
                            try {
                                BUILD_FORMAT.parse(name);
                                if (d.isDirectory()) {
                                    builds++;
                                }
                            } catch (ParseException x) {
                                // symlink etc., ignore
                            }
                        }
                    }
                }
                items.getJobTotal().add(builds);
                Items.Stats s = items.getJobStats().get(key);
                if (s == null) {
                    items.getJobStats().put(key, s = new Items.Stats());
                }
                s.add(builds);
            }
            if (i instanceof ItemGroup) {
                Items.Stats s = items.getContainerStats().get(key);
                if (s == null) {
                    items.getContainerStats().put(key, s = new Items.Stats());
                }
                s.add(((ItemGroup) i).getItems().size());
            }
        }

        return items;
    }

    /**
     * A pre-check to see if a string is a build timestamp formatted date.
     *
     * @param s the string.
     * @return {@code true} if it is likely that the string will parse as a build timestamp formatted date.
     */
    static boolean mayBeDate(String s) {
        if (s == null || s.length() != "yyyy-MM-dd_HH-mm-ss".length()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            switch (s.charAt(i)) {
                case '-':
                    switch (i) {
                        case 4:
                        case 7:
                        case 13:
                        case 16:
                            break;
                        default:
                            return false;
                    }
                    break;
                case '_':
                    if (i != 10) {
                        return false;
                    }
                    break;
                case '0':
                case '1':
                    switch (i) {
                        case 4: // -
                        case 7: // -
                        case 10: // _
                        case 13: // -
                        case 16: // -
                            return false;
                        default:
                            break;
                    }
                    break;
                case '2':
                    switch (i) {
                        case 4: // -
                        case 5: // months 0-1
                        case 7: // -
                        case 10: // _
                        case 13: // -
                        case 16: // -
                            return false;
                        default:
                            break;
                    }
                    break;
                case '3':
                    switch (i) {
                        case 0: // year will safely begin with digit 2 for next 800-odd years
                        case 4: // -
                        case 5: // months 0-1
                        case 7: // -
                        case 10: // _
                        case 11: // hours 0-2
                        case 13: // -
                        case 16: // -
                            return false;
                        default:
                            break;
                    }
                    break;
                case '4':
                case '5':
                    switch (i) {
                        case 0: // year will safely begin with digit 2 for next 800-odd years
                        case 4: // -
                        case 5: // months 0-1
                        case 7: // -
                        case 8: // days 0-3
                        case 10: // _
                        case 11: // hours 0-2
                        case 13: // -
                        case 16: // -
                            return false;
                        default:
                            break;
                    }
                    break;
                case '6':
                case '7':
                case '8':
                case '9':
                    switch (i) {
                        case 0: // year will safely begin with digit 2 for next 800-odd years
                        case 4: // -
                        case 5: // months 0-1
                        case 7: // -
                        case 8: // days 0-3
                        case 10: // _
                        case 11: // hours 0-2
                        case 13: // -
                        case 14: // minutes 0-5
                        case 16: // -
                        case 17: // seconds 0-5
                            return false;
                        default:
                            break;
                    }
                    break;
                default:
                    return false;
            }
        }
        return true;
    }
}
