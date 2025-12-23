package com.inductiveautomation.ignition.gateway.llm.gateway.providers;

/**
 * Indicates the level of tool/function calling support for an LLM provider.
 */
public enum ToolCallingSupport {
    /**
     * Provider supports function/tool calling natively (Claude, GPT-4, etc.)
     */
    NATIVE,

    /**
     * Must use prompt-based tool calling (older models)
     */
    PROMPTING,

    /**
     * No tool support
     */
    NONE
}
