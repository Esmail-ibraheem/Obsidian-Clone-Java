package com.obsidianclone.watch;

/**
 * Published (via Spring's {@code ApplicationEventPublisher}) when a vault file
 * is created, modified, or deleted on disk — whether by the API itself or by an
 * external editor. The index updates from it and the WebSocket broadcaster
 * pushes it to clients.
 *
 * @param type the change kind
 * @param path vault-relative POSIX path
 */
public record FileChangeEvent(ChangeType type, String path) {
}
