import { EditorState, Range } from "@codemirror/state";
import { Decoration, DecorationSet } from "@codemirror/view";
import { syntaxTree } from "@codemirror/language";

/**
 * Cursor-aware Live Preview decorations. For each markdown construct we style
 * the rendered content and HIDE the syntax marks (`#`, `**`, `` ` ``) — except
 * on the line the cursor/selection is on, where the raw source is revealed so
 * it can be edited. This is the core of the Obsidian-style editing feel.
 */

const HEADING_CLASS: Record<string, string> = {
  ATXHeading1: "cm-h1",
  ATXHeading2: "cm-h2",
  ATXHeading3: "cm-h3",
  ATXHeading4: "cm-h4",
  ATXHeading5: "cm-h5",
  ATXHeading6: "cm-h6",
};

const INLINE_CLASS: Record<string, string> = {
  StrongEmphasis: "cm-strong",
  Emphasis: "cm-em",
  InlineCode: "cm-inline-code",
};

// Syntax marks hidden when the cursor isn't on their line.
const HIDE_MARKS = new Set(["EmphasisMark", "CodeMark"]);

const hidden = Decoration.replace({});

/** True if any selection range touches the line spanning [from, to]. */
function selectionTouchesLine(state: EditorState, from: number, to: number): boolean {
  for (const r of state.selection.ranges) {
    if (r.from <= to && r.to >= from) {
      return true;
    }
  }
  return false;
}

export function buildDecorations(state: EditorState): DecorationSet {
  const out: Range<Decoration>[] = [];
  const tree = syntaxTree(state);

  tree.iterate({
    enter: (node) => {
      const name = node.name;

      // Headings: style the text, hide the leading "## " when off-cursor.
      const headingClass = HEADING_CLASS[name];
      if (headingClass) {
        const line = state.doc.lineAt(node.from);
        const text = state.doc.sliceString(line.from, line.to);
        const match = /^(\s{0,3}#{1,6}\s+)/.exec(text);
        const markEnd = line.from + (match ? match[1].length : 0);
        if (markEnd < line.to) {
          out.push(Decoration.mark({ class: headingClass }).range(markEnd, line.to));
        }
        if (markEnd > line.from && !selectionTouchesLine(state, line.from, line.to)) {
          out.push(hidden.range(line.from, markEnd));
        }
        return;
      }

      // Inline emphasis / code: style the content span.
      const inlineClass = INLINE_CLASS[name];
      if (inlineClass) {
        out.push(Decoration.mark({ class: inlineClass }).range(node.from, node.to));
        return;
      }

      // Hide the inline syntax marks themselves when off-cursor.
      if (HIDE_MARKS.has(name) && node.to > node.from) {
        const line = state.doc.lineAt(node.from);
        if (!selectionTouchesLine(state, line.from, line.to)) {
          out.push(hidden.range(node.from, node.to));
        }
      }
    },
  });

  return Decoration.set(out, true);
}
