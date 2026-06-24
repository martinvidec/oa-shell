package dev.oashell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.oashell.persistence.ChannelSession;
import dev.oashell.persistence.ChannelSessionRepository;
import dev.oashell.session.SessionService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class SessionServiceTest {

    @Test
    void renameSetsDisplayNameForOwner() {
        ChannelSessionRepository repo = mock(ChannelSessionRepository.class);
        ChannelSession session = new ChannelSession(1L, "conn-1");
        when(repo.findById(10L)).thenReturn(Optional.of(session));

        new SessionService(repo).rename(1L, 10L, "  Mein Projekt  ");

        assertThat(session.getDisplayName()).isEqualTo("Mein Projekt");
        verify(repo).save(session);
    }

    @Test
    void renameRejectsForeignSession() {
        ChannelSessionRepository repo = mock(ChannelSessionRepository.class);
        when(repo.findById(10L)).thenReturn(Optional.of(new ChannelSession(2L, "conn-2")));

        assertThatThrownBy(() -> new SessionService(repo).rename(1L, 10L, "x"))
                .isInstanceOf(AccessDeniedException.class);
    }
}
