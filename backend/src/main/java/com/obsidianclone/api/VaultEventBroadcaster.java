package com.obsidianclone.api;

import java.util.Map;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.obsidianclone.watch.FileChangeEvent;

/**
 * Pushes vault filesystem changes to subscribed clients over WebSocket so the
 * file tree and open editors stay in sync with on-disk reality (including
 * external edits). Payload: {@code {type: created|modified|deleted, path}}.
 */
@Component
public class VaultEventBroadcaster {

    static final String TOPIC = "/topic/vault";

    private final SimpMessagingTemplate messaging;

    public VaultEventBroadcaster(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    @EventListener
    public void onFileChange(FileChangeEvent event) {
        messaging.convertAndSend(TOPIC, Map.of(
                "type", event.type().name().toLowerCase(),
                "path", event.path()));
    }
}
