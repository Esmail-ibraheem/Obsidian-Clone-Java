import { useWorkspaceStore } from "@/stores/workspaceStore";
import EditorPane from "@/components/workspace/EditorPane";
import TabBar from "@/components/workspace/TabBar";

/** Renders the open panes side by side (flat split model for M1). */
export default function PaneTree() {
  const panes = useWorkspaceStore((s) => s.panes);

  return (
    <div className="workspace">
      {panes.map((pane) => (
        <div className="pane" key={pane.id}>
          <TabBar pane={pane} />
          <div className="pane-content">
            {pane.active ? (
              <EditorPane key={pane.active} path={pane.active} />
            ) : (
              <div className="empty-state">No note open</div>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
