import {
  BacklinkEntry,
  ConflictError,
  FileNode,
  NoteContent,
  RenameResult,
} from "@/api/types";

// Same-origin: the Vite dev server proxies /api to the backend, and in prod the
// backend serves the SPA itself.
const API = "/api";

async function asError(res: Response): Promise<never> {
  let message = `${res.status} ${res.statusText}`;
  try {
    const body = await res.json();
    if (body?.message) {
      message = body.message;
    }
  } catch {
    /* non-JSON error body */
  }
  throw new Error(message);
}

function encodePath(path: string): string {
  return encodeURIComponent(path);
}

export const api = {
  async getTree(): Promise<FileNode[]> {
    const res = await fetch(`${API}/vault/tree`);
    if (!res.ok) return asError(res);
    return res.json();
  },

  async readFile(path: string): Promise<NoteContent> {
    const res = await fetch(`${API}/files?path=${encodePath(path)}`);
    if (!res.ok) return asError(res);
    return res.json();
  },

  async writeFile(path: string, content: string, baseMtime?: number): Promise<NoteContent> {
    const qs = baseMtime != null ? `&baseMtime=${baseMtime}` : "";
    const res = await fetch(`${API}/files?path=${encodePath(path)}${qs}`, {
      method: "PUT",
      headers: { "Content-Type": "text/plain; charset=utf-8" },
      body: content,
    });
    if (res.status === 409) {
      const body = await res.json();
      throw new ConflictError(
        body.message ?? "conflict",
        body.currentContent ?? "",
        body.currentMtime ?? 0,
      );
    }
    if (!res.ok) return asError(res);
    return res.json();
  },

  async createFile(path: string, content = ""): Promise<NoteContent> {
    const res = await fetch(`${API}/files`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ path, type: "file", content }),
    });
    if (!res.ok) return asError(res);
    return res.json();
  },

  async createFolder(path: string): Promise<void> {
    const res = await fetch(`${API}/files`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ path, type: "folder" }),
    });
    if (!res.ok) return asError(res);
  },

  async deleteEntry(path: string): Promise<void> {
    const res = await fetch(`${API}/files?path=${encodePath(path)}`, { method: "DELETE" });
    if (!res.ok) return asError(res);
  },

  async rename(from: string, to: string, updateLinks = true): Promise<RenameResult> {
    const res = await fetch(`${API}/files/rename`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ from, to, updateLinks }),
    });
    if (!res.ok) return asError(res);
    return res.json();
  },

  async getBacklinks(path: string): Promise<BacklinkEntry[]> {
    const res = await fetch(`${API}/backlinks?path=${encodePath(path)}`);
    if (!res.ok) return asError(res);
    return res.json();
  },

  attachmentUrl(path: string): string {
    const parts = path.split("/").map(encodeURIComponent).join("/");
    return `${API}/attachments/${parts}`;
  },
};
