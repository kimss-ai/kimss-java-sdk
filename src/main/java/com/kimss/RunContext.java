package com.kimss;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Declared-run lineage for Invisible Proxy clients (OpenAI / Anthropic through Kimss).
 *
 * <p>Mirrors Python {@code kimss.run_context.RunContext} / Forge semantics. Gateway response
 * headers mint the <em>next</em> child hop; call {@link KimssProxy#delegate(String)} (or promote
 * pending) before the child agent's request.
 */
public final class RunContext {
  public static final String HEADER_RUN_ID = "X-Kimss-Run-Id";
  public static final String HEADER_DEPTH = "X-Kimss-Depth";
  public static final String HEADER_PARENT_SPAN = "X-Kimss-Parent-Span";
  public static final String HEADER_SPAN_ID = "X-Kimss-Span-Id";
  public static final String HEADER_LINEAGE = "X-Kimss-Lineage";

  private static final String[] LINEAGE_KEYS = {
    HEADER_RUN_ID, HEADER_DEPTH, HEADER_PARENT_SPAN, HEADER_SPAN_ID, HEADER_LINEAGE
  };

  private final String runId;
  private final int depth;
  private final String parentSpan;
  private final String spanId;
  private final String lineage;

  public RunContext(String runId, int depth, String parentSpan, String spanId, String lineage) {
    this.runId = Objects.requireNonNull(runId, "runId").trim();
    if (this.runId.isEmpty()) {
      throw new IllegalArgumentException("runId must not be empty");
    }
    this.depth = depth;
    this.parentSpan = parentSpan != null && !parentSpan.isBlank() ? parentSpan.trim() : null;
    String sid = spanId != null && !spanId.isBlank() ? spanId.trim() : UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    this.spanId = sid;
    this.lineage = lineage != null && !lineage.isBlank() ? lineage.trim() : null;
  }

  /** New root context (depth 0, fresh run + span ids). */
  public static RunContext root() {
    String run = UUID.randomUUID().toString().replace("-", "");
    String span = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    return new RunContext(run, 0, null, span, null);
  }

  public String runId() {
    return runId;
  }

  public int depth() {
    return depth;
  }

  public Optional<String> parentSpan() {
    return Optional.ofNullable(parentSpan);
  }

  public String spanId() {
    return spanId;
  }

  public Optional<String> lineage() {
    return Optional.ofNullable(lineage);
  }

  /** Lineage headers to send on the next gateway hop. */
  public Map<String, String> headers() {
    Map<String, String> out = new LinkedHashMap<>();
    out.put(HEADER_RUN_ID, runId);
    out.put(HEADER_DEPTH, String.valueOf(depth));
    out.put(HEADER_SPAN_ID, spanId);
    if (parentSpan != null) {
      out.put(HEADER_PARENT_SPAN, parentSpan);
    }
    if (lineage != null) {
      out.put(HEADER_LINEAGE, lineage);
    }
    return Collections.unmodifiableMap(out);
  }

  /** Extract Kimss lineage headers from an HTTP header map (any casing). */
  public static Map<String, String> lineageHeadersFrom(Map<String, String> headers) {
    if (headers == null || headers.isEmpty()) {
      return Collections.emptyMap();
    }
    Map<String, String> lower = new LinkedHashMap<>();
    for (Map.Entry<String, String> e : headers.entrySet()) {
      if (e.getKey() == null || e.getValue() == null) {
        continue;
      }
      String v = e.getValue().trim();
      if (!v.isEmpty()) {
        lower.put(e.getKey().toLowerCase(Locale.ROOT), v);
      }
    }
    Map<String, String> out = new LinkedHashMap<>();
    for (String key : LINEAGE_KEYS) {
      String val = lower.get(key.toLowerCase(Locale.ROOT));
      if (val != null) {
        out.put(key, val);
      }
    }
    return Collections.unmodifiableMap(out);
  }

  /** Parse a RunContext from lineage headers; {@code empty} if incomplete. */
  public static Optional<RunContext> fromLineageHeaders(Map<String, String> headers) {
    Map<String, String> lin = lineageHeadersFrom(headers);
    if (lin.isEmpty()) {
      return Optional.empty();
    }
    String runId = lin.get(HEADER_RUN_ID);
    String depthRaw = lin.get(HEADER_DEPTH);
    String span = lin.get(HEADER_SPAN_ID);
    if (runId == null || depthRaw == null || span == null) {
      return Optional.empty();
    }
    final int depth;
    try {
      depth = Integer.parseInt(depthRaw.trim());
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
    return Optional.of(
        new RunContext(runId, depth, lin.get(HEADER_PARENT_SPAN), span, lin.get(HEADER_LINEAGE)));
  }
}
