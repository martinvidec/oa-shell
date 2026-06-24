package dev.oashell.auth;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * In-memory Denylist widerrufener Access-Token (per JWT-{@code jti}). Wird beim
 * Bridge-Handshake geprüft, sodass ein widerrufenes Gerät sich nicht erneut verbinden
 * kann (Revoke, AK-9).
 *
 * <p>Hinweis: in-memory (Restart leert die Liste; Tokens haben begrenzte Lebensdauer).
 * Persistente/verschlüsselte Token-Speicherung ist ein dokumentierter Folge-Schritt
 * (siehe docs/06-haertung-claude-chat.md).
 */
@Component
public class RevokedTokenStore {

    private final Set<String> revoked = ConcurrentHashMap.newKeySet();

    public void revoke(String jti) {
        if (jti != null && !jti.isBlank()) {
            revoked.add(jti);
        }
    }

    public boolean isRevoked(String jti) {
        return jti != null && revoked.contains(jti);
    }
}
