import { test } from 'node:test';
import assert from 'node:assert/strict';
import { devLoginDefaults } from './devLogin.ts';

test('devLoginDefaults: 开发模式预填已配置的账号与密码', () => {
  assert.deepEqual(devLoginDefaults({ MODE: 'development', VITE_DEV_LOGIN_IDENTIFIER: ' admin@example.test ', VITE_DEV_LOGIN_PASSWORD: 'local-password-value' }),
    { loginIdentifier: 'admin@example.test', password: 'local-password-value' });
});

test('devLoginDefaults: 生产模式即使存在变量也不预填', () => {
  assert.deepEqual(devLoginDefaults({ MODE: 'production', VITE_DEV_LOGIN_IDENTIFIER: 'admin@example.test', VITE_DEV_LOGIN_PASSWORD: 'local-password-value' }), {});
});

test('devLoginDefaults: 未配置或空值时不预填', () => {
  assert.deepEqual(devLoginDefaults({ MODE: 'development' }), {});
  assert.deepEqual(devLoginDefaults({ MODE: 'development', VITE_DEV_LOGIN_IDENTIFIER: '  ', VITE_DEV_LOGIN_PASSWORD: '' }), {});
});
