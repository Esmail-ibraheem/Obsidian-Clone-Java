import { beforeEach, describe, expect, it, vi } from "vitest";

vi.mock("sockjs-client", () => ({ default: vi.fn() }));
vi.mock("@stomp/stompjs", () => ({ Client: vi.fn() }));
vi.mock("@/api/client", () => ({
  api: {
    readFile: vi.fn(() => Promise.resolve({ path: "a.md", content: "server", mtime: 60, size: 6 })),
    getTree: vi.fn(() => Promise.resolve([])),
  },
}));

import { handleEvent } from "@/ws/wsClient";
import { useEditorStore } from "@/stores/editorStore";

describe("wsClient.handleEvent", () => {
  beforeEach(() => useEditorStore.setState({ docs: {} }));

  it("reloads an open clean doc on a modified event", async () => {
    useEditorStore.setState({
      docs: { "a.md": { content: "old", baseMtime: 50, dirty: false, loading: false, missing: false, conflict: null } },
    });
    await handleEvent({ type: "modified", path: "a.md" });
    expect(useEditorStore.getState().docs["a.md"].content).toBe("server");
  });

  it("marks an open doc missing on a deleted event", async () => {
    useEditorStore.setState({
      docs: { "a.md": { content: "x", baseMtime: 1, dirty: false, loading: false, missing: false, conflict: null } },
    });
    await handleEvent({ type: "deleted", path: "a.md" });
    expect(useEditorStore.getState().docs["a.md"].missing).toBe(true);
  });

  it("ignores events for files that are not open", async () => {
    await handleEvent({ type: "modified", path: "b.md" });
    expect(useEditorStore.getState().docs["b.md"]).toBeUndefined();
  });
});
