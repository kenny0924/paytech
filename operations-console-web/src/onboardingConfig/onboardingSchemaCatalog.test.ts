import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import en from '../i18n/en/onboarding-schema.json' with { type: 'json' };

/** 配置页、审核工作台与 Client 详情使用的文案键族（镜像自 client-console-web 的 onboarding-schema）。 */
const families = ['jurisdiction', 'field', 'catalog', 'group', 'declaration', 'identifierType', 'attribute', 'section', 'requirementGroup', 'organizationType', 'paymentMethod'];

test('every onboardingSchema.* label key of the displayed families in client-master DML has an onboarding-schema.* English base text (S-04)', () => {
  const sql = readFileSync(new URL('../../../paytech/client-master/docs/database/sql/client-master-initial-data.sql', import.meta.url), 'utf8');
  const keys = new Set(sql.match(/onboardingSchema\.[A-Za-z0-9_.]+/g) ?? []);
  const needed = [...keys].filter(key => families.includes(key.split('.')[1]));
  assert.ok(needed.length > 0, 'country-pack DML carries label keys');
  const base = en as Record<string, string>;
  assert.deepEqual(needed.filter(key => !base['onboarding-schema.' + key.slice('onboardingSchema.'.length)]), []);
});

test('the mirrored namespace holds only the displayed families', () => {
  for (const key of Object.keys(en)) assert.ok(key.startsWith('onboarding-schema.') && families.includes(key.split('.')[1]), key);
});

test('the mirror covers every key family the config page renders and matches the client portal text', () => {
  const base = en as Record<string, string>;
  for (const family of families) assert.ok(Object.keys(base).some(key => key.split('.')[1] === family), family);
  const portal = JSON.parse(readFileSync(new URL('../../../client-console-web/src/i18n/en/onboarding-schema.json', import.meta.url), 'utf8')) as Record<string, string>;
  assert.deepEqual(Object.keys(base).filter(key => portal[key] !== base[key]), []);
  assert.ok(base['onboarding-schema.section.DECLARATIONS'], 'section names');
});
