package edu.bits.itsm.ai;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import edu.bits.itsm.shared.BusinessException;
import java.net.*;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** All requests target a loopback fake provider; no OpenRouter account, key or model is used. */
class OpenRouterAdapterTest {
  HttpServer server;
  ExecutorService executor;
  ObjectMapper json = new ObjectMapper();
  @BeforeEach void start() throws Exception {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    executor = Executors.newCachedThreadPool(); server.setExecutor(executor);
  }
  @AfterEach void stop() { server.stop(0); executor.shutdownNow(); }
  OpenRouterAdapter adapter(Duration timeout) {
    return new OpenRouterAdapter(new AiConfiguration("test-only-placeholder", "test-only-model"), json,
        HttpClient.newHttpClient(), URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/chat"), timeout);
  }
  void response(int status, String body) {
    server.createContext("/chat", exchange -> {
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, bytes.length);
      try (var out = exchange.getResponseBody()) { out.write(bytes); }
    }); server.start();
  }
  @Test void successSendsServerSideConfigurationAndParsesAdvice() throws Exception {
    var auth = new AtomicReference<String>(); var request = new AtomicReference<String>();
    server.createContext("/chat", exchange -> {
      auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
      request.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] bytes = "{\"choices\":[{\"message\":{\"content\":\"Review the network status.\"}}]}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, bytes.length);
      try (var out = exchange.getResponseBody()) { out.write(bytes); }
    }); server.start();
    assertEquals("Review the network status.", adapter(Duration.ofSeconds(3)).complete(Map.of("model", "test-only-model")));
    assertEquals("Bearer test-only-placeholder", auth.get());
    assertEquals("test-only-model", json.readTree(request.get()).path("model").asText());
  }
  @ParameterizedTest @ValueSource(ints = {401, 429, 500})
  void providerErrorsNeverExposeResponseOrCredential(int status) {
    response(status, "Sensitive provider diagnostic test-only-placeholder");
    var error = assertThrows(BusinessException.class, () -> adapter(Duration.ofSeconds(3)).complete(Map.of()));
    assertFalse(error.getMessage().contains("Sensitive")); assertFalse(error.getMessage().contains("test-only-placeholder"));
  }
  @ParameterizedTest @ValueSource(strings = {"", "not JSON", "{}", "{\"choices\":[{\"message\":{\"content\":\" \"}}]}", "{\"choices\":[{\"message\":{\"content\":12}}]}"})
  void malformedOrEmptyProviderContentIsRejected(String body) {
    response(200, body);
    assertThrows(BusinessException.class, () -> adapter(Duration.ofSeconds(3)).complete(Map.of()));
  }
  @Test void oversizedBodyIsCancelled() {
    response(200, "x".repeat(70000));
    assertThrows(BusinessException.class, () -> adapter(Duration.ofSeconds(3)).complete(Map.of()));
  }
  @Test void stalledResponseBodyHasAWholeRequestDeadline() {
    var headersSent = new CountDownLatch(1); var release = new CountDownLatch(1);
    server.createContext("/chat", exchange -> {
      exchange.sendResponseHeaders(200, 0); exchange.getResponseBody().write('{'); exchange.getResponseBody().flush();
      headersSent.countDown();
      try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
      exchange.close();
    }); server.start();
    try {
      assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
        var error = assertThrows(BusinessException.class, () -> adapter(Duration.ofMillis(800)).complete(Map.of()));
        assertTrue(headersSent.await(1, TimeUnit.SECONDS));
        assertTrue(error.getMessage().contains("timed out"));
      });
    } finally { release.countDown(); }
  }
  @Test void interruptionIsPreserved() {
    response(200, "{}");
    try {
      Thread.currentThread().interrupt();
      var error = assertThrows(BusinessException.class, () -> adapter(Duration.ofSeconds(3)).complete(Map.of()));
      assertTrue(error.getMessage().contains("interrupted")); assertTrue(Thread.currentThread().isInterrupted());
    } finally { Thread.interrupted(); }
  }
}
