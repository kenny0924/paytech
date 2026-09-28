import * as decode from './riskResponseValidation.ts';
import { authoringContext, candidateDetail, candidatePage, createCandidateResponse, createRuleSetResponse,
  freezeRuleSetResponse, ruleSetDetail, ruleSetPage, validateCandidateResponse } from './riskFixtures.ts';
import type { ValueType } from './riskApiModels.ts';
import type { WirePath } from './riskFixtures.ts';

// Contract cases only: importing this module never registers tests.
export const successRoots = [
  ['authoring', decode.decodeRuleAuthoringContext, authoringContext],
  ['rule page', decode.decodeRuleSetPage, ruleSetPage],
  ['rule detail', decode.decodeRuleSetDetail, ruleSetDetail],
  ['create rule', decode.decodeCreateRuleSetVersionResponse, createRuleSetResponse],
  ['freeze rule', decode.decodeFreezeRuleSetVersionResponse, freezeRuleSetResponse],
  ['candidate page', decode.decodeCandidatePage, candidatePage],
  ...(['draft', 'pending', 'passed', 'rejected'] as const).map(state => [`candidate ${state}`, decode.decodeCandidateDetail, candidateDetail(state)] as const),
  ['create candidate', decode.decodeCreateCandidateResponse, createCandidateResponse],
  ['validate passed', decode.decodeValidateCandidateResponse, validateCandidateResponse()],
  ['validate rejected', decode.decodeValidateCandidateResponse, validateCandidateResponse(true)],
] as const;
export function* paths(value: unknown, path: WirePath = []): Generator<WirePath> {
  yield path;
  if (value && typeof value === 'object') for (const [key, child] of Object.entries(value)) yield* paths(child, [...path, key]);
}
export const invalidIds = ['', '0', '01', '-1', '+1', '1.0', '1e2', ' 1', '1 ', '1\n', '1\r', 'a', 1, true];
export const invalidHashes = ['', 'a'.repeat(63), 'a'.repeat(65), 'A'.repeat(64), 'g'.repeat(64), 1];
export const invalidInstants = ['0000-01-01T00:00:00.000000Z', '0999-12-31T23:59:59.999999Z',
  '2026-02-29T00:00:00.000000Z', '2024-02-30T00:00:00.000000Z', '2026-13-01T00:00:00.000000Z',
  '2026-01-00T00:00:00.000000Z', '2026-01-01T24:00:00.000000Z', '2026-01-01T00:60:00.000000Z',
  '2026-01-01T00:00:60.000000Z', '2026-01-01T00:00:00Z', '2026-01-01T00:00:00.00000Z',
  '2026-01-01T00:00:00.0000000Z', '2026-01-01T00:00:00.000000+00:00', '2026-01-01t00:00:00.000000z'];
export const validInstants = ['1000-01-01T00:00:00.000000Z', '9999-12-31T23:59:59.999999Z', '2024-02-29T00:00:00.000001Z'];
export const literalMatrix: Record<ValueType, { valid: readonly unknown[]; invalid: readonly unknown[] }> = {
  STRING: { valid: ['', 'AE', 'x'.repeat(512), '😀'.repeat(256)], invalid: [null, true, 1, 'x'.repeat(513), '😀'.repeat(256) + 'a'] },
  NUMBER: {
    valid: ['0', '0.00000000', '1', '-1', '1.0', '1.00000000', '0.00000001', '-0.00000001', '9999999999999999', '-9999999999999999', '9999999999999999.99999999', '-9999999999999999.99999999'],
    invalid: [null, true, 1, 0, -1, '', '+1', '01', '00', '-01', '1e0', '1E0', '.1', '-.1', '1.', ' 1', '1 ', '1\n', '1\r', 'NaN', 'Infinity', '-0', '-0.0', '-0.00000000', '10000000000000000', '-10000000000000000', '0.000000001', '9999999999999999.999999999'],
  },
  BOOLEAN: { valid: [true, false], invalid: [null, 'true', 'false', 'TRUE', 'FALSE', '1', '0', 1, 0, '', {}, []] },
  DATETIME: { valid: validInstants, invalid: [null, true, 1, '', ...invalidInstants] },
  DURATION: { valid: ['0', '1', '1000', '9999999999999999999999999999999999999999'], invalid: [null, true, 0, 1, '', '-1', '-0', '+1', '00', '01', '1.0', '.1', '1e0', 'PT1S', ' 1', '1 ', '1\n', '1\r'] },
};
export const operatorMatrix = [
  ['EQ', 1, ['STRING', 'NUMBER', 'BOOLEAN', 'DATETIME', 'DURATION']],
  ['NE', 1, ['STRING', 'NUMBER', 'BOOLEAN', 'DATETIME', 'DURATION']],
  ['GT', 1, ['NUMBER', 'DATETIME', 'DURATION']], ['GTE', 1, ['NUMBER', 'DATETIME', 'DURATION']],
  ['LT', 1, ['NUMBER', 'DATETIME', 'DURATION']], ['LTE', 1, ['NUMBER', 'DATETIME', 'DURATION']],
  ['IN', 1, ['STRING', 'NUMBER', 'BOOLEAN', 'DATETIME', 'DURATION']],
  ['NOT_IN', 1, ['STRING', 'NUMBER', 'BOOLEAN', 'DATETIME', 'DURATION']],
  ['BETWEEN', 2, ['NUMBER', 'DATETIME', 'DURATION']],
  ['IS_NULL', 0, ['STRING', 'NUMBER', 'BOOLEAN', 'DATETIME', 'DURATION']],
  ['IS_NOT_NULL', 0, ['STRING', 'NUMBER', 'BOOLEAN', 'DATETIME', 'DURATION']],
] as const;
