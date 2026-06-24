package dev.oashell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.oashell.chat.BrowserHub;
import dev.oashell.permission.PermissionService;
import dev.oashell.session.SessionRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class PermissionServiceTest {

    private SessionRegistry sessionRegistry;
    private BrowserHub browserHub;
    private PermissionService service;

    @BeforeEach
    void setUp() {
        sessionRegistry = mock(SessionRegistry.class);
        browserHub = mock(BrowserHub.class);
        service = new PermissionService(sessionRegistry, browserHub, new ObjectMapper());
    }

    @Test
    void relayRequestPushesToOwnerBrowser() {
        service.relayRequest(1L, 10L, "conn-1", "abcde", "Write", "Datei schreiben", "hello.txt");

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(browserHub).sendToUserSession(eq(1L), eq(10L), payload.capture());
        assertThat(payload.getValue())
                .contains("\"type\":\"permission_request\"")
                .contains("abcde")
                .contains("Write");
        verifyNoInteractions(sessionRegistry);
    }

    @Test
    void verdictRoutesAllowToChannel() throws Exception {
        service.relayRequest(1L, 10L, "conn-1", "abcde", "Write", "", "");
        service.applyVerdict(1L, "abcde", "allow");

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(sessionRegistry).send(eq("conn-1"), payload.capture());
        assertThat(payload.getValue())
                .contains("\"type\":\"permission_verdict\"")
                .contains("abcde")
                .contains("\"behavior\":\"allow\"");
    }

    @Test
    void verdictDenyIsForwarded() throws Exception {
        service.relayRequest(1L, 10L, "conn-1", "abcde", "Bash", "", "");
        service.applyVerdict(1L, "abcde", "deny");
        verify(sessionRegistry).send(eq("conn-1"), org.mockito.ArgumentMatchers.contains("\"behavior\":\"deny\""));
    }

    @Test
    void unknownRequestIdIsIgnored() throws Exception {
        service.applyVerdict(1L, "zzzzz", "allow");
        verifyNoInteractions(sessionRegistry);
    }

    @Test
    void foreignUserVerdictIsIgnored() throws Exception {
        service.relayRequest(1L, 10L, "conn-1", "abcde", "Write", "", "");
        service.applyVerdict(2L, "abcde", "allow");
        verifyNoInteractions(sessionRegistry);
    }

    @Test
    void multipleOpenRequestsAreNotMixed() throws Exception {
        service.relayRequest(1L, 10L, "conn-1", "aaaaa", "Write", "", "");
        service.relayRequest(1L, 10L, "conn-1", "bbbbb", "Bash", "", "");

        service.applyVerdict(1L, "aaaaa", "allow");
        verify(sessionRegistry, times(1))
                .send(eq("conn-1"), org.mockito.ArgumentMatchers.contains("aaaaa"));

        service.applyVerdict(1L, "bbbbb", "deny");
        verify(sessionRegistry, times(1))
                .send(eq("conn-1"), org.mockito.ArgumentMatchers.contains("bbbbb"));
        verify(sessionRegistry, times(2)).send(eq("conn-1"), any());
    }

    @Test
    void verdictAppliedOnlyOnce() throws Exception {
        service.relayRequest(1L, 10L, "conn-1", "abcde", "Write", "", "");
        service.applyVerdict(1L, "abcde", "allow");
        service.applyVerdict(1L, "abcde", "allow"); // zweites Mal -> ignoriert
        verify(sessionRegistry, times(1)).send(eq("conn-1"), any());
    }
}
