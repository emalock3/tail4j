package tail4j;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.file.StandardOpenOption.*;

public class TailFile implements Runnable {
    private final SourceHolder sourceHolder;
    private final Charset sourceCharset;
    private final OutputStream out;
    private final Charset destCharset;
    private final AtomicReference<ReadingPos> readingPos;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final Semaphore semaphore = new Semaphore(1);

    TailFile(SourceHolder sourceHolder, Charset sourceCharset, OutputStream out, Charset destCharset,
             ReadingPos readingPos) {
        if (!Files.exists(sourceHolder.getSource())) {
            throw new IllegalArgumentException(String.format("source[%s] is not exists.", sourceHolder.getSource()));
        }
        this.sourceHolder = sourceHolder;
        this.readingPos = new AtomicReference<>(readingPos);
        this.sourceCharset = sourceCharset;
        this.out = out;
        this.destCharset = destCharset;
    }

    public void handleModifyEvent(Path eventContext) {
        if (sourceHolder.isTargetEvent(eventContext)) {
            semaphore.release();
        }
    }

    public void handleDeleteEvent() throws IOException {
        readingPos.get().close();
    }

    public void shutdown() {
        shutdown.set(true);
        semaphore.release();
    }

    public void shutdownLater(final long delay, final TimeUnit timeUnit) {
        if (!shutdown.get()) {
            new java.lang.Thread() {
                public void run() {
                    try {
                        sleep(timeUnit.toMillis(delay));
                    } catch (InterruptedException e) {
                        interrupt();
                    }
                    shutdown();
                }
            }.start();
        }
    }

