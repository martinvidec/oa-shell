package dev.oashell.bridge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.oashell.chat.BrowserHub;
import dev.oashell.chat.ChatService;
import dev.oashell.files.FileViewService;
import dev.oashell.persistence.ChannelSession;
import dev.oashell.permission.PermissionService;
import dev.oashell.persistence.ChannelSessionRepository;
import dev.oashell.persistence.SessionStatus;
import dev.oashell.session.SessionRegistry;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * WebSocket-Handler der Bridge: nimmt die ausgehende Verbindung des Channels entgegen
 * (Auth erfolgt im {@link BridgeTokenHandshakeInterceptor}), registriert/persistiert
 * die Session und dispatcht eingehende Envelopes.
 *
 * <p>Envelope-Format: {@code {"type": ..., ...}} (NDJSON über WS-Text). Das Routing
 * der Typen reply/permission_request/file_* an Browser/Anfrager folgt in #9/#11/#14.
 */
@Component
public class BridgeWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(BridgeWebSocketHandler.class);

    private final ObjectMapper mapper;
    private final ChannelSessionRepository sessions;
    private final SessionRegistry registry;
    private final ChatService chatService;
    private final PermissionService permissionService;
    private final FileViewService fileViewService;
    private final BrowserHub browserHub;

    public BridgeWebSocketHandler(ObjectMapper mapper, ChannelSessionRepository sessions,
            SessionRegistry registry, ChatService chatService, PermissionService permissionService,
            FileViewService fileViewService, BrowserHub browserHub) {
        this.mapper = mapper;
        this.sessions = sessions;
        this.registry = registry;
        this.chatService = chatService;
        this.permissionService = permissionService;
        this.fileViewService = fileViewService;
        this.browserHub = browserHub;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession ws) {
        Long userId = (Long) ws.getAttributes().get(BridgeTokenHandshakeInterceptor.ATTR_USER_ID);
        String jti = (String) ws.getAttributes().get(BridgeTokenHandshakeInterceptor.ATTR_JTI);
        ChannelSession entity = new ChannelSession(userId, ws.getId());
        entity.setJti(jti);
        ChannelSession session = sessions.save(entity);
        registry.register(new SessionRegistry.Live(ws, userId, session.getId(), jti));
        log.info("Bridge verbunden: user={} session={} conn={}", userId, session.getId(), ws.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession ws, TextMessage message) {
        JsonNode node;
        try {
            node = mapper.readTree(message.getPayload());
        } catch (Exception ex) {
            log.warn("Ungültiges Envelope auf conn={}: {}", ws.getId(), ex.getMessage());
            return;
        }
        String type = node.path("type").asText("");
        switch (type) {
            case "hello" -> onHello(ws, node);
            case "reply" -> onReply(ws, node);
            case "permission_request" -> onPermissionRequest(ws, node);
            case "file_tree_result", "file_content_result" ->
                    fileViewService.onResult(node.path("requestId").asText(), node);
            default -> log.debug("Unbekanntes Envelope '{}' von conn={}", type, ws.getId());
        }
    }

    private void onPermissionRequest(WebSocketSession ws, JsonNode node) {
        SessionRegistry.Live live = registry.get(ws.getId());
        if (live == null) {
            return;
        }
        permissionService.relayRequest(live.userId(), live.dbSessionId(), ws.getId(),
                node.path("request_id").asText(),
                node.path("tool_name").asText(),
                node.path("description").asText(""),
                node.path("input_preview").asText(""));
    }

    private void onReply(WebSocketSession ws, JsonNode node) {
        SessionRegistry.Live live = registry.get(ws.getId());
        if (live == null) {
            return;
        }
        chatService.deliverReply(live.userId(), node.path("chat_id").asText(), node.path("text").asText(""));
    }

    private void onHello(WebSocketSession ws, JsonNode node) {
        SessionRegistry.Live live = registry.get(ws.getId());
        if (live == null) {
            return;
        }
        String cwdBasename = node.path("cwdBasename").asText(null);
        sessions.findById(live.dbSessionId()).ifPresent(session -> {
            session.setCwdBasename(cwdBasename);
            session.setDisplayName(cwdBasename != null ? cwdBasename : "Session");
            session.setLastSeenAt(Instant.now());
            sessions.save(session);
        });
        log.info("hello von conn={} cwd={}", ws.getId(), cwdBasename);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession ws, CloseStatus status) {
        SessionRegistry.Live live = registry.remove(ws.getId());
        if (live != null) {
            sessions.findById(live.dbSessionId()).ifPresent(session -> {
                session.setStatus(SessionStatus.DISCONNECTED);
                session.setLastSeenAt(Instant.now());
                sessions.save(session);
            });
            // Browser live über das Session-Ende informieren (Session-Ende wird erkannt).
            browserHub.sendToUser(live.userId(),
                    "{\"type\":\"sessionStatus\",\"sessionId\":" + live.dbSessionId() + ",\"status\":\"DISCONNECTED\"}");
            log.info("Bridge getrennt: session={} conn={} status={}", live.dbSessionId(), ws.getId(), status);
        }
    }
}
