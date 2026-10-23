import { test } from 'node:test';
import assert from 'node:assert/strict';
import { IntlMessageFormat } from 'intl-messageformat';
import { formatValues, highlight, parameterNames, parseIcu, renameFix, sampleRows, validateTranslation } from './icu.ts';

const codes = (source: string, translation: string) => validateTranslation(source, translation).map(issue => issue.code);
const PLURAL = '{count, plural, one {# member} other {# members}}';

test('valid messages parse without issues and report parameters in first-use order', () => {
  for (const text of ['Send invitation', 'Hello {name}', PLURAL, '{n, selectordinal, one {#st} two {#nd} few {#rd} other {#th}}',
    '{role, select, admin {Admin {name}} other {Member}}', 'Expires on {date, date, medium} at {time, time, short}',
    '{amount, number}', "It''s '{literal}' text", '{count, plural, offset:1 =0 {nobody} one {you} other {you and # others}}']) {
    assert.deepEqual(parseIcu(text).issues, [], text);
  }
  assert.deepEqual(parameterNames('{a} and {count, plural, other {# of {b}}} and {a}'), ['a', 'count', 'b']);
  assert.deepEqual(parameterNames("'{notAnArg}' {real}"), ['real']);
});

test('the parser agrees with intl-messageformat on what is valid syntax', () => {
  const valid = ['Hi {name}', PLURAL, "'{x}'", '{a, select, x {X} other {O}}'];
  const broken = ['Hi {name', 'Hi name}', '{count, plural, one {#}', '{count, plural, one # other {#}}', '{, plural, other {x}}'];
  for (const text of valid) {
    assert.doesNotThrow(() => new IntlMessageFormat(text, 'en'), text);
    assert.deepEqual(parseIcu(text).issues, [], text);
  }
  for (const text of broken) assert.ok(parseIcu(text).issues.length > 0, text);
});

test('syntax and structure problems carry a code and a position (B5)', () => {
  assert.deepEqual(codes('', '   '), ['empty']);
  const unclosed = validateTranslation('Hi {name}', 'Hi {name');
  assert.equal(unclosed[0].code, 'unclosed_brace');
  assert.equal(unclosed[0].start, 3);
  assert.deepEqual(codes('Hi', 'Hi }'), ['unmatched_close']);
  assert.deepEqual(codes(PLURAL, '{count, plural, one {# member}}'), ['missing_other']);
  assert.deepEqual(codes('{r, select, a {A} other {B}}', '{r, select, a {A}}'), ['missing_other']);
  assert.deepEqual(codes('{n}', '{n, choice, 0#none|1#one}'), ['choice_unsupported']);
  assert.deepEqual(codes('{n}', '{n, spellout}'), ['unsupported_type']);
  assert.deepEqual(codes(PLURAL, '{count, plural, one # other {#}}'), ['missing_fragment']);
  assert.deepEqual(codes('{a}', '{}'), ['bad_argument']);
});

test('parameter sets must match the English base; # is not a parameter', () => {
  assert.deepEqual(codes(PLURAL, '{count, plural, other {# 名成员}}'), []);
  assert.deepEqual(codes('Leave {teamName}?', '离开 {teamName}？'), []);
  assert.deepEqual(codes('Hi {name} from {team}', 'Hi'), ['missing_parameter', 'missing_parameter']);
  assert.deepEqual(codes('Hi', 'Hi {name}'), ['extra_parameter']);
  // A base that does not parse is not compared (the backend rejects it at import time).
  assert.deepEqual(codes('{broken', 'anything'), []);
});

test('a translated parameter name becomes one rename issue with a one-click fix (prototype 02)', () => {
  const translation = '{名字} 已被移出团队';
  const issues = validateTranslation('{name} was removed from the team', translation);
  assert.equal(issues.length, 1);
  assert.equal(issues[0].code, 'renamed_parameter');
  assert.deepEqual(issues[0].params, { expected: 'name', actual: '名字' });
  assert.equal(translation.slice(issues[0].start, issues[0].end), '名字');
  const fix = renameFix(translation, issues);
  assert.deepEqual(fix, { expected: 'name', actual: '名字', text: '{name} 已被移出团队' });
  assert.deepEqual(validateTranslation('{name} was removed from the team', fix!.text), []);
  assert.equal(renameFix('x', validateTranslation('Hi {a} {b}', 'x')), null);
});

test('highlight covers the whole text, classifies tokens and marks issue ranges', () => {
  const segments = highlight(PLURAL);
  assert.equal(segments.map(item => item.text).join(''), PLURAL);
  const kinds = (kind: string) => segments.filter(item => item.kind === kind).map(item => item.text.trim());
  assert.deepEqual(kinds('argument'), ['count']);
  assert.deepEqual(kinds('keyword'), ['plural']);
  assert.deepEqual(kinds('selector'), ['one', 'other']);
  assert.equal(kinds('pound').length, 2);
  const text = '{名字} 已被移出团队';
  const marked = highlight(text, validateTranslation('{name} x', text));
  assert.equal(marked.map(item => item.text).join(''), text);
  assert.deepEqual(marked.filter(item => item.error).map(item => item.text), ['名字']);
});

test('preview samples drive plural with 1, 5, 0 and select with each branch, and format with real values', () => {
  assert.deepEqual(sampleRows(PLURAL).map(row => row.count), ['1', '5', '0']);
  assert.deepEqual(sampleRows('{role, select, admin {A} viewer {V} other {O}}').map(row => row.role), ['admin', 'viewer', 'other']);
  assert.deepEqual(sampleRows('{name} left'), [{ name: 'name' }]);
  assert.deepEqual(sampleRows('Plain'), [{}]);
  const values = formatValues(PLURAL, { count: '5' });
  assert.equal(new IntlMessageFormat(PLURAL, 'en').format(values), '5 members');
  assert.equal(new IntlMessageFormat('{count, plural, other {# 名成员}}', 'zh-CN').format(formatValues(PLURAL, { count: '1' })), '1 名成员');
  assert.equal(formatValues(PLURAL, { count: 'x' }).count, 0);
});
