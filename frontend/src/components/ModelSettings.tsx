import { FormEvent, useState } from "react";
import { createModelConfig, deleteModelConfig, updateModelConfig } from "../lib/api";
import type { ModelConfig, ModelConfigPayload, ModelType, ReasoningEffort } from "../types/config";

type Props = {
  models: ModelConfig[];
  onRefresh: () => void | Promise<void>;
};

const MODEL_TYPES: ModelType[] = ["TEXT", "REASONING", "EMBEDDING", "STT", "TTS", "MULTIMODAL"];
const REASONING_EFFORTS: ReasoningEffort[] = ["LOW", "MEDIUM", "HIGH"];

const emptyForm = {
  name: "",
  provider: "",
  modelType: "TEXT" as ModelType,
  modelName: "",
  baseUrl: "",
  apiKeyRef: "",
  defaultTemperature: "",
  defaultMaxTokens: "",
  defaultReasoningEffort: "MEDIUM" as ReasoningEffort,
  embeddingDim: "",
  inputModalities: "",
  outputModalities: "",
  extraParams: "{}",
  enabled: true
};

type ModelForm = typeof emptyForm;

function splitList(value: string): string[] {
  return value
    .split(",")
    .map((item) => item.trim())
    .filter(Boolean);
}

function toForm(model: ModelConfig): ModelForm {
  return {
    name: model.name,
    provider: model.provider,
    modelType: model.modelType,
    modelName: model.modelName,
    baseUrl: model.baseUrl ?? "",
    apiKeyRef: model.apiKeyRef ?? "",
    defaultTemperature: model.defaultTemperature?.toString() ?? "",
    defaultMaxTokens: model.defaultMaxTokens?.toString() ?? "",
    defaultReasoningEffort: model.defaultReasoningEffort ?? "MEDIUM",
    embeddingDim: model.embeddingDim?.toString() ?? "",
    inputModalities: model.inputModalities?.join(", ") ?? "",
    outputModalities: model.outputModalities?.join(", ") ?? "",
    extraParams: model.extraParams || "{}",
    enabled: model.enabled ?? true
  };
}

function toPayload(form: ModelForm): ModelConfigPayload {
  return {
    name: form.name.trim(),
    provider: form.provider.trim(),
    modelType: form.modelType,
    modelName: form.modelName.trim(),
    baseUrl: form.baseUrl.trim() || undefined,
    apiKeyRef: form.apiKeyRef.trim() || undefined,
    defaultTemperature: form.defaultTemperature ? Number(form.defaultTemperature) : undefined,
    defaultMaxTokens: form.defaultMaxTokens ? Number(form.defaultMaxTokens) : undefined,
    defaultReasoningEffort: form.modelType === "REASONING" ? form.defaultReasoningEffort : undefined,
    embeddingDim: form.embeddingDim ? Number(form.embeddingDim) : undefined,
    inputModalities: splitList(form.inputModalities),
    outputModalities: splitList(form.outputModalities),
    extraParams: form.extraParams.trim() || "{}",
    enabled: form.enabled
  };
}

