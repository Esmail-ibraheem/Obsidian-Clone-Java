import { useState, type MouseEvent } from "react";
import { FileNode } from "@/api/types";
import { useWorkspaceStore, activePath } from "@/stores/workspaceStore";

interface Props {
  node: FileNode;
  depth: number;
  onContextMenu: (e: MouseEvent, node: FileNode) => void;
}

export default function TreeNode({ node, depth, onContextMenu }: Props) {
  const [open, setOpen] = useState(depth < 1);
  const openTab = useWorkspaceStore((s) => s.openTab);
  const active = useWorkspaceStore(activePath);

  const indent = { paddingLeft: 6 + depth * 14 };

  if (node.type === "FOLDER") {
    return (
      <div className="tree-item">
        <div className="tree-row" style={indent} onClick={() => setOpen(!open)} onContextMenu={(e) => onContextMenu(e, node)}>
          <span className="tree-twisty">{open ? "▾" : "▸"}</span>
          <span>{node.name}</span>
        </div>
        {open &&
          node.children?.map((child) => (
            <TreeNode key={child.path} node={child} depth={depth + 1} onContextMenu={onContextMenu} />
          ))}
      </div>
    );
  }

  const label = node.name.replace(/\.md$/, "");
  return (
    <div className="tree-item">
      <div
        className={`tree-row${active === node.path ? " active" : ""}`}
        style={indent}
        onClick={(e) => openTab(node.path, { split: e.ctrlKey || e.metaKey })}
        onContextMenu={(e) => onContextMenu(e, node)}
      >
        <span className="tree-twisty" />
        <span>{label}</span>
      </div>
    </div>
  );
}
