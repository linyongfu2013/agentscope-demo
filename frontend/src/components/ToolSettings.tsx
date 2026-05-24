import { FormEvent, useState } from "react";
import { createToolConfig, deleteToolConfig, testToolConfig, updateToolConfig } from "../lib/api";
import type { ToolAuthType, ToolConfig, ToolConfigPayload, ToolType } from "../types/config";

type Props = {
  tools: ToolConfig[];
  onRefresh: () => void | Promise<void>;
};

const TOOL_TYPES: ToolType[] = ["JSON_RPC", "MCP", "BUILTIN"];
const AUTH_TYPES: ToolAuthType[] = ["NONE", "API_KEY", "BEARER_TOKEN", "BASIC"];

const emptyForm = {
  name: "",
  description: "",
  toolType: "JSON_RPC" as ToolType,
  endpoint: "",
  authType: "NONE" as ToolAuthType,
  authRef: "",
  inputSchema: "{}",
  config: "{}",
  timeoutSeconds: "30",
  enabled: true
};

type ToolForm = typeof emptyForm;

function toForm(tool: ToolConfig): ToolForm {
  return {
    name: tool.name,
    description: tool.description ?? "",
    toolType: tool.toolType,
    endpoint: tool.endpoint ?? "",
    authType: tool.authType ?? "NONE",
    authRef: tool.authRef ?? "",
    inputSchema: tool.inputSchema || "{}",
    config: tool.config || "{}",
    timeoutSeconds: tool.timeoutMs ? String(Math.round(tool.timeoutMs / 1000)) : "30",
    enabled: tool.enabled ?? true
  };
}

function toPayload(form: ToolForm): ToolConfigPayload {
  return {
    name: form.name.trim(),
    description: form.description.trim() || undefined,
    toolType: form.toolType,
    endpoint: form.endpoint.trim() || undefined,
    authType: form.authType,
    authRef: form.authRef.trim() || undefined,
    inputSchema: form.inputSchema.trim() || "{}",
    config: form.config.trim() || "{}",
    timeoutMs: Number(form.timeoutSeconds || "30") * 1000,
    enabled: form.enabled
  };
}

