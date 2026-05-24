CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS tenants (
    id varchar(64) PRIMARY KEY DEFAULT 'default',
    name varchar(128) NOT NULL,
    status varchar(32) NOT NULL DEFAULT 'ACTIVE',
    created_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO tenants (id, name)
VALUES ('default', 'Default Tenant')
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS users (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id varchar(64) NOT NULL DEFAULT 'default' REFERENCES tenants(id),
    username varchar(64) NOT NULL,
    password_hash varchar(255) NOT NULL,
    role varchar(32) NOT NULL DEFAULT 'USER',
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, username)
);

CREATE TABLE IF NOT EXISTS conversations (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id varchar(64) NOT NULL DEFAULT 'default' REFERENCES tenants(id),
    user_id uuid REFERENCES users(id),
    title varchar(255),
    mode varchar(32) NOT NULL DEFAULT 'CHAT',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS chat_messages (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id varchar(64) NOT NULL DEFAULT 'default' REFERENCES tenants(id),
    conversation_id uuid NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    role varchar(32) NOT NULL,
    content text NOT NULL,
    metadata jsonb NOT NULL DEFAULT '{}',
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS agent_configs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id varchar(64) NOT NULL DEFAULT 'default' REFERENCES tenants(id),
    owner_user_id uuid REFERENCES users(id),
    name varchar(128) NOT NULL,
    system_prompt text NOT NULL,
    model_name varchar(128) NOT NULL DEFAULT 'deepseek-chat',
    temperature numeric(4,3) NOT NULL DEFAULT 0.700,
    max_tokens int NOT NULL DEFAULT 4096,
    tools jsonb NOT NULL DEFAULT '[]',
    knowledge_base_ids uuid[] NOT NULL DEFAULT '{}',
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TYPE task_run_status AS ENUM ('PENDING', 'RUNNING', 'SUCCESS', 'FAILED', 'CANCELLED');

CREATE TABLE IF NOT EXISTS agent_task_runs (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id varchar(64) NOT NULL DEFAULT 'default' REFERENCES tenants(id),
    conversation_id uuid REFERENCES conversations(id),
    agent_config_id uuid REFERENCES agent_configs(id),
    user_prompt text NOT NULL,
    intent varchar(32) NOT NULL,
    status task_run_status NOT NULL DEFAULT 'PENDING',
    final_answer text,
    error_message text,
    created_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz
);

CREATE TABLE IF NOT EXISTS agent_task_events (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id varchar(64) NOT NULL DEFAULT 'default' REFERENCES tenants(id),
    task_run_id uuid NOT NULL REFERENCES agent_task_runs(id) ON DELETE CASCADE,
    event_type varchar(64) NOT NULL,
    payload jsonb NOT NULL,
    sequence_no bigint NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (task_run_id, sequence_no)
);

CREATE TABLE IF NOT EXISTS knowledge_bases (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id varchar(64) NOT NULL DEFAULT 'default' REFERENCES tenants(id),
    owner_user_id uuid REFERENCES users(id),
    name varchar(128) NOT NULL,
    description text,
    embedding_model varchar(128) NOT NULL,
    embedding_dim int NOT NULL DEFAULT 1536,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TYPE kb_file_status AS ENUM ('PENDING', 'PARSING', 'EMBEDDING', 'SUCCESS', 'FAILED');

CREATE TABLE IF NOT EXISTS knowledge_files (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id varchar(64) NOT NULL DEFAULT 'default' REFERENCES tenants(id),
    knowledge_base_id uuid NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    storage_key varchar(512) NOT NULL,
    filename varchar(255) NOT NULL,
    mime_type varchar(128),
    status kb_file_status NOT NULL DEFAULT 'PENDING',
    progress int NOT NULL DEFAULT 0 CHECK (progress BETWEEN 0 AND 100),
    chunk_count int NOT NULL DEFAULT 0,
    embedded_chunk_count int NOT NULL DEFAULT 0,
    retry_count int NOT NULL DEFAULT 0,
    max_retry_count int NOT NULL DEFAULT 2,
    error_message text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS knowledge_chunks (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id varchar(64) NOT NULL DEFAULT 'default' REFERENCES tenants(id),
    knowledge_base_id uuid NOT NULL REFERENCES knowledge_bases(id) ON DELETE CASCADE,
    file_id uuid NOT NULL REFERENCES knowledge_files(id) ON DELETE CASCADE,
    chunk_index int NOT NULL,
    content text NOT NULL,
    metadata jsonb NOT NULL DEFAULT '{}',
    embedding vector(1536),
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (file_id, chunk_index)
);

CREATE INDEX IF NOT EXISTS idx_conversations_tenant_user ON conversations(tenant_id, user_id);
CREATE INDEX IF NOT EXISTS idx_messages_tenant_conversation ON chat_messages(tenant_id, conversation_id);
CREATE INDEX IF NOT EXISTS idx_agent_configs_tenant ON agent_configs(tenant_id);
CREATE INDEX IF NOT EXISTS idx_task_events_run_seq ON agent_task_events(task_run_id, sequence_no);
CREATE INDEX IF NOT EXISTS idx_kb_files_status ON knowledge_files(tenant_id, status);
CREATE INDEX IF NOT EXISTS idx_chunks_tenant_kb ON knowledge_chunks(tenant_id, knowledge_base_id);
CREATE INDEX IF NOT EXISTS idx_chunks_embedding_hnsw ON knowledge_chunks USING hnsw (embedding vector_cosine_ops);
