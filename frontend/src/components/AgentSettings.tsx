import { FormEvent, useState } from "react";
import { createAgentConfig } from "../lib/api";
import type { AgentConfig, AgentConfigPayload, ModelConfig, ReasoningEffort, ToolConfig } from "../types/config";

type Props = {
  agents: AgentConfig[];
  models: ModelConfig[];
  tools: ToolConfig[];
  onRefresh: () => void | Promise<void>;
};

const REASONING_EFFORTS: ReasoningEffort[] = ["LOW", "MEDIUM", "HIGH"];

const emptyForm = {
  name: "",
  systemPrompt: "",
  primaryModelId: "",
  reasoningModelId: "",
  embeddingModelId: "",
  defaultReasoningEffort: "MEDIUM" as ReasoningEffort,
  toolIds: [] as string[]
};

export function AgentSettings({ agents, models, tools, onRefresh }: Props) {
  const [form, setForm] = useState(emptyForm);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  const reasoningModels = models.filter((model) => model.modelType === "REASONING");
  const embeddingModels = models.filter((model) => model.modelType === "EMBEDDING");

  function toggleTool(toolId: string) {
    setForm((current) => ({
      ...current,
      toolIds: current.toolIds.includes(toolId)
        ? current.toolIds.filter((id) => id !== toolId)
        : [...current.toolIds, toolId]
    }));
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError("");
    try {
      const payload: AgentConfigPayload = {
        name: form.name.trim(),
        systemPrompt: form.systemPrompt.trim() || undefined,
        primaryModelId: form.primaryModelId || undefined,
        reasoningModelId: form.reasoningModelId || undefined,
        embeddingModelId: form.embeddingModelId || undefined,
        defaultReasoningEffort: form.defaultReasoningEffort,
        toolIds: form.toolIds,
        knowledgeBaseIds: []
      };
      await createAgentConfig(payload);
      setForm(emptyForm);
      await onRefresh();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Failed to create agent config");
    } finally {
      setSaving(false);
    }
  }

  function modelName(id?: string) {
    return models.find((model) => model.id === id)?.name ?? "None";
  }

  return (
    <section className="mx-auto w-full max-w-4xl px-4 py-10">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold">Agents</h1>
        <p className="mt-1 text-sm text-muted-foreground">Default model and tool bindings for agent configs.</p>
      </div>

      <form onSubmit={submit} className="mb-8 space-y-4 border-y border-border py-4">
        {error ? <p className="text-sm text-red-600">{error}</p> : null}
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block text-sm sm:col-span-2">
            <span className="mb-1 block font-medium">Name</span>
            <input aria-label="Agent name" value={form.name} onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))} className="h-10 w-full rounded-md border border-border bg-background px-3 outline-none" required />
          </label>
          <label className="block text-sm">
            <span className="mb-1 block font-medium">Primary model</span>
            <select aria-label="Primary model" value={form.primaryModelId} onChange={(event) => setForm((current) => ({ ...current, primaryModelId: event.target.value }))} className="h-10 w-full rounded-md border border-border bg-background px-3 outline-none">
              <option value="">Select a model</option>
              {models.map((model) => <option key={model.id} value={model.id}>{model.name}</option>)}
            </select>
          </label>
          <label className="block text-sm">
            <span className="mb-1 block font-medium">Reasoning model</span>
            <select aria-label="Reasoning model" value={form.reasoningModelId} onChange={(event) => setForm((current) => ({ ...current, reasoningModelId: event.target.value }))} className="h-10 w-full rounded-md border border-border bg-background px-3 outline-none">
              <option value="">None</option>
              {reasoningModels.map((model) => <option key={model.id} value={model.id}>{model.name}</option>)}
            </select>
          </label>
          <label className="block text-sm">
            <span className="mb-1 block font-medium">Embedding model</span>
            <select aria-label="Embedding model" value={form.embeddingModelId} onChange={(event) => setForm((current) => ({ ...current, embeddingModelId: event.target.value }))} className="h-10 w-full rounded-md border border-border bg-background px-3 outline-none">
              <option value="">None</option>
              {embeddingModels.map((model) => <option key={model.id} value={model.id}>{model.name}</option>)}
            </select>
          </label>
          <label className="block text-sm">
            <span className="mb-1 block font-medium">Reasoning effort</span>
            <select aria-label="Agent reasoning effort" value={form.defaultReasoningEffort} onChange={(event) => setForm((current) => ({ ...current, defaultReasoningEffort: event.target.value as ReasoningEffort }))} className="h-10 w-full rounded-md border border-border bg-background px-3 outline-none">
              {REASONING_EFFORTS.map((effort) => <option key={effort} value={effort}>{effort}</option>)}
            </select>
          </label>
          <label className="block text-sm sm:col-span-2">
            <span className="mb-1 block font-medium">System prompt</span>
            <textarea aria-label="System prompt" value={form.systemPrompt} onChange={(event) => setForm((current) => ({ ...current, systemPrompt: event.target.value }))} rows={4} className="w-full rounded-md border border-border bg-background px-3 py-2 outline-none" />
          </label>
        </div>

        <div>
          <h2 className="mb-2 text-sm font-medium">Bound tools</h2>
          <div className="grid gap-2 sm:grid-cols-2">
            {tools.length === 0 ? (
              <p className="text-sm text-muted-foreground">No tools configured.</p>
            ) : (
              tools.map((tool) => (
                <label key={tool.id} className="flex items-center gap-2 text-sm text-muted-foreground">
                  <input type="checkbox" checked={form.toolIds.includes(tool.id)} onChange={() => toggleTool(tool.id)} className="h-3.5 w-3.5" />
                  {tool.name}
                </label>
              ))
            )}
          </div>
        </div>

        <button type="submit" disabled={saving} className="h-9 rounded-md bg-foreground px-3 text-sm text-background disabled:opacity-50">Create agent</button>
      </form>

      <div className="divide-y divide-border border-y border-border">
        {agents.length === 0 ? (
          <p className="py-4 text-sm text-muted-foreground">No agents configured.</p>
        ) : (
          agents.map((agent) => (
            <article key={agent.id} className="grid gap-2 py-4 sm:grid-cols-[1fr_auto]">
              <div>
                <h2 className="font-medium">{agent.name}</h2>
                {agent.systemPrompt ? <p className="line-clamp-2 text-sm text-muted-foreground">{agent.systemPrompt}</p> : null}
              </div>
              <div className="text-sm text-muted-foreground sm:text-right">
                <div>Primary: {modelName(agent.primaryModelId)}</div>
                <div>Reasoning: {modelName(agent.reasoningModelId)}</div>
                <div>Embedding: {modelName(agent.embeddingModelId)}</div>
                <div>{agent.toolIds.length} tools</div>
              </div>
            </article>
          ))
        )}
      </div>
    </section>
  );
}
