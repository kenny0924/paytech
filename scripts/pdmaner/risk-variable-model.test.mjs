import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const root = new URL('../../', import.meta.url);
const model = JSON.parse(readFileSync(new URL('risk-compliance-service/docs/database/risk-compliance.pdma.json', root)));
const approved = JSON.parse(readFileSync(new URL('requirements/20260912/operations-console-统一风控变量管理与条件编排/contracts/design-manifest.json', root)));

test('variable model preserves every approved column and exact string identity', () => {
  for (const table of approved.tables) {
    const entity = model.entities.find(e => e.defKey === table.name);
    assert.ok(entity, `缺少已批准表 ${table.name}`);
    assert.deepEqual(entity.fields.map(f => f.defKey), table.fields.map(f => f.name));
    for (const column of table.fields) {
      const field = entity.fields.find(f => f.defKey === column.name);
      const type = field.type + (field.len ? `(${field.len}${field.scale ? ',' + field.scale : ''})` : '');
      assert.equal(type, column.type.split(' ')[0]);
      assert.equal(field.notNull, !column.nullable);
      assert.equal(field.comment, column.comment);
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
