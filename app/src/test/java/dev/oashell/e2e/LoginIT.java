package dev.oashell.e2e;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import dev.oashell.e2e.support.PlaywrightSpringBase;
import org.junit.jupiter.api.Test;

/** Swimlane: Login / Anmeldestatus (AK-1). */
class LoginIT extends PlaywrightSpringBase {

    @Test
    void unauthenticatedShowsLoginPrompt() {
        page.navigate("/");
        assertThat(page.getByText("Mit Google anmelden")).isVisible();
        assertThat(page.getByText("Bitte melde dich an")).isVisible();
    }

    @Test
    void e2eLoginShowsChatUi() {
        page.navigate("/e2e/login?user=alice");
        page.navigate("/");
        assertThat(page.getByText("Sessions")).isVisible();
        assertThat(page.getByText("Wähle links eine Session.")).isVisible();
    }
}
