package com.agentscope.demo.chat;

public final class AgentExecutionPrompt {

    static final String MARKER = "[AgentScope self-directed execution policy]";

    private static final String POLICY = """

            %s
            - If the user asks a simple question, answer directly without creating a plan or calling tools.
            - If the user asks for a complex task, create a plan, execute the plan step by step, call tools when useful, and summarize the result.
            - Treat research, code changes, file operations, tool use, multi-step analysis, and verification requests as complex tasks.
            - Do not spend a separate turn classifying the request. Decide and act within this same model run.
            """.formatted(MARKER);

    private AgentExecutionPrompt() {
    }

    static String apply(String systemPrompt) {
        String base = systemPrompt == null || systemPrompt.isBlank() ? "You are a helpful assistant." : systemPrompt.trim();
        if (base.contains(MARKER)) {
            return base;
        }
        return base + POLICY;
    }
}
