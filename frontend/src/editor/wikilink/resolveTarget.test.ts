import { describe, expect, it } from "vitest";
import { resolveTarget } from "@/editor/wikilink/resolveTarget";

const files = ["Welcome.md", "Folder/Nested Note.md", "x/Note.md", "img.png"];

describe("resolveTarget", () => {
  it("resolves a bare name by basename regardless of folder", () => {
    expect(resolveTarget("Nested Note", files)).toBe("Folder/Nested Note.md");
  });

  it("resolves an exact path with or without extension", () => {
    expect(resolveTarget("Folder/Nested Note", files)).toBe("Folder/Nested Note.md");
    expect(resolveTarget("Folder/Nested Note.md", files)).toBe("Folder/Nested Note.md");
  });

  it("returns null for an unknown target", () => {
    expect(resolveTarget("Ghost", files)).toBeNull();
  });

  it("does not basename-fallback when the target names a missing folder", () => {
    expect(resolveTarget("bogus/Note", files)).toBeNull();
  });

  it("resolves an image attachment by name", () => {
    expect(resolveTarget("img.png", files)).toBe("img.png");
  });

  it("is case-insensitive on the basename", () => {
    expect(resolveTarget("welcome", files)).toBe("Welcome.md");
  });
});
