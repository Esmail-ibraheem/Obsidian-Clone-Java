package com.obsidianclone.watch;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * inotify-based watcher using {@link WatchService}. Registers the vault root and
 * all subdirectories, and registers newly-created directories on the fly. Fast
 * and event-driven, but unreliable across the Windows&lt;-&gt;WSL bind mount —
 * use {@link PollingWatchStrategy} there.
 */
public class NativeWatchStrategy implements WatchStrategy {

    private static final Logger log = LoggerFactory.getLogger(NativeWatchStrategy.class);

    private final Map<WatchKey, Path> keys = new HashMap<>();
    private WatchService watchService;
    private Thread thread;
    private volatile boolean running;

    @Override
    public void start(Path root, BiConsumer<ChangeType, Path> onChange) {
        try {
            watchService = FileSystems.getDefault().newWatchService();
            registerRecursively(root);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start native watcher", e);
        }
        running = true;
        thread = new Thread(() -> loop(onChange), "vault-watcher");
        thread.setDaemon(true);
        thread.start();
        log.info("Native vault watcher started");
    }

    @Override
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException ignored) {
                // closing best-effort
            }
        }
    }

    private void registerRecursively(Path root) throws IOException {
        if (!Files.isDirectory(root)) {
            return;
        }
        try (Stream<Path> dirs = Files.walk(root)) {
            dirs.filter(Files::isDirectory).forEach(this::register);
        }
    }

    private void register(Path dir) {
        try {
            WatchKey key = dir.register(watchService,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_DELETE);
            keys.put(key, dir);
        } catch (IOException e) {
            log.warn("Failed to watch {}: {}", dir, e.getMessage());
        }
    }

    private void loop(BiConsumer<ChangeType, Path> onChange) {
        while (running) {
            WatchKey key;
            try {
                key = watchService.take();
            } catch (InterruptedException | java.nio.file.ClosedWatchServiceException e) {
                Thread.currentThread().interrupt();
                return;
            }
            Path dir = keys.get(key);
            if (dir == null) {
                key.reset();
                continue;
            }
            for (WatchEvent<?> event : key.pollEvents()) {
                WatchEvent.Kind<?> kind = event.kind();
                if (kind == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }
                Path changed = dir.resolve((Path) event.context());
                if (kind == StandardWatchEventKinds.ENTRY_CREATE) {
                    if (Files.isDirectory(changed)) {
                        register(changed); // watch new subtree
                    } else {
                        onChange.accept(ChangeType.CREATED, changed);
                    }
                } else if (kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                    if (!Files.isDirectory(changed)) {
                        onChange.accept(ChangeType.MODIFIED, changed);
                    }
                } else if (kind == StandardWatchEventKinds.ENTRY_DELETE) {
                    onChange.accept(ChangeType.DELETED, changed);
                }
            }
            if (!key.reset()) {
                keys.remove(key);
            }
        }
    }
}
