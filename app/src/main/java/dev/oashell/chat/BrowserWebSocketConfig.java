package dev.oashell.chat;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registriert den Browser-Endpunkt {@code /ws}. Authentifizierung erfolgt über die
 * App-Session (oauth2Login); der Principal wird vom Default-Handshake-Handler an die
 * WebSocket-Session übernommen. ({@code @EnableWebSocket} liegt in der Bridge-Config.)
 */
@Configuration
public class BrowserWebSocketConfig implements WebSocketConfigurer {

    private final BrowserWebSocketHandler handler;

    public BrowserWebSocketConfig(BrowserWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Kein setAllowedOriginPatterns: Browser-WS bleibt same-origin (Schutz vor CSWSH).
        registry.addHandler(handler, "/ws");
    }
}
