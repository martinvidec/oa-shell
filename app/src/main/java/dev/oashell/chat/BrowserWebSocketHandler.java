package dev.oashell.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.oashell.permission.PermissionService;
import dev.oashell.persistence.AppUser;
import dev.oashell.persistence.AppUserRepository;
import dev.oashell.session.SessionService;
import java.security.Principal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/**
 * Browser-WebSocket {@code /ws}: authentifiziert über die App-Session (oauth2Login).
 * Nimmt Nutzer-Nachrichten entgegen und routet sie an die gewählte Session; Replies
 * werden über den {@link BrowserHub} an den Browser gestreamt.
 */
@Component
public class BrowserWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(BrowserWebSocketHandler.class);

    private final ObjectMapper mapper;
    private final AppUserRepository users;
    private final ChatService chatService;
    private final BrowserHub browserHub;
    private final PermissionService permissionService;
    private final SessionService sessionService;

    public BrowserWebSocketHandler(ObjectMapper mapper, AppUserRepository users,
            ChatService chatService, BrowserHub browserHub, PermissionService permissionService,
            SessionService sessionService) {
        this.mapper = mapper;
        this.users = users;
        this.chatService = chatService;
        this.browserHub = browserHub;
        this.permissionService = permissionService;
        this.sessionService = sessionService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession ws) throws Exception {
        Long userId = resolveUserId(ws);
        if (userId == null) {
            ws.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        browserHub.register(ws, userId);
        log.info("Browser verbunden: user={} conn={}", userId, ws.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession ws, TextMessage message) throws Exception {
        Long userId = resolveUserId(ws);
        if (userId == null) {
            return;
        }
        JsonNode node;
        try {
            node = mapper.readTree(message.getPayload());
        } catch (Exception ex) {
            return;
        }
        String type = node.path("type").asText("");
        switch (type) {
            case "selectSession" -> browserHub.select(ws.getId(), node.path("sessionId").asLong());
            case "chat" -> handleChat(ws, userId, node);
            case "permissionVerdict" -> permissionService.applyVerdict(
                    userId, node.path("request_id").asText(), node.path("behavior").asText());
            case "renameSession" -> handleRename(userId, node);
            default -> log.debug("Unbekanntes Browser-Envelope '{}'", type);
        }
    }

    private void handleChat(WebSocketSession ws, Long userId, JsonNode node) throws Exception {
        long sessionId = node.path("sessionId").asLong();
        String text = node.path("text").asText("");
        try {
            chatService.sendUserMessage(userId, sessionId, text);
        } catch (AccessDeniedException | IllegalArgumentException ex) {
            ws.sendMessage(new TextMessage("{\"type\":\"error\",\"message\":\"" + ex.getMessage() + "\"}"));
        }
    }

    private void handleRename(Long userId, JsonNode node) {
        try {
            sessionService.rename(userId, node.path("sessionId").asLong(), node.path("name").asText());
        } catch (Exception ex) {
            log.debug("Umbenennen fehlgeschlagen: {}", ex.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession ws, CloseStatus status) {
        browserHub.remove(ws.getId());
    }

    private Long resolveUserId(WebSocketSession ws) {
        Principal principal = ws.getPrincipal();
        if (principal == null) {
            return null;
        }
        return users.findByGoogleSub(principal.getName()).map(AppUser::getId).orElse(null);
    }
}
