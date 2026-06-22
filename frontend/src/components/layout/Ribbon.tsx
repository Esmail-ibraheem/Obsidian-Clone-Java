import { api } from "@/api/client";
import { useVaultStore } from "@/stores/vaultStore";
import { useWorkspaceStore } from "@/stores/workspaceStore";

/** Left icon bar (Obsidian's ribbon). M1: new note + reload. */
export default function Ribbon() {
  const loadTree = useVaultStore((s) => s.loadTree);
  const openTab = useWorkspaceStore((s) => s.openTab);

  async function newNote() {
    const name = window.prompt("New note name", "Untitled");
    if (!name) return;
    const path = name.endsWith(".md") ? name : `${name}.md`;
    await api.createFile(path, `# ${name.replace(/\.md$/, "")}\n\n`);
    await loadTree();
    openTab(path);
  }

  return (
    <div className="ribbon">
      <button title="New note" onClick={newNote}>
        ✎
      </button>
      <button title="Reload vault" onClick={() => loadTree()}>
        ⟳
      </button>
    </div>
  );
}
