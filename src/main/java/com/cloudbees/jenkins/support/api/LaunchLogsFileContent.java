package com.cloudbees.jenkins.support.api;

import com.cloudbees.jenkins.support.filter.PasswordRedactor;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.io.IOUtils;

public class LaunchLogsFileContent extends FileContent {

    public LaunchLogsFileContent(String name, String[] filterableParameters, File file, long maxSize) {
        super(name, filterableParameters, file, maxSize);
    }

    @Override
    protected InputStream getInputStream() throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            List<String> strings = IOUtils.readLines(inputStream, Charset.defaultCharset());
            byte[] bytes = strings.stream()
                    .map(line -> PasswordRedactor.get().redact(line))
                    .collect(Collectors.joining("\n", "", "\n"))
                    .getBytes(Charset.defaultCharset());
            return new ByteArrayInputStream(bytes);
        }
    }

    @Override
    protected String getSimpleValueOrRedactedPassword(String value) {
        return PasswordRedactor.get().redact(value);
    }
}
