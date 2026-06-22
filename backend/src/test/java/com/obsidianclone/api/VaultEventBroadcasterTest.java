package com.obsidianclone.api;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.obsidianclone.watch.ChangeType;
import com.obsidianclone.watch.FileChangeEvent;

class VaultEventBroadcasterTest {

    @Test
    void broadcastsChangeToVaultTopic() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        VaultEventBroadcaster broadcaster = new VaultEventBroadcaster(template);

        broadcaster.onFileChange(new FileChangeEvent(ChangeType.MODIFIED, "notes/a.md"));

        verify(template).convertAndSend("/topic/vault",
                (Object) Map.of("type", "modified", "path", "notes/a.md"));
    }
}
