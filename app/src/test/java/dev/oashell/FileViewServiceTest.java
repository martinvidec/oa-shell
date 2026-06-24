package dev.oashell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.oashell.files.FileViewService;
import dev.oashell.persistence.ChannelSession;
import dev.oashell.persistence.ChannelSessionRepository;
import dev.oashell.session.SessionRegistry;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.socket.WebSocketSession;

class FileViewServiceTest {

    private SessionRegistry registry;
    private ChannelSessionRepository sessions;
    private ObjectMapper mapper;
    private FileViewService service;

    @BeforeEach
    void setUp() {
        registry = mock(SessionRegistry.class);
        sessions = mock(ChannelSessionRepository.class);
        mapper = new ObjectMapper();
        service = new FileViewService(registry, sessions, mapper);
    }

    @Test
    void treeCorrelatesChannelResult() throws Exception {
        when(sessions.findById(10L)).thenReturn(Optional.of(new ChannelSession(1L, "conn-1")));
        when(registry.get("conn-1"))
                .thenReturn(new SessionRegistry.Live(mock(WebSocketSession.class), 1L, 10L));

        // Channel "antwortet" synchron auf die gesendete Anfrage.
        doAnswer(inv -> {
            JsonNode env = mapper.readTree(inv.getArgument(1, String.class));
            String reqId = env.get("requestId").asText();
            ObjectNode result = mapper.createObjectNode();
            result.put("type", "file_tree_result").put("requestId", reqId);
            result.set("entries", mapper.createArrayNode().add(mapper.createObjectNode().put("name", "hello.txt")));
            service.onResult(reqId, result);
            return null;
        }).when(registry).send(eq("conn-1"), anyString());

        JsonNode result = service.tree(1L, 10L, ".");
        assertThat(result.get("entries").get(0).get("name").asText()).isEqualTo("hello.txt");
    }

    @Test
    void rejectsForeignSession() {
        when(sessions.findById(10L)).thenReturn(Optional.of(new ChannelSession(2L, "conn-2")));
        assertThatThrownBy(() -> service.tree(1L, 10L, "."))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void failsWhenSessionNotConnected() {
        when(sessions.findById(10L)).thenReturn(Optional.of(new ChannelSession(1L, "conn-1")));
        when(registry.get("conn-1")).thenReturn(null);
        assertThatThrownBy(() -> service.content(1L, 10L, "hello.txt"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
