import assert from 'node:assert/strict';
import test from 'node:test';
import {
  getBrowserLocalStorage,
  loadNavigationCollapsed,
  saveNavigationCollapsed,
  type NavigationPreferenceStorage,
} from './navigationPreference.ts';

function memoryStorage(initialValue: string | null = null): NavigationPreferenceStorage & { value: string | null } {
  return {
    value: initialValue,
    getItem() { return this.value; },
    setItem(_key, value) { this.value = value; },
  };
}

test('导航折叠偏好默认展开', () => {
  assert.equal(loadNavigationCollapsed(), false);
  assert.equal(loadNavigationCollapsed(memoryStorage()), false);
});

test('只有字符串 true 会恢复为折叠状态', () => {
  assert.equal(loadNavigationCollapsed(memoryStorage('true')), true);
  for (const storedValue of ['false', 'TRUE', '1', ' true ', '']) {
    assert.equal(loadNavigationCollapsed(memoryStorage(storedValue)), false, storedValue);
  }
});

test('折叠和展开状态都保存为对应字符串', () => {
  const storage = memoryStorage();

  saveNavigationCollapsed(storage, true);
  assert.equal(storage.value, 'true');
  assert.equal(loadNavigationCollapsed(storage), true);

  saveNavigationCollapsed(storage, false);
  assert.equal(storage.value, 'false');
  assert.equal(loadNavigationCollapsed(storage), false);
});

test('存储读写失败时安全降级且不抛异常', () => {
  const readFailure: NavigationPreferenceStorage = {
    getItem() { throw new Error('localStorage unavailable'); },
    setItem() {},
  };
  const writeFailure: NavigationPreferenceStorage = {
    getItem() { return null; },
    setItem() { throw new Error('localStorage full'); },
  };

  assert.equal(loadNavigationCollapsed(readFailure), false);
  assert.doesNotThrow(() => saveNavigationCollapsed(writeFailure, true));
});

test('浏览器 localStorage 可访问时返回该存储', () => {
  const originalWindow = Object.getOwnPropertyDescriptor(globalThis, 'window');
  const storage = memoryStorage();
  try {
    Object.defineProperty(globalThis, 'window', {
      configurable: true,
      value: { localStorage: storage },
    });
    assert.equal(getBrowserLocalStorage(), storage);
  } finally {
    if (originalWindow) Object.defineProperty(globalThis, 'window', originalWindow);
    else Reflect.deleteProperty(globalThis, 'window');
  }
});

test('浏览器 localStorage 不存在或不可访问时返回 undefined', () => {
  const originalWindow = Object.getOwnPropertyDescriptor(globalThis, 'window');
  try {
    Reflect.deleteProperty(globalThis, 'window');
    assert.equal(getBrowserLocalStorage(), undefined);

    Object.defineProperty(globalThis, 'window', {
      configurable: true,
      get() { throw new Error('window unavailable'); },
    });
    assert.equal(getBrowserLocalStorage(), undefined);
  } finally {
    if (originalWindow) Object.defineProperty(globalThis, 'window', originalWindow);
    else Reflect.deleteProperty(globalThis, 'window');
  }
});
