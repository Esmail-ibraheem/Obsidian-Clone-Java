import { useEditorStore } from "@/stores/editorStore";
import { Pane, useWorkspaceStore } from "@/stores/workspaceStore";

export default function TabBar({ pane }: { pane: Pane }) {
  const setActiveTab = useWorkspaceStore((s) => s.setActiveTab);
  const closeTab = useWorkspaceStore((s) => s.closeTab);
  const splitActive = useWorkspaceStore((s) => s.splitActive);
  const setActivePane = useWorkspaceStore((s) => s.setActivePane);
  const docs = useEditorStore((s) => s.docs);

  return (
    <div className="tab-bar" onMouseDown={() => setActivePane(pane.id)}>
      {pane.tabs.map((path) => {
        const name = (path.split("/").pop() ?? path).replace(/\.md$/, "");
        const dirty = docs[path]?.dirty;
        return (
          <div
            key={path}
            className={`tab${pane.active === path ? " active" : ""}`}
            onClick={() => setActiveTab(pane.id, path)}
          >
            <span>{name}</span>
            {dirty ? <span className="dirty-dot" /> : null}
            <button
              className="close"
              title="Close tab"
              onClick={(e) => {
                e.stopPropagation();
                closeTab(pane.id, path);
              }}
            >
              ×
            </button>
          </div>
        );
      })}
      <div style={{ flex: 1 }} />
      <button className="close" title="Split right" style={{ padding: "0 10px" }} onClick={() => splitActive()}>
        ⊟
      </button>
    </div>
  );
}
