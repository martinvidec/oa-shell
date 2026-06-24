package dev.oashell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.oashell.chat.BrowserHub;
import dev.oashell.chat.ChatService;
import dev.oashell.persistence.ChannelSession;
import dev.oashell.persistence.ChannelSessionRepository;
import dev.oashell.session.SessionRegistry;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;

class ChatServiceTest {

    private SessionRegistry sessionRegistry;
    private ChannelSessionRepository sessions;
    private BrowserHub browserHub;
    private ChatService chatService;

    @BeforeEach
    void setUp() {
        sessionRegistry = mock(SessionRegistry.class);
        sessions = mock(ChannelSessionRepository.class);
        browserHub = mock(BrowserHub.class);
        chatService = new ChatService(sessionRegistry, sessions, browserHub, new ObjectMapper());
    }

    @Test
    void sendUserMessageRoutesToOwnersChannel() throws Exception {
        when(sessions.findById(10L)).thenReturn(Optional.of(new ChannelSession(1L, "conn-1")));

        chatService.sendUserMessage(1L, 10L, "hallo");

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(sessionRegistry).send(eq("conn-1"), payload.capture());
        assertThat(payload.getValue())
                .contains("\"type\":\"chat\"")
                .contains("hallo")
                .contains("\"chat_id\":\"10\"");
    }

    @Test
    void sendUserMessageRejectsForeignSession() {
        when(sessions.findById(10L)).thenReturn(Optional.of(new ChannelSession(2L, "conn-2")));

        assertThatThrownBy(() -> chatService.sendUserMessage(1L, 10L, "x"))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(sessionRegistry);
    }

    @Test
    void sendUserMessageRejectsUnknownSession() {
        when(sessions.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendUserMessage(1L, 99L, "x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deliverReplySendsToOwnersBrowser() {
        chatService.deliverReply(1L, "10", "antwort");

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(browserHub).sendToUserSession(eq(1L), eq(10L), payload.capture());
        assertThat(payload.getValue())
                .contains("\"type\":\"reply\"")
                .contains("antwort")
                .contains("\"sessionId\":10");
    }
}
