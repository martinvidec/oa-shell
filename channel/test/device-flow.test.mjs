import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  requestDeviceAuthorization,
  pollForToken,
  DeviceFlowError,
} from '../dist/device-flow.js';

function res(status, body) {
  return {
    ok: status >= 200 && status < 300,
    status,
    json: async () => body,
    text: async () => JSON.stringify(body),
  };
}

const AUTH = {
  device_code: 'dc',
  user_code: 'ABCD-EFGH',
  verification_uri: '/activate',
  expires_in: 300,
  interval: 1,
};

test('requestDeviceAuthorization parst die Antwort', async () => {
  const fetchFn = async () =>
    res(200, { ...AUTH, verification_uri_complete: '/activate?user_code=ABCD-EFGH' });
  const auth = await requestDeviceAuthorization('http://app', 'oa-shell-channel', 'session files', fetchFn);
  assert.equal(auth.device_code, 'dc');
  assert.equal(auth.user_code, 'ABCD-EFGH');
});

test('requestDeviceAuthorization wirft bei HTTP-Fehler', async () => {
  const fetchFn = async () => res(400, { error: 'invalid_client' });
  await assert.rejects(
    requestDeviceAuthorization('http://app', 'c', 's', fetchFn),
    (e) => e instanceof DeviceFlowError,
  );
});

test('pollForToken: authorization_pending -> dann Erfolg', async () => {
  let n = 0;
  const fetchFn = async () => {
    n += 1;
    return n < 3 ? res(400, { error: 'authorization_pending' }) : res(200, { access_token: 'tok', token_type: 'Bearer' });
  };
  const token = await pollForToken('http://app', 'c', AUTH, {
    fetchFn,
    sleep: async () => {},
    now: () => 0,
  });
  assert.equal(token.access_token, 'tok');
  assert.ok(n >= 3);
});

test('pollForToken: slow_down erhöht das Intervall um 5s', async () => {
  const sleeps = [];
  let n = 0;
  const fetchFn = async () => {
    n += 1;
    return n === 1 ? res(400, { error: 'slow_down' }) : res(200, { access_token: 't', token_type: 'Bearer' });
  };
  await pollForToken('http://app', 'c', AUTH, {
    fetchFn,
    sleep: async (ms) => { sleeps.push(ms); },
    now: () => 0,
  });
  assert.deepEqual(sleeps.slice(0, 2), [1000, 6000]);
});

test('pollForToken: access_denied -> DeviceFlowError', async () => {
  const fetchFn = async () => res(400, { error: 'access_denied' });
  await assert.rejects(
    pollForToken('http://app', 'c', AUTH, { fetchFn, sleep: async () => {}, now: () => 0 }),
    (e) => e instanceof DeviceFlowError && e.code === 'access_denied',
  );
});

test('pollForToken: läuft nach Ablauf in expired_token', async () => {
  let t = 0;
  const fetchFn = async () => res(400, { error: 'authorization_pending' });
  await assert.rejects(
    pollForToken('http://app', 'c', { ...AUTH, expires_in: 1 }, {
      fetchFn,
      sleep: async () => { t += 1000; },
      now: () => t,
    }),
    (e) => e instanceof DeviceFlowError && e.code === 'expired_token',
  );
});
