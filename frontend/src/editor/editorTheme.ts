import { EditorView } from "@codemirror/view";

/** Dark, prose-oriented theme + the Live Preview rendered styles. */
export const editorTheme = EditorView.theme(
  {
    "&": {
      height: "100%",
      backgroundColor: "var(--bg-primary)",
      color: "var(--text-normal)",
      fontSize: "16px",
    },
    ".cm-scroller": {
      fontFamily: "var(--font-text)",
      lineHeight: "1.6",
      padding: "16px max(24px, 8%)",
      overflow: "auto",
    },
    ".cm-content": { caretColor: "var(--text-normal)" },
    "&.cm-focused": { outline: "none" },
    ".cm-cursor, .cm-dropCursor": { borderLeftColor: "var(--text-normal)" },
    "&.cm-focused .cm-selectionBackground, .cm-selectionBackground, ::selection": {
      backgroundColor: "var(--bg-selection)",
    },
    ".cm-line": { padding: "0" },

    // ---- Live Preview rendered styles ----
    ".cm-h1": { fontSize: "1.9em", fontWeight: "700", lineHeight: "1.3" },
    ".cm-h2": { fontSize: "1.6em", fontWeight: "700", lineHeight: "1.3" },
    ".cm-h3": { fontSize: "1.35em", fontWeight: "600" },
    ".cm-h4": { fontSize: "1.18em", fontWeight: "600" },
    ".cm-h5": { fontSize: "1.05em", fontWeight: "600" },
    ".cm-h6": { fontSize: "1em", fontWeight: "600", color: "var(--text-muted)" },
    ".cm-strong": { fontWeight: "700" },
    ".cm-em": { fontStyle: "italic" },
    ".cm-inline-code": {
      fontFamily: "var(--font-mono)",
      fontSize: "0.9em",
      backgroundColor: "var(--bg-tertiary)",
      borderRadius: "4px",
      padding: "0.1em 0.3em",
    },
    ".cm-wikilink": {
      color: "var(--text-accent)",
      cursor: "pointer",
      textDecoration: "none",
    },
    ".cm-wikilink:hover": { textDecoration: "underline" },
    ".cm-wikilink-unresolved": { color: "#bf7c5e" },
    ".cm-embed-image": {
      maxWidth: "100%",
      borderRadius: "6px",
      display: "block",
      margin: "4px 0",
    },
  },
  { dark: true },
);
