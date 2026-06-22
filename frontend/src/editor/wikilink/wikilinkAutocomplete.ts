import { CompletionContext, CompletionResult } from "@codemirror/autocomplete";

/**
 * Autocomplete for wikilinks: after typing `[[`, suggests vault note names and
 * inserts `Name]]` on selection. `getFiles` returns vault-relative paths.
 */
export function wikilinkCompletionSource(getFiles: () => string[]) {
  return (context: CompletionContext): CompletionResult | null => {
    const before = context.matchBefore(/\[\[[^\]\n]*$/);
    if (!before) return null;
    // Don't pop up on an empty `[[` unless explicitly invoked.
    if (before.text === "[[" && !context.explicit) {
      // still offer, but allow filtering as the user types
    }

    const options = getFiles()
      .filter((path) => path.endsWith(".md"))
      .map((path) => {
        const name = path.slice(path.lastIndexOf("/") + 1).replace(/\.md$/, "");
        return {
          label: name,
          detail: path.includes("/") ? path : undefined,
          apply: `${name}]]`,
          type: "text",
        };
      });

    return { from: before.from + 2, options, filter: true };
  };
}
