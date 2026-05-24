import { Boxes, Database, MessageSquare, PanelLeft, Settings2, SquarePen, Wrench } from "lucide-react";
import { useEffect, useState } from "react";
import { AgentSettings } from "./components/AgentSettings";
import { ChatComposer } from "./components/ChatComposer";
import { ChatStreamBlocks } from "./components/ChatStreamBlocks";
import { ModelSettings } from "./components/ModelSettings";
import { ToolSettings } from "./components/ToolSettings";
import { fetchAgents, fetchModels, fetchTools, transcribeAudio } from "./lib/api";
import { mergeStreamEvent, readSseEvents, type StreamBlock } from "./lib/stream";
import type { AgentConfig, ChatSubmit, ModelConfig, ToolConfig } from "./types/config";

type View = "chat" | "models" | "tools" | "agents" | "knowledge";

const NAV_ITEMS: Array<{ id: View; label: string; icon: typeof MessageSquare }> = [
  { id: "chat", label: "Chat", icon: MessageSquare },
  { id: "models", label: "Models", icon: Settings2 },
  { id: "tools", label: "Tools", icon: Wrench },
  { id: "agents", label: "Agents", icon: Boxes },
  { id: "knowledge", label: "Knowledge", icon: Database }
];

function createErrorBlock(message: string): StreamBlock {
  return {
    id: `error-${Date.now()}`,
    type: "error",
    title: "错误",
    content: message,
    collapsed: false
  };
}

async function responseErrorMessage(response: Response): Promise<string> {
  let detail = "";
  try {
    detail = await response.text();
  } catch {
    detail = "";
  }
  const prefix = `请求失败 (${response.status})`;
  return detail.trim() ? `${prefix}: ${detail.trim()}` : prefix;
}

export function App() {
  const [blocks, setBlocks] = useState<StreamBlock[]>([]);
  const [streaming, setStreaming] = useState(false);
  const [activeView, setActiveView] = useState<View>("chat");
  const [models, setModels] = useState<ModelConfig[]>([]);
  const [tools, setTools] = useState<ToolConfig[]>([]);
  const [agents, setAgents] = useState<AgentConfig[]>([]);
  const [draftPrompt, setDraftPrompt] = useState<string | undefined>(undefined);

  async function refreshConfigs() {
    const [loadedModels, loadedTools, loadedAgents] = await Promise.all([fetchModels(), fetchTools(), fetchAgents()]);
    setModels(loadedModels);
    setTools(loadedTools);
    setAgents(loadedAgents);
  }

  useEffect(() => {
    let cancelled = false;
    Promise.all([fetchModels(), fetchTools(), fetchAgents()]).then(([loadedModels, loadedTools, loadedAgents]) => {
      if (!cancelled) {
        setModels(loadedModels);
        setTools(loadedTools);
        setAgents(loadedAgents);
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  async function submit(payload: ChatSubmit) {
    if (!payload.prompt.trim() || streaming) {
      return;
    }

    setBlocks([]);
    setStreaming(true);
    try {
      const response = await fetch("/api/chat/stream", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
      });
      if (!response.ok) {
        setBlocks([createErrorBlock(await responseErrorMessage(response))]);
        return;
      }
      for await (const streamEvent of readSseEvents(response)) {
        setBlocks((current) => mergeStreamEvent(current, streamEvent));
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : "未知网络错误";
      setBlocks([createErrorBlock(`请求异常: ${message}`)]);
    } finally {
      setStreaming(false);
    }
  }

  async function transcribe(blob: Blob) {
    setBlocks([]);
    setStreaming(true);
    try {
      const sttModelId = models.find((model) => model.modelType === "STT")?.id;
      const transcript = await transcribeAudio(blob, sttModelId);
      setDraftPrompt(transcript);
      if (transcript) {
        setBlocks([
          {
            id: `voice-${Date.now()}`,
            type: "assistant",
            title: "Voice transcript",
            content: transcript,
            collapsed: false
          }
        ]);
      }
    } catch (error) {
      const message = error instanceof Error ? error.message : "未知网络错误";
      setBlocks([createErrorBlock(`转写失败: ${message}`)]);
    } finally {
      setStreaming(false);
    }
  }

  return (
    <div className="flex min-h-screen bg-background text-foreground">
      <aside className="hidden w-72 shrink-0 border-r border-border bg-muted/40 p-3 md:block">
        <div className="mb-4 flex items-center justify-between">
          <button className="grid h-9 w-9 place-items-center rounded-md hover:bg-muted" aria-label="Toggle sidebar">
            <PanelLeft size={18} />
          </button>
          <button className="grid h-9 w-9 place-items-center rounded-md hover:bg-muted" aria-label="New chat">
            <SquarePen size={18} />
          </button>
        </div>
        <nav className="space-y-1">
          {NAV_ITEMS.map((item) => {
            const Icon = item.icon;
            return (
              <button
                key={item.id}
                onClick={() => setActiveView(item.id)}
                className={`flex w-full items-center gap-2 rounded-md px-3 py-2 text-left text-sm hover:bg-muted ${
                  activeView === item.id ? "bg-muted" : ""
                }`}
              >
                <Icon size={16} />
                {item.label}
              </button>
            );
          })}
        </nav>
      </aside>

      <main className="flex min-w-0 flex-1 flex-col">
        <header className="border-b border-border bg-background px-3 py-2 md:hidden">
          <nav className="flex gap-1 overflow-x-auto" aria-label="Primary">
            {NAV_ITEMS.map((item) => {
              const Icon = item.icon;
              return (
                <button
                  key={item.id}
                  onClick={() => setActiveView(item.id)}
                  className={`flex h-9 shrink-0 items-center gap-1 rounded-md px-3 text-sm ${
                    activeView === item.id ? "bg-muted" : "text-muted-foreground"
                  }`}
                >
                  <Icon size={15} />
                  {item.label}
                </button>
              );
            })}
          </nav>
        </header>

        {activeView === "chat" ? (
          <div className="mx-auto flex w-full max-w-4xl flex-1 flex-col px-4">
            <section className="flex-1 overflow-y-auto py-10">
              {blocks.length === 0 ? (
                <div className="grid min-h-[60vh] place-items-center text-center">
                  <h1 className="text-3xl font-semibold">AgentScope Enterprise AI</h1>
                </div>
              ) : (
                <ChatStreamBlocks blocks={blocks} />
              )}
            </section>

            <ChatComposer
              agents={agents}
              models={models}
              tools={tools}
              streaming={streaming}
              draftPrompt={draftPrompt}
              onSubmit={submit}
              onTranscribe={transcribe}
            />
          </div>
        ) : null}

        {activeView === "models" ? <ModelSettings models={models} onRefresh={refreshConfigs} /> : null}
        {activeView === "tools" ? <ToolSettings tools={tools} onRefresh={refreshConfigs} /> : null}
        {activeView === "agents" ? <AgentSettings agents={agents} models={models} tools={tools} onRefresh={refreshConfigs} /> : null}
        {activeView === "knowledge" ? (
          <section className="mx-auto w-full max-w-4xl py-10">
            <h1 className="text-2xl font-semibold">Knowledge</h1>
            <p className="mt-1 text-sm text-muted-foreground">Knowledge base configuration remains in the existing flow.</p>
          </section>
        ) : null}
      </main>
    </div>
  );
}
