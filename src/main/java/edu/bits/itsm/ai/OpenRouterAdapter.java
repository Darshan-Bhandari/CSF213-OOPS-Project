package edu.bits.itsm.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.bits.itsm.shared.BusinessException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class OpenRouterAdapter implements AiTransport {
  private final AiConfiguration config;
  private final ObjectMapper json;
  private final HttpClient client;

  public OpenRouterAdapter(AiConfiguration config, ObjectMapper json) {
    this.config = config;
    this.json = json;
    client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
  }

  public String complete(Map<String, Object> body) {
    if (!config.isConfigured())
      throw new BusinessException(
          "AI unavailable: add your OpenRouter key and selected model to local configuration.");
    try {
      var request =
          HttpRequest.newBuilder(URI.create("https://openrouter.ai/api/v1/chat/completions"))
              .timeout(Duration.ofSeconds(25))
              .header("Authorization", "Bearer " + config.apiKey())
              .header("Content-Type", "application/json")
              .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
              .build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
      try (var stream = response.body()) {
        byte[] bytes = stream.readNBytes(65537);
        if (response.statusCode() != 200 || bytes.length > 65536)
          throw new BusinessException(
              "AI provider unavailable or response too large. Try again later.");
        var content = json.readTree(bytes).path("choices").path(0).path("message").path("content");
        if (!content.isTextual() || content.asText().isBlank() || content.asText().length() > 8000)
          throw new BusinessException("AI returned an invalid suggestion.");
        return content.asText();
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new BusinessException("AI request interrupted.");
    } catch (java.io.IOException ex) {
      throw new BusinessException("AI provider could not be reached; incident data is unchanged.");
    }
  }
}