export function ModelSettings({ models, onRefresh }: Props) {
  const [form, setForm] = useState<ModelForm>(emptyForm);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);

  function update<K extends keyof ModelForm>(key: K, value: ModelForm[K]) {
    setForm((current) => ({ ...current, [key]: value }));
  }

  function resetForm() {
    setForm(emptyForm);
    setEditingId(null);
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError("");
    try {
      const payload = toPayload(form);
      if (editingId) {
        await updateModelConfig(editingId, payload);
      } else {
        await createModelConfig(payload);
      }
      resetForm();
      await onRefresh();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Failed to save model config");
    } finally {
      setSaving(false);
    }
  }

  async function remove(id: string) {
    setError("");
    try {
      await deleteModelConfig(id);
      await onRefresh();
      if (editingId === id) {
        resetForm();
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Failed to delete model config");
    }
  }

  return (
    <section className="mx-auto w-full max-w-4xl px-4 py-10">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold">Models</h1>
        <p className="mt-1 text-sm text-muted-foreground">Configured runtime models available to chat and agents.</p>
      </div>

      <form onSubmit={submit} className="mb-8 space-y-4 border-y border-border py-4">
        {error ? <p className="text-sm text-red-600">{error}</p> : null}
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block text-sm">
            <span className="mb-1 block font-medium">Name</span>
            <input aria-label="Name" value={form.name} onChange={(event) => update("name", event.target.value)} className="h-10 w-full rounded-md border border-border bg-background px-3 outline-none" required />
          </label>
          <label className="block text-sm">
            <span className="mb-1 block font-medium">Provider</span>
            <input aria-label="Provider" value={form.provider} onChange={(event) => update("provider", event.target.value)} className="h-10 w-full rounded-md border border-border bg-background px-3 outline-none" required />
          </label>
          <label className="block text-sm">
            <span className="mb-1 block font-medium">Model type</span>
            <select aria-label="Model type" value={form.modelType} onChange={(event) => update("modelType", event.target.value as ModelType)} className="h-10 w-full rounded-md border border-border bg-background px-3 outline-none">
              {MODEL_TYPES.map((type) => <option key={type} value={type}>{type}</option>)}
            </select>
          </label>
          <label className="block text-sm">
            <span className="mb-1 block font-medium">Model name</span>
            <input aria-label="Model name" value={form.modelName} onChange={(event) => update("modelName", event.target.value)} className="h-10 w-full rounded-md border border-border bg-background px-3 outline-none" required />
          </label>
          <label className="block text-sm">
            <span className="mb-1 block font-medium">Base URL</span>
            <input aria-label="Base URL" value={form.baseUrl} onChange={(event) => update("baseUrl", event.target.value)} className="h-10 w-full rounded-md border border-border bg-background px-3 outline-none" />
          </label>
          <label className="block text-sm">
            <span className="mb-1 block font-medium">API key ref</span>
            <input aria-label="API key ref" value={form.apiKeyRef} onChange={(event) => update("apiKeyRef", event.target.value)} className="h-10 w-full rounded-md border border-border bg-background px-3 outline-none" />
          </label>
          <label className="block text-sm">
            <span className="mb-1 block font-medium">Temperature</span>
            <input aria-label="Temperature" type="number" step="0.1" value={form.defaultTemperature} onChange={(event) => update("defaultTemperature", event.target.value)} className="h-10 w-full rounded-md border border-border bg-background px-3 outline-none" />
          </label>
          <label className="block text-sm">
            <span className="mb-1 block font-medium">Max tokens</span>
            <input aria-label="Max tokens" type="number" value={form.defaultMaxTokens} onChange={(event) => update("defaultMaxTokens", event.target.value)} className="h-10 w-full rounded-md border border-border bg-background px-3 outline-none" />
          </label>
          <label className="block text-sm">
            <span className="mb-1 block font-medium">Reasoning effort</span>
            <select aria-label="Default reasoning effort" value={form.defaultReasoningEffort} onChange={(event) => update("defaultReasoningEffort", event.target.value as ReasoningEffort)} className="h-10 w-full rounded-md border border-border bg-background px-3 outline-none">
              {REASONING_EFFORTS.map((effort) => <option key={effort} value={effort}>{effort}</option>)}
            </select>
          </label>
          <label className="block text-sm">
            <span className="mb-1 block font-medium">Embedding dim</span>
            <input aria-label="Embedding dim" type="number" value={form.embeddingDim} onChange={(event) => update("embeddingDim", event.target.value)} className="h-10 w-full rounded-md border border-border bg-background px-3 outline-none" />
          </label>
          <label className="block text-sm">
            <span className="mb-1 block font-medium">Input modalities</span>
            <input aria-label="Input modalities" value={form.inputModalities} onChange={(event) => update("inputModalities", event.target.value)} className="h-10 w-full rounded-md border border-border bg-background px-3 outline-none" placeholder="text, image" />
          </label>
          <label className="block text-sm">
            <span className="mb-1 block font-medium">Output modalities</span>
            <input aria-label="Output modalities" value={form.outputModalities} onChange={(event) => update("outputModalities", event.target.value)} className="h-10 w-full rounded-md border border-border bg-background px-3 outline-none" placeholder="text, audio" />
          </label>
        </div>
        <label className="block text-sm">
          <span className="mb-1 block font-medium">JSON extra params</span>
          <textarea aria-label="JSON extra params" value={form.extraParams} onChange={(event) => update("extraParams", event.target.value)} rows={4} className="w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-sm outline-none" />
        </label>
        <label className="flex items-center gap-2 text-sm text-muted-foreground">
          <input type="checkbox" checked={form.enabled} onChange={(event) => update("enabled", event.target.checked)} className="h-3.5 w-3.5" />
          Enabled
        </label>
        <div className="flex flex-wrap gap-2">
          <button type="submit" disabled={saving} className="h-9 rounded-md bg-foreground px-3 text-sm text-background disabled:opacity-50">Save model</button>
          {editingId ? <button type="button" onClick={resetForm} className="h-9 rounded-md border border-border px-3 text-sm">Cancel edit</button> : null}
        </div>
      </form>

      <div className="divide-y divide-border border-y border-border">
        {models.length === 0 ? (
          <p className="py-4 text-sm text-muted-foreground">No models configured.</p>
        ) : (
          models.map((model) => (
            <article key={model.id} className="grid gap-3 py-4 sm:grid-cols-[1fr_auto]">
              <div>
                <h2 className="font-medium">{model.name}</h2>
                <p className="text-sm text-muted-foreground">{model.provider}</p>
                {model.baseUrl ? <p className="text-sm text-muted-foreground">{model.baseUrl}</p> : null}
              </div>
              <div className="space-y-2 text-sm text-muted-foreground sm:text-right">
                <div>{model.modelType}</div>
                <div>{model.modelName}</div>
                <div className="flex gap-2 sm:justify-end">
                  <button type="button" onClick={() => { setEditingId(model.id); setForm(toForm(model)); }} className="rounded-md border border-border px-2 py-1 text-foreground">Edit</button>
                  <button type="button" onClick={() => void remove(model.id)} className="rounded-md border border-border px-2 py-1 text-foreground">Delete</button>
                </div>
              </div>
            </article>
          ))
        )}
      </div>
    </section>
  );
}
