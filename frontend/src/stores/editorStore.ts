import { create } from "zustand";
import { api } from "@/api/client";

export interface ConflictState {
  serverContent: string;
  serverMtime: number;
}

export interface EditorDoc {
  content: string;
  baseMtime: number;
  dirty: boolean;
  loading: boolean;
  missing: boolean;
  conflict: ConflictState | null;
}

interface EditorState {
  docs: Record<string, EditorDoc>;
  loadFile: (path: string) => Promise<void>;
  setContent: (path: string, content: string) => void;
  markSaved: (path: string, mtime: number) => void;
  /** A WS "modified" arrived for this path; reconcile against local state. */
  applyExternal: (path: string, content: string, mtime: number) => void;
  setConflict: (path: string, serverContent: string, serverMtime: number) => void;
  resolveKeepMine: (path: string) => void;
  markMissing: (path: string) => void;
}

const EMPTY: EditorDoc = {
  content: "",
  baseMtime: 0,
  dirty: false,
  loading: false,
  missing: false,
  conflict: null,
};

export const useEditorStore = create<EditorState>((set, get) => ({
  docs: {},

  async loadFile(path) {
    set((s) => ({ docs: { ...s.docs, [path]: { ...EMPTY, ...s.docs[path], loading: true } } }));
    try {
      const note = await api.readFile(path);
      set((s) => ({
        docs: {
          ...s.docs,
          [path]: { content: note.content, baseMtime: note.mtime, dirty: false, loading: false, missing: false, conflict: null },
        },
      }));
    } catch {
      set((s) => ({ docs: { ...s.docs, [path]: { ...EMPTY, ...s.docs[path], loading: false, missing: true } } }));
    }
  },

  setContent(path, content) {
    set((s) => {
      const doc = s.docs[path] ?? EMPTY;
      if (doc.content === content) return s;
      return { docs: { ...s.docs, [path]: { ...doc, content, dirty: true } } };
    });
  },

  markSaved(path, mtime) {
    set((s) => {
      const doc = s.docs[path];
      if (!doc) return s;
      return { docs: { ...s.docs, [path]: { ...doc, baseMtime: mtime, dirty: false, conflict: null } } };
    });
  },

  applyExternal(path, content, mtime) {
    const doc = get().docs[path];
    if (!doc) return; // not open
    // Our own just-saved echo: same mtime -> nothing to do (prevents reload flicker).
    if (mtime === doc.baseMtime) return;
    if (doc.dirty) {
      // Local unsaved edits + external change -> conflict for the user to resolve.
      set((s) => ({ docs: { ...s.docs, [path]: { ...doc, conflict: { serverContent: content, serverMtime: mtime } } } }));
    } else {
      set((s) => ({ docs: { ...s.docs, [path]: { ...doc, content, baseMtime: mtime, dirty: false } } }));
    }
  },

  setConflict(path, serverContent, serverMtime) {
    set((s) => {
      const doc = s.docs[path] ?? EMPTY;
      return { docs: { ...s.docs, [path]: { ...doc, conflict: { serverContent, serverMtime } } } };
    });
  },

  resolveKeepMine(path) {
    set((s) => {
      const doc = s.docs[path];
      if (!doc || !doc.conflict) return s;
      // Adopt server mtime as base so the next save overwrites cleanly; keep our content.
      return { docs: { ...s.docs, [path]: { ...doc, baseMtime: doc.conflict.serverMtime, conflict: null, dirty: true } } };
    });
  },

  markMissing(path) {
    set((s) => {
      const doc = s.docs[path] ?? EMPTY;
      return { docs: { ...s.docs, [path]: { ...doc, missing: true } } };
    });
  },
}));
