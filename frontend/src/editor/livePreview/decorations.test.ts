import { describe, expect, it } from "vitest";
import { EditorState } from "@codemirror/state";
import { ensureSyntaxTree } from "@codemirror/language";
import { markdown, markdownLanguage } from "@codemirror/lang-markdown";
import { buildDecorations, LivePreviewContext } from "@/editor/livePreview/decorations";
import { ImageEmbedWidget, WikiLinkWidget } from "@/editor/livePreview/widgets";

interface Deco {
  from: number;
  to: number;
  hidden: boolean;
  cls?: string;
}

function decosFor(doc: string, cursor: number): Deco[] {
  const state = EditorState.create({
    doc,
    selection: { anchor: cursor },
    extensions: [markdown({ base: markdownLanguage })],
  });
  // Force a full parse so syntaxTree() is complete without a view/viewport.
  ensureSyntaxTree(state, state.doc.length, 5000);

  const set = buildDecorations(state);
  const result: Deco[] = [];
  const iter = set.iter();
  while (iter.value) {
    const cls = (iter.value.spec as { class?: string })?.class;
    result.push({ from: iter.from, to: iter.to, hidden: !cls, cls });
    iter.next();
  }
  return result;
}

describe("buildDecorations (Live Preview)", () => {
  it("hides heading marks and styles content when the cursor is elsewhere", () => {
    const d = decosFor("## Title\n\nbody", 11); // cursor in 'body'
    expect(d.some((x) => x.hidden && x.from === 0 && x.to === 3)).toBe(true);
    expect(d.some((x) => x.cls === "cm-h2" && x.from === 3)).toBe(true);
  });

  it("reveals heading marks when the cursor is on the heading line", () => {
    const d = decosFor("## Title\n\nbody", 1);
    expect(d.some((x) => x.hidden && x.from === 0 && x.to === 3)).toBe(false);
    expect(d.some((x) => x.cls === "cm-h2")).toBe(true);
  });

  it("hides bold marks off-cursor and styles the content", () => {
    const d = decosFor("**bold** here\n\nx", 15); // cursor on last line
    expect(d.some((x) => x.hidden && x.from === 0 && x.to === 2)).toBe(true);
    expect(d.some((x) => x.hidden && x.from === 6 && x.to === 8)).toBe(true);
    // The whole StrongEmphasis span is styled bold; the marks are hidden above,
    // so only "bold" renders.
    expect(d.some((x) => x.cls === "cm-strong" && x.from === 0 && x.to === 8)).toBe(true);
  });

  it("reveals bold marks when the cursor is inside the bold span", () => {
    const d = decosFor("**bold** here\n\nx", 4);
    expect(d.some((x) => x.hidden)).toBe(false);
    expect(d.some((x) => x.cls === "cm-strong")).toBe(true);
  });

  it("styles inline code and hides backticks off-cursor", () => {
    const d = decosFor("a `code` b\n\nx", 12); // cursor on last line
    expect(d.some((x) => x.cls === "cm-inline-code")).toBe(true);
    expect(d.some((x) => x.hidden)).toBe(true);
  });
});

function widgetsFor(doc: string, cursor: number, files: string[]) {
  const state = EditorState.create({
    doc,
    selection: { anchor: cursor },
    extensions: [markdown({ base: markdownLanguage })],
  });
  ensureSyntaxTree(state, state.doc.length, 5000);
  const context: LivePreviewContext = { getFiles: () => files, onOpen: () => {} };
  const set = buildDecorations(state, context);

  const out: unknown[] = [];
  const iter = set.iter();
  while (iter.value) {
    const widget = (iter.value.spec as { widget?: unknown }).widget;
    if (widget) out.push(widget);
    iter.next();
  }
  return out;
}

describe("buildDecorations (wikilinks)", () => {
  it("renders a resolved wikilink widget off-cursor", () => {
    const widgets = widgetsFor("see [[Welcome]] here\n\nx", 22, ["Welcome.md"]);
    const link = widgets.find((w): w is WikiLinkWidget => w instanceof WikiLinkWidget);
    expect(link).toBeTruthy();
    expect(link!.resolved).toBe(true);
    expect(link!.target).toBe("Welcome");
  });

  it("marks a wikilink to a missing note as unresolved", () => {
    const widgets = widgetsFor("see [[Ghost]] here\n\nx", 20, ["Welcome.md"]);
    const link = widgets.find((w): w is WikiLinkWidget => w instanceof WikiLinkWidget);
    expect(link!.resolved).toBe(false);
  });

  it("uses the alias as display text", () => {
    const widgets = widgetsFor("see [[Welcome|home]] x\n\ny", 24, ["Welcome.md"]);
    const link = widgets.find((w): w is WikiLinkWidget => w instanceof WikiLinkWidget);
    expect(link!.display).toBe("home");
  });

  it("renders an image embed widget", () => {
    const widgets = widgetsFor("![[img.png]]\n\nx", 14, ["img.png"]);
    expect(widgets.some((w) => w instanceof ImageEmbedWidget)).toBe(true);
  });

  it("does not render a widget on the cursor's own line", () => {
    const widgets = widgetsFor("see [[Welcome]] here", 6, ["Welcome.md"]);
    expect(widgets.length).toBe(0);
  });

  it("ignores wikilinks inside inline code", () => {
    const widgets = widgetsFor("`[[Welcome]]` and text\n\nx", 24, ["Welcome.md"]);
    expect(widgets.length).toBe(0);
  });
});
