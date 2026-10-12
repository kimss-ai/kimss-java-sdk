/**
 * Canonical Kimss gateway proxy using the official OpenAI Java OkHttp client.
 *
 * Requires dependency: com.openai:openai-java (OkHttp) in your app — not kimss-java.
 *
 * Env:
 *   KIMSS_API_KEY or KIMSS_WORKSPACE_KEY
 *   KIMSS_AGENT_ID
 *   KIMSS_MODEL
 * Optional: KIMSS_GATEWAY_URL (default https://api.kimss.ai/v1), KIMSS_AGENT_NAME
 *
 * Compile/run from your application module that already depends on openai-java.
 * This file is documentation-first for AI assistants and developers.
 */
public class GatewayProxyFirstCall {
  public static void main(String[] args) {
    String key = firstNonBlank(System.getenv("KIMSS_API_KEY"), System.getenv("KIMSS_WORKSPACE_KEY"));
    String agentId = System.getenv("KIMSS_AGENT_ID");
    String model = System.getenv("KIMSS_MODEL");
    if (key == null || agentId == null || agentId.isBlank() || model == null || model.isBlank()) {
      System.err.println("Set KIMSS_API_KEY (or KIMSS_WORKSPACE_KEY), KIMSS_AGENT_ID, and KIMSS_MODEL");
      System.exit(1);
    }
    String base = firstNonBlank(System.getenv("KIMSS_GATEWAY_URL"), "https://api.kimss.ai/v1");
    String name = firstNonBlank(System.getenv("KIMSS_AGENT_NAME"), "Gateway Proxy Agent");

    // Pseudocode-shaped for assistants: use OpenAIOkHttpClient.builder() in your app.
    System.out.println("Configure OpenAIOkHttpClient:");
    System.out.println("  baseUrl=" + base);
    System.out.println("  apiKey=<workspace key>");
    System.out.println("  header X-Kimss-Agent-Id=" + agentId);
    System.out.println("  header X-Kimss-Agent-Name=" + name);
    System.out.println("  then chat.completions.create(model=" + model + ", ...)");
    System.out.println("See AI_INTEGRATION.md for the full snippet.");
  }

  private static String firstNonBlank(String a, String b) {
    if (a != null && !a.isBlank()) {
      return a.trim();
    }
    if (b != null && !b.isBlank()) {
      return b.trim();
    }
    return null;
  }
}
