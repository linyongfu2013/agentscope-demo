CREATE TABLE IF NOT EXISTS model_configs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id varchar(64) NOT NULL DEFAULT 'default' REFERENCES tenants(id),
    name varchar(128) NOT NULL,
    provider varchar(64) NOT NULL,
    model_type varchar(32) NOT NULL,
    model_name varchar(128) NOT NULL,
    base_url text,
    api_key_ref text,
    default_temperature numeric(4,3),
    default_max_tokens int,
    default_reasoning_effort varchar(32),
    embedding_dim int,
    input_modalities jsonb NOT NULL DEFAULT '[]',
    output_modalities jsonb NOT NULL DEFAULT '[]',
    extra_params jsonb NOT NULL DEFAULT '{}',
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, name)
);

CREATE TABLE IF NOT EXISTS tool_configs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id varchar(64) NOT NULL DEFAULT 'default' REFERENCES tenants(id),
    name varchar(128) NOT NULL,
    description text,
    tool_type varchar(32) NOT NULL,
    endpoint text,
    auth_type varchar(32),
    auth_ref text,
    input_schema jsonb NOT NULL DEFAULT '{}',
    config jsonb NOT NULL DEFAULT '{}',
    timeout_ms int NOT NULL DEFAULT 30000,
    enabled boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, name)
);

ALTER TABLE agent_configs
    ADD COLUMN IF NOT EXISTS primary_model_id uuid REFERENCES model_configs(id),
    ADD COLUMN IF NOT EXISTS reasoning_model_id uuid REFERENCES model_configs(id),
    ADD COLUMN IF NOT EXISTS embedding_model_id uuid REFERENCES model_configs(id),
    ADD COLUMN IF NOT EXISTS default_reasoning_effort varchar(32);

CREATE TABLE IF NOT EXISTS agent_tool_bindings (
    agent_config_id uuid NOT NULL REFERENCES agent_configs(id) ON DELETE CASCADE,
    tool_config_id uuid NOT NULL REFERENCES tool_configs(id) ON DELETE CASCADE,
    tenant_id varchar(64) NOT NULL DEFAULT 'default' REFERENCES tenants(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (agent_config_id, tool_config_id)
);

CREATE TABLE IF NOT EXISTS audio_artifacts (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id varchar(64) NOT NULL DEFAULT 'default' REFERENCES tenants(id),
    conversation_id uuid REFERENCES conversations(id),
    message_id uuid REFERENCES chat_messages(id),
    direction varchar(16) NOT NULL,
    storage_key text NOT NULL,
    mime_type varchar(128) NOT NULL,
    duration_ms int,
    transcript text,
    model_config_id uuid REFERENCES model_configs(id),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_model_configs_tenant_type ON model_configs(tenant_id, model_type);
CREATE INDEX IF NOT EXISTS idx_tool_configs_tenant_type ON tool_configs(tenant_id, tool_type);
CREATE INDEX IF NOT EXISTS idx_audio_artifacts_tenant_conversation ON audio_artifacts(tenant_id, conversation_id);
