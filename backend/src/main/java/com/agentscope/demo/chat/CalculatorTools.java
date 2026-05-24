package com.agentscope.demo.chat;

import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;

public class CalculatorTools {

    @Tool(name = "calculator_add", description = "Add two decimal numbers")
    public double add(
            @ToolParam(name = "a", description = "First number") double a,
            @ToolParam(name = "b", description = "Second number") double b
    ) {
        return a + b;
    }
}
