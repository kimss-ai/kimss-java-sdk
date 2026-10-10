package com.kimss;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for Invisible Proxy auto RunContext (no live gateway). */
class KimssProxyTest {

  @AfterEach
  void tearDown() {
    KimssProxy.clearRunContexts();
  }

  @Test
  void requestHeadersMintRootWhenAutoRoot() {
    KimssProxy proxy =
        KimssProxy.builder().apiKey("kimss_test").agentId("orchestrator").agentName("Orch").build();
    Map<String, String> h = proxy.requestHeaders();
    assertEquals("orchestrator", h.get("X-Kimss-Agent-Id"));
    assertEquals("Orch", h.get("X-Kimss-Agent-Name"));
    assertTrue(h.containsKey("X-Kimss-Run-Id"));
    assertEquals("0", h.get("X-Kimss-Depth"));
    assertTrue(h.containsKey("X-Kimss-Span-Id"));
    assertTrue(proxy.currentContext().isPresent());
  }

  @Test
  void absorbAndDelegateAdvancesChild() {
    KimssProxy proxy = KimssProxy.builder().apiKey("kimss_test").agentId("orchestrator").build();
    proxy.requestHeaders(); // mint root
    String runId = proxy.currentContext().get().runId();

    Map<String, String> resp = new LinkedHashMap<>();
    resp.put("X-Kimss-Run-Id", runId);
    resp.put("X-Kimss-Depth", "1");
    resp.put("X-Kimss-Parent-Span", "parentspan01");
    resp.put("X-Kimss-Span-Id", "childspan000001");
    resp.put("X-Kimss-Lineage", "hmac.token.example");
    assertTrue(proxy.absorbResponseHeaders(resp).isPresent());

    RunContext child = proxy.delegate("researcher", "Researcher");
    assertEquals(1, child.depth());
    assertEquals("hmac.token.example", child.lineage().orElse(null));
    assertEquals("researcher", proxy.agentId());
    Map<String, String> next = proxy.requestHeaders();
    assertEquals("1", next.get("X-Kimss-Depth"));
    assertEquals("researcher", next.get("X-Kimss-Agent-Id"));
  }

  @Test
  void fromLineageHeadersCaseInsensitive() {
    Map<String, String> raw = new LinkedHashMap<>();
    raw.put("x-kimss-run-id", "runabc");
    raw.put("x-kimss-depth", "2");
    raw.put("x-kimss-span-id", "spanxyz");
    raw.put("x-kimss-lineage", "tok");
    RunContext ctx = RunContext.fromLineageHeaders(raw).orElseThrow();
    assertEquals("runabc", ctx.runId());
    assertEquals(2, ctx.depth());
    assertEquals("tok", ctx.lineage().orElse(null));
  }

  @Test
  void delegateWithoutPendingFails() {
    KimssProxy proxy = KimssProxy.builder().apiKey("kimss_test").agentId("a").build();
    proxy.resetRun();
    assertThrows(IllegalStateException.class, () -> proxy.delegate("b"));
  }

  @Test
  void resetRunClearsPending() {
    KimssProxy proxy = KimssProxy.builder().apiKey("kimss_test").agentId("a").build();
    proxy.requestHeaders();
    Map<String, String> resp = new LinkedHashMap<>();
    resp.put("X-Kimss-Run-Id", "r1");
    resp.put("X-Kimss-Depth", "1");
    resp.put("X-Kimss-Span-Id", "s1");
    resp.put("X-Kimss-Lineage", "t");
    proxy.absorbResponseHeaders(resp);
    assertTrue(proxy.pendingChild().isPresent());
    proxy.resetRun();
    assertFalse(proxy.pendingChild().isPresent());
    assertEquals(0, proxy.currentContext().get().depth());
  }
}
