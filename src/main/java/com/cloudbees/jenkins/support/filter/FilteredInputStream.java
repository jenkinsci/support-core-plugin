package com.cloudbees.jenkins.support.filter;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.function.Function;

/**
 * A custom {@link InputStream} that filters lines of text read from an underlying {@link InputStream} using a provided
 * mapping function. Each line read from the input stream is processed by the function before being returned, allowing
 * for modifications such as redaction or transformation of the line content.
 *
 * @author Basil Crow
 */
public class FilteredInputStream extends InputStream {

    private final Function<String, String> filter;
    private final Charset encoding;
    private final BufferedReader reader;
    private ByteArrayInputStream buffer;

    /**
     * Constructs a filtered stream using the provided filter and encoding.
     *
     * @param is Input stream to read line content from
     * @param encoding Character set to use for decoding and encoding bytes read from this stream
     * @param filter Filter to apply to lines read from this stream
     */
    public FilteredInputStream(
            @NonNull InputStream is, @NonNull Charset encoding, @NonNull Function<String, String> filter) {
        this.encoding = encoding;
        this.reader = new BufferedReader(new InputStreamReader(is, encoding));
        this.filter = filter;
    }

    @Override
    public int read() throws IOException {
        if (buffer == null) {
            String line = reader.readLine();
            if (line != null) {
                line = filter.apply(line);
                line += System.lineSeparator();
                buffer = new ByteArrayInputStream(line.getBytes(encoding));
            } else {
                return -1;
            }
        }
        int ch = buffer.read();
        if (ch != -1) {
            return ch;
        } else {
            buffer = null;
            return read();
        }
    }

    @Override
    public void close() throws IOException {
        reader.close();
    }
}
