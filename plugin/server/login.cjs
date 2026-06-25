#!/usr/bin/env node
"use strict";
var __create = Object.create;
var __defProp = Object.defineProperty;
var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
var __getOwnPropNames = Object.getOwnPropertyNames;
var __getProtoOf = Object.getPrototypeOf;
var __hasOwnProp = Object.prototype.hasOwnProperty;
var __copyProps = (to, from, except, desc) => {
  if (from && typeof from === "object" || typeof from === "function") {
    for (let key of __getOwnPropNames(from))
      if (!__hasOwnProp.call(to, key) && key !== except)
        __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
  }
  return to;
};
var __toESM = (mod, isNodeMode, target) => (target = mod != null ? __create(__getProtoOf(mod)) : {}, __copyProps(
  // If the importer is in node compatibility mode or this is not an ESM
  // file that has been converted to a CommonJS file using a Babel-
  // compatible transform (i.e. "__esModule" has not been set), then set
  // "default" to the CommonJS "module.exports" for node compatibility.
  isNodeMode || !mod || !mod.__esModule ? __defProp(target, "default", { value: mod, enumerable: true }) : target,
  mod
));

// src/login.ts
var import_node_child_process = require("node:child_process");

// src/config.ts
var import_node_os = __toESM(require("node:os"), 1);
var import_node_path = __toESM(require("node:path"), 1);
function loadConfig(fallbackAppUrl) {
  const appUrl = process.env.OASHELL_APP_URL ?? fallbackAppUrl ?? "http://127.0.0.1:8080";
  const wsUrl = process.env.OASHELL_WS_URL ?? appUrl.replace(/^http/i, "ws") + "/bridge";
  const credentialsPath = process.env.OASHELL_CREDENTIALS ?? import_node_path.default.join(import_node_os.default.homedir(), ".oa-shell", "credentials.json");
  return { appUrl, wsUrl, credentialsPath };
}

// src/credentials.ts
var import_node_fs = __toESM(require("node:fs"), 1);
var import_node_path2 = __toESM(require("node:path"), 1);
function saveCredentials(file, creds) {
  const dir = import_node_path2.default.dirname(file);
  import_node_fs.default.mkdirSync(dir, { recursive: true, mode: 448 });
  const tmp = `${file}.tmp`;
  import_node_fs.default.writeFileSync(tmp, JSON.stringify(creds, null, 2), { mode: 384 });
  import_node_fs.default.chmodSync(tmp, 384);
  import_node_fs.default.renameSync(tmp, file);
  import_node_fs.default.chmodSync(file, 384);
}

// src/device-flow.ts
var DeviceFlowError = class extends Error {
  constructor(code, message) {
    super(message);
    this.code = code;
    this.name = "DeviceFlowError";
  }
  code;
};
var DEVICE_AUTH_GRANT = "urn:ietf:params:oauth:grant-type:device_code";
function form(params) {
  return new URLSearchParams(params).toString();
}
var defaultFetch = (url, init) => globalThis.fetch(url, init);
async function requestDeviceAuthorization(appUrl, clientId, scope, fetchFn = defaultFetch) {
  const res = await fetchFn(`${appUrl}/oauth2/device_authorization`, {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded", Accept: "application/json" },
    body: form({ client_id: clientId, scope })
  });
  if (!res.ok) {
    throw new DeviceFlowError(
      "device_authorization_failed",
      `Device-Authorization fehlgeschlagen (HTTP ${res.status}): ${await res.text()}`
    );
  }
  return await res.json();
}
async function pollForToken(appUrl, clientId, auth, options = {}) {
  const fetchFn = options.fetchFn ?? defaultFetch;
  const sleep = options.sleep ?? ((ms) => new Promise((r) => setTimeout(r, ms)));
  const now = options.now ?? (() => Date.now());
  let interval = auth.interval ?? 5;
  const deadline = now() + auth.expires_in * 1e3;
  for (; ; ) {
    if (now() >= deadline) {
      throw new DeviceFlowError("expired_token", "Der Aktivierungs-Code ist abgelaufen. Bitte erneut anmelden.");
    }
    await sleep(interval * 1e3);
    const res = await fetchFn(`${appUrl}/oauth2/token`, {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded", Accept: "application/json" },
      body: form({ grant_type: DEVICE_AUTH_GRANT, device_code: auth.device_code, client_id: clientId })
    });
    const data = await res.json().catch(() => ({}));
    if (res.ok) {
      return data;
    }
    const error = typeof data.error === "string" ? data.error : "invalid_grant";
    switch (error) {
      case "authorization_pending":
        options.onPending?.();
        continue;
      case "slow_down":
        interval += 5;
        continue;
      case "expired_token":
        throw new DeviceFlowError("expired_token", "Der Aktivierungs-Code ist abgelaufen. Bitte erneut anmelden.");
      case "access_denied":
        throw new DeviceFlowError("access_denied", "Die Anmeldung wurde abgelehnt.");
      default:
        throw new DeviceFlowError(error, `Token-Abruf fehlgeschlagen: ${error}`);
    }
  }
}

// src/login.ts
var CLIENT_ID = "oa-shell-channel";
var SCOPE = "session files";
function openBrowser(url) {
  const cmd = process.platform === "darwin" ? "open" : process.platform === "win32" ? "start" : "xdg-open";
  try {
    const child = (0, import_node_child_process.spawn)(cmd, [url], { stdio: "ignore", detached: true, shell: process.platform === "win32" });
    child.on("error", () => void 0);
    child.unref();
  } catch {
  }
}
async function main() {
  const argUrl = process.argv[2]?.trim();
  const cfg = loadConfig();
  if (argUrl) {
    cfg.appUrl = argUrl;
    cfg.wsUrl = process.env.OASHELL_WS_URL ?? argUrl.replace(/^http/i, "ws") + "/bridge";
  }
  console.log(`oa-shell login \u2192 ${cfg.appUrl}`);
  const auth = await requestDeviceAuthorization(cfg.appUrl, CLIENT_ID, SCOPE);
  const url = auth.verification_uri_complete ?? auth.verification_uri;
  console.log("\nZum Anmelden im Browser \xF6ffnen:");
  console.log(`  ${url}`);
  console.log(`
Falls nach einem Code gefragt wird:  ${auth.user_code}
`);
  openBrowser(url);
  process.stdout.write("Warte auf Best\xE4tigung ");
  let token;
  try {
    token = await pollForToken(cfg.appUrl, CLIENT_ID, auth, {
      onPending: () => process.stdout.write(".")
    });
  } catch (err) {
    process.stdout.write("\n");
    if (err instanceof DeviceFlowError) {
      console.error(`Fehlgeschlagen: ${err.message}`);
      process.exit(1);
    }
    throw err;
  }
  process.stdout.write("\n");
  const creds = {
    appUrl: cfg.appUrl,
    accessToken: token.access_token,
    tokenType: token.token_type,
    refreshToken: token.refresh_token,
    scope: token.scope,
    expiresAt: token.expires_in ? Date.now() + token.expires_in * 1e3 : void 0,
    obtainedAt: Date.now()
  };
  saveCredentials(cfg.credentialsPath, creds);
  console.log(`\u2713 Angemeldet. Zugangsdaten gespeichert: ${cfg.credentialsPath}`);
}
main().catch((err) => {
  console.error(err instanceof Error ? err.message : String(err));
  process.exit(1);
});
