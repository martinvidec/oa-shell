// OAuth 2.0 Device Authorization Grant (RFC 8628) — Client-Seite.
// fetch/sleep/now sind injizierbar, damit die Polling-Logik ohne Netzwerk testbar ist.

export interface MinimalResponse {
  ok: boolean;
  status: number;
  json: () => Promise<unknown>;
  text: () => Promise<string>;
}

export type FetchLike = (
  url: string,
  init: { method: string; headers: Record<string, string>; body: string },
) => Promise<MinimalResponse>;

export interface DeviceAuthorization {
  device_code: string;
  user_code: string;
  verification_uri: string;
  verification_uri_complete?: string;
  expires_in: number;
  interval?: number;
}

export interface TokenSet {
  access_token: string;
  token_type: string;
  refresh_token?: string;
  expires_in?: number;
  scope?: string;
}

/** Fehler des Device-Flows mit OAuth-Fehlercode (z. B. expired_token, access_denied). */
export class DeviceFlowError extends Error {
  constructor(public readonly code: string, message: string) {
    super(message);
    this.name = 'DeviceFlowError';
  }
}

const DEVICE_AUTH_GRANT = 'urn:ietf:params:oauth:grant-type:device_code';

function form(params: Record<string, string>): string {
  return new URLSearchParams(params).toString();
}

const defaultFetch: FetchLike = (url, init) =>
  (globalThis.fetch as unknown as FetchLike)(url, init);

export async function requestDeviceAuthorization(
  appUrl: string,
  clientId: string,
  scope: string,
  fetchFn: FetchLike = defaultFetch,
): Promise<DeviceAuthorization> {
  const res = await fetchFn(`${appUrl}/oauth2/device_authorization`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded', Accept: 'application/json' },
    body: form({ client_id: clientId, scope }),
  });
  if (!res.ok) {
    throw new DeviceFlowError(
      'device_authorization_failed',
      `Device-Authorization fehlgeschlagen (HTTP ${res.status}): ${await res.text()}`,
    );
  }
  return (await res.json()) as DeviceAuthorization;
}

export interface PollOptions {
  fetchFn?: FetchLike;
  sleep?: (ms: number) => Promise<void>;
  now?: () => number;
  onPending?: () => void;
}

/**
 * Pollt das Token-Endpoint bis Approval. Behandelt authorization_pending (weiter),
 * slow_down (Intervall +5s), expired_token/access_denied (Abbruch) und das Ablaufen
 * des Codes.
 */
export async function pollForToken(
  appUrl: string,
  clientId: string,
  auth: DeviceAuthorization,
  options: PollOptions = {},
): Promise<TokenSet> {
  const fetchFn = options.fetchFn ?? defaultFetch;
  const sleep = options.sleep ?? ((ms: number) => new Promise<void>((r) => setTimeout(r, ms)));
  const now = options.now ?? (() => Date.now());

  let interval = auth.interval ?? 5;
  const deadline = now() + auth.expires_in * 1000;

  for (;;) {
    if (now() >= deadline) {
      throw new DeviceFlowError('expired_token', 'Der Aktivierungs-Code ist abgelaufen. Bitte erneut anmelden.');
    }
    await sleep(interval * 1000);

    const res = await fetchFn(`${appUrl}/oauth2/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded', Accept: 'application/json' },
      body: form({ grant_type: DEVICE_AUTH_GRANT, device_code: auth.device_code, client_id: clientId }),
    });

    const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
    if (res.ok) {
      return data as unknown as TokenSet;
    }

    const error = typeof data.error === 'string' ? data.error : 'invalid_grant';
    switch (error) {
      case 'authorization_pending':
        options.onPending?.();
        continue;
      case 'slow_down':
        interval += 5;
        continue;
      case 'expired_token':
        throw new DeviceFlowError('expired_token', 'Der Aktivierungs-Code ist abgelaufen. Bitte erneut anmelden.');
      case 'access_denied':
        throw new DeviceFlowError('access_denied', 'Die Anmeldung wurde abgelehnt.');
      default:
        throw new DeviceFlowError(error, `Token-Abruf fehlgeschlagen: ${error}`);
    }
  }
}
