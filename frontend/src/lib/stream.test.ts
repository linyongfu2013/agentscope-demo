import { describe, expect, it } from "vitest";
import { labelOf, mergeStreamEvent, type ChatStreamEvent } from "./stream";

describe("mergeStreamEvent", () => {
  it("keeps intermediate AgentScope events collapsed by default", () => {
    const event: ChatStreamEvent = {
      id: "1",
      runId: "run-1",
      sequence: 1,
      type: "tool_result",
      collapsed: true,
      payload: { content: "Observation: calculator returned 4" }
    };

    const blocks = mergeStreamEvent([], event);

    expect(blocks).toHaveLength(1);
    expect(blocks[0]).toMatchObject({
      id: "1",
      type: "tool_result",
      collapsed: true,
      content: "Observation: calculator returned 4"
    });
  });

  it("appends final answers expanded", () => {
    const event: ChatStreamEvent = {
      id: "2",
      runId: "run-1",
      sequence: 2,
      type: "final_answer",
      collapsed: false,
      payload: { content: "Final **answer**" }
    };

    const blocks = mergeStreamEvent([], event);

    expect(blocks[0].collapsed).toBe(false);
    expect(blocks[0].content).toBe("Final **answer**");
  });

  it("labels model and voice events", () => {
    expect(labelOf("execution_strategy")).toBe("执行策略");
    expect(labelOf("model_resolved")).toBe("模型选择");
    expect(labelOf("warning")).toBe("提示");
    expect(labelOf("voice_transcription")).toBe("语音转写");
    expect(labelOf("audio_output")).toBe("语音输出");
  });

  it("carries audio payload metadata", () => {
    const event: ChatStreamEvent = {
      id: "3",
      runId: "run-1",
      sequence: 3,
      type: "audio_output",
      collapsed: false,
      payload: { audioUrl: "/api/audio/1", mimeType: "audio/mpeg" }
    };

    const blocks = mergeStreamEvent([], event);

    expect(blocks[0]).toMatchObject({
      audioUrl: "/api/audio/1",
      mimeType: "audio/mpeg"
    });
  });
});
