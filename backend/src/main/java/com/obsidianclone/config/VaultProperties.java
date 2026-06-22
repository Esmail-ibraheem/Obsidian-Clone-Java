package com.obsidianclone.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code vault.*} configuration. {@code vault.root} is the absolute
 * path to the vault folder (the source of truth for all notes).
 */
@ConfigurationProperties(prefix = "vault")
public class VaultProperties {

    private Path root = Path.of("/vault");
    private Watch watch = new Watch();

    public Path getRoot() {
        return root;
    }

    public void setRoot(Path root) {
        this.root = root;
    }

    public Watch getWatch() {
        return watch;
    }

    public void setWatch(Watch watch) {
        this.watch = watch;
    }

    /** Filesystem-watch tuning (see the watch module). */
    public static class Watch {
        /** {@code native}, {@code polling}, or {@code auto}. */
        private String mode = "auto";
        /** Polling interval in milliseconds (polling strategy only). */
        private long intervalMs = 1000;

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public long getIntervalMs() {
            return intervalMs;
        }

        public void setIntervalMs(long intervalMs) {
            this.intervalMs = intervalMs;
        }
    }
}
