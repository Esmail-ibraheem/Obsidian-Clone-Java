import { autocompletion } from "@codemirror/autocomplete";
import { defaultKeymap, history, historyKeymap } from "@codemirror/commands";
import { markdown } from "@codemirror/lang-markdown";
import { syntaxHighlighting, defaultHighlightStyle } from "@codemirror/language";
import { EditorState } from "@codemirror/state";
import { EditorView, keymap } from "@codemirror/view";
import { editorTheme } from "@/editor/editorTheme";
import { LivePreviewContext } from "@/editor/livePreview/decorations";
import { livePreviewExtension } from "@/editor/livePreview/livePreviewPlugin";
import { wikilinkCompletionSource } from "@/editor/wikilink/wikilinkAutocomplete";

export interface EditorOptions {
  parent: HTMLElement;
  doc: string;
  onChange: (doc: string) => void;
  context: LivePreviewContext;
}

/** Build a CodeMirror 6 markdown editor with inline Live Preview + wikilinks. */
export function createEditor(options: EditorOptions): EditorView {
  const changeListener = EditorView.updateListener.of((update) => {
    if (update.docChanged) {
      options.onChange(update.state.doc.toString());
    }
  });

  const state = EditorState.create({
    doc: options.doc,
    extensions: [
      history(),
      keymap.of([...defaultKeymap, ...historyKeymap]),
      markdown(),
      syntaxHighlighting(defaultHighlightStyle),
      autocompletion({ override: [wikilinkCompletionSource(options.context.getFiles)] }),
      EditorView.lineWrapping,
      editorTheme,
      livePreviewExtension(options.context),
      changeListener,
    ],
  });

  return new EditorView({ state, parent: options.parent });
}
