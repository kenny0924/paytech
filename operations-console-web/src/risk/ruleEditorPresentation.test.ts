import assert from 'node:assert/strict';
import test from 'node:test';
import { appendCondition, removeConditionChild, conditionCount, durationToMillis, displayDuration } from './ruleEditorPresentation.ts';
import type { EditorCondition } from './ruleBuilder.ts';

const predicate: EditorCondition = { id: 'amount', kind: 'CONDITION', fieldSide: 'RIGHT', fieldCode: 'net_amount', operator: 'GT', values: ['9999999999999999.12345678'] };
const negation: EditorCondition = { id: 'not', kind: 'NOT', children: [predicate] };
test('adding a sibling preserves a historical NOT subtree, operand direction and exact decimal', () => {
  const next = appendCondition(negation, { ...predicate, id: 'other' }, 'ANY');
  assert.equal(next.kind, 'ANY');
  assert.equal(next.children[0], negation);
  assert.equal(conditionCount(next), 2);
  assert.deepEqual(negation.children[0], predicate);
});
test('removing a group sibling collapses a single child without rewriting it or emptying NOT', () => {
  const group = appendCondition(negation, { ...predicate, id: 'other' });
  assert.equal(removeConditionChild(group, 1), negation);
  assert.equal(removeConditionChild(negation, 0), negation);
  assert.equal(removeConditionChild(group, 99), group);
});
test('duration units convert with integer precision and invalid text remains invalid', () => {
  assert.equal(durationToMillis('30', 'MINUTE'), '1800000');
  assert.equal(durationToMillis('9007199254740993', 'SECOND'), '9007199254740993000');
  assert.equal(durationToMillis('1.5', 'MINUTE'), '1.5');
  assert.equal(durationToMillis('', 'MINUTE'), '');
});
test('existing millisecond durations round trip exactly including sub-second values', () => {
  for (const original of ['1', '999', '1000', '1800000', '3600000', '31536000000']) {
    const display = displayDuration(original);
    assert.equal(durationToMillis(display.value, display.unit), original);
  }
  assert.deepEqual(displayDuration('1800000'), { value: '30', unit: 'MINUTE' });
});
