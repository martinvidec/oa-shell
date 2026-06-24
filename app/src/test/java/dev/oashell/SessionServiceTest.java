package dev.oashell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.oashell.auth.RevokedTokenStore;
import dev.oashell.persistence.ChannelSession;
import dev.oashell.persistence.ChannelSessionRepository;
import dev.oashell.persistence.SessionStatus;
import dev.oashell.session.SessionRegistry;
import dev.oashell.session.SessionService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;

class SessionServiceTest {

    private ChannelSessionRepository repo;
    private SessionRegistry registry;
    private RevokedTokenStore revoked;
    private SessionService service;

    @BeforeEach
    void setUp() {
        repo = mock(ChannelSessionRepository.class);
        registry = mock(SessionRegistry.class);
        revoked = mock(RevokedTokenStore.class);
        service = new SessionService(repo, registry, revoked);
    }

    @Test
    void renameSetsDisplayNameForOwner() {
        ChannelSession session = new ChannelSession(1L, "conn-1");
        when(repo.findById(10L)).thenReturn(Optional.of(session));

        service.rename(1L, 10L, "  Mein Projekt  ");

        assertThat(session.getDisplayName()).isEqualTo("Mein Projekt");
        verify(repo).save(session);
    }

    @Test
    void renameRejectsForeignSession() {
        when(repo.findById(10L)).thenReturn(Optional.of(new ChannelSession(2L, "conn-2")));
        assertThatThrownBy(() -> service.rename(1L, 10L, "x")).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void disconnectRevokesTokenAndClosesConnection() throws Exception {
        ChannelSession session = new ChannelSession(1L, "conn-1");
        session.setJti("jti-1");
        when(repo.findById(10L)).thenReturn(Optional.of(session));
        WebSocketSession ws = mock(WebSocketSession.class);
        when(registry.get("conn-1")).thenReturn(new SessionRegistry.Live(ws, 1L, 10L, "jti-1"));

        service.disconnect(1L, 10L);

        verify(revoked).revoke("jti-1");
        verify(ws).close(any(CloseStatus.class));
        assertThat(session.getStatus()).isEqualTo(SessionStatus.DISCONNECTED);
        verify(repo).save(session);
    }

    @Test
    void disconnectRejectsForeignSession() {
        when(repo.findById(10L)).thenReturn(Optional.of(new ChannelSession(2L, "conn-2")));
        assertThatThrownBy(() -> service.disconnect(1L, 10L)).isInstanceOf(AccessDeniedException.class);
    }
}
