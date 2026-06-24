package dev.oashell.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

/**
 * Konfiguriert die App als OAuth2 Authorization Server (Endpoints {@code /oauth2/*})
 * und föderiert die Nutzer-Identität an Google (Browser-Login, Authorization Code).
 * Der registrierte Client {@code oa-shell-channel} unterstützt zusätzlich Device Grant
 * (RFC 8628) für den headless-Login des Channels (Issue #5/#6).
 */
@Configuration
public class AuthorizationServerConfig {

    /** Zu Google weiterleiten, wenn ein AS-Request (z. B. /oauth2/authorize) eine Anmeldung braucht. */
    private static final String LOGIN_REDIRECT = "/oauth2/authorization/google";

    /** Eigene Consent-/Approval-Seite (Device-Grant und Authorization Code). */
    private static final String CONSENT_PAGE_URI = "/oauth2/consent";

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
            RegisteredClientRepository registeredClientRepository,
            AuthorizationServerSettings authorizationServerSettings) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);

        // Public-Client-Authentifizierung (nur client_id) für den Device-Grant.
        DeviceClientAuthenticationConverter deviceClientAuthenticationConverter =
                new DeviceClientAuthenticationConverter(
                        authorizationServerSettings.getDeviceAuthorizationEndpoint());
        DeviceClientAuthenticationProvider deviceClientAuthenticationProvider =
                new DeviceClientAuthenticationProvider(registeredClientRepository);

        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                // Device Authorization Grant (RFC 8628): die ausgegebene verification_uri
                // zeigt auf unsere /activate-Seite; die Approval läuft über die Consent-Seite.
                .deviceAuthorizationEndpoint(device -> device.verificationUri("/activate"))
                .deviceVerificationEndpoint(device -> device.consentPage(CONSENT_PAGE_URI))
                .clientAuthentication(clientAuthentication -> clientAuthentication
                        .authenticationConverter(deviceClientAuthenticationConverter)
                        .authenticationProvider(deviceClientAuthenticationProvider))
                .authorizationEndpoint(authorization -> authorization.consentPage(CONSENT_PAGE_URI))
                .oidc(Customizer.withDefaults());
        http
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint(LOGIN_REDIRECT),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)))
                .oauth2ResourceServer(rs -> rs.jwt(Customizer.withDefaults()));
        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain appSecurityFilterChain(HttpSecurity http,
            AppUserOidcUserService oidcUserService) throws Exception {
        http
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/", "/error", "/healthz", "/actuator/health",
                                "/css/**", "/js/**", "/favicon.ico").permitAll()
                        .anyRequest().authenticated())
                .oauth2Login(o -> o.userInfoEndpoint(u -> u.oidcUserService(oidcUserService)));
        return http.build();
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository() {
        RegisteredClient channel = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("oa-shell-channel")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrantType(AuthorizationGrantType.DEVICE_CODE)
                .redirectUri("http://127.0.0.1/callback")
                .scope(OidcScopes.OPENID)
                .scope("session")
                .scope("files")
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        // Der Nutzer bestätigt das Gerät/die Scopes auf der Approval-Seite,
                        // wodurch das Device an sein Konto gebunden wird.
                        .requireAuthorizationConsent(true)
                        .build())
                .build();
        return new InMemoryRegisteredClientRepository(channel);
    }

    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService() {
        return new InMemoryOAuth2AuthorizationConsentService();
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        KeyPair keyPair = generateRsaKey();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();
        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(UUID.randomUUID().toString())
                .build();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder().build();
    }

    private static KeyPair generateRsaKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("RSA-Schlüssel konnte nicht erzeugt werden", ex);
        }
    }
}
