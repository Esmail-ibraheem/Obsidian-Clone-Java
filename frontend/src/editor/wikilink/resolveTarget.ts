/**
 * Resolve a wikilink target to a vault file path, mirroring the backend's
 * resolution (com.obsidianclone.index.LinkGraph): exact path (±.md), then
 * basename match (shortest path) — but only for bare names, not folder paths.
 */
export function resolveTarget(target: string, files: string[]): string | null {
  const t = target.trim().replace(/\\/g, "/");
  if (!t) return null;

  if (files.includes(t)) return t;
  if (!t.endsWith(".md") && files.includes(t + ".md")) return t + ".md";

  const lower = t.toLowerCase();
  for (const f of files) {
    if (f.toLowerCase() === lower || f.toLowerCase() === lower + ".md") return f;
  }

  if (!t.includes("/")) {
    const base = baseKey(t);
    const matches = files.filter((f) => baseKey(f) === base);
    if (matches.length > 0) {
      return matches.sort(
        (a, b) => a.split("/").length - b.split("/").length || a.localeCompare(b),
      )[0];
    }
  }
  return null;
}

function baseKey(path: string): string {
  const name = path.slice(path.lastIndexOf("/") + 1);
  return (name.endsWith(".md") ? name.slice(0, -3) : name).toLowerCase();
}
