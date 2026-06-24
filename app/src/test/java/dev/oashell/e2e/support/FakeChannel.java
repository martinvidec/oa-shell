package dev.oashell.e2e.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Simulierter Channel für E2E-Tests: verbindet sich token-authentifiziert mit der
 * Bridge {@code /bridge}, sendet {@code hello} und kann Antworten skripten
 * (reply, permission_request, file_tree_result). Eingehende Envelopes (chat,
 * permission_verdict, file_tree …) werden für Assertions gepuffert.
 */
public class FakeChannel implements AutoCloseable {

    private final ObjectMapper mapper = new ObjectMapper();
    private final WebSocket ws;
    private final BlockingQueue<JsonNode> inbound = new LinkedBlockingQueue<>();

    public FakeChannel(int port, JWKSource<SecurityContext> jwkSource, String sub, String jti,
            String cwdBasename) throws Exception {
        String token = mintToken(jwkSource, sub, jti);
        this.ws = HttpClient.newHttpClient().newWebSocketBuilder()
                .header("Authorization", "Bearer " + token)
                .buildAsync(URI.create("ws://localhost:" + port + "/bridge"), new Listener())
                .get(10, TimeUnit.SECONDS);
        send(Map.of("type", "hello", "cwd", "/projects/" + cwdBasename,
                "cwdBasename", cwdBasename, "channelVersion", "e2e"));
    }

    /** Kanonischer Datei-Baum, mit dem auf file_tree-Anfragen geantwortet wird. */
    private volatile List<Map<String, Object>> cannedTree = List.of(
            Map.of("name", "hello.txt", "type", "file", "size", 2),
            Map.of("name", "src", "type", "dir"));

    public void setCannedTree(List<Map<String, Object>> entries) {
        this.cannedTree = entries;
    }

    public void send(Object envelope) {
        try {
            // Nicht blockieren (auch aus dem Listener heraus aufrufbar).
            ws.sendText(mapper.writeValueAsString(envelope), true);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public void sendReply(String chatId, String text) {
        send(Map.of("type", "reply", "chat_id", chatId, "text", text));
    }

    public void sendPermissionRequest(String requestId, String tool, String description, String preview) {
        send(Map.of("type", "permission_request", "request_id", requestId,
                "tool_name", tool, "description", description, "input_preview", preview));
    }

    public void sendFileTreeResult(String requestId, List<Map<String, Object>> entries) {
        send(Map.of("type", "file_tree_result", "requestId", requestId, "entries", entries));
    }

    /** Wartet bis zu {@code seconds} auf das nächste eingehende Envelope eines Typs. */
    public JsonNode awaitInbound(String type, int seconds) throws InterruptedException {
        long end = System.nanoTime() + seconds * 1_000_000_000L;
        while (System.nanoTime() < end) {
            JsonNode node = inbound.poll(200, TimeUnit.MILLISECONDS);
            if (node != null && type.equals(node.path("type").asText())) {
                return node;
            }
        }
        return null;
    }

    @Override
    public void close() {
        try {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
        } catch (Exception ignored) {
            // best effort
        }
    }

    private final class Listener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                try {
                    JsonNode node = mapper.readTree(buffer.toString());
                    inbound.add(node);
                    autoRespond(node);
                } catch (Exception ignored) {
                    // kein gültiges JSON -> verwerfen
                }
                buffer.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        private void autoRespond(JsonNode node) {
            String type = node.path("type").asText();
            String requestId = node.path("requestId").asText();
            if ("file_tree".equals(type)) {
                sendFileTreeResult(requestId, cannedTree);
            } else if ("file_content".equals(type)) {
                send(Map.of("type", "file_content_result", "requestId", requestId, "content", "hi", "size", 2));
            }
        }
    }

    private static String mintToken(JWKSource<SecurityContext> jwkSource, String sub, String jti) {
        JwtEncoder encoder = new NimbusJwtEncoder(jwkSource);
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id(jti)
                .subject(sub)
                .issuer("oa-shell")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
