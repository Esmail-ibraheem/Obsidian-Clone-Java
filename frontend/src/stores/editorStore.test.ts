import { beforeEach, describe, expect, it } from "vitest";
import { EditorDoc, useEditorStore } from "@/stores/editorStore";

function doc(overrides: Partial<EditorDoc> = {}): EditorDoc {
  return { content: "x", baseMtime: 50, dirty: false, loading: false, missing: false, conflict: null, ...overrides };
}

describe("editorStore", () => {
  beforeEach(() => useEditorStore.setState({ docs: {} }));

  it("setContent marks the doc dirty", () => {
    useEditorStore.setState({ docs: { "a.md": doc() } });
    useEditorStore.getState().setContent("a.md", "changed");
    const d = useEditorStore.getState().docs["a.md"];
    expect(d.content).toBe("changed");
    expect(d.dirty).toBe(true);
  });

  it("markSaved clears dirty and advances baseMtime", () => {
    useEditorStore.setState({ docs: { "a.md": doc({ dirty: true }) } });
    useEditorStore.getState().markSaved("a.md", 99);
    const d = useEditorStore.getState().docs["a.md"];
    expect(d.dirty).toBe(false);
    expect(d.baseMtime).toBe(99);
  });

  it("applyExternal ignores our own save echo (same mtime)", () => {
    useEditorStore.setState({ docs: { "a.md": doc({ content: "mine", baseMtime: 50 }) } });
    useEditorStore.getState().applyExternal("a.md", "mine", 50);
    expect(useEditorStore.getState().docs["a.md"].content).toBe("mine");
  });

  it("applyExternal reloads a clean doc when mtime differs", () => {
    useEditorStore.setState({ docs: { "a.md": doc({ content: "old", baseMtime: 50 }) } });
    useEditorStore.getState().applyExternal("a.md", "new", 60);
    const d = useEditorStore.getState().docs["a.md"];
    expect(d.content).toBe("new");
    expect(d.baseMtime).toBe(60);
  });

  it("applyExternal raises a conflict on a dirty doc instead of clobbering", () => {
    useEditorStore.setState({ docs: { "a.md": doc({ content: "mine", baseMtime: 50, dirty: true }) } });
    useEditorStore.getState().applyExternal("a.md", "theirs", 60);
    const d = useEditorStore.getState().docs["a.md"];
    expect(d.content).toBe("mine");
    expect(d.conflict).toEqual({ serverContent: "theirs", serverMtime: 60 });
  });

  it("resolveKeepMine keeps content but adopts server mtime so the next save wins", () => {
    useEditorStore.setState({
      docs: { "a.md": doc({ content: "mine", baseMtime: 50, dirty: true, conflict: { serverContent: "theirs", serverMtime: 60 } }) },
    });
    useEditorStore.getState().resolveKeepMine("a.md");
    const d = useEditorStore.getState().docs["a.md"];
    expect(d.content).toBe("mine");
    expect(d.baseMtime).toBe(60);
    expect(d.conflict).toBeNull();
  });
});
