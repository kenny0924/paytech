import assert from 'node:assert/strict';
import test from 'node:test';
import { freezeTimeRange, resolveTimeRange } from './timeRange.ts';

const local = (year: number, month: number, day: number) => {
  const date = new Date(year, month - 1, day);
  const offset = (value: number) => String(value).padStart(2, '0');
  return `${date.getFullYear()}-${offset(date.getMonth() + 1)}-${offset(date.getDate())}T00:00:00`;
};

test('quick time ranges use complete browser-local calendar days with an exclusive next-day end', () => {
  const now = new Date(2026, 8, 4, 18, 23, 45);
  assert.deepEqual(resolveTimeRange({ preset: 'TODAY' }, now), {
    start: local(2026, 9, 4), end: local(2026, 9, 5),
  });
  assert.deepEqual(resolveTimeRange({ preset: 'LAST_3_DAYS' }, now), {
    start: local(2026, 9, 2), end: local(2026, 9, 5),
  });
  assert.deepEqual(resolveTimeRange({ preset: 'LAST_7_DAYS' }, now), {
    start: local(2026, 8, 29), end: local(2026, 9, 5),
  });
});

test('an empty selection stays unfiltered and custom values are returned without timezone conversion', () => {
  assert.deepEqual(resolveTimeRange(undefined, new Date(2026, 8, 4)), {});
  const value = { preset: 'CUSTOM' as const, start: '2026-09-01T08:30:15', end: '2026-09-04T17:45:30' };
  assert.deepEqual(resolveTimeRange(value, new Date(2026, 8, 4)), { start: value.start, end: value.end });
  assert.deepEqual(value, { preset: 'CUSTOM', start: '2026-09-01T08:30:15', end: '2026-09-04T17:45:30' });
});

test('a submitted shortcut freezes its displayed natural-day bounds across later renders', () => {
  const selected = { preset: 'TODAY' as const };
  const submittedAt = new Date(2026, 8, 4, 23, 59, 59);
  const frozen = freezeTimeRange(selected, submittedAt);
  assert.deepEqual(frozen, { preset: 'TODAY', start: local(2026, 9, 4), end: local(2026, 9, 5) });
  assert.deepEqual(resolveTimeRange(frozen, new Date(2026, 8, 5, 0, 0, 1)), {
    start: local(2026, 9, 4), end: local(2026, 9, 5),
  });
  assert.deepEqual(selected, { preset: 'TODAY' });
});
