import { EditorState, Range } from "@codemirror/state";
import { Decoration, DecorationSet } from "@codemirror/view";
import { syntaxTree } from "@codemirror/language";
import { SyntaxNode } from "@lezer/common";
import { api } from "@/api/client";
import { resolveTarget } from "@/editor/wikilink/resolveTarget";
import { ImageEmbedWidget, WikiLinkWidget } from "@/editor/livePreview/widgets";

/** Context the editor provides so links can resolve + navigate. */
export interface LivePreviewContext {
  getFiles: () => string[];
  onOpen: (target: string, split: boolean) => void;
}

const WIKILINK = /(!?)\[\[([^\]\n]+)\]\]/g;
const IMAGE_EXT = /\.(png|jpe?g|gif|svg|webp|bmp)$/i;

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

/** True if the position is inside any code construct (inline or fenced). */
function isInCode(state: EditorState, pos: number): boolean {
  let node: SyntaxNode | null = syntaxTree(state).resolveInner(pos, 1);
  for (; node; node = node.parent) {
    if (/Code/.test(node.name)) return true;
  }
  return false;
}

function scanWikilinks(state: EditorState, context: LivePreviewContext, out: Range<Decoration>[]): void {
  const text = state.doc.toString();
  WIKILINK.lastIndex = 0;
  let m: RegExpExecArray | null;
  while ((m = WIKILINK.exec(text)) !== null) {
    const from = m.index;
    const to = from + m[0].length;
    if (isInCode(state, from)) continue;

    const line = state.doc.lineAt(from);
    if (selectionTouchesLine(state, line.from, line.to)) continue; // reveal raw on the cursor line

    const embed = m[1] === "!";
    const inner = m[2];
    const pipe = inner.indexOf("|");
    const rawTarget = (pipe >= 0 ? inner.slice(0, pipe) : inner).trim();
    const alias = pipe >= 0 ? inner.slice(pipe + 1).trim() : "";
    const target = rawTarget.replace(/[#^].*$/, "").trim(); // drop heading/block anchor

    if (embed && IMAGE_EXT.test(target)) {
      const path = resolveTarget(target, context.getFiles()) ?? target;
      out.push(
        Decoration.replace({
          widget: new ImageEmbedWidget(api.attachmentUrl(path), alias || target),
        }).range(from, to),
      );
    } else {
      const resolved = resolveTarget(target, context.getFiles());
      out.push(
        Decoration.replace({
          widget: new WikiLinkWidget(alias || rawTarget, target, resolved !== null, context.onOpen),
        }).range(from, to),
      );
    }
  }
}

export function buildDecorations(state: EditorState, context?: LivePreviewContext): DecorationSet {
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

  if (context) {
    scanWikilinks(state, context, out);
  }

  return Decoration.set(out, true);
}
