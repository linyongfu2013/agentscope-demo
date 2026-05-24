import "@testing-library/jest-dom/vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ChatComposer } from "./ChatComposer";
import type { AgentConfig, ModelConfig, ToolConfig } from "../types/config";

const models: ModelConfig[] = [
  { id: "text-1", name: "Text", modelType: "TEXT", provider: "mock", modelName: "text" },
  { id: "reason-1", name: "Reasoner", modelType: "REASONING", provider: "mock", modelName: "reasoner" }
];

const tools: ToolConfig[] = [
  { id: "tool-1", name: "Calculator", toolType: "BUILTIN", description: "Run calculations" }
];
const agents: AgentConfig[] = [
  { id: "agent-1", name: "Research Agent", toolIds: ["tool-1"], knowledgeBaseIds: [] }
];

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("ChatComposer", () => {
  it("sends selected reasoning model and HIGH effort", () => {
    const submit = vi.fn();
    render(<ChatComposer agents={agents} models={models} tools={tools} streaming={false} onSubmit={submit} onTranscribe={vi.fn()} />);

    fireEvent.change(screen.getByLabelText("Agent"), { target: { value: "agent-1" } });
    fireEvent.change(screen.getByLabelText("Model"), { target: { value: "reason-1" } });
    fireEvent.change(screen.getByLabelText("Reasoning effort"), { target: { value: "HIGH" } });
    fireEvent.click(screen.getByLabelText("Calculator"));
    fireEvent.change(screen.getByPlaceholderText("Message AgentScope"), { target: { value: "plan this" } });
    fireEvent.click(screen.getByLabelText("Send message"));

    expect(submit).toHaveBeenCalledWith({
      prompt: "plan this",
      agentConfigId: "agent-1",
      modelId: "reason-1",
      reasoningEffort: "HIGH",
      toolIds: ["tool-1"]
    });
  });

  it("shows reasoning effort only for reasoning models", () => {
    render(<ChatComposer agents={[]} models={models} tools={[]} streaming={false} onSubmit={vi.fn()} onTranscribe={vi.fn()} />);

    expect(screen.queryByLabelText("Reasoning effort")).not.toBeInTheDocument();

    fireEvent.change(screen.getByLabelText("Model"), { target: { value: "reason-1" } });

    expect(screen.getByLabelText("Reasoning effort")).toBeInTheDocument();
  });

  it("records audio and returns the blob when stopped", async () => {
    const submit = vi.fn();
    const transcribe = vi.fn();
    const chunks = [new Blob(["voice"], { type: "audio/webm" })];
    class FakeMediaRecorder extends EventTarget {
      static isTypeSupported = () => true;
      state = "inactive";
      ondataavailable: ((event: BlobEvent) => void) | null = null;
      onstop: (() => void) | null = null;

      start() {
        this.state = "recording";
      }

      stop() {
        this.state = "inactive";
        this.ondataavailable?.({ data: chunks[0] } as BlobEvent);
        this.onstop?.();
      }
    }
    vi.stubGlobal("MediaRecorder", FakeMediaRecorder);
    Object.defineProperty(navigator, "mediaDevices", {
      configurable: true,
      value: { getUserMedia: vi.fn().mockResolvedValue({ getTracks: () => [{ stop: vi.fn() }] }) }
    });

    render(<ChatComposer agents={[]} models={models} tools={[]} streaming={false} onSubmit={submit} onTranscribe={transcribe} />);

    fireEvent.change(screen.getByPlaceholderText("Message AgentScope"), { target: { value: "voice note" } });
    fireEvent.click(screen.getByLabelText("Record audio"));
    expect(await screen.findByLabelText("Stop recording")).toBeInTheDocument();
    fireEvent.click(screen.getByLabelText("Stop recording"));

    await vi.waitFor(() => expect(transcribe).toHaveBeenCalledTimes(1));
    expect(transcribe.mock.calls[0][0]).toBeInstanceOf(Blob);
    expect(submit).not.toHaveBeenCalled();
  });
});
