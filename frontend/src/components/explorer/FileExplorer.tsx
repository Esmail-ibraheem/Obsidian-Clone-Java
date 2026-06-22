import { useEffect, useState } from "react";
import { api } from "@/api/client";
import { FileNode } from "@/api/types";
import { useVaultStore } from "@/stores/vaultStore";
import { useWorkspaceStore } from "@/stores/workspaceStore";
import TreeNode from "@/components/explorer/TreeNode";

export interface ContextTarget {
  x: number;
  y: number;
  node: FileNode | null; // null => vault root
}

export default function FileExplorer() {
  const tree = useVaultStore((s) => s.tree);
  const loadTree = useVaultStore((s) => s.loadTree);
  const openTab = useWorkspaceStore((s) => s.openTab);
  const [menu, setMenu] = useState<ContextTarget | null>(null);

  useEffect(() => {
    const close = () => setMenu(null);
    window.addEventListener("click", close);
    return () => window.removeEventListener("click", close);
  }, []);

  function parentDir(node: FileNode | null): string {
    if (!node) return "";
    if (node.type === "FOLDER") return node.path;
    const slash = node.path.lastIndexOf("/");
    return slash >= 0 ? node.path.slice(0, slash) : "";
  }

  async function newNote(node: FileNode | null) {
    const name = window.prompt("New note name", "Untitled");
    if (!name) return;
    const dir = parentDir(node);
    const path = `${dir ? dir + "/" : ""}${name.endsWith(".md") ? name : name + ".md"}`;
    await api.createFile(path, "");
    await loadTree();
    openTab(path);
  }

  async function newFolder(node: FileNode | null) {
    const name = window.prompt("New folder name", "Folder");
    if (!name) return;
    const dir = parentDir(node);
    await api.createFolder(`${dir ? dir + "/" : ""}${name}`);
    await loadTree();
  }

  async function rename(node: FileNode) {
    const next = window.prompt("Rename to", node.name);
    if (!next || next === node.name) return;
    const slash = node.path.lastIndexOf("/");
    const dir = slash >= 0 ? node.path.slice(0, slash + 1) : "";
    await api.rename(node.path, `${dir}${next}`);
    await loadTree();
  }

  async function remove(node: FileNode) {
    if (!window.confirm(`Delete "${node.name}"?`)) return;
    await api.deleteEntry(node.path);
    await loadTree();
  }

  return (
    <>
      <div className="sidebar-header">
        <span>Files</span>
        <span>
          <button
            title="New note"
            style={{ background: "transparent", border: "none", color: "inherit", cursor: "pointer" }}
            onClick={() => newNote(null)}
          >
            ✎
          </button>
        </span>
      </div>
      <div
        className="sidebar-body"
        onContextMenu={(e) => {
          e.preventDefault();
          setMenu({ x: e.clientX, y: e.clientY, node: null });
        }}
      >
        {tree.map((node) => (
          <TreeNode
            key={node.path}
            node={node}
            depth={0}
            onContextMenu={(e, n) => {
              e.preventDefault();
              e.stopPropagation();
              setMenu({ x: e.clientX, y: e.clientY, node: n });
            }}
          />
        ))}
      </div>

      {menu && (
        <div className="context-menu" style={{ left: menu.x, top: menu.y }} onClick={(e) => e.stopPropagation()}>
          <button onClick={() => { void newNote(menu.node); setMenu(null); }}>New note</button>
          <button onClick={() => { void newFolder(menu.node); setMenu(null); }}>New folder</button>
          {menu.node && (
            <>
              <button onClick={() => { void rename(menu.node!); setMenu(null); }}>Rename</button>
              <button onClick={() => { void remove(menu.node!); setMenu(null); }}>Delete</button>
            </>
          )}
        </div>
      )}
    </>
  );
}
