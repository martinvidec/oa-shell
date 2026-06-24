package dev.oashell.bridge;

import dev.oashell.persistence.AppUser;
import dev.oashell.persistence.AppUserRepository;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Authentifiziert die ausgehende Channel-Verbindung am {@code /bridge}-Handshake über
 * ein Bearer-Token (Device-Grant-Access-Token). Das Token wird als JWT validiert und
 * der zugehörige {@link AppUser} über die {@code sub}-Claim (= Google-{@code sub})
 * aufgelöst. Ohne gültiges Token wird der Handshake abgelehnt.
 */
@Component
public class BridgeTokenHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER_ID = "appUserId";

    private final JwtDecoder jwtDecoder;
    private final AppUserRepository users;

    public BridgeTokenHandshakeInterceptor(JwtDecoder jwtDecoder, AppUserRepository users) {
        this.jwtDecoder = jwtDecoder;
        this.users = users;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String header = request.getHeaders().getFirst("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }
        String token = header.substring("Bearer ".length()).trim();

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(token);
        } catch (Exception ex) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        Optional<AppUser> user = users.findByGoogleSub(jwt.getSubject());
        if (user.isEmpty()) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put(ATTR_USER_ID, user.get().getId());
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler wsHandler, Exception exception) {
        // nichts zu tun
    }
}
