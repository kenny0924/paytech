import assert from 'node:assert/strict';
import test from 'node:test';
import type { ConsoleMenu } from './menu.ts';
import { menuTrail } from './menuTrail.ts';

const menu = (resourceId: string, displayName: string, frontendRoute: string | null, children: ConsoleMenu[] = []) =>
  ({ resourceId, displayName, frontendRoute, children }) as unknown as ConsoleMenu;
const tree = [
  menu('finance', '财务', null, [menu('setup', '账务基础资料', '/finance/setup'), menu('ledger', '账本查询', '/finance/ledger')]),
  menu('tenants', '租户访问', '/tenant-access'),
];
const names = (pathname: string) => menuTrail(tree, pathname).map(item => item.displayName);

test('顶部路径包含目录与当前页面', () => {
  assert.deepEqual(names('/finance/setup'), ['财务', '账务基础资料']);
  assert.deepEqual(names('/tenant-access'), ['租户访问']);
});

test('详情子路径归到所属页面，前缀相似的路径不误命中', () => {
  assert.deepEqual(names('/tenant-access/123/members/9'), ['租户访问']);
  assert.deepEqual(names('/finance/setupx'), []);
  assert.deepEqual(names('/unknown'), []);
});
