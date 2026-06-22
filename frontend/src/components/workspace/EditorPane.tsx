import { useEffect, useRef } from "react";
import { api } from "@/api/client";
import { ConflictError } from "@/api/types";
import { useEditorStore } from "@/stores/editorStore";

/**
 * Phase 5 editor: a plain textarea bound to the editor store with debounced
 * autosave and conflict handling. Phase 6 replaces the textarea with the
 * CodeMirror 6 inline Live Preview editor while keeping this load/save shell.
 */
export default function EditorPane({ path }: { path: string }) {
  const doc = useEditorStore((s) => s.docs[path]);
  const loadFile = useEditorStore((s) => s.loadFile);
  const setContent = useEditorStore((s) => s.setContent);
  const markSaved = useEditorStore((s) => s.markSaved);
  const setConflict = useEditorStore((s) => s.setConflict);
  const resolveKeepMine = useEditorStore((s) => s.resolveKeepMine);
  const timer = useRef<number | undefined>(undefined);

  useEffect(() => {
    void loadFile(path);
  }, [path, loadFile]);

  function scheduleSave() {
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(async () => {
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

  if (!doc || doc.loading) return <div className="empty-state">Loading…</div>;
  if (doc.missing) return <div className="empty-state">This note no longer exists.</div>;

  return (
    <div className="editor-host" style={{ display: "flex", flexDirection: "column", height: "100%" }}>
      {doc.conflict && (
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
      <textarea
        className="plain-editor"
        style={{ flex: 1, minHeight: 0, height: "auto" }}
        spellCheck={false}
        value={doc.content}
        onChange={(e) => {
          setContent(path, e.target.value);
          scheduleSave();
        }}
      />
    </div>
  );
}
