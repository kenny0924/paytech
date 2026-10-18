import assert from 'node:assert/strict';
import test from 'node:test';
import { decodePath, extractLinks, parseRelativeTarget } from './markdown-links.mjs';

const raws = (text) => [...extractLinks(text)].map((link) => link.raw);

test('extracts inline links, images and reference definitions with their positions', () => {
  const text = 'See [a](../x/a.md#part) and ![b](./img.png "title").\n[ref]: ../y/b.md\n';
  const links = [...extractLinks(text)];
  assert.deepEqual(links.map((link) => link.raw), ['../x/a.md#part', './img.png', '../y/b.md']);
  const first = links[0];
  assert.equal(text.split('\n')[first.line - 1].slice(first.from, first.to), '../x/a.md#part');
});

test('skips fenced code blocks and inline code spans', () => {
  const text = '```bash\ncd old && echo [x](../in-fence.md)\n```\n`[y](../in-span.md)` [z](../real.md)\n~~~\n[w](../tilde.md)\n~~~\n';
  assert.deepEqual(raws(text), ['../real.md']);
});

test('keeps balanced parentheses and angle-bracket destinations intact', () => {
  assert.deepEqual(raws('[a](dir/file(1).md) [b](<dir with space/c.md>)'), ['dir/file(1).md', '<dir with space/c.md>']);
});

test('classifies only relative file targets', () => {
  assert.equal(parseRelativeTarget('https://example.com/a.md'), null);
  assert.equal(parseRelativeTarget('mailto:someone@example.com'), null);
  assert.equal(parseRelativeTarget('#anchor'), null);
  assert.equal(parseRelativeTarget('/absolute/path.md'), null);
  assert.deepEqual(parseRelativeTarget('../a.md#L10'), { path: '../a.md', suffix: '#L10', angle: false });
  assert.deepEqual(parseRelativeTarget('<b c.md?x=1>'), { path: 'b c.md', suffix: '?x=1', angle: true });
});

test('decodes percent-encoded paths and tolerates invalid encoding', () => {
  assert.equal(decodePath('%E8%AE%BE%E8%AE%A1.md'), '设计.md');
  assert.equal(decodePath('100%.md'), '100%.md');
});
