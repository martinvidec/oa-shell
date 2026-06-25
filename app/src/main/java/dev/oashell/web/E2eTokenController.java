package dev.oashell.web;

import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import dev.oashell.persistence.AppUser;
import dev.oashell.persistence.AppUserRepository;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mintet ein Channel-Token mit dem laufenden Signaturschlüssel — nur unter Profil
 * {@code e2e} (für lokale E2E-Durchläufe ohne den interaktiven Device-Grant, der
 * separat getestet ist). In Produktion existiert dieser Endpunkt nicht.
 */
@Profile("e2e")
@RestController
public class E2eTokenController {

    private final JWKSource<SecurityContext> jwkSource;
    private final AppUserRepository users;

    public E2eTokenController(JWKSource<SecurityContext> jwkSource, AppUserRepository users) {
        this.jwkSource = jwkSource;
        this.users = users;
    }

    @GetMapping("/e2e/token")
    public String token(@RequestParam("user") String user) {
        users.findByGoogleSub(user)
                .orElseGet(() -> users.save(new AppUser(user, user + "@e2e.test", user)));

        JwtEncoder encoder = new NimbusJwtEncoder(jwkSource);
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .id("e2e-" + user)
                .subject(user)
                .issuer("oa-shell")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
        return encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(SignatureAlgorithm.RS256).build(), claims)).getTokenValue();
    }
}
