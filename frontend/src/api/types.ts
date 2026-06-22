// Mirrors the backend DTOs (see com.obsidianclone.*).

export type FileNodeType = "FILE" | "FOLDER";

export interface FileNode {
  name: string;
  path: string;
  type: FileNodeType;
  children: FileNode[] | null;
}

export interface NoteContent {
  path: string;
  content: string;
  mtime: number;
  size: number;
}

export interface BacklinkEntry {
  sourcePath: string;
  line: number;
  snippet: string;
}

export interface RenameResult {
  from: string;
  to: string;
  updatedNotes: string[];
}

export type VaultChangeType = "created" | "modified" | "deleted";

export interface VaultEvent {
  type: VaultChangeType;
  path: string;
}

/** Thrown by writeFile when the server reports a 409 mtime conflict. */
export class ConflictError extends Error {
  readonly currentContent: string;
  readonly currentMtime: number;

  constructor(message: string, currentContent: string, currentMtime: number) {
    super(message);
    this.name = "ConflictError";
    this.currentContent = currentContent;
    this.currentMtime = currentMtime;
  }
}
