package dev.oashell.bridge;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
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

    /**
     * Hebt die WS-Nachrichten-Puffergröße von Tomcat an. Datei-Inhalte kommen als eine
     * {@code file_content_result}-Textnachricht über die Bridge; der Channel cappt bei
     * ~200 KB ({@code MAX_BYTES}). Der Tomcat-Default von 8 KB schloss die Verbindung bei
     * größeren Dateien mit Code 1009 ("message too big") → Reconnect als neue Session →
     * {@code 503} beim Datei-Abruf. 1 MB deckt 200 KB Inhalt inkl. JSON-Overhead ab.
     *
     * <p>Umsetzung über Tomcat-Context-Init-Parameter statt {@code ServletServerContainerFactoryBean}:
     * der Customizer greift nur, wenn tatsächlich ein Server gebaut wird, sodass Tests mit
     * Mock-Servlet-Umgebung (ohne {@code jakarta.websocket.server.ServerContainer}) weiter laden.
     * Gilt containerweit (Bridge {@code /bridge} und Browser {@code /ws}).
     */
    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> webSocketBufferCustomizer() {
        String oneMiB = String.valueOf(1024 * 1024);
        return factory -> factory.addContextCustomizers(context -> {
            context.addParameter("org.apache.tomcat.websocket.textBufferSize", oneMiB);
            context.addParameter("org.apache.tomcat.websocket.binaryBufferSize", oneMiB);
        });
    }
}
