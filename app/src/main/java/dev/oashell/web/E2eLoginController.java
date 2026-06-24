package dev.oashell.web;

import dev.oashell.persistence.AppUser;
import dev.oashell.persistence.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dev-Login ausschließlich für E2E-Tests (nur aktiv unter Spring-Profil {@code e2e}).
 * Authentifiziert eine Browser-Session ohne echtes Google-OAuth, damit Playwright die
 * angemeldete UI ansteuern kann. In Produktion existiert dieser Endpunkt nicht.
 */
@Profile("e2e")
@RestController
public class E2eLoginController {

    private final AppUserRepository users;
    private final SecurityContextRepository contextRepository = new HttpSessionSecurityContextRepository();

    public E2eLoginController(AppUserRepository users) {
        this.users = users;
    }

    @GetMapping("/e2e/login")
    public String login(@RequestParam("user") String user,
            HttpServletRequest request, HttpServletResponse response) {
        users.findByGoogleSub(user)
                .orElseGet(() -> users.save(new AppUser(user, user + "@e2e.test", user)));

        Authentication auth = new UsernamePasswordAuthenticationToken(
                user, "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(auth);
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);

        return "ok:" + user;
    }
}
