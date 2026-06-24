package dev.oashell.web;

import java.security.Principal;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Eigene Consent-/Approval-Seite ({@code /oauth2/consent}) für Device Grant und
 * Authorization Code. Vorlage: Spring Authorization Server Sample.
 */
@Controller
public class AuthorizationConsentController {

    private final RegisteredClientRepository registeredClientRepository;
    private final OAuth2AuthorizationConsentService authorizationConsentService;

    public AuthorizationConsentController(RegisteredClientRepository registeredClientRepository,
            OAuth2AuthorizationConsentService authorizationConsentService) {
        this.registeredClientRepository = registeredClientRepository;
        this.authorizationConsentService = authorizationConsentService;
    }

    @GetMapping("/oauth2/consent")
    public String consent(Principal principal, Model model,
            @RequestParam(OAuth2ParameterNames.CLIENT_ID) String clientId,
            @RequestParam(OAuth2ParameterNames.SCOPE) String scope,
            @RequestParam(OAuth2ParameterNames.STATE) String state,
            @RequestParam(name = OAuth2ParameterNames.USER_CODE, required = false) String userCode) {

        Set<String> scopesToApprove = new HashSet<>();
        Set<String> previouslyApprovedScopes = new HashSet<>();
        RegisteredClient registeredClient = this.registeredClientRepository.findByClientId(clientId);
        OAuth2AuthorizationConsent currentAuthorizationConsent =
                this.authorizationConsentService.findById(registeredClient.getId(), principal.getName());
        Set<String> authorizedScopes = currentAuthorizationConsent != null
                ? currentAuthorizationConsent.getScopes()
                : Collections.emptySet();
        for (String requestedScope : StringUtils.delimitedListToStringArray(scope, " ")) {
            if (OidcScopes.OPENID.equals(requestedScope)) {
                continue;
            }
            if (authorizedScopes.contains(requestedScope)) {
                previouslyApprovedScopes.add(requestedScope);
            } else {
                scopesToApprove.add(requestedScope);
            }
        }

        model.addAttribute("clientId", clientId);
        model.addAttribute("state", state);
        model.addAttribute("scopes", withDescription(scopesToApprove));
        model.addAttribute("previouslyApprovedScopes", withDescription(previouslyApprovedScopes));
        model.addAttribute("principalName", principal.getName());
        model.addAttribute("userCode", userCode);
        model.addAttribute("requestURI",
                StringUtils.hasText(userCode) ? "/oauth2/device_verification" : "/oauth2/authorize");

        return "consent";
    }

    private static Set<ScopeWithDescription> withDescription(Set<String> scopes) {
        Set<ScopeWithDescription> result = new HashSet<>();
        for (String scope : scopes) {
            result.add(new ScopeWithDescription(scope));
        }
        return result;
    }

    public static class ScopeWithDescription {
        private static final String DEFAULT_DESCRIPTION =
                "Unbekannter Scope – beim Gewähren vorsichtig sein.";
        private static final Map<String, String> SCOPE_DESCRIPTIONS = new HashMap<>();

        static {
            SCOPE_DESCRIPTIONS.put("session",
                    "Erlaubt der App, Nachrichten in deine Claude-Session zu schicken und Antworten zu empfangen.");
            SCOPE_DESCRIPTIONS.put("files",
                    "Erlaubt der App, den Datei-Baum/-Inhalt des Session-Arbeitsverzeichnisses anzuzeigen.");
            SCOPE_DESCRIPTIONS.put(OidcScopes.PROFILE,
                    "Erlaubt der App, deine Profilinformationen zu lesen.");
        }

        public final String scope;
        public final String description;

        ScopeWithDescription(String scope) {
            this.scope = scope;
            this.description = SCOPE_DESCRIPTIONS.getOrDefault(scope, DEFAULT_DESCRIPTION);
        }
    }
}
