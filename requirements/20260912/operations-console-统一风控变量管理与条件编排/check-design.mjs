import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';
const here = path.dirname(fileURLToPath(import.meta.url));
const workspace = path.resolve(here, '../../..');
const read = p => fs.readFileSync(path.join(here, p), 'utf8');
const json = p => JSON.parse(read(p));
const m = json('contracts/design-manifest.json');
assert.equal(m.status, 'APPROVED_STAGE_4_IN_PROGRESS');
assert.equal(m.apis.length, 9);
assert.equal(new Set(m.apis.map(a => `${a.method} ${a.path}`)).size, 9);
assert(m.apis.every(a => a.path.startsWith('/decision-variables/v1/')));
const ddl = read('db/risk-compliance-service/ddl.sql');
assert.equal((ddl.match(/CREATE TABLE `/g) || []).length, 2);
assert.equal((ddl.match(/ALTER TABLE `/g) || []).length, 4);
assert.equal((ddl.match(/ADD COLUMN `/g) || []).length, 5);
assert.equal((ddl.match(/MODIFY COLUMN `/g) || []).length, 4);
assert(!/\b(?:DROP|TRUNCATE)\s+(?:TABLE|DATABASE)|\bCHECK\s*\(|\bFOREIGN\s+KEY\s*\(/i.test(ddl));
assert.equal(m.tables.reduce((n, t) => n + t.fields.length, 0), 41);
assert.equal([...m.tables, ...m.existingTableAdditions].reduce((n, t) => n + t.indexes.length, 0), 11);
for (const t of m.tables) {
  const body = ddl.match(new RegExp('CREATE TABLE `' + t.name + '` \\(([\\s\\S]+?)\\n\\) ENGINE'))[1];
  assert.equal(new Set(t.fields.map(f => f.name)).size, t.fields.length);
  for (const f of t.fields) {
    assert(body.includes('`' + f.name + '` ' + f.type + (f.nullable ? ' NULL' : ' NOT NULL')));
    assert(body.includes("COMMENT '" + f.comment.replaceAll("'", "''") + "'"));
  }
  for (const i of t.indexes) assert(i.columns.every(c => t.fields.some(f => f.name === c)));
}
const riskSchema = fs.readFileSync(path.join(workspace, 'risk-compliance-service/docs/database/sql/risk-compliance-schema.sql'), 'utf8');
assert(m.tables.every(t => riskSchema.includes('CREATE TABLE `' + t.name + '`')), '已批准变量表必须同步到完整 Schema');
const vars = new Map(m.variables.map(v => [v.variable_code, v]));
assert.equal(vars.size, 17);
const uniqueMembers = new Set();
for (const member of m.members) {
  assert.equal(vars.get(member.variable_code).source_type, 'COLLECTION');
  const key = `${member.variable_code}:${member.value_key}`;
  assert(!uniqueMembers.has(key)); uniqueMembers.add(key);
}
for (const v of vars.values()) {
  assert(/^[A-Z][A-Z0-9_]{0,63}$/.test(v.variable_code));
  if (v.currency_variable_code) {
    const c = vars.get(v.currency_variable_code);
    assert(c && c.source_type === 'INPUT' && c.value_type === 'STRING' && c.format_code === 'UPPER_ALPHA_3');
    assert.equal(c.point_code, v.point_code);
  }
  if (v.allowed_values_variable_code) {
    const s = vars.get(v.allowed_values_variable_code);
    assert(s && s.source_type === 'COLLECTION' && s.value_type === v.value_type);
  }
  if (v.source_type === 'NODE_OUTPUT') assert(!v.rule_condition_flag && !v.action_parameter_flag && v.system_managed_flag);
}
assert.equal(vars.get('INPUT_CLIENT_ID').rule_condition_flag, 0);
assert.equal(vars.get('INPUT_CLIENT_ID').flow_condition_flag, 0);
assert.equal(vars.get('INPUT_CLIENT_ID').action_parameter_flag, 1);
const countRows = sql => Object.fromEntries([...sql.matchAll(/INSERT INTO (\w+) \([^;]+?\) VALUES\n([\s\S]+?);/g)]
  .map(x => [x[1], (x[2].match(/^  \(/gm) || []).length]));
assert.deepEqual(countRows(read('db/risk-compliance-service/dml.sql')), { t_risk_variable: 17, t_risk_variable_member: 9 });
const platformSql = read('db/platform-support-service/dml.sql');
assert.deepEqual(countRows(platformSql), m.platformCounts);
assert.equal(Object.values(m.platformCounts).reduce((a, b) => a + b, 0), 113);
assert.equal((platformSql.match(/^UPDATE /gm) || []).length, 1);
assert(!/INSERT IGNORE|ON DUPLICATE KEY|REPLACE INTO/i.test(platformSql));
const platformSchema = fs.readFileSync(path.join(workspace, 'platform-support-service/docs/database/sql/platform-support-schema.sql'), 'utf8');
for (const insert of platformSql.matchAll(/INSERT INTO (\w+) \(([^)]+)\)/g)) {
  const table = platformSchema.match(new RegExp('CREATE TABLE `' + insert[1] + '` \\(([\\s\\S]+?)\\n\\) ENGINE'));
  assert(table, insert[1]);
  for (const column of insert[2].split(',').map(x => x.trim())) assert(table[1].includes('`' + column + '`'), insert[1] + '.' + column);
}
assert(platformSql.includes("target_service_code='operations-console-bff' AND channel='OPERATIONS'"));
for (const a of m.apis) {
  assert(platformSql.includes("'" + a.routeCode + "'"));
  assert(platformSql.includes("'/api/operations" + a.path + "'"));
  assert(platformSql.includes("'" + a.path + "'"));
  for (const q of a.query) assert(platformSql.includes("'" + q + "'"));
}
const require = createRequire(path.join(workspace, 'requirements-site/package.json'));
const Ajv = require('ajv');
const ajv = new Ajv({ allErrors: true, jsonPointers: true });
const validate = ajv.compile(json('contracts/condition.schema.json'));
const examples = json('contracts/condition.examples.json');
for (const e of examples) assert(validate(e.condition), e.name + ': ' + JSON.stringify(validate.errors));
const create = ajv.compile(json('contracts/variable-create.schema.json'));
assert(create(json('contracts/variable-create.example.json')), JSON.stringify(create.errors));
const badConditions = [];
const mutate = fn => { const x = structuredClone(examples[1].condition); fn(x); badConditions.push(x); };
mutate(x => x.schemaVersion = 1);
mutate(x => x.root.source = { type: 'CONTEXT', fieldCode: 'net_amount' });
mutate(x => x.root.operands[0].variableCode = 'lowercase');
mutate(x => x.root.operands[0].fieldCode = 'net_amount');
mutate(x => x.root.operands[0].literalValue = '8000');
mutate(x => delete x.root.operands[0].valueType);
for (const condition of badConditions) assert(!validate(condition));
const badCreate = structuredClone(json('contracts/variable-create.example.json'));
badCreate.systemManaged = true;
assert(!create(badCreate));
// Structural JSON Schema and model-aware examples are design checks, not runtime implementation tests.
function checkExpression(expr) {
  if (expr.kind === 'GROUP') {
    if (expr.operator === 'NOT') assert.equal(expr.children.length, 1);
    expr.children.forEach(checkExpression); return;
  }
  const left = expr.operands.filter(o => o.operandRole === 'LEFT');
  const right = expr.operands.filter(o => o.operandRole === 'RIGHT');
  assert.equal(left.length, 1); assert.equal(left[0].valueSequence, 1);
  const type = left[0].valueType;
  for (const o of expr.operands) {
    assert.equal(o.valueType, type);
    if (o.sourceType === 'VARIABLE') assert.equal(vars.get(o.variableCode)?.value_type, o.valueType);
  }
  assert(right.every((o, i) => o.valueSequence === i + 1));
  if (['IS_NULL', 'IS_NOT_NULL'].includes(expr.operator)) assert.equal(right.length, 0);
  else if (expr.operator === 'BETWEEN') assert.equal(right.length, 2);
  else if (['IN', 'NOT_IN'].includes(expr.operator)) {
    if (right.some(o => o.sourceType === 'VARIABLE')) {
      assert.equal(right.length, 1); assert.equal(vars.get(right[0].variableCode).source_type, 'COLLECTION');
    } else assert(right.length >= 1 && right.length <= 100);
  } else assert.equal(right.length, 1);
}
examples.forEach(e => checkExpression(e.condition.root));
const demo = json('contracts/test-demo-template.json');
assert.equal(demo.status, 'REVIEW_TEMPLATE_NOT_EXECUTABLE');
assert.equal(demo.ruleRequestTemplate.rules[0].action, null);
assert.equal(demo.flowRequestTemplate.graph.nodes.length, 5);
assert.equal(demo.flowRequestTemplate.graph.edges.length, 4);
assert.equal(demo.ruleRequestTemplate.operandFormat, 'VARIABLE_REFERENCE');
for (const edge of demo.flowRequestTemplate.graph.edges) if (edge.condition) {
  assert(validate(edge.condition)); checkExpression(edge.condition.root);
}
assert(!JSON.stringify(demo.ruleRequestTemplate).includes('CONTEXT'));
assert(!JSON.stringify(demo.flowRequestTemplate).includes('NODE_RESULT'));
assert(JSON.stringify(demo.flowRequestTemplate).includes('NODE_MATCHED'));
const files = fs.readdirSync(here).filter(f => f.endsWith('.md')).map(f => path.join(here, f));
files.push(path.join(here, 'db/README.md'));
let links = 0;
for (const file of files) for (const link of fs.readFileSync(file, 'utf8').matchAll(/\[[^\]]*\]\(([^)]+)\)/g)) {
  const target = link[1];
  if (/^(https?:|#)/.test(target)) continue;
  const [relative, anchor] = target.split('#');
  const resolved = path.resolve(path.dirname(file), decodeURIComponent(relative));
  assert(fs.existsSync(resolved), file + ' -> ' + target);
  if (anchor && fs.statSync(resolved).isFile()) assert(fs.readFileSync(resolved, 'utf8').includes('id="' + anchor + '"'), target);
  links++;
}
const boards = ['zROEt','G5rUZ2','bgpJE','Pft7o','bbtED','P3SDn','Jit5X'];
for (const id of boards) {
  const png = fs.readFileSync(path.join(here, 'assets/pencil/' + id + '.png'));
  assert.equal(png.subarray(1,4).toString(), 'PNG');
  assert.equal(png.readUInt32BE(16), 1440);
  assert(png.readUInt32BE(20) >= 980);
}
assert(fs.readFileSync(path.join(here, 'assets/pencil/export.pdf')).subarray(0,5).toString() === '%PDF-');
console.log(JSON.stringify({stage:'design only',counts:m.counts,conditionExamples:examples.length,
  invalidContractCases:badConditions.length+1,links,pencilBoards:boards.length}, null, 2));
