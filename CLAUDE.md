# Kimss Java SDK — Claude Code

Read [AI_INTEGRATION.md](AI_INTEGRATION.md) (canonical public: [kimss-control-plane](https://raw.githubusercontent.com/kimss-ai/kimss-control-plane/main/AI_INTEGRATION.md)). Dual-listener gateway: OpenAI `/v1` and Anthropic `/v1/messages`. Keep the vendor Java client or JDK `HttpClient`. Do not add `com.kimss:kimss-java` for new gateway onboarding. Never generate `agents().run` for new integrations. HTTP 451 and tool `policy_violation` / `authority_boundary` are Guardrails, not a bad base URL.
