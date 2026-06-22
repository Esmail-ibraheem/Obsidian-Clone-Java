import { beforeEach, describe, expect, it } from "vitest";
import { activePath, useWorkspaceStore } from "@/stores/workspaceStore";

function reset() {
  useWorkspaceStore.setState({ panes: [{ id: "p0", tabs: [], active: null }], activePaneId: "p0" });
}

describe("workspaceStore", () => {
  beforeEach(reset);

  it("opens a tab in the active pane", () => {
    useWorkspaceStore.getState().openTab("a.md");
    const s = useWorkspaceStore.getState();
    expect(s.panes[0].tabs).toEqual(["a.md"]);
    expect(activePath(s)).toBe("a.md");
  });

  it("does not duplicate an already-open tab", () => {
    const { openTab } = useWorkspaceStore.getState();
    openTab("a.md");
    openTab("b.md");
    openTab("a.md");
    const s = useWorkspaceStore.getState();
    expect(s.panes[0].tabs).toEqual(["a.md", "b.md"]);
    expect(s.panes[0].active).toBe("a.md");
  });

  it("splits into a new pane and focuses it", () => {
    useWorkspaceStore.getState().openTab("a.md");
    useWorkspaceStore.getState().openTab("b.md", { split: true });
    const s = useWorkspaceStore.getState();
    expect(s.panes).toHaveLength(2);
    expect(s.panes[1].active).toBe("b.md");
    expect(s.activePaneId).toBe(s.panes[1].id);
  });

  it("closing the last tab in a split pane removes that pane", () => {
    useWorkspaceStore.getState().openTab("a.md");
    useWorkspaceStore.getState().openTab("b.md", { split: true });
    const pane2 = useWorkspaceStore.getState().panes[1];
    useWorkspaceStore.getState().closeTab(pane2.id, "b.md");
    const s = useWorkspaceStore.getState();
    expect(s.panes).toHaveLength(1);
    expect(s.panes[0].tabs).toEqual(["a.md"]);
  });

  it("always keeps at least one pane", () => {
    useWorkspaceStore.getState().openTab("a.md");
    useWorkspaceStore.getState().closeTab("p0", "a.md");
    const s = useWorkspaceStore.getState();
    expect(s.panes).toHaveLength(1);
    expect(s.panes[0].tabs).toEqual([]);
  });
});
