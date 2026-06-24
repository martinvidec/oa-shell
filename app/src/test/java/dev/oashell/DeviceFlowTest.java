package dev.oashell;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Prüft den OAuth 2.0 Device Authorization Grant (RFC 8628): Device-Authorization-
 * Endpoint, Token-Polling vor Approval und die login-geschützte Aktivierungsseite.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DeviceFlowTest {

    @LocalServerPort
    private int port;

    private final HttpClient client =
            HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    private HttpResponse<String> postForm(String path, String body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return client.send(req, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void deviceAuthorizationEndpointReturnsCodes() throws Exception {
        HttpResponse<String> res = postForm("/oauth2/device_authorization",
                "client_id=oa-shell-channel&scope=session+files");

        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.body())
                .contains("device_code")
                .contains("user_code")
                .contains("verification_uri")
                .contains("expires_in");
        // Hinweis: 'interval' ist laut RFC 8628 optional; SAS gibt es nicht zwingend aus
        // (Client nutzt dann den Default von 5 Sekunden).
    }

    @Test
    void tokenPollingBeforeApprovalIsPending() throws Exception {
        HttpResponse<String> auth = postForm("/oauth2/device_authorization",
                "client_id=oa-shell-channel&scope=session+files");
        Matcher m = Pattern.compile("\"device_code\"\\s*:\\s*\"([^\"]+)\"").matcher(auth.body());
        assertThat(m.find()).isTrue();
        String deviceCode = m.group(1);

        HttpResponse<String> token = postForm("/oauth2/token",
                "grant_type=urn:ietf:params:oauth:grant-type:device_code"
                        + "&device_code=" + deviceCode
                        + "&client_id=oa-shell-channel");

        assertThat(token.statusCode()).isEqualTo(400);
        assertThat(token.body()).contains("authorization_pending");
    }

    @Test
    void activatePageRequiresLogin() throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/activate"))
                .header("Accept", "text/html")
                .GET()
                .build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        // Unauthentifiziert -> Redirect zur Anmeldung (bindet das Gerät später an das Konto).
        assertThat(res.statusCode()).isBetween(300, 399);
    }
}
