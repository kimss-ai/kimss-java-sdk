# Changelog

## [Unreleased]

## [0.3.2] — 2026-10-10

### Fixed

- Maven Central: drop conflicting Portal deployments before upload; publish `0.3.2` after `0.3.1` was blocked by an in-flight deployment. Same `KimssProxy` payload as 0.3.0/0.3.1.

## [0.3.1] — 2026-10-10

### Fixed

- Maven Central publish (SSOT: `kimssApi/kimss_java_sdk` → mirror [kimss-java-sdk](https://github.com/kimss-ai/kimss-java-sdk)): sanitize staging (strip `maven-metadata*`), verify `.pom` in zip, upload via Central Publisher API. Fixes portal rejection `Bundle has content that does NOT have a .pom file: ai/kimss/kimss-java`.

### Added

- Same as 0.3.0 (`KimssProxy` / `RunContext`) — 0.3.0 never landed on Central.

## [0.3.0] — 2026-10-10

### Added

- **`KimssProxy`** — Invisible Proxy helper with automatic `RunContext` / lineage headers (`requestHeaders`, `absorbResponseHeaders`, `delegate`, JDK `chatCompletions`). Parity with Python `kimss.proxy.KimssProxy` (PyPI `kimss>=2.2.0`).
- **`RunContext`** — declared-run lineage model + header parse helpers.
- Example: `examples/ProxyAutoLineage.java`.
- Unit tests: `KimssProxyTest` (no live gateway).

### Notes

- Plain gateway chat still uses the official OpenAI/Anthropic Java client (no Maven required for Path A).
- Use `KimssProxy` when you want Swarm Runs Observe/Enforce without hand-rolling `X-Kimss-Run-Id` headers.
- `KimssClient` / `AgentsApi.run` remain deprecated for chat.

## [0.2.0] — 2026-08-21

### Deprecated

- `AgentsApi.run(...)` and `ModelsApi.create(...)` are `@Deprecated`. Prefer OpenAI OkHttp with `baseUrl("https://api.kimss.ai/v1")` and `X-Kimss-Agent-Id` headers ([AI_INTEGRATION.md](AI_INTEGRATION.md)).

### Changed

- Docs repositioned around the gateway proxy pattern; package is residual control-plane / legacy only.

## [0.1.2]

Initial Maven Central publish (`ai.kimss:kimss-java`).
