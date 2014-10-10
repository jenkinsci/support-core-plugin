package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportLogFormatter;
import com.cloudbees.jenkins.support.SupportPlugin;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.FileContent;
import com.cloudbees.jenkins.support.api.PrintedContent;
import com.cloudbees.jenkins.support.api.SupportContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.SuppressWarnings;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.logging.LogRecorder;
import hudson.model.Computer;
import hudson.model.Node;
import hudson.model.PeriodicWork;
import hudson.remoting.Callable;
import hudson.remoting.Pipe;
import hudson.remoting.VirtualChannel;
import hudson.security.Permission;
import hudson.slaves.SlaveComputer;
import hudson.util.DaemonThreadFactory;
import hudson.util.ExceptionCatchingThreadFactory;
import hudson.util.IOUtils;
import hudson.util.RingBufferLogHandler;
import hudson.util.io.ReopenableFileOutputStream;
import hudson.util.io.ReopenableRotatingFileOutputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import jenkins.model.Jenkins;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang.StringUtils;

/**
 * Log files from the different nodes
 */
@Extension(ordinal = 100.0) // put this first as largest content and can let the slower ones complete
public class JenkinsLogs extends Component {

    private static final Logger LOGGER = Logger.getLogger(JenkinsLogs.class.getName());
    private final Map<String,LogRecorder> logRecorders = Jenkins.getInstance().getLog().logRecorders;
    private final File customLogs = new File(new File(Jenkins.getInstance().getRootDir(), "logs"), "custom");

    @NonNull
    @Override
    public Set<Permission> getRequiredPermissions() {
        return Collections.singleton(Jenkins.ADMINISTER);
    }

    @NonNull
    @Override
    public String getDisplayName() {
        return "Log Recorders";
    }

    @Override
    public void start(@NonNull SupportContext context) {
        Logger.getLogger("").addHandler(new CustomHandler());
    }

