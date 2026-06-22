import { useEffect, useRef } from "react";
import { EditorView } from "@codemirror/view";
import { api } from "@/api/client";
import { ConflictError } from "@/api/types";
import { createEditor } from "@/editor/createEditor";
import { resolveTarget } from "@/editor/wikilink/resolveTarget";
import { useEditorStore } from "@/stores/editorStore";
import { flattenFiles, useVaultStore } from "@/stores/vaultStore";
import { useWorkspaceStore } from "@/stores/workspaceStore";

/** Resolve a wikilink target and open it, creating the note if it doesn't exist. */
async function openTarget(target: string, split: boolean): Promise<void> {
  const files = flattenFiles(useVaultStore.getState().tree);
  const resolved = resolveTarget(target, files);
  if (resolved) {
    useWorkspaceStore.getState().openTab(resolved, { split });
    return;
  }
  const newPath = target.endsWith(".md") ? target : `${target}.md`;
  try {
    await api.createFile(newPath, `# ${target}\n\n`);
    await useVaultStore.getState().loadTree();
  } catch {
    // raced with another create; fall through and open whatever exists
  }
  useWorkspaceStore.getState().openTab(newPath, { split });
}

/**
 * Hosts the CodeMirror 6 inline Live Preview editor for one note. The view is
 * created once the note has loaded; edits flow into the store + debounced
 * autosave, external changes (WS reload) are pushed back into the view, and
 * wikilinks navigate/create on click. Re-mounted per note via a key on the path.
 */
export default function EditorPane({ path }: { path: string }) {
  const doc = useEditorStore((s) => s.docs[path]);
  const loadFile = useEditorStore((s) => s.loadFile);
  const setContent = useEditorStore((s) => s.setContent);
  const markSaved = useEditorStore((s) => s.markSaved);
  const setConflict = useEditorStore((s) => s.setConflict);
  const resolveKeepMine = useEditorStore((s) => s.resolveKeepMine);

  const hostRef = useRef<HTMLDivElement | null>(null);
  const viewRef = useRef<EditorView | null>(null);
  const saveTimer = useRef<number | undefined>(undefined);

  useEffect(() => {
    void loadFile(path);
  }, [path, loadFile]);

  function scheduleSave() {
    window.clearTimeout(saveTimer.current);
    saveTimer.current = window.setTimeout(async () => {
      const current = useEditorStore.getState().docs[path];
      if (!current || !current.dirty) return;
      try {
        const saved = await api.writeFile(path, current.content, current.baseMtime);
        markSaved(path, saved.mtime);
      } catch (e) {
        if (e instanceof ConflictError) {
          setConflict(path, e.currentContent, e.currentMtime);
        }
      }
    }, 400);
  }

  useEffect(() => {
    if (viewRef.current || !hostRef.current || !doc || doc.loading) return;
    viewRef.current = createEditor({
      parent: hostRef.current,
      doc: doc.content,
      onChange: (text) => {
        setContent(path, text);
        scheduleSave();
      },
      context: {
        getFiles: () => flattenFiles(useVaultStore.getState().tree),
        onOpen: (target, split) => void openTarget(target, split),
      },
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [doc, path]);

  useEffect(() => {
    return () => {
      viewRef.current?.destroy();
      viewRef.current = null;
      window.clearTimeout(saveTimer.current);
    };
  }, []);

  // Push external content changes (load completion, WS reload) into the view.
  useEffect(() => {
    const view = viewRef.current;
    if (!view || !doc || doc.loading) return;
    const current = view.state.doc.toString();
    if (current !== doc.content) {
      view.dispatch({ changes: { from: 0, to: current.length, insert: doc.content } });
    }
  }, [doc?.content, doc?.loading]);

  return (
    <div className="editor-host" style={{ display: "flex", flexDirection: "column", height: "100%" }}>
      {doc?.missing && <div className="empty-state">This note no longer exists.</div>}
      {doc?.conflict && (
        <div className="conflict-banner">
          <span>This note changed on disk.</span>
          <button
            onClick={() => {
              const c = doc.conflict!;
              setContent(path, c.serverContent);
              markSaved(path, c.serverMtime);
            }}
          >
            Load theirs
          </button>
          <button onClick={() => resolveKeepMine(path)}>Keep mine</button>
        </div>
      )}
      <div ref={hostRef} style={{ flex: 1, minHeight: 0, overflow: "hidden" }} />
    </div>
  );
}
