import { DecorationSet, EditorView, ViewPlugin, ViewUpdate } from "@codemirror/view";
import { buildDecorations } from "@/editor/livePreview/decorations";

/**
 * Recomputes Live Preview decorations whenever the document or selection
 * changes, so syntax reveals/hides as the cursor moves between lines.
 */
export const livePreview = ViewPlugin.fromClass(
  class {
    decorations: DecorationSet;

    constructor(view: EditorView) {
      this.decorations = buildDecorations(view.state);
    }

    update(update: ViewUpdate) {
      if (update.docChanged || update.selectionSet || update.viewportChanged) {
        this.decorations = buildDecorations(update.state);
      }
    }
  },
  {
    decorations: (plugin) => plugin.decorations,
  },
);
