# Härtung & Security-Review (Issue #16)

> Bezug: [Spezifikation](04-spezifikation-claude-chat.md) · [Anforderungen](03-anforderungsanalyse-claude-chat.md)

Zusammenfassung der Härtungsmaßnahmen und des Security-Reviews. Was bewusst auf
spätere Schritte verschoben ist, ist hier transparent festgehalten.

## 1. Token-Lebenszyklus & Revoke (AK-9)

- **Revoke umgesetzt:** „Trennen" einer Session schließt die Bridge-Verbindung und
  setzt das JWT-`jti` auf eine **Denylist** (`RevokedTokenStore`), die beim
  Bridge-Handshake geprüft wird → ein Reconnect mit demselben Token wird abgelehnt.
  Das `jti` wird an der Session persistiert, sodass Revoke auch ohne aktive
  Verbindung greift. (Test: `SessionServiceTest`, `BridgeWebSocketTest.rejectsRevokedToken`.)
- **Geräte-/Session-Liste:** `GET /api/sessions`; Trennen über die WS
  (`disconnectSession`), Ownership-geprüft.
- **Bewusst verschoben (Folge-Schritt):** Die `RevokedTokenStore`-Denylist und der
  SAS-Authorization-Store sind **in-memory**. Für Produktion:
  - persistente **JDBC**-Token-Speicherung (`JdbcOAuth2AuthorizationService` +
    SAS-Schema), damit Tokens/Revokes Neustarts überstehen;
  - **Verschlüsselung at-rest** (DB-/Volume-Verschlüsselung oder feldweise).
  Begründung: vermeidet eine riskante Schema-Migration in diesem Schritt; Tokens
  haben ohnehin begrenzte Lebensdauer.

## 2. Verbindungs-Lebenszyklus (FA-32/33)

- **Reconnect:** Channel (`bridge-client.ts`) und Browser (`chat.js`) verbinden nach
  Abbruch automatisch neu (getestet bzw. implementiert).
- **Session-Ende wird erkannt:** Der Bridge-Handler setzt beim Schließen
  `DISCONNECTED` und sendet ein Live-Update (`sessionStatus`) an die Browser des
  Nutzers; die Sidebar aktualisiert den Status.

## 3. Transport-Sicherheit (NFA-15)

- **TLS/WSS** wird beim **Deployment** durch einen vorgelagerten TLS-Proxy terminiert.
  Die App übernimmt Original-Schema/Host aus den Forwarded-Headern
  (`server.forward-headers-strategy=framework`), damit Redirects und WS-URLs korrekt
  `https`/`wss` sind. In Produktion zusätzlich HSTS/Secure-Cookies am Proxy bzw. via
  Spring Security konfigurieren.

## 4. Secrets / Logging (NFA-04)

- **Keine Secrets in Logs:** Audit (`grep` über `log.*`) zeigt kein Logging von
  Token/Authorization/Bearer/Secret/Password. Der Channel loggt das Token nie
  (nur Pfad der Credentials).
- **Google-Client-Credentials** kommen ausschließlich aus der Umgebung
  (Dummy-Defaults für Build/CI); nichts davon liegt im Repo.
- **Pairing/Token** des Channels liegen lokal mit Dateirechten **0600**.

## 5. Channel-Preview-Vertrag gekapselt (NFA-06)

- Der Channel-Vertrag (claude↔channel-Methodennamen, channel↔app-Envelopes) ist an
  **einer Stelle** gekapselt: `channel/src/protocol.ts`. Channels sind ein
  **Research-Preview**-Feature — bei Protokoll-/Flag-Änderungen ist nur diese Stelle
  (plus ggf. die Java-Envelope-Behandlung) anzupassen. Erprobte Referenz: `spike/`.

## 6. Mandantentrennung / Ownership (NFA-01)

Durchgängig erzwungen: Chat (`ChatService`), Permission-Relay (`PermissionService`),
Datei-Proxy (`FileViewService`), Umbenennen/Trennen (`SessionService`) prüfen jeweils,
dass die Session dem angemeldeten Nutzer gehört; Browser-Delivery (`BrowserHub`) liefert
nur an den Eigentümer der gewählten Session.

## 7. Offene Folge-Schritte (dokumentiert)

- Persistente + verschlüsselte Token-/Authorization-Speicherung (s. §1).
- OS-/Container-Sandboxing der Channel-Prozesse (Modell A: Code läuft auf der
  Nutzer-Maschine mit dessen Freigaben; serverseitig keine Code-Ausführung).
- Rate-Limiting / Idle-Timeouts pro Verbindung.
