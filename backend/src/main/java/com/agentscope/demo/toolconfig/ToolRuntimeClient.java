package com.agentscope.demo.toolconfig;

import java.util.Map;

public interface ToolRuntimeClient {

    ToolInvocationResult test(ToolConfigEntity entity);

    ToolInvocationResult invoke(ToolConfigEntity entity, Map<String, Object> input);
}
