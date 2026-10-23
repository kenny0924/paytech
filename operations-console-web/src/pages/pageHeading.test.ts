import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const topLevelPages = ['Accounts.tsx', 'Roles.tsx', 'Audit.tsx', 'GatewayConfiguration.tsx'];

test('top-level page headings do not repeat the menu description', () => {
  for (const file of topLevelPages) {
    const source = readFileSync(new URL(file, import.meta.url), 'utf8');
    const start = source.indexOf('<div className="page-heading">');
    const end = source.indexOf('</div>', start);
    assert.notEqual(start, -1, `${file} has no page heading`);
    assert.notEqual(end, -1, `${file} page heading is not closed`);
    assert.doesNotMatch(source.slice(start, end), /<p\b/, `${file} still shows the menu description`);
  }
});
