package com.cloudbees.jenkins.support.api;

import com.cloudbees.jenkins.support.filter.FilteredInputStream;
import com.cloudbees.jenkins.support.filter.PasswordRedactor;
import com.cloudbees.jenkins.support.impl.SlaveLaunchLogs;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.function.Function;

/**
 * @see SlaveLaunchLogs
 */
public class LaunchLogsFileContent extends FileContent {

    public LaunchLogsFileContent(String name, String[] filterableParameters, File file, long maxSize) {
        super(name, filterableParameters, file, maxSize);
    }

    @Override
    protected InputStream getInputStream() throws IOException {
        Function<String, String> filter = PasswordRedactor.get()::redact;
        return new FilteredInputStream(new FileInputStream(file), Charset.defaultCharset(), filter);
    }

    @Override
    protected String getSimpleValueOrRedactedPassword(String value) {
        return PasswordRedactor.get().redact(value);
    }
}
