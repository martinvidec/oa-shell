package dev.oashell.web;

import dev.oashell.persistence.AppUser;
import dev.oashell.persistence.AppUserRepository;
import dev.oashell.persistence.ChannelSession;
import dev.oashell.persistence.ChannelSessionRepository;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liefert die gekoppelten Sessions des angemeldeten Nutzers (für Liste/Umschalter im
 * Frontend). Der Switcher-Komfort (Benennen etc.) folgt in #15.
 */
@RestController
public class SessionController {

    private final AppUserRepository users;
    private final ChannelSessionRepository sessions;

    public SessionController(AppUserRepository users, ChannelSessionRepository sessions) {
        this.users = users;
        this.sessions = sessions;
    }

    @GetMapping("/api/sessions")
    public List<SessionDto> list(Authentication auth) {
        return users.findByGoogleSub(auth.getName())
                .map(AppUser::getId)
                .map(sessions::findByUserId)
                .orElseGet(List::of)
                .stream()
                .map(SessionDto::from)
                .toList();
    }

    public record SessionDto(Long id, String name, String status, String cwdBasename) {
        static SessionDto from(ChannelSession s) {
            String name = s.getDisplayName() != null ? s.getDisplayName()
                    : (s.getCwdBasename() != null ? s.getCwdBasename() : "Session " + s.getId());
            return new SessionDto(s.getId(), name, s.getStatus().name(), s.getCwdBasename());
        }
    }
}
