export type ChatStreamEvent = {
  id: string;
  runId: string;
  sequence: number;
  type: string;
  collapsed: boolean;
  payload: {
    content?: string;
    [key: string]: unknown;
  };
};

export type StreamBlock = {
  id: string;
  type: string;
  title: string;
  content: string;
  audioUrl?: string;
  mimeType?: string;
  collapsed: boolean;
};

const LABELS: Record<string, string> = {
  intent: "意图路由",
  rag_hint: "知识检索",
  reasoning: "执行过程",
  tool_result: "工具调用",
  task_summary: "任务计划",
  model_resolved: "模型选择",
  warning: "提示",
  voice_transcription: "语音转写",
  audio_output: "语音输出",
  final_answer: "最终结果",
  error: "错误"
};

export function labelOf(type: string): string {
  return LABELS[type] ?? type;
}

export function mergeStreamEvent(blocks: StreamBlock[], event: ChatStreamEvent): StreamBlock[] {
  const content = String(event.payload.content ?? "");
  const block: StreamBlock = {
    id: event.id,
    type: event.type,
    title: labelOf(event.type),
    content,
    audioUrl: typeof event.payload.audioUrl === "string" ? event.payload.audioUrl : undefined,
    mimeType: typeof event.payload.mimeType === "string" ? event.payload.mimeType : undefined,
    collapsed: event.collapsed
  };

  if (event.type === "reasoning" && blocks.at(-1)?.type === "reasoning") {
    return blocks.map((existing, index) =>
      index === blocks.length - 1
        ? { ...existing, content: existing.content + content, collapsed: true }
        : existing
    );
  }

  return [...blocks, block];
}

export async function* readSseEvents(response: Response): AsyncGenerator<ChatStreamEvent> {
  if (!response.body) {
    return;
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { value, done } = await reader.read();
    if (done) {
      break;
    }
    buffer += decoder.decode(value, { stream: true });
    const frames = buffer.split("\n\n");
    buffer = frames.pop() ?? "";

    for (const frame of frames) {
      const dataLine = frame.split("\n").find((line) => line.startsWith("data:"));
      if (dataLine) {
        yield JSON.parse(dataLine.slice(5).trim()) as ChatStreamEvent;
      }
    }
  }
}
