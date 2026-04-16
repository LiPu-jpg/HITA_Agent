package com.stupidtree.hitax.agent.core

class AgentToolRegistry {
    private val tools = LinkedHashMap<String, AgentTool<*, *>>()

    fun <I, O> register(tool: AgentTool<I, O>) {
        tools[tool.name] = tool
    }

    @Suppress("UNCHECKED_CAST")
    fun <I, O> get(name: String): AgentTool<I, O>? {
        return tools[name] as? AgentTool<I, O>
    }

    fun names(): List<String> = tools.keys.toList()
}
