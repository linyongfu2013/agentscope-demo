export type ModelType = "TEXT" | "REASONING" | "EMBEDDING" | "STT" | "TTS" | "MULTIMODAL";
export type ReasoningEffort = "LOW" | "MEDIUM" | "HIGH";
export type ToolType = "BUILTIN" | "JSON_RPC" | "MCP";
export type ToolAuthType = "NONE" | "API_KEY" | "BEARER_TOKEN" | "BASIC";

export type ModelConfig = {
  id: string;
  tenantId?: string;
  name: string;
  provider: string;
  modelType: ModelType;
  modelName: string;
  baseUrl?: string;
  apiKeyRef?: string;
  defaultTemperature?: number;
  defaultMaxTokens?: number;
  defaultReasoningEffort?: ReasoningEffort;
  embeddingDim?: number;
  inputModalities?: string[];
  outputModalities?: string[];
  extraParams?: string;
  enabled?: boolean;
  createdAt?: string;
  updatedAt?: string;
};

export type ToolConfig = {
  id: string;
  tenantId?: string;
  name: string;
  toolType: ToolType;
  description?: string;
  endpoint?: string;
  authType?: ToolAuthType;
  authRef?: string;
  inputSchema?: string;
  config?: string;
  timeoutMs?: number;
  enabled?: boolean;
  createdAt?: string;
  updatedAt?: string;
};

export type AgentConfig = {
  id: string;
  tenantId?: string;
  name: string;
  systemPrompt?: string;
  primaryModelId?: string;
  reasoningModelId?: string;
  embeddingModelId?: string;
  defaultReasoningEffort?: ReasoningEffort;
  toolIds: string[];
  knowledgeBaseIds: string[];
};

export type ModelConfigPayload = Omit<ModelConfig, "id" | "tenantId" | "createdAt" | "updatedAt">;
export type ToolConfigPayload = {
  name: string;
  description?: string;
  toolType: ToolType;
  endpoint?: string;
  authType?: ToolAuthType;
  authRef?: string;
  inputSchema?: string;
  config?: string;
  timeoutMs?: number;
  enabled?: boolean;
};
export type AgentConfigPayload = {
  name: string;
  systemPrompt?: string;
  primaryModelId?: string;
  reasoningModelId?: string;
  embeddingModelId?: string;
  defaultReasoningEffort?: ReasoningEffort;
  toolIds: string[];
  knowledgeBaseIds: string[];
};

export type ChatSubmit = {
  prompt: string;
  agentConfigId?: string;
  modelId?: string;
  reasoningEffort?: ReasoningEffort;
  toolIds: string[];
};
