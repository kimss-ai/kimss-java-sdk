package com.kimss;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Invisible Proxy client with automatic declared-run lineage (Java parity of Python {@code KimssProxy}).
 *
 * <p>Setup-once for non-Forge customers:
 *
 * <pre>{@code
 * KimssProxy proxy = KimssProxy.builder()
 *     .apiKey(System.getenv("KIMSS_API_KEY"))
 *     .agentId("orchestrator")
 *     .build();
 * JsonNode r = proxy.chatCompletions("custom:model", List.of(Map.of("role", "user", "content", "hi")));
 * proxy.delegate("researcher");
 * proxy.chatCompletions(...);
 * }</pre>
 *
 * <p>Toggle Observe/Enforce in Guardrails — keep this client; no redeploy to turn off.
 *
 * <p>For the official OpenAI Java client, call {@link #requestHeaders()} and pass those as default /
 * per-request headers, then {@link #absorbResponseHeaders(Map)} from the response.
 */
public final class KimssProxy {
  public static final String DEFAULT_OPENAI_GATEWAY = "https://api.kimss.ai/v1";
  public static final String DEFAULT_ANTHROPIC_GATEWAY = "https://api.kimss.ai";

  private static final ThreadLocal<RunContext> CURRENT = new ThreadLocal<>();
  private static final ThreadLocal<RunContext> PENDING = new ThreadLocal<>();

  private final String apiKey;
  private String agentId;
  private String agentName;
  private final String openaiBaseUrl;
  private final String anthropicBaseUrl;
  private final Duration timeout;
  private final boolean autoRoot;
  private final HttpClient http;
  private final ObjectMapper mapper;
  private RunContext pendingChild;

  private KimssProxy(Builder b) {
    String aid = Objects.requireNonNull(b.agentId, "agentId").trim();
    if (aid.isEmpty()) {
      throw new IllegalArgumentException("agentId is required");
    }
    String key = b.apiKey;
    if (key == null || key.isBlank()) {
      key = firstEnv("KIMSS_WORKSPACE_KEY", "KIMSS_API_KEY");
    }
    this.apiKey = key != null ? key.trim() : "";
    this.agentId = aid;
    this.agentName = b.agentName != null && !b.agentName.isBlank() ? b.agentName.trim() : null;
    String oai =
        b.openaiBaseUrl != null && !b.openaiBaseUrl.isBlank()
            ? b.openaiBaseUrl.trim()
            : firstEnv("KIMSS_GATEWAY_URL", null);
    if (oai == null || oai.isBlank()) {
      oai = DEFAULT_OPENAI_GATEWAY;
    }
    while (oai.endsWith("/")) {
      oai = oai.substring(0, oai.length() - 1);
    }
    this.openaiBaseUrl = oai;
    String anth =
        b.anthropicBaseUrl != null && !b.anthropicBaseUrl.isBlank()
            ? b.anthropicBaseUrl.trim()
            : firstEnv("ANTHROPIC_BASE_URL", "KIMSS_BASE_URL");
    if (anth == null || anth.isBlank()) {
      anth = DEFAULT_ANTHROPIC_GATEWAY;
    }
    while (anth.endsWith("/")) {
      anth = anth.substring(0, anth.length() - 1);
    }
    if (anth.endsWith("/v1")) {
      anth = anth.substring(0, anth.length() - 3);
    }
    this.anthropicBaseUrl = anth;
    this.timeout = b.timeout != null ? b.timeout : Duration.ofSeconds(120);
    this.autoRoot = b.autoRoot;
    this.http =
        b.http != null
            ? b.http
            : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    this.mapper = b.mapper != null ? b.mapper : new ObjectMapper();
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Clear thread-local contexts (tests / new top-level run). */
  public static void clearRunContexts() {
    CURRENT.remove();
    PENDING.remove();
  }

  public static Optional<RunContext> getRunContext() {
    return Optional.ofNullable(CURRENT.get());
  }

  public static Optional<RunContext> getPendingChild() {
    return Optional.ofNullable(PENDING.get());
  }

  public RunContext resetRun() {
    clearRunContexts();
    pendingChild = null;
    RunContext root = RunContext.root();
    CURRENT.set(root);
    return root;
  }

  public Optional<RunContext> currentContext() {
    return getRunContext();
  }

  public Optional<RunContext> pendingChild() {
    return Optional.ofNullable(pendingChild != null ? pendingChild : PENDING.get());
  }

  /** Agent + lineage headers for the next gateway hop. */
  public Map<String, String> requestHeaders() {
    return requestHeaders(Collections.emptyMap());
  }

  public Map<String, String> requestHeaders(Map<String, String> extra) {
    if (autoRoot && CURRENT.get() == null) {
      CURRENT.set(RunContext.root());
    }
    RunContext ctx = CURRENT.get();
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("X-Kimss-Agent-Id", agentId);
    if (agentName != null) {
      headers.put("X-Kimss-Agent-Name", agentName);
    }
    if (ctx != null) {
      headers.putAll(ctx.headers());
    }
    if (extra != null) {
      for (Map.Entry<String, String> e : extra.entrySet()) {
        if (e.getKey() != null && e.getValue() != null) {
          headers.put(e.getKey(), e.getValue());
        }
      }
    }
    return Collections.unmodifiableMap(headers);
  }

  /** Store server-minted next-hop identity from response headers. */
  public Optional<RunContext> absorbResponseHeaders(Map<String, String> headers) {
    Optional<RunContext> pending = RunContext.fromLineageHeaders(headers);
    if (pending.isPresent()) {
      PENDING.set(pending.get());
      pendingChild = pending.get();
    }
    return pending;
  }

  /**
   * Advance to the gateway-minted child hop and switch agent attribution.
   *
   * @throws IllegalStateException if no pending child lineage exists
   */
  public RunContext delegate(String agentId) {
    return delegate(agentId, null);
  }

  public RunContext delegate(String agentId, String agentName) {
    String aid = Objects.requireNonNull(agentId, "agentId").trim();
    if (aid.isEmpty()) {
      throw new IllegalArgumentException("agentId is required for delegate");
    }
    RunContext ctx;
    if (pendingChild != null) {
      ctx = pendingChild;
      pendingChild = null;
      PENDING.remove();
      CURRENT.set(ctx);
    } else {
      RunContext pending = PENDING.get();
      if (pending == null) {
        throw new IllegalStateException(
            "No pending child lineage. Call the parent hop first so the gateway "
                + "can mint X-Kimss-Lineage on the response.");
      }
      PENDING.remove();
      CURRENT.set(pending);
      ctx = pending;
    }
    this.agentId = aid;
    if (agentName != null) {
      this.agentName = agentName.isBlank() ? null : agentName.trim();
    }
    return ctx;
  }

  public String agentId() {
    return agentId;
  }

  public String openaiBaseUrl() {
    return openaiBaseUrl;
  }

  public String anthropicBaseUrl() {
    return anthropicBaseUrl;
  }

  /**
   * JDK HttpClient chat completions (no OpenAI Java dependency). Absorbs lineage from response
   * headers.
   */
  public JsonNode chatCompletions(String model, List<Map<String, String>> messages) {
    return chatCompletions(model, messages, null, null, null);
  }

  public JsonNode chatCompletions(
      String model,
      List<Map<String, String>> messages,
      List<Object> tools,
      Integer maxTokens,
      Double temperature) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalStateException("apiKey required (or set KIMSS_API_KEY / KIMSS_WORKSPACE_KEY)");
    }
    String url = openaiBaseUrl + "/chat/completions";
    ObjectNode payload = mapper.createObjectNode();
    payload.put("model", model);
    ArrayNode msgs = payload.putArray("messages");
    if (messages != null) {
      for (Map<String, String> m : messages) {
        ObjectNode n = msgs.addObject();
        if (m.get("role") != null) {
          n.put("role", m.get("role"));
        }
        if (m.get("content") != null) {
          n.put("content", m.get("content"));
        }
      }
    }
    if (tools != null && !tools.isEmpty()) {
      payload.set("tools", mapper.valueToTree(tools));
      payload.put("tool_choice", "auto");
    }
    if (maxTokens != null) {
      payload.put("max_tokens", maxTokens);
    }
    if (temperature != null) {
      payload.put("temperature", temperature);
    }
    try {
      String json = mapper.writeValueAsString(payload);
      HttpRequest.Builder req =
          HttpRequest.newBuilder()
              .uri(URI.create(url))
              .timeout(timeout)
              .header("Content-Type", "application/json")
              .header("Authorization", "Bearer " + apiKey)
              .header("Accept", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json));
      for (Map.Entry<String, String> h : requestHeaders().entrySet()) {
        req.header(h.getKey(), h.getValue());
      }
      HttpResponse<String> res = http.send(req.build(), HttpResponse.BodyHandlers.ofString());
      int code = res.statusCode();
      String raw = res.body() == null ? "" : res.body();
      Map<String, String> respHeaders = new LinkedHashMap<>();
      res.headers()
          .map()
          .forEach(
              (k, vals) -> {
                if (k != null && vals != null && !vals.isEmpty()) {
                  respHeaders.put(k, vals.get(0));
                }
              });
      absorbResponseHeaders(respHeaders);
      if (code < 200 || code >= 300) {
        throw KimssException.fromHttp(code, raw);
      }
      if (raw.isBlank()) {
        return mapper.createObjectNode();
      }
      JsonNode data = mapper.readTree(raw);
      Map<String, String> lin = RunContext.lineageHeadersFrom(respHeaders);
      if (!lin.isEmpty() && data instanceof ObjectNode) {
        ((ObjectNode) data).set("_kimss_lineage", mapper.valueToTree(lin));
      }
      return data;
    } catch (KimssException e) {
      throw e;
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new KimssException(0, "client_error", "chatCompletions failed: " + e.getMessage(), e);
    }
  }

  private static String firstEnv(String a, String b) {
    String v = System.getenv(a);
    if (v != null && !v.isBlank()) {
      return v;
    }
    if (b != null) {
      v = System.getenv(b);
      if (v != null && !v.isBlank()) {
        return v;
      }
    }
    return null;
  }

  public static final class Builder {
    private String apiKey;
    private String agentId;
    private String agentName;
    private String openaiBaseUrl;
    private String anthropicBaseUrl;
    private Duration timeout;
    private boolean autoRoot = true;
    private HttpClient http;
    private ObjectMapper mapper;

    public Builder apiKey(String apiKey) {
      this.apiKey = apiKey;
      return this;
    }

    public Builder agentId(String agentId) {
      this.agentId = agentId;
      return this;
    }

    public Builder agentName(String agentName) {
      this.agentName = agentName;
      return this;
    }

    public Builder openaiBaseUrl(String openaiBaseUrl) {
      this.openaiBaseUrl = openaiBaseUrl;
      return this;
    }

    public Builder anthropicBaseUrl(String anthropicBaseUrl) {
      this.anthropicBaseUrl = anthropicBaseUrl;
      return this;
    }

    public Builder timeout(Duration timeout) {
      this.timeout = timeout;
      return this;
    }

    public Builder autoRoot(boolean autoRoot) {
      this.autoRoot = autoRoot;
      return this;
    }

    public Builder http(HttpClient http) {
      this.http = http;
      return this;
    }

    public Builder mapper(ObjectMapper mapper) {
      this.mapper = mapper;
      return this;
    }

    public KimssProxy build() {
      return new KimssProxy(this);
    }
  }
}
