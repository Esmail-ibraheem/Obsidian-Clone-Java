import { afterEach, describe, expect, it, vi } from "vitest";
import { api } from "@/api/client";
import { ConflictError } from "@/api/types";

afterEach(() => vi.unstubAllGlobals());

function stubFetch(impl: (url: string, init?: RequestInit) => unknown) {
  vi.stubGlobal("fetch", vi.fn((url: string, init?: RequestInit) => Promise.resolve(impl(url, init))));
}

describe("api client", () => {
  it("getTree parses the JSON tree", async () => {
    stubFetch(() => ({ ok: true, status: 200, json: () => Promise.resolve([{ name: "a.md", path: "a.md", type: "FILE", children: null }]) }));
    const tree = await api.getTree();
    expect(tree[0].name).toBe("a.md");
  });

  it("writeFile throws ConflictError on HTTP 409", async () => {
    stubFetch(() => ({
      ok: false,
      status: 409,
      json: () => Promise.resolve({ message: "changed", currentContent: "server", currentMtime: 5 }),
    }));
    await expect(api.writeFile("a.md", "mine", 1)).rejects.toBeInstanceOf(ConflictError);
  });

  it("writeFile sends content as the request body", async () => {
    let captured: RequestInit | undefined;
    stubFetch((_url, init) => {
      captured = init;
      return { ok: true, status: 200, json: () => Promise.resolve({ path: "a.md", content: "mine", mtime: 9, size: 4 }) };
    });
    const saved = await api.writeFile("a.md", "mine");
    expect(saved.mtime).toBe(9);
    expect(captured?.method).toBe("PUT");
    expect(captured?.body).toBe("mine");
  });

  it("attachmentUrl encodes path segments", () => {
    expect(api.attachmentUrl("Folder/My Image.png")).toBe("/api/attachments/Folder/My%20Image.png");
  });
});
