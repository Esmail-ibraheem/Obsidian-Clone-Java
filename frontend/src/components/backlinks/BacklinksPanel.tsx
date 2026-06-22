import { useEffect, useState } from "react";
import { api } from "@/api/client";
import { BacklinkEntry } from "@/api/types";
import { activePath, useWorkspaceStore } from "@/stores/workspaceStore";

export default function BacklinksPanel() {
  const active = useWorkspaceStore(activePath);
  const openTab = useWorkspaceStore((s) => s.openTab);
  const [links, setLinks] = useState<BacklinkEntry[]>([]);

  useEffect(() => {
    if (!active) {
      setLinks([]);
      return;
    }
    let cancelled = false;
    api
      .getBacklinks(active)
      .then((l) => !cancelled && setLinks(l))
      .catch(() => !cancelled && setLinks([]));
    return () => {
      cancelled = true;
    };
  }, [active]);

  return (
    <>
      <div className="sidebar-header">Backlinks</div>
      <div className="sidebar-body">
        {!active && <div style={{ color: "var(--text-faint)", padding: 6 }}>No note open</div>}
        {active && links.length === 0 && (
          <div style={{ color: "var(--text-faint)", padding: 6 }}>No backlinks found</div>
        )}
        {links.map((bl, i) => (
          <div className="backlink-group" key={`${bl.sourcePath}:${bl.line}:${i}`}>
            <div className="backlink-source" onClick={() => openTab(bl.sourcePath)}>
              {(bl.sourcePath.split("/").pop() ?? bl.sourcePath).replace(/\.md$/, "")}
            </div>
            <div className="backlink-snippet">{bl.snippet}</div>
          </div>
        ))}
      </div>
    </>
  );
}
