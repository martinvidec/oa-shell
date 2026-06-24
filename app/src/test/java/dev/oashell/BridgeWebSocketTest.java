package dev.oashell;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.oashell.persistence.AppUser;
import dev.oashell.persistence.AppUserRepository;
import dev.oashell.persistence.ChannelSession;
import dev.oashell.persistence.ChannelSessionRepository;
import dev.oashell.persistence.SessionStatus;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BridgeWebSocketTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JWKSource<SecurityContext> jwkSource;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private ChannelSessionRepository sessions;

    private final HttpClient http = HttpClient.newHttpClient();

    private String mintToken(String sub) {
        JwtEncoder encoder = new NimbusJwtEncoder(jwkSource);
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(sub)
                .issuer("oa-shell")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private WebSocket connect(String token) throws Exception {
        WebSocket.Builder builder = http.newWebSocketBuilder();
        if (token != null) {
            builder = builder.header("Authorization", "Bearer " + token);
        }
        return builder.buildAsync(URI.create("ws://localhost:" + port + "/bridge"),
                new WebSocket.Listener() {
                }).get(5, TimeUnit.SECONDS);
    }

    private <T> T await(Supplier<Optional<T>> supplier) throws InterruptedException {
        for (int i = 0; i < 60; i++) {
            Optional<T> value = supplier.get();
            if (value.isPresent()) {
                return value.get();
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Bedingung nicht erfüllt (Timeout)");
    }

    @Test
    void rejectsConnectionWithoutToken() {
        assertThatThrownBy(() -> connect(null))
                .as("Handshake ohne Bearer-Token muss scheitern")
                .isInstanceOf(Exception.class);
    }

    @Test
    void rejectsConnectionWithInvalidToken() {
        assertThatThrownBy(() -> connect("not-a-valid-jwt"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void acceptsValidTokenRegistersSessionAndTracksStatus() throws Exception {
        AppUser user = users.save(new AppUser("sub-bridge-1", "bridge@example.com", "Bridge Test"));
        String token = mintToken("sub-bridge-1");

        WebSocket ws = connect(token);
        ws.sendText("{\"type\":\"hello\",\"cwdBasename\":\"myproj\"}", true);

        ChannelSession session = await(() -> sessions.findByUserId(user.getId()).stream()
                .filter(s -> "myproj".equals(s.getCwdBasename()))
                .findFirst());
        assertThat(session.getStatus()).isEqualTo(SessionStatus.CONNECTED);
        assertThat(session.getUserId()).isEqualTo(user.getId());

        ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye").get(5, TimeUnit.SECONDS);

        ChannelSession closed = await(() -> sessions.findById(session.getId())
                .filter(s -> s.getStatus() == SessionStatus.DISCONNECTED));
        assertThat(closed.getStatus()).isEqualTo(SessionStatus.DISCONNECTED);
    }
}
