# Kimss Java SDK — Claude Code

Read [AI_INTEGRATION.md](AI_INTEGRATION.md) (canonical public: [kimss-control-plane](https://raw.githubusercontent.com/kimss-ai/kimss-control-plane/main/AI_INTEGRATION.md)). Dual-listener gateway: OpenAI `/v1` and Anthropic `/v1/messages`. Path A: keep the vendor Java client or JDK `HttpClient` — never generate `agents().run` for chat. Optional swarm: `KimssProxy` (this package ≥0.3.0) for lineage. HTTP 451 and tool `policy_violation` / `authority_boundary` are Guardrails, not a bad base URL.