export function ToolSettings({ tools, onRefresh }: Props) {
  const [form, setForm] = useState<ToolForm>(emptyForm);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [saving, setSaving] = useState(false);

  function update<K extends keyof ToolForm>(key: K, value: ToolForm[K]) {
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
    setMessage("");
    try {
      const payload = toPayload(form);
      if (editingId) {
        await updateToolConfig(editingId, payload);
      } else {
        await createToolConfig(payload);
      }
      resetForm();
      await onRefresh();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Failed to save tool config");
    } finally {
      setSaving(false);
    }
  }

  async function remove(id: string) {
    setError("");
    setMessage("");
    try {
      await deleteToolConfig(id);
      await onRefresh();
      if (editingId === id) {
        resetForm();
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Failed to delete tool config");
    }
  }

  async function testTool(id: string) {
    setError("");
    setMessage("");
    try {
      await testToolConfig(id);
      setMessage("Tool test completed.");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Failed to test tool config");
    }
  }

  return (
    <section className="mx-auto w-full max-w-4xl px-4 py-10">
      <div className="mb-6">
        <h1 className="text-2xl font-semibold">Tools</h1>
        <p className="mt-1 text-sm text-muted-foreground">Tool endpoints that agents can bind during execution.</p>
      </div>

      <form onSubmit={submit} className="mb-8 space-y-4 border-y border-border py-4">
        {error ? <p className="text-sm text-red-600">{error}</p> : null}
        {message ? <p className="text-sm text-muted-foreground">{message}</p> : null}
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block text-sm">
            <span className="mb-1 block font-medium">Name</span>
            <input aria-label="Tool name" value={form.name} onChange={(event) => update("name", event.target.value)} className="h-10 w-full rounded-md border border-border bg-background px-3 outline-none" required />
          </label>
          <label className="block text-sm">
            <span className="mb-1 block font-medium">Tool type</span>
            <select aria-label="Tool type" value={form.toolType} onChange={(event) => update("toolType", event.target.value as ToolType)} className="h-10 w-full rounded-md border border-border bg-background px-3 outline-none">
              {TOOL_TYPES.map((type) => <option key={type} value={type}>{type}</option>)}
            </select>
          </label>
          <label className="block text-sm sm:col-span-2">
            <span className="mb-1 block font-medium">Endpoint URL</span>
            <input aria-label="Endpoint URL" value={form.endpoint} onChange={(event) => update("endpoint", event.target.value)} className="h-10 w-full rounded-md border border-border bg-background px-3 outline-none" />
          </label>
          <label className="block text-sm sm:col-span-2">
            <span className="mb-1 block font-medium">Description</span>
            <textarea aria-label="Description" value={form.description} onChange={(event) => update("description", event.target.value)} rows={2} className="w-full rounded-md border border-border bg-background px-3 py-2 outline-none" />
          </label>
          <label className="block text-sm">
            <span className="mb-1 block font-medium">Timeout seconds</span>
            <input aria-label="Timeout seconds" type="number" min="1" value={form.timeoutSeconds} onChange={(event) => update("timeoutSeconds", event.target.value)} className="h-10 w-full rounded-md border border-border bg-background px-3 outline-none" />
          </label>
          <label className="block text-sm">
            <span className="mb-1 block font-medium">Auth type</span>
            <select aria-label="Auth type" value={form.authType} onChange={(event) => update("authType", event.target.value as ToolAuthType)} className="h-10 w-full rounded-md border border-border bg-background px-3 outline-none">
              {AUTH_TYPES.map((type) => <option key={type} value={type}>{type}</option>)}
            </select>
          </label>
          <label className="block text-sm sm:col-span-2">
            <span className="mb-1 block font-medium">Auth ref</span>
            <input aria-label="Auth ref" value={form.authRef} onChange={(event) => update("authRef", event.target.value)} className="h-10 w-full rounded-md border border-border bg-background px-3 outline-none" />
          </label>
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <label className="block text-sm">
            <span className="mb-1 block font-medium">JSON config</span>
            <textarea aria-label="JSON config" value={form.config} onChange={(event) => update("config", event.target.value)} rows={5} className="w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-sm outline-none" />
          </label>
          <label className="block text-sm">
            <span className="mb-1 block font-medium">JSON input schema</span>
            <textarea aria-label="JSON input schema" value={form.inputSchema} onChange={(event) => update("inputSchema", event.target.value)} rows={5} className="w-full rounded-md border border-border bg-background px-3 py-2 font-mono text-sm outline-none" />
          </label>
        </div>
        <label className="flex items-center gap-2 text-sm text-muted-foreground">
          <input type="checkbox" checked={form.enabled} onChange={(event) => update("enabled", event.target.checked)} className="h-3.5 w-3.5" />
          Enabled
        </label>
        <div className="flex flex-wrap gap-2">
          <button type="submit" disabled={saving} className="h-9 rounded-md bg-foreground px-3 text-sm text-background disabled:opacity-50">Save tool</button>
          {editingId ? <button type="button" onClick={resetForm} className="h-9 rounded-md border border-border px-3 text-sm">Cancel edit</button> : null}
        </div>
      </form>

      <div className="divide-y divide-border border-y border-border">
        {tools.length === 0 ? (
          <p className="py-4 text-sm text-muted-foreground">No tools configured.</p>
        ) : (
          tools.map((tool) => (
            <article key={tool.id} className="grid gap-3 py-4 sm:grid-cols-[1fr_auto]">
              <div>
                <h2 className="font-medium">{tool.name}</h2>
                {tool.description ? <p className="text-sm text-muted-foreground">{tool.description}</p> : null}
                {tool.endpoint ? <p className="text-sm text-muted-foreground">{tool.endpoint}</p> : null}
              </div>
              <div className="space-y-2 text-sm text-muted-foreground sm:text-right">
                <div>{tool.toolType}</div>
                <div>{tool.timeoutMs ? `${Math.round(tool.timeoutMs / 1000)}s` : ""}</div>
                <div className="flex flex-wrap gap-2 sm:justify-end">
                  <button type="button" onClick={() => void testTool(tool.id)} className="rounded-md border border-border px-2 py-1 text-foreground">Test</button>
                  <button type="button" onClick={() => { setEditingId(tool.id); setForm(toForm(tool)); }} className="rounded-md border border-border px-2 py-1 text-foreground">Edit</button>
                  <button type="button" onClick={() => void remove(tool.id)} className="rounded-md border border-border px-2 py-1 text-foreground">Delete</button>
                </div>
              </div>
            </article>
          ))
        )}
      </div>
    </section>
  );
}
