import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const root = new URL('../../', import.meta.url);
const model = JSON.parse(readFileSync(new URL('paytech/risk-decision/docs/database/risk-decision.pdma.json', root)));
const approved = JSON.parse(readFileSync(new URL('requirements/20260912/operations-console-统一风控变量管理与条件编排/contracts/design-manifest.json', root)));

// 2026-10-14 支付反欺诈一期（agent-docs/decisions/2026-10-14-risk-payment-fraud-phase1.md）在 RCO-19 合同之后
// 批准的增量：新增 Feature 来源列，来源类型增加 VELOCITY、LIST。其余列仍逐项冻结为 RCO-19 原合同。
const LATER_AMENDMENTS = Object.freeze({
  t_risk_variable: Object.freeze({
    addedFields: Object.freeze(['feature_version_id']),
    comments: Object.freeze({
      source_type: '来源类型，允许 INPUT、CONSTANT、COLLECTION、NODE_OUTPUT、VELOCITY、LIST；创建后固定。允许值：INPUT、CONSTANT、COLLECTION、NODE_OUTPUT、VELOCITY、LIST。',
    }),
  }),
});

test('variable model preserves every approved column and exact string identity', () => {
  for (const table of approved.tables) {
    const entity = model.entities.find(e => e.defKey === table.name);
    assert.ok(entity, `缺少已批准表 ${table.name}`);
    const amendment = LATER_AMENDMENTS[table.name] ?? { addedFields: [], comments: {} };
    assert.deepEqual(entity.fields.map(f => f.defKey).filter(name => !amendment.addedFields.includes(name)),
      table.fields.map(f => f.name));
    for (const column of table.fields) {
      const field = entity.fields.find(f => f.defKey === column.name);
      const type = field.type + (field.len ? `(${field.len}${field.scale ? ',' + field.scale : ''})` : '');
      assert.equal(type, column.type.split(' ')[0]);
      assert.equal(field.notNull, !column.nullable);
      assert.equal(field.comment, amendment.comments[column.name] ?? column.comment);
      if (column.type.includes('ascii_bin')) assert.equal(field.extProps.collation, 'ascii_bin');
      if (column.type.includes('utf8mb4_0900_bin')) assert.equal(field.extProps.collation, 'utf8mb4_0900_bin');
    }
  }
});

test('rule and action references have indexed permanent variable codes', () => {
  for (const table of approved.existingTableAdditions) {
    const entity = model.entities.find(e => e.defKey === table.table);
    for (const column of table.fields) assert.ok(entity.fields.some(f => f.defKey === column.name));
    for (const index of table.indexes) assert.ok(entity.indexes.some(i => i.defKey === index.name));
  }
});
