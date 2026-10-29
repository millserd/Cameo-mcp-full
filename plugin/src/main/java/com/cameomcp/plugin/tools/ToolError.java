package com.cameomcp.plugin.tools;

/** An expected failure (bad input, missing element...). Its message goes back to the agent as-is. */
public class ToolError extends RuntimeException {
    public ToolError(String message) { super(message); }
}
