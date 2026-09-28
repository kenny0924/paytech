import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const topLevelPages = ['Accounts.tsx', 'Roles.tsx', 'Audit.tsx', 'GatewayConfiguration.tsx'];

test('一级页面标题不重复展示菜单说明', () => {
  for (const file of topLevelPages) {
    const source = readFileSync(new URL(file, import.meta.url), 'utf8');
    const start = source.indexOf('<div className="page-heading">');
    const end = source.indexOf('</div>', start);
    assert.notEqual(start, -1, `${file} 缺少一级页面标题`);
    assert.notEqual(end, -1, `${file} 的一级页面标题未闭合`);
    assert.doesNotMatch(source.slice(start, end), /<p\b/, `${file} 仍展示菜单说明`);
  }
});
