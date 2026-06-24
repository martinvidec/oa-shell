package dev.oashell.files;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.oashell.persistence.ChannelSession;
import dev.oashell.persistence.ChannelSessionRepository;
import dev.oashell.session.SessionRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Fragt Datei-Baum/-Inhalt der gewählten Session über die Bridge beim Channel an und
 * korreliert die asynchrone Antwort per {@code requestId}. Die Sandbox (Begrenzung auf
 * {@code cwd}) liegt im Channel (#13); hier werden Ownership und Verbindung geprüft.
 */
@Service
public class FileViewService {

    private static final long TIMEOUT_MS = 8000;

    private final SessionRegistry registry;
    private final ChannelSessionRepository sessions;
    private final ObjectMapper mapper;
    private final Map<String, CompletableFuture<JsonNode>> pending = new ConcurrentHashMap<>();
    private final AtomicLong seq = new AtomicLong();

    public FileViewService(SessionRegistry registry, ChannelSessionRepository sessions, ObjectMapper mapper) {
        this.registry = registry;
        this.sessions = sessions;
        this.mapper = mapper;
    }

    public JsonNode tree(Long userId, Long sessionId, String path) {
        return request(userId, sessionId, "file_tree", path);
    }

    public JsonNode content(Long userId, Long sessionId, String path) {
        return request(userId, sessionId, "file_content", path);
    }

    /** Wird vom Bridge-Handler aufgerufen, wenn ein file_*_result eintrifft. */
    public void onResult(String requestId, JsonNode result) {
        CompletableFuture<JsonNode> future = pending.get(requestId);
        if (future != null) {
            future.complete(result);
        }
    }

    private JsonNode request(Long userId, Long sessionId, String type, String path) {
        ChannelSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Unbekannte Session"));
        if (!session.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Kein Zugriff auf diese Session");
        }
        if (registry.get(session.getConnectionId()) == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Session nicht verbunden");
        }

        String requestId = "f" + seq.incrementAndGet();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pending.put(requestId, future);
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("type", type);
            envelope.put("requestId", requestId);
            envelope.put("path", path == null ? "." : path);
            registry.send(session.getConnectionId(), mapper.writeValueAsString(envelope));
            return future.get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            throw new ResponseStatusException(HttpStatus.GATEWAY_TIMEOUT, "Channel antwortet nicht");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unterbrochen");
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Fehler beim Datei-Abruf");
        } finally {
            pending.remove(requestId);
        }
    }
}
