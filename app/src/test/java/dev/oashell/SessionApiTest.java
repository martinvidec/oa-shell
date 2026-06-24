package dev.oashell;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.oashell.persistence.AppUser;
import dev.oashell.persistence.AppUserRepository;
import dev.oashell.persistence.ChannelSession;
import dev.oashell.persistence.ChannelSessionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SessionApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private AppUserRepository users;
    @Autowired
    private ChannelSessionRepository sessions;

    @Test
    @WithMockUser(username = "sub-list-1")
    void listsOwnSessions() throws Exception {
        AppUser user = users.save(new AppUser("sub-list-1", "list@example.com", "List User"));
        ChannelSession session = new ChannelSession(user.getId(), "conn-list-1");
        session.setCwdBasename("proj");
        sessions.save(session);

        mvc.perform(get("/api/sessions").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].cwdBasename").value("proj"))
                .andExpect(jsonPath("$[0].status").value("CONNECTED"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        // Unauthentifiziert -> Umleitung zur Anmeldung (Zugriff verweigert).
        mvc.perform(get("/api/sessions").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is3xxRedirection());
    }
}
