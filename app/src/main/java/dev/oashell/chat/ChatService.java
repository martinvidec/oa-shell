package dev.oashell.chat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.oashell.persistence.ChannelSession;
import dev.oashell.persistence.ChannelSessionRepository;
import dev.oashell.session.SessionRegistry;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

/**
 * Routet Chat-Nachrichten Browser ↔ gewählte Session. Erzwingt Ownership
 * (eine Session ist nur für ihren Eigentümer ansteuerbar).
 */
@Service
public class ChatService {

    private final SessionRegistry sessionRegistry;
    private final ChannelSessionRepository sessions;
    private final BrowserHub browserHub;
    private final ObjectMapper mapper;

    public ChatService(SessionRegistry sessionRegistry, ChannelSessionRepository sessions,
            BrowserHub browserHub, ObjectMapper mapper) {
        this.sessionRegistry = sessionRegistry;
        this.sessions = sessions;
        this.browserHub = browserHub;
        this.mapper = mapper;
    }

    /** Browser → Session: schickt die Nutzer-Nachricht über die Bridge an den Channel. */
    public void sendUserMessage(Long userId, Long sessionId, String text) {
        ChannelSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Unbekannte Session: " + sessionId));
        if (!session.getUserId().equals(userId)) {
            throw new AccessDeniedException("Session gehört nicht zum Nutzer");
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", "chat");
        envelope.put("text", text);
        envelope.put("chat_id", String.valueOf(sessionId));
        try {
            sessionRegistry.send(session.getConnectionId(), write(envelope));
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Channel → Browser: liefert eine Reply (chat_id = Session-ID) an die Browser des
     * Eigentümers. {@code ownerUserId} stammt aus der authentifizierten Bridge-Verbindung.
     */
    public void deliverReply(Long ownerUserId, String chatId, String text) {
        Long sessionId = parseLong(chatId);
        if (sessionId == null) {
            return;
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("type", "reply");
        envelope.put("sessionId", sessionId);
        envelope.put("text", text);
        browserHub.sendToUserSession(ownerUserId, sessionId, write(envelope));
    }

    private String write(Map<String, Object> envelope) {
        try {
            return mapper.writeValueAsString(envelope);
        } catch (JsonProcessingException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    private static Long parseLong(String value) {
        try {
            return value == null ? null : Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
