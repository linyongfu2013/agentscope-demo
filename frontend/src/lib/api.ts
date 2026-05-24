import type {
  AgentConfig,
  AgentConfigPayload,
  ModelConfig,
  ModelConfigPayload,
  ToolConfig,
  ToolConfigPayload
} from "../types/config";

export async function fetchModels(): Promise<ModelConfig[]> {
  const response = await fetch("/api/model-configs");
  return response.ok ? response.json() : [];
}

export async function fetchTools(): Promise<ToolConfig[]> {
  const response = await fetch("/api/tool-configs");
  return response.ok ? response.json() : [];
}

export async function fetchAgents(): Promise<AgentConfig[]> {
  const response = await fetch("/api/agent-configs");
  return response.ok ? response.json() : [];
}

async function readError(response: Response): Promise<string> {
  let detail = "";
  try {
    detail = await response.text();
  } catch {
    detail = "";
  }
  return detail.trim() ? `Request failed (${response.status}): ${detail.trim()}` : `Request failed (${response.status})`;
}

async function requestJson<T>(url: string, init: RequestInit): Promise<T> {
  const response = await fetch(url, init);
  if (!response.ok) {
    throw new Error(await readError(response));
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json();
}

function jsonRequest(method: string, body: unknown): RequestInit {
  return {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body)
  };
}

export function createModelConfig(payload: ModelConfigPayload): Promise<ModelConfig> {
  return requestJson("/api/model-configs", jsonRequest("POST", payload));
}

export function updateModelConfig(id: string, payload: ModelConfigPayload): Promise<ModelConfig> {
  return requestJson(`/api/model-configs/${id}`, jsonRequest("PUT", payload));
}

export function deleteModelConfig(id: string): Promise<void> {
  return requestJson(`/api/model-configs/${id}`, { method: "DELETE" });
}

export function createToolConfig(payload: ToolConfigPayload): Promise<ToolConfig> {
  return requestJson("/api/tool-configs", jsonRequest("POST", payload));
}

export function updateToolConfig(id: string, payload: ToolConfigPayload): Promise<ToolConfig> {
  return requestJson(`/api/tool-configs/${id}`, jsonRequest("PUT", payload));
}

export function deleteToolConfig(id: string): Promise<void> {
  return requestJson(`/api/tool-configs/${id}`, { method: "DELETE" });
}

export function testToolConfig(id: string): Promise<unknown> {
  return requestJson(`/api/tool-configs/${id}/test`, { method: "POST" });
}

export function createAgentConfig(payload: AgentConfigPayload): Promise<AgentConfig> {
  return requestJson("/api/agent-configs", jsonRequest("POST", payload));
}

export async function transcribeAudio(blob: Blob, sttModelId?: string): Promise<string> {
  const formData = new FormData();
  formData.append("file", blob, "recording.webm");
  const query = sttModelId ? `?sttModelId=${encodeURIComponent(sttModelId)}` : "";
  const response = await fetch(`/api/voice/transcriptions${query}`, {
    method: "POST",
    body: formData
  });
  if (!response.ok) {
    throw new Error(await readError(response));
  }
  const data = (await response.json()) as { transcript?: string };
  return data.transcript ?? "";
}
