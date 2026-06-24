package dev.oashell.web;

import dev.oashell.persistence.AppUser;
import dev.oashell.persistence.AppUserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Liefert die Chat-Oberfläche und ihren Anmeldestatus. Die eigentliche Interaktion
 * (Sessions, Chat, Streaming) läuft per WebSocket {@code /ws} und {@code /api/sessions}.
 */
@Controller
public class HomeController {

    private final AppUserRepository users;

    public HomeController(AppUserRepository users) {
        this.users = users;
    }

    @GetMapping("/")
    public String index(Authentication auth, Model model) {
        boolean authenticated = auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
        model.addAttribute("authenticated", authenticated);
        model.addAttribute("email", authenticated
                ? users.findByGoogleSub(auth.getName()).map(AppUser::getEmail).orElse(auth.getName())
                : null);
        return "index";
    }

    @GetMapping("/healthz")
    @ResponseBody
    public String healthz() {
        return "ok";
    }
}
