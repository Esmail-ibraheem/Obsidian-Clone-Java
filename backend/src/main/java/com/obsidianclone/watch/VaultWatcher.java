package com.obsidianclone.watch;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import com.obsidianclone.config.VaultProperties;
import com.obsidianclone.vault.VaultPathResolver;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Watches the vault for filesystem changes and republishes them as
 * {@link FileChangeEvent}s (with vault-relative paths) for the index and the
 * WebSocket broadcaster. The strategy is chosen by {@code vault.watch.mode}:
 * {@code native} uses inotify; everything else (incl. {@code auto}) uses
 * polling, which is the safe default for the Windows&lt;-&gt;WSL bind mount.
 */
@Component
public class VaultWatcher {

    private static final Logger log = LoggerFactory.getLogger(VaultWatcher.class);

    private final VaultProperties properties;
    private final VaultPathResolver resolver;
    private final ApplicationEventPublisher publisher;
    private WatchStrategy strategy;

    public VaultWatcher(VaultProperties properties, VaultPathResolver resolver,
                        ApplicationEventPublisher publisher) {
        this.properties = properties;
        this.resolver = resolver;
        this.publisher = publisher;
    }

    @PostConstruct
    public void start() {
        String mode = properties.getWatch().getMode();
        if ("native".equalsIgnoreCase(mode)) {
            strategy = new NativeWatchStrategy();
        } else {
            strategy = new PollingWatchStrategy(properties.getWatch().getIntervalMs());
        }
        log.info("Starting vault watcher (mode={})", mode);
        strategy.start(resolver.root(), (type, absolutePath) -> {
            String relative = resolver.toRelative(absolutePath);
            publisher.publishEvent(new FileChangeEvent(type, relative));
        });
    }

    @PreDestroy
    public void stop() {
        if (strategy != null) {
            strategy.stop();
        }
    }
}
