package edu.bits.itsm.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.bits.itsm.shared.BusinessException;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class OpenRouterAdapter implements AiTransport {
  private final AiConfiguration config;
  private final ObjectMapper json;
  private final HttpClient client;
  private final URI endpoint;
  private final Duration timeout;

  @Autowired
  public OpenRouterAdapter(AiConfiguration config, ObjectMapper json) {
    this(config, json, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
        URI.create("https://openrouter.ai/api/v1/chat/completions"), Duration.ofSeconds(25));
  }

  // Test seam for a loopback fake provider. Production always uses the fixed HTTPS endpoint above.
  OpenRouterAdapter(AiConfiguration config, ObjectMapper json, HttpClient client, URI endpoint, Duration timeout) {
    this.config = config;
    this.json = json;
    this.client = client;
    this.endpoint = endpoint;
    this.timeout = timeout;
  }

  public String complete(Map<String, Object> body) {
    if (!config.isConfigured())
      throw new BusinessException(
          "AI unavailable: add your OpenRouter key and selected model to local configuration.");
    CompletableFuture<HttpResponse<byte[]>> pending = null;
    try {
      var request = HttpRequest.newBuilder(endpoint).timeout(timeout)
          .header("Authorization", "Bearer " + config.apiKey())
          .header("Content-Type", "application/json")
          .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build();
      pending = client.sendAsync(request, info -> new LimitedBodySubscriber());
      // Cover the complete response body, including providers that send headers then stall.
      var response = pending.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
      if (response.statusCode() != 200)
        throw new BusinessException("AI provider unavailable. Try again later.");
      var envelope = json.readTree(response.body());
      if (envelope == null) throw new BusinessException("AI returned an empty response.");
      var content = envelope.path("choices").path(0).path("message").path("content");
      if (!content.isTextual() || content.asText().isBlank() || content.asText().length() > 8000)
        throw new BusinessException("AI returned an invalid suggestion.");
      return content.asText();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new BusinessException("AI request interrupted.");
    } catch (TimeoutException ex) {
      throw new BusinessException("AI request timed out; incident data is unchanged.");
    } catch (IOException | ExecutionException ex) {
      throw new BusinessException("AI provider could not be reached or returned invalid content; incident data is unchanged.");
    } finally {
      if (pending != null && !pending.isDone()) pending.cancel(true);
    }
  }

  /** Cancel oversized bodies before buffering more than 64 KiB. */
  private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
    private Flow.Subscription subscription;
    private long received;
    private boolean failed;
    public CompletionStage<byte[]> getBody() { return delegate.getBody(); }
    public void onSubscribe(Flow.Subscription subscription) {
      this.subscription = subscription;
      delegate.onSubscribe(subscription);
    }
    public void onNext(List<ByteBuffer> buffers) {
      if (failed) return;
      for (var buffer : buffers) received += buffer.remaining();
      if (received > 65536) {
        failed = true; subscription.cancel(); delegate.onError(new IOException("Response exceeds limit"));
      } else delegate.onNext(buffers);
    }
    public void onError(Throwable error) { if (!failed) { failed = true; delegate.onError(error); } }
    public void onComplete() { if (!failed) delegate.onComplete(); }
  }
}
