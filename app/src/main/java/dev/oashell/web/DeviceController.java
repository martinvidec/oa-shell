package dev.oashell.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Geräte-Aktivierung (Device Authorization Grant). Der Nutzer öffnet die in der
 * {@code verification_uri} genannte Seite {@code /activate}, gibt den {@code user_code}
 * ein und wird zur Consent-/Approval-Seite geführt; die Bestätigung bindet das Gerät
 * an sein (angemeldetes) Konto. Vorlage: Spring Authorization Server Sample.
 */
@Controller
public class DeviceController {

    @GetMapping("/activate")
    public String activate(@RequestParam(value = "user_code", required = false) String userCode) {
        if (userCode != null) {
            return "redirect:/oauth2/device_verification?user_code=" + userCode;
        }
        return "device-activate";
    }

    @GetMapping("/activated")
    public String activated() {
        return "device-activated";
    }

    @GetMapping(value = "/", params = "success")
    public String success() {
        return "device-activated";
    }
}
