# Changelog

## [Unreleased]

### Fixed

- Maven Central publish: bump `central-publishing-maven-plugin` **0.6.0 → 0.9.0** so staging drops `maven-metadata-central-staging.xml` (Central rejected 0.3.0 with “Bundle has content that does NOT have a .pom file: ai/kimss/kimss-java”).

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
