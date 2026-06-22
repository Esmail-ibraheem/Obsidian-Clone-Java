import { useEffect } from "react";
import "@/styles/theme.css";
import Ribbon from "@/components/layout/Ribbon";
import FileExplorer from "@/components/explorer/FileExplorer";
import PaneTree from "@/components/workspace/PaneTree";
import BacklinksPanel from "@/components/backlinks/BacklinksPanel";
import { useVaultStore } from "@/stores/vaultStore";
import { connectVaultSocket } from "@/ws/wsClient";

export default function App() {
  const loadTree = useVaultStore((s) => s.loadTree);

  useEffect(() => {
    void loadTree();
    connectVaultSocket();
  }, [loadTree]);

  return (
    <div className="app">
      <Ribbon />
      <div className="sidebar">
        <FileExplorer />
      </div>
      <PaneTree />
      <div className="sidebar right">
        <BacklinksPanel />
      </div>
    </div>
  );
}
