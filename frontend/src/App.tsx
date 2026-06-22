import { useEffect, useState } from "react";

/**
 * Phase 0 walking-skeleton app: proves the frontend can reach the backend
 * through the Vite proxy. Replaced by the real Workspace layout in Phase 5.
 */
export default function App() {
  const [status, setStatus] = useState<string>("connecting…");

  useEffect(() => {
    fetch("/api/health")
      .then((r) => r.json())
      .then((d) => setStatus(d.status))
      .catch(() => setStatus("backend unreachable"));
  }, []);

  return (
    <div style={{ fontFamily: "sans-serif", padding: 24 }}>
      <h1>Obsidian Clone</h1>
      <p>
        backend health: <strong data-testid="health">{status}</strong>
      </p>
    </div>
  );
}
