import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createHash } from 'node:crypto';
import Ajv from '../../../requirements-site/node_modules/ajv-formats/node_modules/ajv/dist/2020.js';

const dir = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(dir, '../../..');
const read = (file) => fs.readFileSync(path.join(dir, file), 'utf8');
const json = (file) => JSON.parse(read(file));
const assert = (ok, message) => { if (!ok) throw new Error(message); };

const baseline = json('db/risk-compliance-service/model-baseline.json');
const modelBytes = fs.readFileSync(path.join(root, baseline.target));
// 已合入模型：仍保留原设计基线 Hash 作历史证据，逐实体核对当前追加内容。
const modelHash = createHash('sha256').update(modelBytes).digest('hex');
const model = JSON.parse(modelBytes);
const patch = json('db/risk-compliance-service/model.patch.json');
const tables = patch.map((item) => {
  assert(item.op === 'add' && item.path === '/entities/-', 'Only additive entity proposal is allowed');
  return item.value;
});
assert(tables.length === 4 && tables.reduce((n, t) => n + t.fields.length, 0) === 44, 'Risk table/field count drift');
assert(tables.reduce((n, t) => n + t.indexes.filter((i) => i.unique).length, 0) === 5, 'Risk unique index count drift');
assert(tables.reduce((n, t) => n + t.indexes.filter((i) => !i.unique).length, 0) === 2, 'Risk access index count drift');
const proposedTables = [...model.entities.filter(t => !tables.some(p => p.defKey === t.defKey)), ...tables];
function physicalTable(table, catalog) {
  const fields = new Map(table.fields.map(f => [f.id, f.defKey]));
  return {
    defKey: table.defKey,
    fields: table.fields.map(({ id, domain, ...f }) => f),
    indexes: table.indexes.map(i => ({defKey:i.defKey,unique:i.unique,comment:i.comment,
      fields:i.fields.map(f => [fields.get(f.fieldDefKey), f.ascOrDesc])})),
    correlations: table.correlations.map(r => {
      const parent=catalog.find(p=>p.id===r.refEntity);
      return [fields.get(r.myField),parent?.defKey,parent?.fields.find(f=>f.id===r.refField)?.defKey,r.myRows,r.refRows,r.innerType];
    }),
  };
}
assert(model.entities.length === 78, 'Current Risk model must contain 78 entities');
for (const expected of tables) {
  const current = model.entities.find(t => t.defKey === expected.defKey);
  assert(current, `Missing implemented entity ${expected.defKey}`);
  assert(JSON.stringify(physicalTable(current, model.entities)) === JSON.stringify(physicalTable(expected, proposedTables)),
    `Implemented physical model differs from approved ${expected.defKey}`);
}
const allTables = proposedTables;
assert(new Set(allTables.map((e) => e.id)).size === allTables.length, 'Duplicate entity ID');
assert(new Set(allTables.map((e) => e.defKey)).size === allTables.length, 'Duplicate table name');
const ddl = read('db/risk-compliance-service/ddl.sql');
assert(!/\b(FOREIGN\s+KEY|CHECK\s*\(|AUTO_INCREMENT|ALTER\s+TABLE|DROP\s+TABLE)\b/i.test(ddl), 'Unexpected physical/destructive DDL');
assert((ddl.match(/CREATE TABLE/g) || []).length === 4, 'DDL must create four tables');
for (const table of tables) {
  const body = ddl.split('CREATE TABLE `' + table.defKey + '` (')[1]?.split('\n) ENGINE=')[0];
  assert(body, `DDL missing ${table.defKey}`);
  assert(table.fields.filter((f) => f.primaryKey).length === 1, 'Each table needs one PK');
  const ids = new Set(table.fields.map((f) => f.id));
  assert(ids.size === table.fields.length, 'Duplicate field ID');
  for (const f of table.fields) {
    assert(f.defKey.length <= 32 && f.comment && f.defName, 'Field metadata incomplete');
    const type = f.type + (f.len === '' ? '' : '(' + f.len + ')');
    const exact = f.extProps?.characterSet ? ` CHARACTER SET ${f.extProps.characterSet} COLLATE ${f.extProps.collation}` : '';
    const column = `  \`${f.defKey}\` ${type}${exact}${f.notNull ? ' NOT NULL' : ' NULL'}${f.defaultValue ? ' DEFAULT ' + f.defaultValue : ''} COMMENT '${f.comment.replaceAll("'", "''")}'`;
    assert(body.includes(column), `DDL/model mismatch ${table.defKey}.${f.defKey}`);
  }
  for (const index of table.indexes) {
    assert(index.fields.every((f) => ids.has(f.fieldDefKey)), 'Invalid index field');
    assert(index.defKey.length <= 32 && body.includes('`' + index.defKey + '`'), 'Missing index');
  }
  for (const rel of table.correlations) {
    const parent = allTables.find((t) => t.id === rel.refEntity);
    assert(ids.has(rel.myField) && parent?.fields.some((f) => f.id === rel.refField), 'Broken model relation');
  }
}

const registration = json('db/platform-support-service/registration.json');
assert(registration.routes.length === 10, 'Ten API registrations required');
assert(Object.values(registration.counts).reduce((n, x) => n + x, 0) === 118, 'Registration count drift');
const dml = read('db/platform-support-service/dml.sql');
const initial = fs.readFileSync(path.join(root, 'platform-support-service/docs/database/sql/platform-support-initial-data.sql'), 'utf8');
const seenIds = new Set();
let inserts = 0;
for (const [table, spec] of Object.entries(registration.tables)) {
  const section = dml.split(`INSERT INTO ${table} (`)[1]?.split(';')[0];
  assert(section, 'DML table missing ' + table);
  const lines = section.split('\n').filter((l) => l.startsWith('  ('));
  assert(lines.length === spec.rows.length && lines.length === registration.counts[table], 'DML row count mismatch');
  for (let i = 0; i < spec.rows.length; i++) {
    const row = spec.rows[i];
    const tokens = lines[i].match(/'(?:''|[^'])*'|NULL|@\w+|\d+/g).map((v) => v.startsWith("'") ? v.slice(1, -1).replaceAll("''", "'") : v);
    assert(JSON.stringify(tokens) === JSON.stringify(row.map((x) => x === null ? 'NULL' : String(x))), 'SQL/manifest value mismatch');
    const id = String(row[0]);
    assert(!seenIds.has(id), 'ID collision ' + id);
    assert(initial.split(lines[i].trim()).length === 2, 'Canonical initialization missing or duplicates reviewed row ' + id);
    seenIds.add(id);
    inserts++;
  }
}
assert(inserts === 118 && (dml.match(/UPDATE t_role /g) || []).length === 1, 'DML mutation count drift');
for (const route of registration.routes) {
  assert(read('api.md').includes(route.ownerAndBffPath), 'API/route path mismatch');
  assert(new Set(route.queryParams).size === route.queryParams.length, 'Duplicate query');
}

