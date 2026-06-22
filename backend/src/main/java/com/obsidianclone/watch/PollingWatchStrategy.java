package com.obsidianclone.watch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects changes by periodically scanning the vault and diffing each file's
 * (mtime, size) against the previous snapshot. Unlike inotify, this works
 * reliably across the Windows&lt;-&gt;WSL bind mount, which is why it's the dev
 * default. The initial scan establishes a silent baseline (no spurious CREATE
 * events for files that already existed).
 */
public class PollingWatchStrategy implements WatchStrategy {

    private static final Logger log = LoggerFactory.getLogger(PollingWatchStrategy.class);

    private final long intervalMs;
    private final Map<Path, FileMeta> snapshot = new HashMap<>();
    private ScheduledExecutorService scheduler;
    private Path root;
    private BiConsumer<ChangeType, Path> onChange;

    public PollingWatchStrategy(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    @Override
    public void start(Path root, BiConsumer<ChangeType, Path> onChange) {
        this.root = root;
        this.onChange = onChange;
        snapshot.clear();
        snapshot.putAll(scan()); // silent baseline

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "vault-poller");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::tick, intervalMs, intervalMs, TimeUnit.MILLISECONDS);
        log.info("Polling vault watcher started ({} ms interval)", intervalMs);
    }

    @Override
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void tick() {
        Map<Path, FileMeta> current;
        try {
            current = scan();
        } catch (RuntimeException e) {
            log.warn("Vault poll failed: {}", e.getMessage());
            return;
        }

        List<Change> changes = new ArrayList<>();
        for (Map.Entry<Path, FileMeta> e : current.entrySet()) {
            FileMeta previous = snapshot.get(e.getKey());
            if (previous == null) {
                changes.add(new Change(ChangeType.CREATED, e.getKey()));
            } else if (!previous.equals(e.getValue())) {
                changes.add(new Change(ChangeType.MODIFIED, e.getKey()));
            }
        }
        for (Path p : snapshot.keySet()) {
            if (!current.containsKey(p)) {
                changes.add(new Change(ChangeType.DELETED, p));
            }
        }

        // Advance the baseline exactly once, BEFORE delivery, so a listener that
        // throws can't force the whole batch to be replayed on the next tick.
        snapshot.clear();
        snapshot.putAll(current);

        for (Change c : changes) {
            try {
                onChange.accept(c.type(), c.path());
            } catch (RuntimeException ex) {
                log.warn("Change listener failed for {}: {}", c.path(), ex.getMessage());
            }
        }
    }

    private record Change(ChangeType type, Path path) {
    }

    private Map<Path, FileMeta> scan() {
        Map<Path, FileMeta> files = new HashMap<>();
        if (!Files.isDirectory(root)) {
            return files;
        }
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> !isHidden(p))
                    .forEach(p -> {
                        try {
                            files.put(p, new FileMeta(
                                    Files.getLastModifiedTime(p).toMillis(),
                                    Files.size(p)));
                        } catch (IOException ignored) {
                            // file vanished mid-scan; treat as absent
                        }
                    });
        } catch (IOException e) {
            log.warn("Vault scan failed: {}", e.getMessage());
        }
        return files;
    }

    /** Skip dotfiles and anything inside a dot-directory (e.g. .git, .obsidian). */
    private boolean isHidden(Path p) {
        Path rel = root.relativize(p);
        for (Path part : rel) {
            if (part.toString().startsWith(".")) {
                return true;
            }
        }
        return false;
    }

    private record FileMeta(long mtime, long size) {
    }
}
