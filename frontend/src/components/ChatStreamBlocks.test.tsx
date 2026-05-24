import "@testing-library/jest-dom/vitest";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ChatStreamBlocks } from "./ChatStreamBlocks";
import type { StreamBlock } from "../lib/stream";

describe("ChatStreamBlocks", () => {
  it("renders non-final state in a collapsed details panel", () => {
    const blocks: StreamBlock[] = [
      {
        id: "1",
        type: "task_summary",
        title: "任务计划",
        content: "Task Plan: retrieve knowledge",
        collapsed: true
      }
    ];

    render(<ChatStreamBlocks blocks={blocks} />);

    const details = screen.getByTestId("block-task_summary") as HTMLDetailsElement;
    expect(details.open).toBe(false);
    expect(screen.getByText("任务计划")).toBeInTheDocument();
  });

  it("renders final answer outside collapsible panels", () => {
    const blocks: StreamBlock[] = [
      {
        id: "2",
        type: "final_answer",
        title: "最终结果",
        content: "Final answer",
        collapsed: false
      }
    ];

    render(<ChatStreamBlocks blocks={blocks} />);

    expect(screen.getByTestId("final-answer")).toHaveTextContent("Final answer");
  });

  it("renders audio output blocks with a player", () => {
    const blocks: StreamBlock[] = [
      {
        id: "3",
        type: "audio_output",
        title: "语音输出",
        content: "",
        audioUrl: "/api/audio/1",
        mimeType: "audio/mpeg",
        collapsed: false
      }
    ];

    render(<ChatStreamBlocks blocks={blocks} />);

    expect(screen.getByLabelText("Audio message")).toBeInTheDocument();
  });
});
