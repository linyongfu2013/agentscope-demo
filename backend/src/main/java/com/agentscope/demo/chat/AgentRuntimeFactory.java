package com.agentscope.demo.chat;

import com.agentscope.demo.secret.SecretResolver;
import com.agentscope.demo.tenant.TenantContext;
import com.agentscope.demo.toolconfig.ToolRuntimeRegistrar;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.plan.PlanNotebook;
import io.agentscope.core.rag.RAGMode;
import io.agentscope.core.rag.model.RetrieveConfig;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;
import org.springframework.stereotype.Component;

@Component
public class AgentRuntimeFactory {

    private final SecretResolver secretResolver;
    private final ToolRuntimeRegistrar toolRuntimeRegistrar;

    public AgentRuntimeFactory(SecretResolver secretResolver, ToolRuntimeRegistrar toolRuntimeRegistrar) {
        this.secretResolver = secretResolver;
        this.toolRuntimeRegistrar = toolRuntimeRegistrar;
    }

    public AgentRuntime create(AgentConfig config) {
        String apiKey = secretResolver.resolve(config.apiKeyRef()).orElse("");
        if ("mock".equalsIgnoreCase(config.provider()) || apiKey.isBlank()) {
            return new MockAgentRuntime();
        }

        OpenAIChatModel model = OpenAIChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(config.baseUrl())
                .modelName(config.modelName())
                .generateOptions(GenerateOptions.builder()
                        .temperature(config.temperature())
                        .maxTokens(config.maxTokens())
                        .build())
                .build();

        Toolkit toolkit = new Toolkit();
        if (config.tools().contains("calculator")) {
            toolkit.registerTool(new CalculatorTools());
        }
        toolRuntimeRegistrar.registerConfiguredTools(toolkit, config.toolConfigs());

        PlanNotebook planNotebook = PlanNotebook.builder()
                .maxSubtasks(15)
                .needUserConfirm(false)
                .build();

        ReActAgent agent = ReActAgent.builder()
                .name(config.name())
                .sysPrompt(AgentExecutionPrompt.apply(config.systemPrompt()))
                .model(model)
                .toolkit(toolkit)
                .planNotebook(planNotebook)
                .ragMode(RAGMode.AGENTIC)
                .retrieveConfig(RetrieveConfig.builder().limit(5).scoreThreshold(0.35).build())
                .toolExecutionContext(ToolExecutionContext.builder()
                        .register(new TenantToolContext(TenantContext.currentTenantId()))
                        .build())
                .maxIters(12)
                .build();

        return new AgentScopeRuntime(agent);
    }
}
