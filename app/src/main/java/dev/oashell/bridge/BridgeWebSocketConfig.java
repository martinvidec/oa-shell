package dev.oashell.bridge;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Registriert den Bridge-Endpunkt {@code /bridge}, an dem sich die Channel-Server
 * ausgehend (token-authentifiziert) verbinden.
 */
@Configuration
@EnableWebSocket
public class BridgeWebSocketConfig implements WebSocketConfigurer {

    private final BridgeWebSocketHandler handler;
    private final BridgeTokenHandshakeInterceptor tokenInterceptor;

    public BridgeWebSocketConfig(BridgeWebSocketHandler handler,
            BridgeTokenHandshakeInterceptor tokenInterceptor) {
        this.handler = handler;
        this.tokenInterceptor = tokenInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/bridge")
                .addInterceptors(tokenInterceptor)
                // Channel ist kein Browser; Auth erfolgt über das Bearer-Token, nicht über Origin.
                .setAllowedOriginPatterns("*");
    }
}
