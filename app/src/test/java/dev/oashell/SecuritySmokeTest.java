package dev.oashell;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Prüft die Auth-Grundregeln: öffentliche vs. geschützte Routen und die
 * Verfügbarkeit der Authorization-Server-Endpoints. Verwendet bewusst Javas
 * {@link HttpClient} mit deaktiviertem Redirect-Following, um die rohen
 * Statuscodes zu sehen.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecuritySmokeTest {

    @LocalServerPort
    private int port;

    private final HttpClient client =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private HttpResponse<String> get(String path, String accept) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", accept)
                .GET()
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void healthIsPublic() throws Exception {
        assertThat(get("/healthz", "*/*").statusCode()).isEqualTo(200);
    }

    @Test
    void protectedBrowserRequestRedirectsToLogin() throws Exception {
        // Browser (Accept: text/html), unauthentifiziert -> 3xx Redirect zur Anmeldung.
        assertThat(get("/api/me", "text/html").statusCode()).isBetween(300, 399);
    }

    @Test
    void protectedEndpointNotPubliclyReadable() throws Exception {
        // API-Style (kein text/html), unauthentifiziert -> jedenfalls NICHT 200.
        assertThat(get("/api/me", "application/json").statusCode()).isNotEqualTo(200);
    }

    @Test
    void authorizationServerMetadataIsExposed() throws Exception {
        // Beweist, dass der Authorization Server aktiv ist (.well-known Endpoint).
        HttpResponse<String> res = get("/.well-known/oauth-authorization-server", "*/*");
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body()).contains("/oauth2/token").contains("/oauth2/authorize");
    }
}
