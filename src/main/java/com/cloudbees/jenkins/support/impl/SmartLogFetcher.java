package com.cloudbees.jenkins.support.impl;

import com.cloudbees.jenkins.support.SupportPlugin;
import hudson.FilePath;
import hudson.FilePath.FileCallable;
import hudson.Util;
import hudson.model.Node;
import hudson.remoting.Callable;
import hudson.remoting.Pipe;
import hudson.remoting.VirtualChannel;
import hudson.util.IOUtils;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.lang.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.Serializable;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Efficient incremental retrieval of log files from {@link Node}, by taking advantages of
 * the additive nature of log files.
 *
 * <p>
 * Log files tend to get its data appended to the end, so for each file, we look at
 * what we already locally have, and see if the remote file has the exact same header section.
 * If this is the case, we only need to transfer the tail section of it, which cuts the amount
 * of data transfer significantly.
 *
 * @author Stephen Connolly
 */
class SmartLogFetcher {
    private final File cacheDir;

    public SmartLogFetcher() {
        cacheDir = new File(SupportPlugin.getRootDirectory(), "cache");
    }

    /**
     * Directory on this machine we use to cache log files available on the given node.
     */
    File getCacheDir(Node node) {
        String cacheKey = Util.getDigestOf(node.getNodeName() + ":" + node.getRootPath());
        return new File(cacheDir, StringUtils.right(cacheKey, 8));
    }

    public Map<String,File> getLogFiles(Node node, FilePath remote)
            throws InterruptedException, IOException {
        File localCache = getCacheDir(node);

        if (!localCache.isDirectory() && !localCache.mkdirs()) {
            throw new IOException("Could not create local cache directory: " + localCache);
        }

        // build an inventory of what we already have locally
        final Map<String, FileHash> hashes = new LinkedHashMap<String, FileHash>();
        for (File file : localCache.listFiles(new LogFilenameFilter())) {
            hashes.put(file.getName(), new FileHash(file));
        }

        Map<String,Long> offsets = remote.act(new LogFileHashSlurper(hashes));
        hashes.keySet().removeAll(offsets.keySet());
        for (String key: hashes.keySet()) {
            final File deadCacheFile = new File(localCache, key);
            if (!deadCacheFile.delete()) {
                LOGGER.log(Level.WARNING, "Unable to delete redundant cache file: {0}", deadCacheFile);
            }
        }
        Map<String,File> result = new LinkedHashMap<String, File>();
        for (Map.Entry<String, Long> entry : offsets.entrySet()) {
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

    /**
     * MD5 checksum of a head section of a file.
     */
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

        /**
         * Does the given file has the same head section as this file hash?
         */
        public boolean isPartialMatch(File file) throws IOException {
            if (file.length() < length) return false;
            return md5.equals(getDigestOf(new FileInputStream(file), length));
        }

        /**
         * Computes the checksum of a stream upto the specified length.
         */
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

    public static final class LogFileHashSlurper implements FileCallable<Map<String,Long>> {
        private final Map<String,FileHash> cached;

        public LogFileHashSlurper(Map<String, FileHash> cached) {
            this.cached = cached;
        }

        public Map<String, Long> invoke(File path, VirtualChannel channel) throws IOException, InterruptedException {
            Map<String, Long> result = new LinkedHashMap<String, Long>();
            for (File file : path.listFiles(new LogFilenameFilter())) {
                FileHash hash = cached.get(file.getName());
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

    /**
     * Reads a file not from the beginning but from the given offset.
     */
    // TODO: switch to FilePath.readFromOffset() post 1.585
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

    private static final Logger LOGGER = Logger.getLogger(SmartLogFetcher.class.getName());
}
