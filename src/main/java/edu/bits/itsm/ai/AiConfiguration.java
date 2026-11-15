package edu.bits.itsm.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.ai")
public record AiConfiguration(String apiKey, String model) {
  public boolean isConfigured() {
    return apiKey != null && !apiKey.isBlank() && model != null && !model.isBlank();
  }

  @Override
  public String toString() {
    return "AiConfiguration[credentials=REDACTED]";
  }
}