    @Override
    public void addContents(@NonNull Container result) {
        final Formatter formatter = new SupportLogFormatter();
        result.add(new PrintedContent("nodes/master/logs/jenkins.log") {
            @Override
            protected void printTo(PrintWriter out) throws IOException {
                List<LogRecord> records = new ArrayList<LogRecord>(Jenkins.logRecords);
                for (ListIterator<LogRecord> iterator = records.listIterator(records.size());
                     iterator.hasPrevious(); ) {
                    LogRecord logRecord = iterator.previous();
                    out.print(formatter.format(logRecord));
                }
                out.flush();
            }
        });
        result.add(new PrintedContent("nodes/master/logs/all_memory_buffer.log") {
            @Override
            protected void printTo(PrintWriter out) throws IOException {
                for (LogRecord logRecord : SupportPlugin.getInstance().getAllLogRecords()) {
                    out.print(formatter.format(logRecord));
                }
                out.flush();
            }
        });
        for (File f : Jenkins.getInstance().getRootDir().listFiles(ROTATED_LOGFILE_FILTER)) {
            result.add(new FileContent("other-logs/" + f.getName(), f));
        }
        final File supportDir = new File(Jenkins.getInstance().getRootDir(), "support");
        for (File file : supportDir.listFiles(new LogFilenameFilter())){
            result.add(new FileContent("nodes/master/logs/" + file.getName(), file));
        }
        for (Map.Entry<String, LogRecorder> entry : logRecorders.entrySet()) {
            String name = entry.getKey();
            String entryName = "nodes/master/logs/custom/" + name + ".log";
            File storedFile = new File(customLogs, name + ".log");
            if (storedFile.isFile()) {
                result.add(new FileContent(entryName, storedFile));
            } else {
                // Was not stored for some reason; fine, just load the memory buffer.
                final LogRecorder recorder = entry.getValue();
                result.add(new PrintedContent(entryName) {
                    @Override
                    protected void printTo(PrintWriter out) throws IOException {
                        for (LogRecord logRecord : recorder.getLogRecords()) {
                            out.print(formatter.format(logRecord));
                        }
                        out.flush();
                    }
                });
            }
        }

        File cacheDir = new File(supportDir, "cache");
        final boolean needHack = SlaveLogFetcher.isRequired();
        List<java.util.concurrent.Callable<List<FileContent>>> tasks = new ArrayList<java.util.concurrent
                .Callable<List<FileContent>>>();
        for (final Node node : Jenkins.getInstance().getNodes()) {
            String cacheKey = Util.getDigestOf(node.getNodeName() + ":" + node.getRootPath());
            final File nodeCacheDir = new File(cacheDir, StringUtils.right(cacheKey, 8));
            if (node.toComputer() instanceof SlaveComputer) {
                result.add(
                        new PrintedContent("nodes/slave/" + node.getNodeName() + "/jenkins.log") {
                            @Override
                            protected void printTo(PrintWriter out) throws IOException {
                                Computer computer = node.toComputer();
                                if (computer == null) {
                                    out.println("N/A");
                                } else {
                                    try {
                                        List<LogRecord> records = needHack
                                                ? SlaveLogFetcher.getLogRecords(computer)
                                                : computer.getLogRecords();
                                        for (ListIterator<LogRecord> iterator = records.listIterator(records.size());
                                             iterator.hasPrevious(); ) {
                                            LogRecord logRecord = iterator.previous();
                                            out.print(formatter.format(logRecord));
                                        }
                                    } catch (Throwable e) {
                                        out.println();
                                        e.printStackTrace(out);
                                    }
                                }
                                out.flush();

                            }
                        }
                );
            }
            final FilePath rootPath = node.getRootPath();
            if (rootPath != null) {
                tasks.add(new java.util.concurrent.Callable<List<FileContent>>(){
                    public List<FileContent> call() throws Exception {
                        List<FileContent> result = new ArrayList<FileContent>();
                        FilePath supportPath = rootPath.child("support");
                        if (supportPath.isDirectory()) {
                            final Map<String, File> logFiles = getLogFiles(supportPath, nodeCacheDir);
                            for (Map.Entry<String, File> entry : logFiles.entrySet()) {
                                result.add(new FileContent(
                                                "nodes/slave/" + node.getNodeName() + "/logs/" + entry.getKey(),
                                                entry.getValue())
                                );
                            }
                        }
                        return result;
                    }
                });
            }
            result.add(new PrintedContent("nodes/slave/" + node.getNodeName() + "/logs/all_memory_buffer.log") {
                @Override
                protected void printTo(PrintWriter out) throws IOException {
                    try {
                        for (LogRecord logRecord : SupportPlugin.getInstance().getAllLogRecords(node)) {
                            out.print(formatter.format(logRecord));
                        }
                    } catch (InterruptedException e) {
                        e.printStackTrace(out);
                    }
                    out.flush();
                }
            });
            final Computer computer = node.toComputer();
            if (computer != null) {
                boolean haveLogFile;
                try {
                    Method getLogFile = computer.getClass().getMethod("getLogFile");
                    getLogFile.setAccessible(true);
                    File logFile = (File) getLogFile.invoke(computer);
                    if (logFile != null && logFile.isFile()) {
                        result.add(new FileContent("nodes/slave/" + node.getNodeName() + "/launch.log", logFile));
                        haveLogFile = true;
                    } else {
                        haveLogFile = false;
                    }
                } catch (ClassCastException e) {
                    haveLogFile = false;
                } catch (InvocationTargetException e) {
                    haveLogFile = false;
                } catch (NoSuchMethodException e) {
                    haveLogFile = false;
                } catch (IllegalAccessException e) {
                    haveLogFile = false;
                }
                if (!haveLogFile) {
                    // fall back to surefire method... even if potential memory hog
                    result.add(
                            new PrintedContent("nodes/slave/" + node.getNodeName() + "/launch.log") {
                                @Override
                                protected void printTo(PrintWriter out) throws IOException {
                                    out.println(computer.getLog());
                                    out.flush();
                                }
                            }
                    );

                }
            }
        }
        if (!tasks.isEmpty()) {
            ExecutorService service = Executors.newFixedThreadPool(
                    Math.max(1, Math.min(Runtime.getRuntime().availableProcessors() * 2, tasks.size())),
                    new ExceptionCatchingThreadFactory(new DaemonThreadFactory())
            );
            try {
                long expiresNanoTime =
                        System.nanoTime() + TimeUnit.SECONDS.toNanos(SupportPlugin.REMOTE_OPERATION_CACHE_TIMEOUT_SEC);
                for (java.util.concurrent.Future<List<FileContent>> r : service
                        .invokeAll(tasks, SupportPlugin.REMOTE_OPERATION_CACHE_TIMEOUT_SEC,
                                TimeUnit.SECONDS)) {
                    try {
                        for (FileContent c : r
                                .get(Math.max(1, expiresNanoTime - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                            result.add(c);
                        }
                    } catch (ExecutionException e) {
                        LOGGER.log(Level.WARNING, "Could not retrieve some of the remote node extra logs", e);
                    } catch (TimeoutException e) {
                        LOGGER.log(Level.WARNING, "Could not retrieve some of the remote node extra logs", e);
                        r.cancel(false);
                    }
                }
            } catch (InterruptedException e) {
                LOGGER.log(Level.WARNING, "Could not retrieve some of the remote node extra logs", e);
            } finally {
                service.shutdown();
            }
        }
    }

    private static class SlaveLogFetcher implements Callable<List<LogRecord>, RuntimeException> {

        public static boolean isRequired() {
            try {
                SlaveComputer.class.getClassLoader().loadClass(SlaveComputer.class.getName() + "$SlaveLogFetcher");
                return false;
            } catch (ClassNotFoundException e) {
                return true;
            }
        }

        public List<LogRecord> call() throws RuntimeException {
            try {
                Class<?> aClass =
                        SlaveComputer.class.getClassLoader().loadClass(SlaveComputer.class.getName() + "$LogHolder");
                Field logHandler = aClass.getDeclaredField("SLAVE_LOG_HANDLER");
                boolean accessible = logHandler.isAccessible();
                try {
                    if (!accessible) {
                        logHandler.setAccessible(true);
                    }
                    Object instance = logHandler.get(null);
                    if (instance instanceof RingBufferLogHandler) {
                        RingBufferLogHandler handler = (RingBufferLogHandler) instance;
                        return new ArrayList<LogRecord>(handler.getView());
                    }
                } finally {
                    if (!accessible) {
                        logHandler.setAccessible(accessible);
                    }
                }
                throw new RuntimeException("Could not retrieve logs");
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }

        public static List<LogRecord> getLogRecords(Computer computer) throws IOException, InterruptedException {
            VirtualChannel channel = computer.getChannel();
            if (channel == null) {
                return Collections.emptyList();
            } else {
                return channel.call(new SlaveLogFetcher());
            }
        }
    }

    @SuppressWarnings(value="SIC_INNER_SHOULD_BE_STATIC_NEEDS_THIS", justification="customLogs is not static, so this is a bug in FB")
    private final class LogFile {
        private final ReopenableFileOutputStream stream;
        private final Handler handler;
        private int count;
        @SuppressWarnings(value="RV_RETURN_VALUE_IGNORED_BAD_PRACTICE", justification="if mkdirs fails, will just get a stack trace later")
        LogFile(String name) throws IOException {
            customLogs.mkdirs();
            stream = new ReopenableRotatingFileOutputStream(new File(customLogs, name + ".log"), 9);
            // TODO there is no way to avoid rotating when first opened; if .rewind is skipped, the file is just truncated
            stream.rewind();
            handler = new StreamHandler(stream, new SupportLogFormatter());
            handler.setLevel(Level.ALL);
            count = 0;
        }
        void publish(LogRecord record) throws IOException {
            boolean rewind = false;
            synchronized (this) {
                if (count++ > 9999) { // make sure it does not get enormous during a single session
                    count = 0;
                    rewind = true;
                }
            }
            if (rewind) {
                stream.rewind();
            }
            handler.publish(record);
            LogFlusher.scheduleFlush(handler);
        }
    }

    private final class CustomHandler extends Handler {

        private final Map<String,LogFile> logFiles = new HashMap<String,LogFile>();

        @Override public void publish(LogRecord record) {
            for (Map.Entry<String,LogRecorder> entry : logRecorders.entrySet()) {
                for (LogRecorder.Target target : entry.getValue().targets) {
                    if (target.includes(record)) {
                        try {
                            String name = entry.getKey();
                            LogFile logFile;
                            synchronized (logFiles) {
                                logFile = logFiles.get(name);
                                if (logFile == null) {
                                    logFile = new LogFile(name);
                                    logFiles.put(name, logFile);
                                }
                            }
                            logFile.publish(record);
                        } catch (IOException x) {
                            x.printStackTrace(); // TODO probably unsafe to log this
                        }
                    }
                }
            }
        }

        @Override public void flush() {}

        @Override public void close() throws SecurityException {}

    }

    @Extension public static final class LogFlusher extends PeriodicWork {

        private static final Set<Handler> unflushedHandlers = new HashSet<Handler>();

        static synchronized void scheduleFlush(Handler h) {
            unflushedHandlers.add(h);
        }

        @Override public long getRecurrencePeriod() {
            return 3000; // 3s
        }

        @Override protected void doRun() throws Exception {
            Handler[] handlers;
            synchronized (LogFlusher.class) {
                handlers = unflushedHandlers.toArray(new Handler[unflushedHandlers.size()]);
                unflushedHandlers.clear();
            }
            for (Handler h : handlers) {
                h.flush();
            }
        }

    }

    public static InputStream read(FilePath path, final long offset) throws IOException {
        final VirtualChannel channel = path.getChannel();
        final String remote = path.getRemote();
        if(channel ==null) {
            final RandomAccessFile raf = new RandomAccessFile(new File(remote), "r");
            try {
                raf.seek(offset);
            } catch (IOException e) {
                try {
                    raf.close();
                } catch (IOException e1) {
                    // ignore
                }
                throw e;
            }
            return new InputStream() {
                @Override
                public int read() throws IOException {
                    return raf.read();
                }

                @Override
                public void close() throws IOException {
                    raf.close();
                    super.close();
                }

                @Override
                public int read(byte[] b, int off, int len) throws IOException {
                    return raf.read(b, off, len);
                }

                @Override
                public int read(byte[] b) throws IOException {
                    return raf.read(b);
                }
            };
        }

        final Pipe p = Pipe.createRemoteToLocal();
        channel.callAsync(new Callable<Void, IOException>() {
            private static final long serialVersionUID = 1L;

            public Void call() throws IOException {
                final OutputStream out = new GZIPOutputStream(p.getOut(), 8192);
                RandomAccessFile raf = null;
                try {
                    raf = new RandomAccessFile(new File(remote), "r");
                    raf.seek(offset);
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = raf.read(buf)) >= 0) {
                        out.write(buf, 0, len);
                    }
                    return null;
                } finally {
                    IOUtils.closeQuietly(out);
                    if (raf != null) {
                        try {
                            raf.close();
                        } catch (IOException e) {
                            // ignore
                        }
                    }
                }
            }
        });

        return new GZIPInputStream(p.getIn());
    }

    public static Map<String,File> getLogFiles(FilePath remote, File localCache)
            throws InterruptedException, IOException {
        if (!localCache.isDirectory() && !localCache.mkdirs()) {
            throw new IOException("Could not create local cache directory: " + localCache);
        }
        final Map<String, FileHash> hashes = new LinkedHashMap<String, FileHash>();
        for (File file : localCache.listFiles(new LogFilenameFilter())) {
            hashes.put(file.getName(), new FileHash(file));
        }

        Map<String,Long> offsets = remote.act(new LogFileHashSlurper(remote, hashes));
        hashes.keySet().removeAll(offsets.keySet());
        for (String key: hashes.keySet()) {
            final File deadCacheFile = new File(localCache, key);
            if (!deadCacheFile.delete()) {
                LOGGER.log(Level.WARNING, "Unable to delete redundant cache file: {0}", deadCacheFile);
            }
        }
        Map<String,File> result = new LinkedHashMap<String, File>();
        for (Map.Entry<String, Long> entry:offsets.entrySet()) {
            File local = new File(localCache, entry.getKey());
            if (entry.getValue() > 0 && local.isFile()) {
                final long localLength = local.length();
                if (entry.getValue() < Long.MAX_VALUE) {
                    // only copy the new content
                    FileOutputStream fos = null;
                    InputStream is = null;
                    try {
                        fos = new FileOutputStream(local, true);
                        is = read(remote.child(entry.getKey()), localLength);
                        IOUtils.copy(is, fos);
                    } finally {
                        IOUtils.closeQuietly(is);
                        IOUtils.closeQuietly(fos);
                    }
                }
                result.put(entry.getKey(), local);
            } else {
                FileOutputStream fos = null;
                InputStream is = null;
                try {
                    fos = new FileOutputStream(local, false);
                    is = read(remote.child(entry.getKey()), 0);
                    IOUtils.copy(is, fos);
                } finally {
                    IOUtils.closeQuietly(is);
                    IOUtils.closeQuietly(fos);
                }
                result.put(entry.getKey(), local);
            }
        }
        return result;
    }

    public static final class LogFileHashSlurper implements Callable<Map<String,Long>, IOException> {

        private final FilePath path;
        private final Map<String,FileHash> cache;

        public LogFileHashSlurper(FilePath path, Map<String, FileHash> cache) {
            this.path = path;
            this.cache = cache;
        }

        public Map<String, Long> call() throws IOException {
            assert path.getChannel() == null;
            File path = new File(this.path.getRemote());
            Map<String, Long> result = new LinkedHashMap<String, Long>();
            for (File file : path.listFiles(new LogFilenameFilter())) {
                FileHash hash = cache.get(file.getName());
                if (hash != null && hash.isPartialMatch(file)) {
                    if (file.length() == hash.getLength()) {
                        result.put(file.getName(), Long.MAX_VALUE); // indicate have everything
                    } else {
                        result.put(file.getName(), hash.getLength());
                    }
                } else {
                    // read the whole thing
                    result.put(file.getName(), 0L);
                }
            }
            return result;
        }

    }

    private static class LogFilenameFilter implements FilenameFilter {
        public boolean accept(File dir, String name) {
            return name.endsWith(".log");
        }
    }

    public static final class FileHash implements Serializable {
        private static final long serialVersionUID = 1L;
        private static final String ZERO_LENGTH_MD5 = "d41d8cd98f00b204e9800998ecf8427e";
        private final long length;
        private final String md5;

        public FileHash(long length, String md5) {
            this.length = length;
            this.md5 = md5;
        }

        public FileHash(File file) throws IOException {
            this.length = file.length();
            this.md5 = getDigestOf(new FileInputStream(file), length);
        }

        public FileHash(FilePath file) throws IOException, InterruptedException {
            this.length = file.length();
            this.md5 = getDigestOf(file.read(), length);
        }

        public long getLength() {
            return length;
        }

        public String getMd5() {
            return md5;
        }

        public boolean isPartialMatch(File file) throws IOException {
            if (file.length() < length) return false;
            return md5.equals(getDigestOf(new FileInputStream(file), length));
        }

        public static String getDigestOf(InputStream stream, long length) throws IOException {
            try {
                if (length == 0 || stream == null) return ZERO_LENGTH_MD5;
                int bufferSize;
                if (length < 8192L) bufferSize = (int) length;
                else if (length > 65536L) bufferSize = 65536;
                else bufferSize = 8192;
                byte[] buffer = new byte[bufferSize];
                MessageDigest digest = null;
                try {
                    digest = MessageDigest.getInstance("md5");
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalStateException("Java Language Specification mandates MD5 as a supported digest",
                            e);
                }
                int read;
                while (length > 0 && (read = stream.read(buffer, 0, (int) Math.min(bufferSize, length))) != -1) {
                    digest.update(buffer, 0, read);
                    length -= read;
                }
                return Hex.encodeHexString(digest.digest());
            } finally {
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (IOException e) {
                        // ignore
                    }
                }
            }
        }
    }

    /**
     * Matches log files and their rotated names, such as "foo.log" or "foo.log.1"
     */
    private static final FileFilter ROTATED_LOGFILE_FILTER = new FileFilter() {
        final Pattern pattern = Pattern.compile("^.*\\.log(\\.\\d+)?$");

        public boolean accept(File f) {
            return pattern.matcher(f.getName()).matches() && f.length()>0;
        }
    };
}