    @Override
    public void run() {
        try (FileChannel sc = (FileChannel) Files.newByteChannel(sourceHolder.getSource(), EnumSet.of(READ))) {
            readingPos.get().open();
            sc.position(readingPos.get().currentPos(sc));
            ByteBuffer readBuffer = ByteBuffer.allocate(1024 * 1024);
            CharBuffer readCharBuffer = CharBuffer.allocate(1024 * 1024);
            while (!shutdown.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    semaphore.acquire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
                resetPosIfTruncated(sc);
                tail(sc, readBuffer, readCharBuffer);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                readingPos.get().close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void resetPosIfTruncated(FileChannel tc) {
        try {
            if (tc.size() < readingPos.get().currentPos(tc)) {
                tc.position(tc.size());
                readingPos.get().currentPos(tc.size());
            }
        } catch (IOException e) {
            // TODO
            e.printStackTrace();
        }
    }

    private void tail(FileChannel sc, ByteBuffer readBuffer,
                      CharBuffer readCharBuffer) throws IOException {
        readBuffer.clear();
        CharsetDecoder cd = sourceCharset.newDecoder();
        cd.onMalformedInput(CodingErrorAction.REPLACE);
        cd.onUnmappableCharacter(CodingErrorAction.REPLACE);
        try {
            while (sc.read(readBuffer) != -1) {
                readBuffer.flip();
                cd.decode(readBuffer, readCharBuffer, false);
                readCharBuffer.flip();
                out.write(String.valueOf(readCharBuffer).getBytes(destCharset));
                readBuffer.compact();
                readCharBuffer.clear();
            }
            readBuffer.flip();
            cd.decode(readBuffer, readCharBuffer, true);
            cd.flush(readCharBuffer);
            readCharBuffer.flip();
            out.write(String.valueOf(readCharBuffer).getBytes(destCharset));
            readingPos.get().currentPos(sc.position());
        } catch (IOException e) {
            // TODO handle
            e.printStackTrace();
        }
    }

    public static class Builder {
        private final Path source;
        private final Charset sourceCharset;
        private final OutputStream out;
        private final Charset destCharset;
        private final Path positionFile;
        private final boolean reset;
        private final boolean persist;
        private final SourceHolder sourceHolder;
        public Builder(Path source, OutputStream out) {
            this(source, Charset.defaultCharset(), out, Charset.defaultCharset(), null, false, false);
        }
        public Builder(Path source, PrintStream out) {
            this(source, (OutputStream) out);
        }
        private Builder(Path source, Charset sourceCharset, OutputStream out, Charset destCharset,
                        Path positionFile, boolean reset, boolean persist) {
            this.source = throwExIfNull(source, "source").toAbsolutePath().normalize();
            this.sourceCharset = sourceCharset;
            this.out = throwExIfNull(out, "out");
            this.destCharset = destCharset;
            if (positionFile == null) {
                this.positionFile = null;
            } else {
                this.positionFile = positionFile.normalize().toAbsolutePath();
            }
            this.reset = reset;
            this.persist = persist;
            this.sourceHolder = new SourceHolder(this.source);
        }
        private static <T> T throwExIfNull(T target, String name) {
            if (target == null) {
                throw new IllegalArgumentException(String.format("%s must not be null.", name));
            }
            return target;
        }

        public static Path toDefaultPositionFile(Path path) {
            String fileSep = System.getProperty("file.separator");
            String pathSep = System.getProperty("path.separator");
            String tmpDir = System.getProperty("java.io.tmpdir");
            return new File(tmpDir, "tail4j." + path.toAbsolutePath().toString()
                    .replace(pathSep, "_")
                    .replace(fileSep, "_")
                    .replace(':', '_')).toPath();
        }
        public Builder sourceCharset(Charset sourceCharset) {
            return new Builder(source, sourceCharset, out, destCharset, positionFile, reset, persist);
        }
        public Builder destCharset(Charset destCharset) {
            return new Builder(source, sourceCharset, out, destCharset, positionFile, reset, persist);
        }
        public Builder reset(boolean reset) {
            return new Builder(source, sourceCharset, out, destCharset, positionFile, reset, persist);
        }
        public Builder persist(boolean persist) {
            return new Builder(source, sourceCharset, out, destCharset, positionFile, reset, persist);
        }
        public Builder positionFile(Path positionFile) {
            return new Builder(source, sourceCharset, out, destCharset, positionFile, reset, true);
        }
        public Path parentDir() {
            return sourceHolder.getParentDir();
        }
        public SourceHolder holder() {
            return sourceHolder;
        }
        public TailFile.Thread build() {
            if (persist) {
                if (positionFile == null) {
                    return new TailFile.Thread(new TailFile(sourceHolder, sourceCharset, out, destCharset,
                            new ReadingPos.ReadingPosFile(toDefaultPositionFile(source), reset)));
                } else {
                    return new TailFile.Thread(new TailFile(sourceHolder, sourceCharset, out, destCharset,
                            new ReadingPos.ReadingPosFile(positionFile, reset)));
                }
            } else {
                return new TailFile.Thread(new TailFile(sourceHolder, sourceCharset, out, destCharset,
                        ReadingPos.EMPTY));
            }
        }
    }

    public static class SourceHolder {
        private final Path source;
        private final Path parentDir;
        SourceHolder(Path source) {
            this.source = source;
            this.parentDir = source.getParent();
        }
        public boolean isTargetEvent(Path eventContext) {
            return eventContext == null || parentDir.resolve(eventContext).equals(source);
        }
        Path getParentDir() {
            return parentDir;
        }
        public Path getSource() {
            return source;
        }
    }

    public static class Thread extends java.lang.Thread {
        private final TailFile tailFile;
        public Thread(TailFile tailFile) {
            super(tailFile);
            this.tailFile = tailFile;
        }
        public TailFile get() {
            return tailFile;
        }
    }

    static abstract class ReadingPos {

        static final ReadingPos EMPTY = new ReadingPos(){};

        long currentPos(FileChannel sc) throws IOException {
            return sc.position();
        }

        void currentPos(long newPosition) throws IOException {}

        void open() throws IOException {}

        void close() throws IOException {}

        static class ReadingPosFile extends ReadingPos {
            private final Path positionFile;
            private final boolean reset;
            private FileChannel positionFileChannel;
            private FileLock positionFileLock;

            public ReadingPosFile(Path positionFile, boolean reset) {
                this.positionFile = positionFile;
                this.reset = reset;
            }

            @Override
            long currentPos(FileChannel sc) throws IOException {
                if (positionFileChannel != null && positionFileChannel.isOpen()) {
                    ByteBuffer bb = ByteBuffer.allocate(8);
                    positionFileChannel.position(0).read(bb);
                    bb.flip();
                    if (bb.remaining() != 8) {
                        return sc.position();
                    }
                    return bb.getLong();
                } else {
                    return sc.position();
                }
            }

            @Override
            void currentPos(long newPosition) throws IOException {
                if (positionFileChannel != null && positionFileChannel.isOpen()) {
                    ByteBuffer bb = ByteBuffer.allocate(8);
                    bb.putLong(newPosition).flip();
                    positionFileChannel.position(0).write(bb);
                }
            }

            void open() throws IOException {
                if (reset || !Files.exists(positionFile) || Files.size(positionFile) != 8) {
                    Files.write(positionFile, new byte[8], CREATE, WRITE, TRUNCATE_EXISTING);
                }
                this.positionFileChannel = (FileChannel) Files.newByteChannel(
                        positionFile, EnumSet.of(READ, WRITE, DSYNC));
                this.positionFileLock = this.positionFileChannel.tryLock(0, 8, false);
                if (this.positionFileLock == null) {
                    throw new IOException(
                            String.format("another program holds an overlapping lock.[%s]", positionFile));
                }
            }

            void close() throws IOException {
                if (positionFileLock != null && positionFileLock.isValid()) {
                    positionFileLock.close();
                    positionFileLock = null;
                }
                if (positionFileChannel != null && positionFileChannel.isOpen()) {
                    positionFileChannel.close();
                    positionFileChannel = null;
                }
            }

        }
    }
}
