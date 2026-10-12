# Kimss Java SDK — LLM / IDE context

Pair with [AI_INTEGRATION.md](../AI_INTEGRATION.md), [README.md](../README.md), and [KIMSS_ONBOARDING.md](KIMSS_ONBOARDING.md).

## Clean machine checklist

1. JDK **11+**.
2. For **inference**: keep the native SDK. OpenAI Java OkHttp → `baseUrl("https://api.kimss.ai/v1")`. Anthropic Java → `baseUrl("https://api.kimss.ai")` (appends `/v1/messages`). Always send Agent-Id headers. No Kimss SDK required.
3. Optional: Maven `ai.kimss:kimss-java` for legacy/control-plane residual only.
4. Env: `KIMSS_API_KEY` (alias `KIMSS_WORKSPACE_KEY`), `KIMSS_AGENT_ID`.
5. **Deprecated:** `KimssClient.agents().run`, `models().create`.

## Preferred inference

**OpenAI**

```java
OpenAIClient client = OpenAIOkHttpClient.builder()
    .baseUrl("https://api.kimss.ai/v1")
    .apiKey(System.getenv("KIMSS_WORKSPACE_KEY"))
    .putHeader("X-Kimss-Agent-Id", System.getenv("KIMSS_AGENT_ID"))
    .putHeader("X-Kimss-Agent-Name", System.getenv().getOrDefault("KIMSS_AGENT_NAME", "My Agent"))
    .build();
```

**Anthropic** (`baseUrl("https://api.kimss.ai")` — client appends `/v1/messages`)

```java
AnthropicClient client = AnthropicOkHttpClient.builder()
    .baseUrl("https://api.kimss.ai")
    .apiKey(System.getenv("KIMSS_WORKSPACE_KEY"))
    .putHeader("X-Kimss-Agent-Id", System.getenv("KIMSS_AGENT_ID"))
    .putHeader("X-Kimss-Agent-Name", System.getenv().getOrDefault("KIMSS_AGENT_NAME", "My Agent"))
    .build();
```

## Control plane (REST / dashboard)

| Concern | How |
|---------|-----|
| Kill switch | Governance UI or `POST /agent_set_status/` |
| Audit | Gateway Recent calls; `POST /audit_log/` |
| MCP sync | Control Plane / Connected Infrastructure UI |
| Register agent | `POST /v1/agents/register` (HTTP + `X-Kimss-Key`) |
| Vault BYO endpoint + token cap | `POST/PATCH /api/v1/custom-model-endpoints` — see `/docs/custom_model_endpoints` |

## Errors

| HTTP | `detail.error` | Behavior |
|------|----------------|----------|
| 403 | `agent_disabled` | Kill switch — stop |
| 403 | `subscription_required` | Stop; upgrade / switch workspace |
| 429 | `governed_requests_exhausted` / `credit_*` | Stop; surface to user |
| 429 | `custom_endpoint_cap_exceeded` | Provider token cap (`cap_action=block`) — raise cap or wait for next month |
| 429 | `rate_limit_exceeded` | Backoff / Retry-After |
