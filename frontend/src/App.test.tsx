import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, test, vi } from "vitest";

// Avoid opening a real SockJS connection in jsdom.
vi.mock("@/ws/wsClient", () => ({ connectVaultSocket: vi.fn(), disconnectVaultSocket: vi.fn() }));

vi.mock("@/api/client", () => ({
  api: {
    getTree: vi.fn(() =>
      Promise.resolve([{ name: "Welcome.md", path: "Welcome.md", type: "FILE", children: null }]),
    ),
    getBacklinks: vi.fn(() => Promise.resolve([])),
    readFile: vi.fn(() => Promise.resolve({ path: "Welcome.md", content: "# Hi", mtime: 1, size: 4 })),
  },
}));

import App from "@/App";

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  vi.clearAllMocks();
});

test("renders the shell and loads the vault tree", async () => {
  render(<App />);
  expect(screen.getByText("Files")).toBeInTheDocument();
  expect(screen.getByText("Backlinks")).toBeInTheDocument();
  await waitFor(() => expect(screen.getByText("Welcome")).toBeInTheDocument());
});
