import { describe, expect, it } from "vitest";
import { CompletionContext } from "@codemirror/autocomplete";
import { EditorState } from "@codemirror/state";
import { wikilinkCompletionSource } from "@/editor/wikilink/wikilinkAutocomplete";

const source = wikilinkCompletionSource(() => ["Welcome.md", "Folder/Nested Note.md"]);

function complete(doc: string, pos: number, explicit = true) {
  const state = EditorState.create({ doc });
  return source(new CompletionContext(state, pos, explicit));
}

describe("wikilinkCompletionSource", () => {
  it("offers note names after [[ and anchors completion after the brackets", () => {
    const result = complete("see [[Wel", 9);
    expect(result).not.toBeNull();
    expect(result!.from).toBe(6); // just after "[["
    expect(result!.options.map((o) => o.label)).toContain("Welcome");
  });

  it("inserts Name]] on apply", () => {
    const result = complete("[[", 2);
    const welcome = result!.options.find((o) => o.label === "Welcome");
    expect(welcome?.apply).toBe("Welcome]]");
  });

  it("returns null when not inside a wikilink", () => {
    expect(complete("hello world", 11)).toBeNull();
  });
});
