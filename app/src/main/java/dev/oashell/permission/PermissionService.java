package dev.oashell.permission;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.oashell.chat.BrowserHub;
import dev.oashell.session.SessionRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Routet Tool-Freigaben (Permission-Relay): {@code permission_request} vom Channel an
 * den Browser des Eigentümers und das Verdikt zurück an den anfragenden Channel.
 *
 * <p>Offene Anfragen werden je {@code request_id} verfolgt; ein Verdikt wird nur auf
 * eine offene, dem Nutzer gehörende Anfrage angewandt (kein Vermischen mehrerer
 * gleichzeitig offener Anfragen, keine Doppel-Anwendung).
 */
@Service
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    /** Eine offene Freigabe-Anfrage: Eigentümer, Session und die ausstellende Channel-Verbindung. */
    private record Pending(Long userId, Long sessionId, String connectionId) {
    }

    private final Map<String, Pending> open = new ConcurrentHashMap<>();
    private final SessionRegistry sessionRegistry;
    private final BrowserHub browserHub;
    private final ObjectMapper mapper;

    public PermissionService(SessionRegistry sessionRegistry, BrowserHub browserHub, ObjectMapper mapper) {
        this.sessionRegistry = sessionRegistry;
        this.browserHub = browserHub;
        this.mapper = mapper;
    }

    /** Channel → Browser: leitet eine Freigabe-Anfrage an den Browser des Eigentümers weiter. */
    public void relayRequest(Long userId, Long sessionId, String connectionId, String requestId,
            String toolName, String description, String inputPreview) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }
        open.put(requestId, new Pending(userId, sessionId, connectionId));

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", "permission_request");
        envelope.put("sessionId", sessionId);
        envelope.put("request_id", requestId);
        envelope.put("tool_name", toolName);
        envelope.put("description", description);
        envelope.put("input_preview", inputPreview);
        browserHub.sendToUserSession(userId, sessionId, write(envelope));
        log.info("Permission-Request {} (tool={}) -> user={} session={}", requestId, toolName, userId, sessionId);
    }

    /** Browser → Channel: wendet das Verdikt auf eine offene, dem Nutzer gehörende Anfrage an. */
    public void applyVerdict(Long userId, String requestId, String behavior) {
        Pending pending = (requestId == null) ? null : open.get(requestId);
        if (pending == null || !pending.userId().equals(userId)) {
            log.debug("Verdikt ignoriert (unbekannt/fremd): req={} user={}", requestId, userId);
            return;
        }
        open.remove(requestId);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", "permission_verdict");
        envelope.put("request_id", requestId);
        envelope.put("behavior", "deny".equals(behavior) ? "deny" : "allow");
        try {
            sessionRegistry.send(pending.connectionId(), write(envelope));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        log.info("Verdikt {} für Request {} an Channel gesendet", envelope.get("behavior"), requestId);
    }

    private String write(Map<String, Object> envelope) {
        try {
            return mapper.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            throw new UncheckedIOException(ex);
        }
    }
}
