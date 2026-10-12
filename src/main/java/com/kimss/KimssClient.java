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
import java.util.Objects;

/**
 * Kimss Java client for residual control-plane / legacy HTTP helpers. Auth via {@code X-Kimss-Key}.
 *
 * <p><strong>Do not use for chat/completions.</strong> Prefer the official OpenAI OkHttp client with
 * {@code baseUrl("https://api.kimss.ai/v1")} and {@code X-Kimss-Agent-Id} headers (see AI_INTEGRATION.md).
 *
 * <p>{@link #agents()}{@code .run(...)} and {@link #models()}{@code .create(...)} are {@code @Deprecated}.
 */
public final class KimssClient {
  public static final String DEFAULT_BASE_URL = "https://api.kimss.ai";

  private final String apiKey;
  private final String baseUrl;
  private final HttpClient http;
  private final ObjectMapper mapper;
  private final AgentsApi agents;
  private final ModelsApi models;

  private KimssClient(Builder b) {
    this.apiKey = Objects.requireNonNull(b.apiKey, "apiKey").trim();
    if (this.apiKey.isEmpty()) {
      throw new IllegalArgumentException("apiKey must not be empty");
    }
    String base = b.baseUrl != null && !b.baseUrl.isBlank() ? b.baseUrl.trim() : DEFAULT_BASE_URL;
    while (base.endsWith("/")) {
      base = base.substring(0, base.length() - 1);
    }
    this.baseUrl = base;
    this.http = b.http != null ? b.http : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    this.mapper = b.mapper != null ? b.mapper : new ObjectMapper();
    this.agents = new AgentsApi(this);
    this.models = new ModelsApi(this);
  }

  /**
   * Build from {@code KIMSS_API_KEY} (preferred) or alias {@code KIMSS_WORKSPACE_KEY},
   * and optional {@code KIMSS_BASE_URL}.
   */
  public static KimssClient fromEnv() {
    String key = System.getenv("KIMSS_API_KEY");
    if (key == null || key.isBlank()) {
      key = System.getenv("KIMSS_WORKSPACE_KEY");
    }
    if (key == null || key.isBlank()) {
      throw new IllegalStateException("Set KIMSS_API_KEY (or KIMSS_WORKSPACE_KEY)");
    }
    String base = System.getenv("KIMSS_BASE_URL");
    return builder().apiKey(key).baseUrl(base != null && !base.isBlank() ? base : DEFAULT_BASE_URL).build();
  }

  public static Builder builder() {
    return new Builder();
  }

  public AgentsApi agents() {
    return agents;
  }

  public ModelsApi models() {
    return models;
  }

  public String baseUrl() {
    return baseUrl;
  }

  JsonNode postJson(String path, ObjectNode body) {
    String url = baseUrl + (path.startsWith("/") ? path : "/" + path);
    try {
      String json = mapper.writeValueAsString(body);
      HttpRequest req = HttpRequest.newBuilder()
          .uri(URI.create(url))
          .timeout(Duration.ofSeconds(120))
          .header("Content-Type", "application/json")
          .header("X-Kimss-Key", apiKey)
          .header("Accept", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(json))
          .build();
      HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
      int code = res.statusCode();
      String raw = res.body() == null ? "" : res.body();
      if (code < 200 || code >= 300) {
        throw KimssException.fromHttp(code, raw);
      }
      if (raw.isBlank()) {
        return mapper.createObjectNode();
      }
      return mapper.readTree(raw);
    } catch (KimssException e) {
      throw e;
    } catch (IOException | InterruptedException e) {
      if (e instanceof InterruptedException) {
        Thread.currentThread().interrupt();
      }
      throw new KimssException(0, "transport_error", e.getMessage(), e);
    }
  }

  ObjectMapper mapper() {
    return mapper;
  }

  public static final class Builder {
    private String apiKey;
    private String baseUrl = DEFAULT_BASE_URL;
    private HttpClient http;
    private ObjectMapper mapper;

    public Builder apiKey(String apiKey) {
      this.apiKey = apiKey;
      return this;
    }

    public Builder baseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
      return this;
    }

    public Builder httpClient(HttpClient http) {
      this.http = http;
      return this;
    }

    public Builder objectMapper(ObjectMapper mapper) {
      this.mapper = mapper;
      return this;
    }

    public KimssClient build() {
      return new KimssClient(this);
    }
  }
}
