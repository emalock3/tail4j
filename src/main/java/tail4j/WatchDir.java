package tail4j;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static java.nio.file.StandardWatchEventKinds.*;

public class WatchDir extends Thread {
    private final TailFile.Builder tailFileBuilder;
    private final TailFile.SourceHolder tailFileSourceHolder;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicReference<TailFile.Thread> currentTailThread = new AtomicReference<>();
    private final AtomicReference<Throwable> childThreadError = new AtomicReference<>();
    public static final long DEFAULT_ROTATE_WAIT = 5L;
    private final long rotateWait;
    public WatchDir(TailFile.Builder builder, long rotateWait) {
        this.tailFileBuilder = builder;
        this.tailFileSourceHolder = builder.holder();
        this.rotateWait = rotateWait;
    }
    public WatchDir(TailFile.Builder builder) {
        this(builder, DEFAULT_ROTATE_WAIT);
    }

    public void shutdown() {
        shutdown.set(true);
        interrupt();
    }

    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                shutdown();
            }
        });
    }

    static class ExHandler implements UncaughtExceptionHandler {
        private final WatchDir watchDir;
        ExHandler(WatchDir watchDir) {
            this.watchDir = watchDir;
        }
        @Override
        public void uncaughtException(Thread t, Throwable e) {
            watchDir.childThreadError.set(e);
            watchDir.shutdown();
        }
    }

    @Override
    public void run() {
        addShutdownHook();
        TailFile.Thread current = tailFileBuilder.build();
        current.setUncaughtExceptionHandler(new ExHandler(this));
        currentTailThread.set(current);
        current.start();
        try (WatchService ws = FileSystems.getDefault().newWatchService()) {
            tailFileBuilder.parentDir().register(ws, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY, OVERFLOW);
            while (!shutdown.get() && !isInterrupted()) {
                WatchKey key = ws.take();
                handleWatchEvents(key);
                if (!key.reset()) {
                    // directory no longer accessible
                    break;
                }
            }
            Throwable t;
            if ((t = childThreadError.get()) != null) {
                throw new RuntimeException(t);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            interrupt();
        } finally {
            if (currentTailThread.get() != null) {
                currentTailThread.get().get().shutdown();
            }
        }
    }

    private void handleWatchEvents(WatchKey key)
            throws IOException, InterruptedException {
        final TailFile.Thread cur = currentTailThread.get();
        for (WatchEvent<?> event : key.pollEvents()) {
            Path context = (Path) event.context();
            WatchEvent.Kind kind = event.kind();
            if (kind.equals(ENTRY_MODIFY)) {
                // fire modify event
                cur.get().handleModifyEvent(context);
            } else if (kind.equals(ENTRY_DELETE) && tailFileSourceHolder.isTargetEvent(context)) {
                cur.get().handleDeleteEvent();
                cur.get().shutdownLater(rotateWait, TimeUnit.SECONDS);
            } else if (kind.equals(ENTRY_CREATE) && tailFileSourceHolder.isTargetEvent(context)) {
                TailFile.Thread newT = tailFileBuilder.reset(true).build();
                newT.setUncaughtExceptionHandler(new ExHandler(this));
                newT.start();
                currentTailThread.set(newT);
            } else if (kind.equals(OVERFLOW)) {
                // Restart when an overflow occurs.
                cur.get().handleModifyEvent(context);
                cur.get().shutdown();
                cur.join();
                TailFile.Thread newT = tailFileBuilder.reset(true).build();
                newT.setUncaughtExceptionHandler(new ExHandler(this));
                newT.start();
                currentTailThread.set(newT);
            }
        }
    }
}
