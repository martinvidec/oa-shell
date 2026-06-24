package dev.oashell.web;

import dev.oashell.persistence.AppUser;
import dev.oashell.persistence.AppUserRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Geschützter Endpunkt: liefert die App-Identität des angemeldeten Nutzers.
 * Unauthentifizierte Zugriffe werden von Spring Security zur Google-Anmeldung umgeleitet.
 */
@RestController
public class MeController {

    private final AppUserRepository users;

    public MeController(AppUserRepository users) {
        this.users = users;
    }

    @GetMapping("/api/me")
    public Map<String, Object> me(@AuthenticationPrincipal OidcUser principal) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sub", principal.getSubject());
        body.put("email", principal.getEmail());
        body.put("name", principal.getFullName());
        Long appUserId = users.findByGoogleSub(principal.getSubject())
                .map(AppUser::getId)
                .orElse(null);
        body.put("appUserId", appUserId);
        return body;
    }
}
