import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, expect, test, vi } from "vitest";
import App from "@/App";

beforeEach(() => {
  vi.stubGlobal(
    "fetch",
    vi.fn(() =>
      Promise.resolve({ json: () => Promise.resolve({ status: "ok" }) } as Response),
    ),
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
});

test("renders backend health status from /api/health", async () => {
  render(<App />);
  await waitFor(() => expect(screen.getByTestId("health")).toHaveTextContent("ok"));
});
