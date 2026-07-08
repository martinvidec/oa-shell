package dev.oashell.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
 * Swimlane: Markdown-Rendering der Claude-Antworten (#25) inkl. XSS-Neutralisierung.
 * Prüft, dass {@code assistant}-Nachrichten als HTML gerendert werden, eigene Eingaben
 * Plaintext bleiben und präparierte Inhalte (img/script/javascript-Link) nicht wirken.
 */
class MarkdownRenderingIT extends PlaywrightSpringBase {

    @Autowired
    private JWKSource<SecurityContext> jwkSource;

    @Test
    void rendersMarkdownAndNeutralizesXss() throws Exception {
        page.navigate("/e2e/login?user=mara");

        try (FakeChannel channel = new FakeChannel(serverPort, jwkSource, "mara", "jti-mara-1", "mdproj")) {
            waitForApi("/api/sessions", "mdproj");
            page.navigate("/");
            page.getByText("mdproj").first().click();

            // Eigene Eingabe bleibt Plaintext (Markdown-Sonderzeichen nicht interpretiert).
            page.getByPlaceholder("Nachricht an Claude").fill("roh **fett**");
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Senden")).click();
            assertThat(page.locator(".msg--user").last()).containsText("roh **fett**");

            JsonNode chat = channel.awaitInbound("chat", 5);
            assertNotNull(chat, "Channel muss die Chat-Nachricht empfangen");
            String chatId = chat.path("chat_id").asText();

            // Claude-Antwort mit gemischtem Markdown und XSS-Vektoren.
            String md = "# Titel\n\n"
                    + "- eins\n- zwei\n\n"
                    + "Inline `code` und ein [Beispiel](https://example.com).\n\n"
                    + "```java\npublic class A { int x = 1; }\n```\n\n"
                    + "<img src=x onerror=window.__xss=1>\n\n"
                    + "<script>window.__xss=2</script>\n\n"
                    + "[klick](javascript:window.__xss=3)";
            channel.sendReply(chatId, md);

            // Markdown gerendert (assertThat wartet auf das Eintreffen der Reply):
            assertThat(page.locator(".markdown-body h1")).hasText("Titel");
            assertThat(page.locator(".markdown-body li").first()).hasText("eins");
            assertThat(page.locator(".markdown-body code").first()).hasText("code");
            assertThat(page.locator(".markdown-body a[href^='https://example.com']"))
                    .hasAttribute("target", "_blank");

            // Syntax-Hervorhebung (#26): Code-Block erhält die hljs-Klasse und Token-Spans.
            assertThat(page.locator(".markdown-body pre code.hljs")).isVisible();
            assertThat(page.locator(".markdown-body pre .hljs-keyword").first()).isVisible();

            // XSS neutralisiert: kein img-Node, kein Script/Handler ausgeführt.
            assertEquals(0, page.locator(".markdown-body img").count(), "img muss entfernt sein");
            Object xss = page.evaluate("() => window.__xss");
            assertNull(xss, "kein XSS-Vektor darf ausgeführt/gesetzt worden sein");
        }
    }
}
