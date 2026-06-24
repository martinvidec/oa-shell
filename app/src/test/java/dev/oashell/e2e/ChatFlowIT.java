package dev.oashell.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.oashell.e2e.support.FakeChannel;
import dev.oashell.e2e.support.PlaywrightSpringBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Swimlanes: Session-Anzeige/-Wahl, Chat-Roundtrip (AK-4), Permission-Dialog (AK-5)
 * und Datei-Browser (AK-6) — gegen einen simulierten Channel.
 */
class ChatFlowIT extends PlaywrightSpringBase {

    @Autowired
    private JWKSource<SecurityContext> jwkSource;

    @Test
    void chatPermissionAndFiles() throws Exception {
        page.navigate("/e2e/login?user=bob");

        try (FakeChannel channel = new FakeChannel(serverPort, jwkSource, "bob", "jti-bob-1", "myproj")) {
            waitForApi("/api/sessions", "myproj");
            page.navigate("/");

            // Session wählen
            page.getByText("myproj").first().click();

            // --- Chat (AK-4): senden -> Channel empfängt -> Reply erscheint ---
            page.getByPlaceholder("Nachricht an Claude").fill("hallo welt");
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Senden")).click();

            JsonNode chat = channel.awaitInbound("chat", 5);
            assertNotNull(chat, "Channel muss die Chat-Nachricht empfangen");
            assertEquals("hallo welt", chat.path("text").asText());

            channel.sendReply(chat.path("chat_id").asText(), "Antwort von Claude");
            assertThat(page.getByText("Antwort von Claude")).isVisible();

            // --- Permission (AK-5): Request -> Dialog -> Erlauben -> Verdikt ---
            channel.sendPermissionRequest("abcde", "Write", "Datei schreiben", "out.log");
            assertThat(page.getByText("Freigabe erforderlich")).isVisible();
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Erlauben")).click();

            JsonNode verdict = channel.awaitInbound("permission_verdict", 5);
            assertNotNull(verdict, "Channel muss das Verdikt empfangen");
            assertEquals("allow", verdict.path("behavior").asText());
            assertEquals("abcde", verdict.path("request_id").asText());

            // --- Dateien (AK-6): FakeChannel beantwortet file_tree -> Datei sichtbar ---
            assertThat(page.locator("#file-tree").getByText("hello.txt")).isVisible();
        }
    }
}
