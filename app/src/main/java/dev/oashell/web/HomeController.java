package dev.oashell.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Vorläufiger Einstiegs-Controller (Scaffolding). Chat-UI, Session-Umschalter,
 * Datei-Browser und Freigabe-Dialog folgen in den UI-Issues.
 */
@Controller
public class HomeController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/healthz")
    @ResponseBody
    public String healthz() {
        return "ok";
    }
}
