package dev.oashell.web;

import com.fasterxml.jackson.databind.JsonNode;
import dev.oashell.files.FileViewService;
import dev.oashell.persistence.AppUser;
import dev.oashell.persistence.AppUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Read-only Datei-Proxy: liefert Baum/Inhalt der gewählten Session über die Bridge
 * (Sandbox/cwd-Begrenzung erfolgt im Channel).
 */
@RestController
public class FileController {

    private final AppUserRepository users;
    private final FileViewService fileView;

    public FileController(AppUserRepository users, FileViewService fileView) {
        this.users = users;
        this.fileView = fileView;
    }

    @GetMapping("/api/sessions/{id}/files")
    public JsonNode files(@PathVariable Long id,
            @RequestParam(name = "path", defaultValue = ".") String path, Authentication auth) {
        return fileView.tree(userId(auth), id, path);
    }

    @GetMapping("/api/sessions/{id}/file")
    public JsonNode file(@PathVariable Long id,
            @RequestParam(name = "path") String path, Authentication auth) {
        return fileView.content(userId(auth), id, path);
    }

    private Long userId(Authentication auth) {
        return users.findByGoogleSub(auth.getName())
                .map(AppUser::getId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unbekannter Nutzer"));
    }
}
