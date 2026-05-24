import "@testing-library/jest-dom/vitest";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ModelSettings } from "./ModelSettings";
import type { ModelConfig } from "../types/config";

const models: ModelConfig[] = [];

afterEach(() => {
  cleanup();
});

describe("ModelSettings", () => {
  it("creates a model config and refreshes the list", async () => {
    const onRefresh = vi.fn();
    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({ id: "model-1" })
    });
    vi.stubGlobal("fetch", fetchMock);

    render(<ModelSettings models={models} onRefresh={onRefresh} />);

    fireEvent.change(screen.getByLabelText("Name"), { target: { value: "GPT text" } });
    fireEvent.change(screen.getByLabelText("Provider"), { target: { value: "openai" } });
    fireEvent.change(screen.getByLabelText("Model type"), { target: { value: "TEXT" } });
    fireEvent.change(screen.getByLabelText("Model name"), { target: { value: "gpt-4.1" } });
    fireEvent.click(screen.getByRole("button", { name: "Save model" }));

    await waitFor(() => expect(onRefresh).toHaveBeenCalledTimes(1));
    expect(fetchMock).toHaveBeenCalledWith(
      "/api/model-configs",
      expect.objectContaining({
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: expect.stringContaining("\"modelType\":\"TEXT\"")
      })
    );
  });
});