const schema = json('contracts/save-flow.schema.json');
const validate = new Ajv({ strict: false, allErrors: true }).compile(schema);
const sample = json('contracts/save-flow.example.json');
assert(validate(sample), JSON.stringify(validate.errors));
assert(Buffer.byteLength(JSON.stringify(sample)) <= 65536, 'Example exceeds body limit');
for (const mutate of [(x) => x.extra = true, (x) => x.graph.nodes[3].decisionCode = null,
  (x) => x.graph.nodes[1].ruleSetVersionId = 1, (x) => x.graph.nodes[0].position.x = '-0']) {
  const copy = structuredClone(sample); mutate(copy);
  assert(!validate(copy), 'Malformed sample accepted by structural schema');
}

let links = 0;
function checkLinks(folder) {
  for (const item of fs.readdirSync(folder, { withFileTypes: true })) {
    const file = path.join(folder, item.name);
    if (item.isDirectory()) checkLinks(file);
    else if (item.name.endsWith('.md')) {
      const text = fs.readFileSync(file, 'utf8');
      for (const match of text.matchAll(/\]\(([^)]+)\)/g)) {
        let target = match[1].split('#')[0];
        if (!target || /^[a-z]+:/i.test(target)) continue;
        target = target.replace(/^<|>$/g, '');
        assert(fs.existsSync(path.resolve(path.dirname(file), target)), `Broken link in ${file}: ${target}`);
        links++;
      }
    }
  }
}
checkLinks(dir);
console.log(JSON.stringify({ scope: 'approved design and implemented model/registration artifacts; database state recorded separately', modelHash, tables: 4, fields: 44,
  indexes: 11, newApis: 10, modifiedApis: 4, platformInserts: inserts, platformUpdates: 1,
  exampleSchema: 'valid example and four invalid vectors passed', localLinks: links }, null, 2));
