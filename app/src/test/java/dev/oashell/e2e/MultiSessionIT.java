package dev.oashell.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
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

/** Swimlane: mehrere Sessions, Umschalten und Kontext-Isolierung (AK-3). */
class MultiSessionIT extends PlaywrightSpringBase {

    @Autowired
    private JWKSource<SecurityContext> jwkSource;

    @Test
    void switchingKeepsContextsSeparate() throws Exception {
        page.navigate("/e2e/login?user=carol");

        try (FakeChannel chA = new FakeChannel(serverPort, jwkSource, "carol", "jti-carol-a", "proj-a");
                FakeChannel chB = new FakeChannel(serverPort, jwkSource, "carol", "jti-carol-b", "proj-b")) {

            waitForApi("/api/sessions", "proj-a");
            waitForApi("/api/sessions", "proj-b");
            page.navigate("/");

            assertThat(page.getByText("proj-a")).isVisible();
            assertThat(page.getByText("proj-b")).isVisible();

            // proj-a: Nachricht -> Reply (während proj-a gewählt ist)
            page.getByText("proj-a").first().click();
            sendMessage("hallo a");
            JsonNode chatA = chA.awaitInbound("chat", 5);
            assertNotNull(chatA, "proj-a-Channel muss die Nachricht empfangen");
            chA.sendReply(chatA.path("chat_id").asText(), "antwort-a");
            assertThat(page.getByText("antwort-a")).isVisible();

            // Wechsel zu proj-b: antwort-a darf NICHT sichtbar sein (Kontext getrennt)
            page.getByText("proj-b").first().click();
            assertThat(page.getByText("antwort-a")).isHidden();

            sendMessage("hallo b");
            JsonNode chatB = chB.awaitInbound("chat", 5);
            assertNotNull(chatB, "proj-b-Channel muss die Nachricht empfangen");
            chB.sendReply(chatB.path("chat_id").asText(), "antwort-b");
            assertThat(page.getByText("antwort-b")).isVisible();

            // Zurück zu proj-a: antwort-a wieder da (Puffer), antwort-b nicht
            page.getByText("proj-a").first().click();
            assertThat(page.getByText("antwort-a")).isVisible();
            assertThat(page.getByText("antwort-b")).isHidden();
        }
    }

    private void sendMessage(String text) {
        page.getByPlaceholder("Nachricht an Claude").fill(text);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Senden")).click();
    }
}
