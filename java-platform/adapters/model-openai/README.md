# adapter-model-openai

OpenAI-compatible model adapter. `CliProxyApiModelGateway` targets a local
CLIProxyAPI `/v1/chat/completions` endpoint using JDK `HttpClient`.

Configuration is read from `CLIPROXY_API_BASE_URL`, `CLIPROXY_API_KEY`,
`CLIPROXY_MODEL` and `CLIPROXY_TIMEOUT_SECONDS`. The adapter maps upstream
failures to stable `MODEL_*` error codes and never exposes provider response
bodies through the domain model.
