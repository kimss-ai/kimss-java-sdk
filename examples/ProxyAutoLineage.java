/**
 * Invisible Proxy with automatic Run-Id / lineage (setup-once, non-Forge).
 *
 * <p>Env: KIMSS_WORKSPACE_KEY or KIMSS_API_KEY, KIMSS_AGENT_ID, KIMSS_MODEL
 *
 * <p>Compile against the SDK jar; toggle Observe/Enforce in Guardrails — keep this client.
 */
import com.fasterxml.jackson.databind.JsonNode;
import com.kimss.KimssProxy;

import java.util.List;
import java.util.Map;

public final class ProxyAutoLineage {
  public static void main(String[] args) {
    String key = env("KIMSS_WORKSPACE_KEY");
    if (key == null) {
      key = env("KIMSS_API_KEY");
    }
    String agentId = env("KIMSS_AGENT_ID");
    if (agentId == null) {
      agentId = "orchestrator";
    }
    String model = env("KIMSS_MODEL");
    if (key == null || model == null) {
      System.err.println("Set KIMSS_WORKSPACE_KEY (or KIMSS_API_KEY) and KIMSS_MODEL.");
      System.exit(1);
    }

    KimssProxy proxy =
        KimssProxy.builder().apiKey(key).agentId(agentId).agentName("Orchestrator").build();
    JsonNode r =
        proxy.chatCompletions(
            model, List.of(Map.of("role", "user", "content", "Plan a one-step research handoff.")));
    JsonNode content = r.path("choices").path(0).path("message").path("content");
    System.out.println(content.isMissingNode() ? r : content.asText());
    proxy
        .currentContext()
        .ifPresent(ctx -> System.out.println("run_id (Swarm Runs): " + ctx.runId()));
    proxy
        .pendingChild()
        .ifPresent(
            child -> {
              System.out.println("pending child depth: " + child.depth());
              // Child hop: proxy.delegate("researcher"); proxy.chatCompletions(...);
            });
  }

  private static String env(String name) {
    String v = System.getenv(name);
    return v != null && !v.isBlank() ? v.trim() : null;
  }
}
