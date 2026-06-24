package dev.oashell.session;

import dev.oashell.auth.RevokedTokenStore;
import dev.oashell.persistence.ChannelSession;
import dev.oashell.persistence.ChannelSessionRepository;
import dev.oashell.persistence.SessionStatus;
import java.io.IOException;
import java.time.Instant;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.CloseStatus;

/**
 * Verwaltung von Sessions abseits der Live-Verbindung (Umbenennen, Trennen/Revoke).
 * Erzwingt Ownership.
 */
@Service
public class SessionService {

    private final ChannelSessionRepository sessions;
    private final SessionRegistry registry;
    private final RevokedTokenStore revokedTokens;

    public SessionService(ChannelSessionRepository sessions, SessionRegistry registry,
            RevokedTokenStore revokedTokens) {
        this.sessions = sessions;
        this.registry = registry;
        this.revokedTokens = revokedTokens;
    }

    @Transactional
    public void rename(Long userId, Long sessionId, String name) {
        ChannelSession session = owned(userId, sessionId);
        session.setDisplayName((name == null || name.isBlank()) ? null : name.trim());
        sessions.save(session);
    }

    /**
     * Trennt das Gerät und widerruft sein Token (Revoke, AK-9): die laufende
     * Verbindung wird geschlossen und das {@code jti} auf die Denylist gesetzt, sodass
     * ein Reconnect mit demselben Token abgelehnt wird.
     */
    @Transactional
    public void disconnect(Long userId, Long sessionId) {
        ChannelSession session = owned(userId, sessionId);
        revokedTokens.revoke(session.getJti());

        SessionRegistry.Live live = registry.get(session.getConnectionId());
        if (live != null) {
            try {
                live.ws().close(CloseStatus.NORMAL);
            } catch (IOException ignored) {
                // Verbindung womöglich schon zu
            }
        }
        session.setStatus(SessionStatus.DISCONNECTED);
        session.setLastSeenAt(Instant.now());
        sessions.save(session);
    }

    private ChannelSession owned(Long userId, Long sessionId) {
        ChannelSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Unbekannte Session: " + sessionId));
        if (!session.getUserId().equals(userId)) {
            throw new AccessDeniedException("Session gehört nicht zum Nutzer");
        }
        return session;
    }
}
