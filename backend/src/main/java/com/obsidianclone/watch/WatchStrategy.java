package com.obsidianclone.watch;

import java.nio.file.Path;
import java.util.function.BiConsumer;

/**
 * Strategy for observing filesystem changes under the vault root. Two
 * implementations exist: {@link NativeWatchStrategy} (inotify via
 * {@code WatchService}) and {@link PollingWatchStrategy} (periodic mtime scan,
 * reliable across the Windows&lt;-&gt;WSL bind mount). Implementations emit
 * absolute paths; the caller relativizes them.
 */
public interface WatchStrategy {

    /** Begin watching {@code root}, delivering changes to {@code onChange}. */
    void start(Path root, BiConsumer<ChangeType, Path> onChange);

    /** Stop watching and release resources. */
    void stop();
}
