---
description: "oa-shell Device-Grant-Login durchführen und das kontogebundene Token lokal speichern. Optionales Argument ist die App-URL, z. B. http://localhost:8090"
---

# oa-shell Login

Melde die lokale Maschine per OAuth 2.0 Device Authorization Grant an der oa-shell-App an
und speichere das kontogebundene Token unter `~/.oa-shell/credentials.json` (Rechte 0600).
Dieses Token ist zugleich die Authentifizierung des Channels.

Eingabe des Nutzers (App-URL, optional): "$ARGUMENTS"

Gehe **genau** so vor:

1. **App-URL bestimmen.** Wenn "$ARGUMENTS" eine URL enthält, nutze sie. Sonst nimm die
   beim Plugin-Enable konfigurierte URL aus der Umgebungsvariable `CLAUDE_PLUGIN_OPTION_APP_URL`;
   falls auch die leer ist, nutze `http://localhost:8080`. Nenne dem Nutzer die verwendete URL.

2. **Login-CLI im Hintergrund starten.** Die CLI ist `${CLAUDE_PLUGIN_ROOT}/server/login.cjs`
   (falls `${CLAUDE_PLUGIN_ROOT}` nicht aufgelöst ist, lies die Umgebungsvariable `CLAUDE_PLUGIN_ROOT`).
   Führe als Hintergrundprozess aus:

   ```bash
   node "${CLAUDE_PLUGIN_ROOT}/server/login.cjs" "<APP_URL>"
   ```

   Die CLI öffnet den Browser automatisch und gibt eine Bestätigungs-URL aus.

3. **Bestätigungs-URL anzeigen.** Lies die Hintergrund-Ausgabe und zeige dem Nutzer die
   ausgegebene URL deutlich an (Zeile nach „Zum Anmelden im Browser öffnen:"), damit er sie
   im Browser bestätigen kann, falls der automatische Browser-Start nicht greift.

4. **Auf Abschluss warten.** Beobachte die Ausgabe, bis entweder `✓ Angemeldet` erscheint
   (Erfolg) oder eine `Fehlgeschlagen:`-Zeile (Fehler). Pollte die Hintergrund-Ausgabe in
   ruhigen Intervallen; das kann je nach Bestätigung im Browser etwas dauern.

5. **Ergebnis melden.** Bei Erfolg: bestätige knapp, dass das Token gespeichert wurde und der
   nächste Schritt der Channel-Start ist:

   ```
   claude --dangerously-load-development-channels plugin:oa-shell@oa-shell
   ```

   Bei Fehler: gib die Fehlermeldung der CLI wieder und nenne die wahrscheinliche Ursache
   (App nicht erreichbar unter der URL, falscher Port, abgelaufener Code).

Gib das Token **niemals** aus.
