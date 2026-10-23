import { test } from 'node:test';
import assert from 'node:assert/strict';
import { isValidElement } from 'react';
import type { ReactElement } from 'react';
import { code, richText } from './richText.ts';
import { testT } from './testing.ts';

test('richText keeps the whole sentence and places nodes where the translation puts their arguments', () => {
  const t = testT('data-query');
  const parts = richText(t, 'trial.pageInput', { input: 'INPUT' });
  const text = parts.map(part => (isValidElement(part) ? `[${(part as ReactElement<{ children: string }>).props.children}]` : part)).join('');
  assert.equal(text, t('trial.pageInput', { input: '[INPUT]' }));
  assert.equal(parts.filter(isValidElement).length, 1);
});

test('richText passes plain parameters through and code() renders a monospace span', () => {
  const t = testT('data-query');
  const parts = richText(t, 'parameters.intro', { value: code('#{}'), fragment: code('@{}'), ifTag: code('{{#if}}'), eachTag: code('{{#each}}') });
  assert.equal(parts.filter(isValidElement).length, 4);
  const node = code('#{}') as ReactElement<{ className: string; children: string }>;
  assert.equal(node.props.className, 'mono');
  assert.equal(node.props.children, '#{}');
});
