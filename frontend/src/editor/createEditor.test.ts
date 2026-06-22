import { describe, expect, it, vi } from "vitest";
import { createEditor } from "@/editor/createEditor";

describe("createEditor", () => {
  it("mounts a CodeMirror view with Live Preview and reports edits", () => {
    const parent = document.createElement("div");
    document.body.appendChild(parent);
    const onChange = vi.fn();

    const view = createEditor({
      parent,
      doc: "# Heading\n\n**bold**, `code`, and [[Welcome]]\n\n![[img.png]]",
      onChange,
      context: { getFiles: () => ["Welcome.md", "img.png"], onOpen: vi.fn() },
    });

    try {
      expect(view.dom).toBeTruthy();
      expect(view.state.doc.toString()).toContain("Heading");

      view.dispatch({ changes: { from: view.state.doc.length, insert: " more" } });
      expect(onChange).toHaveBeenCalledOnce();
      expect(onChange).toHaveBeenLastCalledWith(expect.stringContaining("more"));
    } finally {
      view.destroy();
      parent.remove();
    }
  });
});
