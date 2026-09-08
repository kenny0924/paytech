import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const service = new URL('../../platform-support-service/', import.meta.url);
const model = JSON.parse(readFileSync(new URL('docs/database/platform-support.pdma.json', service)));
const sql = readFileSync(new URL('docs/database/sql/platform-support-schema.sql', service), 'utf8');
const apiCredentialChangeSql = readFileSync(
  new URL('docs/database/sql/changes/2026-09-08-add-api-credential-validation.sql', service),
  'utf8',
);
const tables = [...sql.matchAll(/CREATE TABLE `(\w+)` \(([\s\S]*?)\) ENGINE=InnoDB([^;]*);/g)];
const expectedTables = [
  'user_account', 'password_credential', 'access_tenant', 'tenant_member', 'role', 'permission',
  'tenant_member_role', 'role_permission', 'auth_session', 'refresh_token_generation', 'jwt_signing_key',
  'api_credential', 'api_credential_scope',
  'gateway_target', 'gateway_route', 'gateway_route_header', 'gateway_route_query_param', 'gateway_guard_rule',
  'audit_chain', 'audit_event', 'audit_event_attr', 'security_event', 'command_idempotency', 'console_resource',
].map(name => `t_${name}`);
const quote = value => `'${value.replaceAll("'", "''")}'`;

test('only the 24 implemented tables exist, without physical CHECK or foreign keys', () => {
  assert.deepEqual(tables.map(table => table[1]).sort(), expectedTables.toSorted());
  assert.doesNotMatch(sql.replace(/^--.*$/gm, ''), /\bCHECK\s*\(|\bFOREIGN\s+KEY\b/i);
});

test('every implemented column, default and complete comment matches PDManer', () => {
  for (const [, name, body, options] of tables) {
    const entity = model.entities.find(entity => entity.defKey === name);
    const columns = [...body.matchAll(/^  `(\w+)` (.*?)(?:,)?$/gm)];
    assert.deepEqual(columns.map(column => column[1]), entity.fields.map(field => field.defKey), name);
    for (const [index, field] of entity.fields.entries()) {
      const type = field.type + (field.len ? `(${field.len}${field.scale ? `,${field.scale}` : ''})` : '');
      const definition = `${type} ${field.notNull ? 'NOT NULL' : 'NULL'}`
        + (field.defaultValue === '' ? '' : ` DEFAULT ${field.defaultValue}`)
        + ` COMMENT ${quote(field.comment)}`;
      // Retain the existing MySQL ASCII overrides for exact path keys and case-insensitive header names.
      const actual = columns[index][2].replace(/ CHARACTER SET ascii COLLATE ascii_(?:bin|general_ci)/, '');
      assert.equal(actual, definition, `${name}.${field.defKey}`);
    }
    assert.ok(options.includes(`COMMENT=${quote(entity.comment)}`), `${name} full table comment`);
  }
});

test('primary keys and every modeled unique/access index are preserved exactly', () => {
  for (const [, name, body] of tables) {
    const entity = model.entities.find(entity => entity.defKey === name);
    const keys = fields => fields.map(field => `\`${field}\``).join(', ');
    assert.ok(body.includes(`PRIMARY KEY (${keys(entity.fields.filter(f => f.primaryKey).map(f => f.defKey))})`), name);
    const indexes = [...body.matchAll(/^  (UNIQUE KEY|KEY) `(\w+)` \(([^)]+)\)(?: COMMENT ('(?:[^']|'')*'))?,?$/gm)];
    assert.equal(indexes.length, entity.indexes.length, `${name} indexes`);
    for (const index of entity.indexes) {
      const actual = indexes.find(item => item[2] === index.defKey);
      assert.ok(actual, `${name}.${index.defKey}`);
      assert.equal(actual[1], index.unique ? 'UNIQUE KEY' : 'KEY');
      assert.equal(actual[3], keys(index.fields.map(item => entity.fields.find(f => f.id === item.fieldDefKey).defKey)));
      assert.equal(actual[4], quote(index.comment));
    }
  }
});

test('existing path/header collations and manual-only initialization are retained', () => {
  for (const field of ['http_method', 'external_path_template', 'downstream_path_template']) {
    assert.match(sql, new RegExp('`' + field + '` VARCHAR\\(\\d+\\) CHARACTER SET ascii COLLATE ascii_bin NOT NULL'));
  }
  assert.match(sql, /`header_name` VARCHAR\(255\) CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL/);
  assert.doesNotMatch(sql, /\b(?:DROP TABLE|TRUNCATE|INSERT INTO|AUTO_INCREMENT)\b/i);
});

test('API Credential review SQL exactly matches the approved current-schema definitions', () => {
  const expectedNames = ['t_api_credential', 't_api_credential_scope'];
  const changeTables = [
    ...apiCredentialChangeSql.matchAll(/CREATE TABLE `(\w+)` \(([\s\S]*?)\) ENGINE=InnoDB([^;]*);/g),
  ];
  assert.deepEqual(changeTables.map(table => table[1]), expectedNames);

  for (const changeTable of changeTables) {
    const schemaTable = tables.find(table => table[1] === changeTable[1]);
    assert.ok(schemaTable, changeTable[1]);
    assert.equal(changeTable[2], schemaTable[2], `${changeTable[1]} body`);
    assert.equal(changeTable[3], schemaTable[3], `${changeTable[1]} options and full table comment`);
  }

  const executableSql = apiCredentialChangeSql.replace(/^--.*$/gm, '');
  assert.doesNotMatch(executableSql, /\bCHECK\s*\(|\bFOREIGN\s+KEY\b/i);
  assert.doesNotMatch(executableSql, /\b(?:DROP|TRUNCATE|INSERT INTO|AUTO_INCREMENT)\b/i);
});
