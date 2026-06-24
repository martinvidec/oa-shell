package dev.oashell.session;

import dev.oashell.persistence.ChannelSession;
import dev.oashell.persistence.ChannelSessionRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verwaltung von Sessions abseits der Live-Verbindung (z. B. Umbenennen). Erzwingt
 * Ownership.
 */
@Service
public class SessionService {

    private final ChannelSessionRepository sessions;

    public SessionService(ChannelSessionRepository sessions) {
        this.sessions = sessions;
    }

    @Transactional
    public void rename(Long userId, Long sessionId, String name) {
        ChannelSession session = sessions.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Unbekannte Session: " + sessionId));
        if (!session.getUserId().equals(userId)) {
            throw new AccessDeniedException("Session gehört nicht zum Nutzer");
        }
        session.setDisplayName((name == null || name.isBlank()) ? null : name.trim());
        sessions.save(session);
    }
}
