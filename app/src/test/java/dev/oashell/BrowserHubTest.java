package dev.oashell;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.oashell.chat.BrowserHub;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

class BrowserHubTest {

    private WebSocketSession ws(String id) {
        WebSocketSession ws = mock(WebSocketSession.class);
        when(ws.getId()).thenReturn(id);
        when(ws.isOpen()).thenReturn(true);
        return ws;
    }

    @Test
    void deliversOnlyToOwnerWhoSelectedTheSession() throws Exception {
        BrowserHub hub = new BrowserHub();
        WebSocketSession a = ws("a"); // user 1, Session 10  -> Empfänger
        WebSocketSession b = ws("b"); // user 1, Session 20  -> nicht
        WebSocketSession c = ws("c"); // user 2, Session 10  -> nicht (fremder Nutzer)

        hub.register(a, 1L);
        hub.select("a", 10L);
        hub.register(b, 1L);
        hub.select("b", 20L);
        hub.register(c, 2L);
        hub.select("c", 10L);

        hub.sendToUserSession(1L, 10L, "{\"type\":\"reply\"}");

        verify(a).sendMessage(any(TextMessage.class));
        verify(b, never()).sendMessage(any());
        verify(c, never()).sendMessage(any());
    }
}
