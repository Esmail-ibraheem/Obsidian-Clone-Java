import { useEffect, useRef } from "react";
import { EditorView } from "@codemirror/view";
import { api } from "@/api/client";
import { ConflictError } from "@/api/types";
import { createEditor } from "@/editor/createEditor";
import { useEditorStore } from "@/stores/editorStore";

/**
 * Hosts the CodeMirror 6 inline Live Preview editor for one note. The editor
 * view is created once the note has loaded; user edits flow into the store +
 * debounced autosave, and external changes (WS reload) are pushed back into the
 * view. Re-mounted per note via a React key on the active path.
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

  // Create the editor once the note's content has loaded.
  useEffect(() => {
    if (viewRef.current || !hostRef.current || !doc || doc.loading) return;
    viewRef.current = createEditor({
      parent: hostRef.current,
      doc: doc.content,
      onChange: (text) => {
        setContent(path, text);
        scheduleSave();
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
