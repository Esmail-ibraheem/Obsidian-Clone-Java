import { DecorationSet, EditorView, ViewPlugin, ViewUpdate } from "@codemirror/view";
import { buildDecorations, LivePreviewContext } from "@/editor/livePreview/decorations";

/**
 * Recomputes Live Preview decorations whenever the document or selection
 * changes, so syntax reveals/hides as the cursor moves between lines. The
 * context supplies link resolution + navigation for wikilink widgets.
 */
export function livePreviewExtension(context: LivePreviewContext) {
  return ViewPlugin.fromClass(
    class {
      decorations: DecorationSet;

      constructor(view: EditorView) {
        this.decorations = buildDecorations(view.state, context);
      }

      update(update: ViewUpdate) {
        if (update.docChanged || update.selectionSet || update.viewportChanged) {
          this.decorations = buildDecorations(update.state, context);
        }
      }
    },
    {
      decorations: (plugin) => plugin.decorations,
    },
  );
}
