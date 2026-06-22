import { create } from "zustand";
import { api } from "@/api/client";
import { FileNode, VaultEvent } from "@/api/types";

interface VaultState {
  tree: FileNode[];
  loading: boolean;
  loadTree: () => Promise<void>;
  /** React to a live filesystem event from the WebSocket. */
  applyEvent: (event: VaultEvent) => void;
}

export const useVaultStore = create<VaultState>((set, get) => ({
  tree: [],
  loading: false,

  async loadTree() {
    set({ loading: true });
    try {
      const tree = await api.getTree();
      set({ tree });
    } finally {
      set({ loading: false });
    }
  },

  applyEvent(event) {
    // Structural changes alter the tree; a plain modify does not. Reloading the
    // whole tree is cheap for typical vaults and always consistent.
    if (event.type === "created" || event.type === "deleted") {
      void get().loadTree();
    }
  },
}));

/** Flatten the tree to file paths (depth-first). Exported for search/quick-switch later. */
export function flattenFiles(nodes: FileNode[]): string[] {
  const out: string[] = [];
  const walk = (list: FileNode[]) => {
    for (const node of list) {
      if (node.type === "FOLDER") {
        if (node.children) walk(node.children);
      } else {
        out.push(node.path);
      }
    }
  };
  walk(nodes);
  return out;
}
