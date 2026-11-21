package lk.sithiyama.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Java equivalent of a Node-style {@code config/gemini.js}.
 *
 * <p>The server reads {@code GEMINI_API_KEY} from {@code .env} (or the process
 * environment). A caller may override it per request with the
 * {@code x-gemini-key} header; the server value is the fallback. The key is
 * never logged and is never placed in a response body.</p>
 */
@Configuration
public class GeminiConfig {

    public static final String API_KEY_HEADER = "x-gemini-key";

    private final String serverKey;
    private final String baseUrl;
    private final int timeoutSeconds;

    public GeminiConfig(
            @Value("${sithiyama.ai.gemini.api-key:}") String serverKey,
            @Value("${sithiyama.ai.gemini.base-url:https://generativelanguage.googleapis.com}") String baseUrl,
            @Value("${sithiyama.ai.gemini.timeout-seconds:30}") int timeoutSeconds) {
        this.serverKey = serverKey == null ? "" : serverKey.trim();
        String configuredBaseUrl = baseUrl == null || baseUrl.isBlank()
                ? "https://generativelanguage.googleapis.com"
                : baseUrl.trim();
        this.baseUrl = configuredBaseUrl.endsWith("/")
                ? configuredBaseUrl.substring(0, configuredBaseUrl.length() - 1)
                : configuredBaseUrl;
        this.timeoutSeconds = Math.max(5, timeoutSeconds);
    }

    /** Header wins when present; otherwise use the server-side .env value. */
    public String resolveKey(String headerKey) {
        if (headerKey != null && !headerKey.isBlank()) {
            return headerKey.trim();
        }
        return serverKey;
    }

    public boolean hasKey(String headerKey) {
        return !resolveKey(headerKey).isEmpty();
    }

    public String baseUrl() {
        return baseUrl;
    }

    @Bean("geminiRestClient")
    public RestClient geminiRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(timeoutSeconds * 1_000);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
