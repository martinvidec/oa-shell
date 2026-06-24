package dev.oashell.auth;

import dev.oashell.persistence.AppUser;
import dev.oashell.persistence.AppUserRepository;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lädt den OIDC-Nutzer von Google und legt dabei den zugehörigen {@link AppUser}
 * (Schlüssel: Google-{@code sub}) an bzw. aktualisiert E-Mail/Name.
 */
@Service
public class AppUserOidcUserService extends OidcUserService {

    private final AppUserRepository users;

    public AppUserOidcUserService(AppUserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        String sub = oidcUser.getSubject();
        String email = oidcUser.getEmail();
        String name = oidcUser.getFullName() != null ? oidcUser.getFullName() : email;

        AppUser appUser = users.findByGoogleSub(sub).orElseGet(() -> new AppUser(sub, email, name));
        appUser.setEmail(email);
        appUser.setDisplayName(name);
        users.save(appUser);

        return oidcUser;
    }
}
