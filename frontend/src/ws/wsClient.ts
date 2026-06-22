import { Client } from "@stomp/stompjs";
import SockJS from "sockjs-client";
import { api } from "@/api/client";
import { VaultEvent } from "@/api/types";
import { useEditorStore } from "@/stores/editorStore";
import { useVaultStore } from "@/stores/vaultStore";

let client: Client | null = null;

/** Connect to the backend's STOMP endpoint and keep the UI in sync with disk. */
export function connectVaultSocket(): void {
  if (client) return;
  client = new Client({
    webSocketFactory: () => new SockJS("/ws") as unknown as WebSocket,
    reconnectDelay: 2000,
    onConnect: () => {
      // Resync after every (re)connect, then subscribe to live changes.
      void useVaultStore.getState().loadTree();
      client?.subscribe("/topic/vault", (message) => {
        try {
          void handleEvent(JSON.parse(message.body) as VaultEvent);
        } catch {
          /* ignore malformed frames */
        }
      });
    },
  });
  client.activate();
}

export function disconnectVaultSocket(): void {
  void client?.deactivate();
  client = null;
}

/** Apply a vault event to the tree and any open editor. Exported for testing. */
export async function handleEvent(event: VaultEvent): Promise<void> {
  useVaultStore.getState().applyEvent(event);

  const editor = useEditorStore.getState();
  if (!(event.path in editor.docs)) return;

  if (event.type === "deleted") {
    editor.markMissing(event.path);
    return;
  }
  try {
    const note = await api.readFile(event.path);
    editor.applyExternal(event.path, note.content, note.mtime);
  } catch {
    /* file vanished between event and read */
  }
}
