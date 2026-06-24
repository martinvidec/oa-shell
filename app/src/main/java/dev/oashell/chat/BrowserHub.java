package dev.oashell.chat;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Registry der Browser-WebSocket-Verbindungen je Nutzer inkl. aktuell gewählter
 * Session. Liefert Nachrichten (z. B. Streaming-Replies) nur an Verbindungen des
 * Eigentümers, die die betreffende Session gewählt haben.
 */
@Component
public class BrowserHub {

    private static final Logger log = LoggerFactory.getLogger(BrowserHub.class);

    private record BrowserConn(WebSocketSession ws, Long userId, AtomicReference<Long> selectedSessionId) {
    }

    private final Map<String, BrowserConn> byConnId = new ConcurrentHashMap<>();

    public void register(WebSocketSession ws, Long userId) {
        byConnId.put(ws.getId(), new BrowserConn(ws, userId, new AtomicReference<>()));
    }

    public void remove(String connectionId) {
        byConnId.remove(connectionId);
    }

    public void select(String connectionId, Long sessionId) {
        BrowserConn conn = byConnId.get(connectionId);
        if (conn != null) {
            conn.selectedSessionId().set(sessionId);
        }
    }

    /** Sendet Text an alle Verbindungen des Nutzers, die {@code sessionId} gewählt haben. */
    public void sendToUserSession(Long userId, Long sessionId, String text) {
        for (BrowserConn conn : byConnId.values()) {
            if (conn.userId().equals(userId)
                    && sessionId.equals(conn.selectedSessionId().get())
                    && conn.ws().isOpen()) {
                try {
                    conn.ws().sendMessage(new TextMessage(text));
                } catch (IOException ex) {
                    log.debug("Browser-Send fehlgeschlagen conn={}: {}", conn.ws().getId(), ex.getMessage());
                }
            }
        }
    }
}
