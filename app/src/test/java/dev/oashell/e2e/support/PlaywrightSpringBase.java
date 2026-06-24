package dev.oashell.e2e.support;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Tracing;
import dev.oashell.OaShellApplication;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

/**
 * Basis für Playwright-E2E-Tests: startet die App in-JVM (Zufallsport, Profil
 * {@code e2e}) und einen Chromium-Browser; pro Test ein frischer Browser-Context mit
 * Tracing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = OaShellApplication.class)
@ActiveProfiles("e2e")
public abstract class PlaywrightSpringBase {

    protected static Playwright playwright;
    protected static Browser browser;

    protected BrowserContext context;
    protected Page page;

    @LocalServerPort
    protected int serverPort;

    protected String baseUrl() {
        return "http://localhost:" + serverPort;
    }

    /** Pollt einen API-Endpunkt (mit den Browser-Cookies), bis die Antwort {@code contains} enthält. */
    protected void waitForApi(String path, String contains) throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            var response = context.request().get(baseUrl() + path);
            if (response.ok() && response.text().contains(contains)) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("'" + contains + "' nicht in " + path + " gefunden");
    }

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        boolean headless = Boolean.parseBoolean(System.getProperty("playwright.headless", "true"));
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(headless));
    }

    @AfterAll
    static void closeBrowser() {
        if (browser != null) {
            browser.close();
        }
        if (playwright != null) {
            playwright.close();
        }
    }

    @BeforeEach
    void createContext() {
        context = browser.newContext(new Browser.NewContextOptions().setBaseURL(baseUrl()));
        context.tracing().start(new Tracing.StartOptions().setScreenshots(true).setSnapshots(true).setSources(true));
        page = context.newPage();
    }

    @AfterEach
    void closeContext(TestInfo info) {
        try {
            Path trace = Paths.get("target/traces/"
                    + info.getTestClass().map(Class::getSimpleName).orElse("E2E")
                    + "_" + info.getDisplayName().replaceAll("\\W+", "_") + ".zip");
            trace.getParent().toFile().mkdirs();
            context.tracing().stop(new Tracing.StopOptions().setPath(trace));
        } catch (Exception ignored) {
            // Trace ist best-effort
        }
        if (context != null) {
            context.close();
        }
    }
}
