package com.cloudbees.jenkins.support.filter;

import com.cloudbees.jenkins.support.SupportPlugin;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
public class AdditionalFromFileStopWords implements StopWords {

    private static final Logger LOGGER = Logger.getLogger(AdditionalFromFileStopWords.class.getName());

    /**
     * Name of the file containing additional user-provided stop words.
     */
    private static final String ADDITIONAL_STOP_WORDS_FILENAME = "additional-stop-words.txt";

    /**
     * Property to set to add <b>additional</b> stop words.
     * The location should point to a line separated file containing words. Each line is treated as a word.
     */
    static final String ADDITIONAL_STOP_WORDS_PROPERTY = ContentMappings.class.getName() + ".additionalStopWordsFile";

    @NonNull
    @Override
    public Set<String> getWords() {
        Set<String> words = new HashSet<>();
        String fileLocationFromProperty = System.getProperty(ADDITIONAL_STOP_WORDS_PROPERTY);
        String fileLocation = fileLocationFromProperty == null
                ? SupportPlugin.getRootDirectory() + "/" + ADDITIONAL_STOP_WORDS_FILENAME
                : fileLocationFromProperty;
        LOGGER.log(Level.FINE, "Attempting to load user provided stop words from ''{0}''.", fileLocation);
        File f = new File(fileLocation);
        if (f.exists()) {
            if (!f.canRead()) {
                LOGGER.log(
                        Level.WARNING,
                        "Could not load user provided stop words as " + fileLocation + " is not readable.");
            } else {
                try {
                    words.addAll(Files.readAllLines(Path.of(fileLocation), Charset.defaultCharset()));
                } catch (IOException ex) {
                    LOGGER.log(
                            Level.WARNING,
                            "Could not load user provided stop words. there was an error reading " + fileLocation,
                            ex);
                }
            }
        } else if (fileLocationFromProperty != null) {
            LOGGER.log(
                    Level.WARNING,
                    "Could not load user provided stop words as " + fileLocationFromProperty + " does not exists.");
        }
        return words;
    }
}
