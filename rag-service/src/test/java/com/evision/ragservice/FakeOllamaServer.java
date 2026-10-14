package com.evision.ragservice;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Hermetic stand-in for Ollama that can stream NDJSON and observe client aborts. */
public final class FakeOllamaServer implements AutoCloseable {

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private final HttpServer server;
    private volatile CountDownLatch chatAborted = new CountDownLatch(1);
    private final AtomicInteger chatRequests = new AtomicInteger();

    private volatile int showStatus = 200;
    private volatile String showBody = "{\"capabilities\":[\"completion\",\"tools\"]}";
    private volatile float[] embedVector = defaultVector();
    private volatile List<String> chatLines = answer("Hello", " world");
    private volatile Duration lineDelay = Duration.ofMillis(10);
    private volatile Duration headerDelay = Duration.ZERO;
    private volatile int chatStatus = 200;
    private volatile String chatErrorBody = "";
    private volatile String lastChatBody;

    private FakeOllamaServer(HttpServer server) {
        this.server = server;
        server.createContext("/api/show", this::handleShow);
        server.createContext("/api/embed", this::handleEmbed);
        server.createContext("/api/chat", this::handleChat);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }

    public static FakeOllamaServer start() {
        try {
            HttpServer http = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            FakeOllamaServer fake = new FakeOllamaServer(http);
            http.start();
            return fake;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public String baseUrl() {
        return "http://localhost:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    /** Restores the default script and re-arms the abort latch, so one server can serve several tests. */
    public void reset() {
        this.chatLines = answer("Hello", " world");
        this.lineDelay = Duration.ofMillis(10);
        this.headerDelay = Duration.ZERO;
        this.chatStatus = 200;
        this.chatErrorBody = "";
        this.chatAborted = new CountDownLatch(1);
    }

    public void show(int status, String body) {
        this.showStatus = status;
        this.showBody = body;
    }

    public void embedVector(float[] vector) {
        this.embedVector = vector;
    }

    public void chat(List<String> lines, Duration delayBetweenLines) {
        this.chatLines = lines;
        this.lineDelay = delayBetweenLines;
    }

    public void chatHeaderDelay(Duration delay) {
        this.headerDelay = delay;
    }

    public void chatStatus(int status, String body) {
        this.chatStatus = status;
        this.chatErrorBody = body;
    }

    public String lastChatBody() {
        return lastChatBody;
    }

    public int chatRequests() {
        return chatRequests.get();
    }

    public boolean awaitChatAborted(Duration timeout) {
        try {
            return chatAborted.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    public static String tokenLine(String text) {
        return "{\"model\":\"fake\",\"message\":{\"role\":\"assistant\",\"content\":" + MAPPER.writeValueAsString(text)
                + "},\"done\":false}";
    }

    public static String doneLine(String doneReason, int promptEvalCount, int evalCount) {
        return "{\"model\":\"fake\",\"message\":{\"role\":\"assistant\",\"content\":\"\"},\"done\":true,\"done_reason\":\""
                + doneReason + "\",\"total_duration\":2000000000,\"load_duration\":1000000000,\"prompt_eval_count\":"
                + promptEvalCount + ",\"prompt_eval_cached_count\":0,\"eval_count\":" + evalCount + "}";
    }

    public static List<String> answer(String... tokens) {
        List<String> lines = new ArrayList<>();
        for (String token : tokens) {
            lines.add(tokenLine(token));
        }
        lines.add(doneLine("stop", 100, tokens.length));
        return lines;
    }

    private static float[] defaultVector() {
        float[] vector = new float[768];
        vector[0] = 1.0f;
        return vector;
    }

    private void handleShow(HttpExchange exchange) throws IOException {
        exchange.getRequestBody().readAllBytes();
        respond(exchange, showStatus, showBody);
    }

    private void handleEmbed(HttpExchange exchange) throws IOException {
        JsonNode request = MAPPER.readTree(exchange.getRequestBody().readAllBytes());
        int count = request.path("input").size();
        StringBuilder vector = new StringBuilder("[");
        float[] values = embedVector;
        for (int i = 0; i < values.length; i++) {
            vector.append(i == 0 ? "" : ",").append(values[i]);
        }
        vector.append(']');
        StringBuilder body = new StringBuilder("{\"embeddings\":[");
        for (int i = 0; i < count; i++) {
            body.append(i == 0 ? "" : ",").append(vector);
        }
        body.append("]}");
        respond(exchange, 200, body.toString());
    }

    private void handleChat(HttpExchange exchange) throws IOException {
        lastChatBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        chatRequests.incrementAndGet();
        sleep(headerDelay);
        if (chatStatus != 200) {
            respond(exchange, chatStatus, chatErrorBody);
            return;
        }
        exchange.getResponseHeaders().add("Content-Type", "application/x-ndjson");
        try {
            exchange.sendResponseHeaders(200, 0);
            OutputStream out = exchange.getResponseBody();
            for (String line : chatLines) {
                out.write((line + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
                sleep(lineDelay);
            }
            out.close();
        } catch (IOException e) {
            chatAborted.countDown();
        } finally {
            exchange.close();
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }

    private static void sleep(Duration duration) {
        if (duration.isZero()) {
            return;
        }
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
