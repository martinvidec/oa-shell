package dev.oashell;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Einstiegspunkt der oa-shell Web-App.
 *
 * <p>Die App ist die "Plattform" (Chat-Oberfläche) für lokal laufende
 * Claude-Code-Sessions; sie hostet selbst keine Sessions. Architektur:
 * {@code docs/04-spezifikation-claude-chat.md}.
 */
@SpringBootApplication
public class OaShellApplication {

    public static void main(String[] args) {
        SpringApplication.run(OaShellApplication.class, args);
    }
}
