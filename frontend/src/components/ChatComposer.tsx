import { Mic, Send, Square } from "lucide-react";
import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import type { AgentConfig, ChatSubmit, ModelConfig, ReasoningEffort, ToolConfig } from "../types/config";

type Props = {
  agents: AgentConfig[];
  models: ModelConfig[];
  tools: ToolConfig[];
  streaming: boolean;
  draftPrompt?: string;
  onSubmit: (payload: ChatSubmit) => void | Promise<void>;
  onTranscribe: (audio: Blob) => void | Promise<void>;
};

const REASONING_EFFORTS: ReasoningEffort[] = ["LOW", "MEDIUM", "HIGH"];

export function ChatComposer({ agents, models, tools, streaming, draftPrompt, onSubmit, onTranscribe }: Props) {
  const [prompt, setPrompt] = useState("");
  const [agentConfigId, setAgentConfigId] = useState("");
  const [modelId, setModelId] = useState(models[0]?.id ?? "");
  const [reasoningEffort, setReasoningEffort] = useState<ReasoningEffort>("MEDIUM");
  const [toolIds, setToolIds] = useState<string[]>([]);
  const [recording, setRecording] = useState(false);
  const [recordingError, setRecordingError] = useState("");
  const recorderRef = useRef<MediaRecorder | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const chunksRef = useRef<Blob[]>([]);

  const selectedModel = useMemo(
    () => models.find((model) => model.id === modelId),
    [modelId, models]
  );
  const showReasoningEffort = selectedModel?.modelType === "REASONING";

  useEffect(() => {
    if (models.length > 0 && !models.some((model) => model.id === modelId)) {
      setModelId(models[0].id);
    }
  }, [modelId, models]);

  useEffect(() => {
    if (agentConfigId && !agents.some((agent) => agent.id === agentConfigId)) {
      setAgentConfigId("");
    }
  }, [agentConfigId, agents]);

  useEffect(() => {
    if (draftPrompt !== undefined) {
      setPrompt(draftPrompt);
    }
  }, [draftPrompt]);

  useEffect(() => {
    return () => {
      streamRef.current?.getTracks().forEach((track) => track.stop());
    };
  }, []);

  function toggleTool(toolId: string) {
    setToolIds((current) =>
      current.includes(toolId) ? current.filter((id) => id !== toolId) : [...current, toolId]
    );
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    const text = prompt.trim();
    if (!text || streaming) {
      return;
    }

    const payload: ChatSubmit = {
      prompt: text,
      agentConfigId: agentConfigId || undefined,
      modelId: modelId || undefined,
      reasoningEffort: showReasoningEffort ? reasoningEffort : undefined,
      toolIds
    };
    onSubmit(payload);
    setPrompt("");
  }

  async function startRecording() {
    setRecordingError("");
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === "undefined") {
      setRecordingError("Audio recording is not supported in this browser.");
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      const recorder = new MediaRecorder(stream);
      chunksRef.current = [];
      streamRef.current = stream;
      recorderRef.current = recorder;
      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          chunksRef.current.push(event.data);
        }
      };
      recorder.onstop = () => {
        const audio = new Blob(chunksRef.current, { type: recorder.mimeType || "audio/webm" });
        chunksRef.current = [];
        stream.getTracks().forEach((track) => track.stop());
        streamRef.current = null;
        recorderRef.current = null;
        setRecording(false);
        if (audio.size > 0) {
          void onTranscribe(audio);
        }
      };
      recorder.start();
      setRecording(true);
    } catch (caught) {
      const message = caught instanceof Error ? caught.message : "Unable to start recording.";
      setRecordingError(message);
      setRecording(false);
    }
  }

  function stopRecording() {
    const recorder = recorderRef.current;
    if (recorder && recorder.state !== "inactive") {
      recorder.stop();
    }
  }

  return (
    <form onSubmit={submit} className="sticky bottom-0 bg-background pb-5">
      <div className="space-y-2 rounded-2xl border border-border bg-background p-2 shadow-sm">
        {recordingError ? <p className="px-3 pt-2 text-sm text-red-600">{recordingError}</p> : null}
        <div className="flex flex-wrap items-center gap-2 px-1 pt-1">
          {agents.length > 0 ? (
            <select
              aria-label="Agent"
              value={agentConfigId}
              onChange={(event) => setAgentConfigId(event.target.value)}
              className="h-8 rounded-md border border-border bg-background px-2 text-sm outline-none"
            >
              <option value="">No agent</option>
              {agents.map((agent) => (
                <option key={agent.id} value={agent.id}>
                  {agent.name}
                </option>
              ))}
            </select>
          ) : null}

          <select
            aria-label="Model"
            value={modelId}
            onChange={(event) => setModelId(event.target.value)}
            className="h-8 rounded-md border border-border bg-background px-2 text-sm outline-none"
          >
            {models.length === 0 ? <option value="">Default model</option> : null}
            {models.map((model) => (
              <option key={model.id} value={model.id}>
                {model.name}
              </option>
            ))}
          </select>

          {showReasoningEffort ? (
            <select
              aria-label="Reasoning effort"
              value={reasoningEffort}
              onChange={(event) => setReasoningEffort(event.target.value as ReasoningEffort)}
              className="h-8 rounded-md border border-border bg-background px-2 text-sm outline-none"
            >
              {REASONING_EFFORTS.map((effort) => (
                <option key={effort} value={effort}>
                  {effort}
                </option>
              ))}
            </select>
          ) : null}

          {tools.length > 0 ? (
            <div className="flex flex-wrap items-center gap-2">
              {tools.map((tool) => (
                <label
                  key={tool.id}
                  className="flex h-8 items-center gap-2 rounded-md border border-border px-2 text-sm text-muted-foreground"
                >
                  <input
                    type="checkbox"
                    checked={toolIds.includes(tool.id)}
                    onChange={() => toggleTool(tool.id)}
                    className="h-3.5 w-3.5"
                  />
                  {tool.name}
                </label>
              ))}
            </div>
          ) : null}
        </div>

        <div className="flex items-end gap-2">
          <textarea
            value={prompt}
            onChange={(event) => setPrompt(event.target.value)}
            rows={1}
            className="max-h-40 min-h-12 flex-1 resize-none bg-transparent px-3 py-3 outline-none"
            placeholder="Message AgentScope"
          />
          <button
            type="button"
            onClick={() => recording ? stopRecording() : void startRecording()}
            className={`grid h-10 w-10 shrink-0 place-items-center rounded-full border border-border hover:bg-muted ${
              recording ? "text-red-600" : "text-muted-foreground"
            }`}
            aria-label={recording ? "Stop recording" : "Record audio"}
            disabled={streaming}
          >
            {recording ? <Square size={15} /> : <Mic size={16} />}
          </button>
          <button
            type="submit"
            disabled={streaming || !prompt.trim()}
            className="grid h-10 w-10 shrink-0 place-items-center rounded-full bg-foreground text-background disabled:opacity-40"
            aria-label="Send message"
          >
            <Send size={16} />
          </button>
        </div>
      </div>
    </form>
  );
}
