import { test } from 'node:test';
import assert from 'node:assert/strict';
import { switchLanguage } from './switchLanguage.ts';

function fakeInstance(prefetch: () => Promise<void>) {
  const calls: string[] = [];
  return { calls, instance: {
    prefetch: async (code: string) => { calls.push('prefetch ' + code); await prefetch(); },
    rememberLocale: (code: string) => { calls.push('remember ' + code); },
  } };
}

test('switching caches the language first, stores the choice, then reloads the same route without ?lang', async () => {
  const { calls, instance } = fakeInstance(async () => {});
  const replaced: string[] = [];
  await switchLanguage('zh-CN', instance, { href: 'https://ops.test/finance/ledger?lang=en&book=7#top', replace: url => { replaced.push(String(url)); } });
  assert.deepEqual(calls, ['prefetch zh-CN', 'remember zh-CN']);
  assert.deepEqual(replaced, ['https://ops.test/finance/ledger?book=7#top']);
});

test('a failed prefetch still switches (the next start falls back to the network or built-in English)', async () => {
  const { calls, instance } = fakeInstance(() => Promise.reject(new Error('offline')));
  const replaced: string[] = [];
  await switchLanguage('en', instance, { href: 'https://ops.test/roles', replace: url => { replaced.push(String(url)); } });
  assert.deepEqual(calls, ['prefetch en', 'remember en']);
  assert.deepEqual(replaced, ['https://ops.test/roles']);
});
