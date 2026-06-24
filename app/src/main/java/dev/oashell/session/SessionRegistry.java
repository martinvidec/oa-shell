package dev.oashell.session;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

/**
 * Hält die aktiven (in-memory) Channel-Verbindungen je Nutzer und bildet die
 * Brücke fürs spätere Routing (Browser ↔ gewählte Session). Persistente
 * Metadaten liegen in {@code ChannelSession}.
 */
@Component
public class SessionRegistry {

    /** Eine aktive Verbindung: WS + Nutzer + zugehörige persistente Session-ID. */
    public record Live(WebSocketSession ws, Long userId, Long dbSessionId) {
    }

    private final Map<String, Live> byConnectionId = new ConcurrentHashMap<>();

    public void register(Live live) {
        byConnectionId.put(live.ws().getId(), live);
    }

    public Live get(String connectionId) {
        return byConnectionId.get(connectionId);
    }

    public Live remove(String connectionId) {
        return byConnectionId.remove(connectionId);
    }

    public Collection<Live> all() {
        return byConnectionId.values();
    }

    /** Aktive Verbindungen eines Nutzers (für Multi-Session-Routing). */
    public List<Live> forUser(Long userId) {
        return byConnectionId.values().stream().filter(l -> l.userId().equals(userId)).toList();
    }

    /** Sendet Text an eine aktive Verbindung (Routing App → Channel). */
    public void send(String connectionId, String text) throws IOException {
        Live live = byConnectionId.get(connectionId);
        if (live != null && live.ws().isOpen()) {
            live.ws().sendMessage(new TextMessage(text));
        }
    }
}
