# spring-ai-2 adapter

Spring AI 2.0 适配层。只有这里允许依赖 Spring AI；将 `ChatClient`、结构化输出、Tool Calling 和流式响应映射为平台自己的 `ModelResponse`、`ToolCall` 和 `Usage`。具体版本先在 `docs/architecture/version-matrix.md` 验证。
