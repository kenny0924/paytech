import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';

const service = new URL('../../paytech/platform-support/', import.meta.url);
const model = JSON.parse(readFileSync(new URL('docs/database/platform-support.pdma.json', service)));
const sql = readFileSync(new URL('docs/database/sql/platform-support-schema.sql', service), 'utf8');
const apiCredentialChangeSql = readFileSync(
  new URL('docs/database/sql/changes/2026-09-08-add-api-credential-validation.sql', service),
  'utf8',
);
const tables = [...sql.matchAll(/CREATE TABLE `(\w+)` \(([\s\S]*?)\) ENGINE=InnoDB([^;]*);/g)];
const expectedTables = [
  'notification_request', 'notification_delivery', 'notification_attempt', 'notification_template',
  'notification_template_content', 'notification_provider', 'notification_route',
  'user_account', 'password_credential', 'access_tenant', 'tenant_member', 'role', 'permission',
  'tenant_member_role', 'role_permission', 'auth_session', 'refresh_token_generation', 'jwt_signing_key',
  'api_credential', 'api_scope', 'api_credential_scope',
  'gateway_target', 'gateway_route', 'gateway_route_header', 'gateway_route_query_param', 'gateway_guard_rule',
  'authentication_challenge', 'file_upload', 'file_scan_policy', 'file_scan_requirement', 'file_object', 'file_owner_ref', 'file_scan_result',
  'audit_chain', 'audit_event', 'audit_event_attr', 'security_event', 'command_idempotency', 'compensation_event', 'console_resource',
  'standard_api', 'api_field', 'integration', 'integration_api', 'integration_script', 'integration_standard_api',
  'integration_verify_account', 'integration_verify_case', 'integration_verify_run', 'integration_verify_run_step',
  'webhook_endpoint', 'webhook_signing_secret', 'webhook_event', 'webhook_delivery', 'webhook_attempt',
].map(name => `t_${name}`);
const quote = value => `'${value.replaceAll("'", "''")}'`;

test('only the 55 approved schema tables exist, without physical CHECK or foreign keys', () => {
  assert.deepEqual(tables.map(table => table[1]).sort(), expectedTables.toSorted());
  assert.doesNotMatch(sql.replace(/^--.*$/gm, ''), /\bCHECK\s*\(|\bFOREIGN\s+KEY\b/i);
});

test('every schema column, default and complete comment matches PDManer', () => {
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
      const charset = field.extProps?.characterSet;
      const collation = field.extProps?.collation;
      if (charset) {
        assert.ok(columns[index][2].includes(` CHARACTER SET ${charset} COLLATE ${collation}`), `${name}.${field.defKey} collation`);
      }
      const actual = charset ? columns[index][2].replace(` CHARACTER SET ${charset} COLLATE ${collation}`, '') : columns[index][2];
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

test('historical credential bootstrap plus the approved Scope index matches the current schema', () => {
  const expectedNames = ['t_api_credential', 't_api_credential_scope'];
  const changeTables = [
    ...apiCredentialChangeSql.matchAll(/CREATE TABLE `(\w+)` \(([\s\S]*?)\) ENGINE=InnoDB([^;]*);/g),
  ];
  assert.deepEqual(changeTables.map(table => table[1]), expectedNames);

  for (const changeTable of changeTables) {
    const schemaTable = tables.find(table => table[1] === changeTable[1]);
    assert.ok(schemaTable, changeTable[1]);
    assert.equal(changeTable[2], schemaTable[2].replace(/,\n  KEY `idx_api_scope_credential`[^\n]*/, ''), `${changeTable[1]} body`);
    assert.equal(changeTable[3], schemaTable[3], `${changeTable[1]} options and full table comment`);
  }

  const executableSql = apiCredentialChangeSql.replace(/^--.*$/gm, '');
  assert.doesNotMatch(executableSql, /\bCHECK\s*\(|\bFOREIGN\s+KEY\b/i);
  assert.doesNotMatch(executableSql, /\b(?:DROP|TRUNCATE|INSERT INTO|AUTO_INCREMENT)\b/i);
});


test('Client API increment adds exactly the approved directory and two lookup indexes', () => {
  const increment = readFileSync(new URL('../../requirements/20260913/platform-support-ClientAPI接入管理/db/ddl.sql', import.meta.url), 'utf8');
  const created = [...increment.matchAll(/CREATE TABLE `(\w+)` \(([\s\S]*?)\) ENGINE=InnoDB([^;]*);/g)];
  assert.deepEqual(created.map(t => t[1]), ['t_api_scope']);
  const directory = tables.find(t => t[1] === 't_api_scope');
  assert.equal(created[0][2], directory[2]); assert.equal(created[0][3], directory[3]);
  const added = [...increment.matchAll(/ALTER TABLE `(\w+)`\s+ADD (KEY `\w+` \([^)]*\) COMMENT '[^']*');/g)];
  assert.deepEqual(added.map(i => i[1]).sort(), ['t_api_credential_scope', 't_gateway_route']);
  for (const index of added) assert.ok(tables.find(t => t[1] === index[1])[2].includes(index[2]));
  assert.doesNotMatch(increment.replace(/^--.*$/gm, ''), /\b(?:DROP|DELETE|INSERT|UPDATE|CHECK|FOREIGN)\b/i);
});


test('manual initial catalog and incremental Client API registrations are the same 127 rows', () => {
  const initial = readFileSync(new URL('docs/database/sql/platform-support-initial-data.sql', service), 'utf8');
  const increment = readFileSync(new URL('../../requirements/20260913/platform-support-ClientAPI接入管理/db/dml.sql', import.meta.url), 'utf8');
  const statements = [...increment.matchAll(/INSERT INTO t_\w+[^;]+;/g)].map(m => m[0]);
  assert.equal(statements.length, 7);
  const variables = { '@plt_api_time': '@platform_initial_time', '@plt_api_operator': '@platform_initial_actor_id', '@plt_api_target': '900000000000000001', '@plt_api_admin': '900000000000000510', '@plt_api_parent': '900000000000000562', '@plt_api_gateway_read': '900000000000000534', '@plt_api_route_list': '900000000000001021', '@plt_api_audit_list': '900000000000001009' };
  for (const statement of statements) {
    const normalized = Object.entries(variables).reduce((sql, [key, value]) => sql.replaceAll(key, value), statement);
    assert.ok(initial.includes(normalized));
  }
  assert.equal(statements.reduce((count, statement) => count + [...statement.matchAll(/^  \(9000000000000/gm)].length, 0), 127);
  assert.equal((initial.replace(/^--.*$/gm, '').match(/START TRANSACTION;/g) || []).length, 1);
  assert.equal((initial.replace(/^--.*$/gm, '').match(/COMMIT;/g) || []).length, 1);
  assert.doesNotMatch(statements.join('\n'), /INSERT INTO t_(?:api_scope|api_credential|access_tenant)\s/);
});


test('Notification increment is the exact seven-table current model and test DDL agrees', () => {
  const increment = readFileSync(new URL('../../requirements/20260920/platform-support-Delivery领域规划/db/notification-create.sql', import.meta.url), 'utf8');
  const testDdl = readFileSync(new URL('paytech-platform-support-test/src/test/resources/init/ddl.sql', service), 'utf8');
  const created = [...increment.matchAll(/CREATE TABLE `(\w+)` \(([\s\S]*?)\) ENGINE=InnoDB([^;]*);/g)];
  assert.deepEqual(created.map(t => t[1]), ['t_notification_request', 't_notification_delivery', 't_notification_attempt',
    't_notification_template', 't_notification_template_content', 't_notification_provider', 't_notification_route']);
  for (const table of created) {
    assert.equal(table[0], tables.find(t => t[1] === table[1])[0]);
    assert.ok(testDdl.includes(table[0]));
  }
  assert.doesNotMatch(increment.replace(/^--.*$/gm, ''), /\b(?:DROP|ALTER|DELETE|INSERT|UPDATE|CHECK|FOREIGN)\b/i);
});

test('External API configuration increment is the exact six-table current model', () => {
  const increment = readFileSync(new URL('../../requirements/20260914/platform-support-外部API配置与执行/db/platform-support-service/integration-create.sql', import.meta.url), 'utf8');
  const created = [...increment.matchAll(/CREATE TABLE `(\w+)` \(([\s\S]*?)\) ENGINE=InnoDB([^;]*);/g)];
  assert.deepEqual(created.map(t => t[1]), ['t_standard_api', 't_api_field', 't_integration', 't_integration_api',
    't_integration_script', 't_integration_standard_api']);
  // 2026-10-14 沙箱验证增量以 MODIFY COLUMN 更新了 t_integration.endpoints 的注释，其余内容仍须与当前 Schema 逐字一致。
  const withoutEndpointsComment = text => text.replace(/^  `endpoints` JSON NOT NULL COMMENT '(?:[^']|'')*',$/m, '');
  for (const table of created) {
    assert.equal(withoutEndpointsComment(table[0]), withoutEndpointsComment(tables.find(t => t[1] === table[1])[0]));
  }
  assert.doesNotMatch(increment.replace(/^--.*$/gm, ''), /\b(?:DROP|ALTER|DELETE|INSERT|UPDATE|CHECK|FOREIGN)\b/i);
});

test('Channel sandbox verification increment is the exact four-table current model plus the endpoints comment', () => {
  const increment = readFileSync(new URL('../../requirements/20261014/platform-support-渠道沙箱验证/db/ddl.sql', import.meta.url), 'utf8');
  const created = [...increment.matchAll(/CREATE TABLE `(\w+)` \(([\s\S]*?)\) ENGINE=InnoDB([^;]*);/g)];
  assert.deepEqual(created.map(t => t[1]), ['t_integration_verify_account', 't_integration_verify_case',
    't_integration_verify_run', 't_integration_verify_run_step']);
  for (const table of created) assert.equal(table[0], tables.find(t => t[1] === table[1])[0]);
  const endpoints = model.entities.find(e => e.defKey === 't_integration').fields.find(f => f.defKey === 'endpoints');
  assert.ok(increment.includes(`ALTER TABLE \`t_integration\` MODIFY COLUMN \`endpoints\` JSON NOT NULL COMMENT ${quote(endpoints.comment)};`));
  assert.doesNotMatch(increment.replace(/^--.*$/gm, '').replace(/^ALTER TABLE `t_integration` MODIFY COLUMN `endpoints`.*$/m, ''),
    /\b(?:DROP|ALTER|DELETE|INSERT|UPDATE|CHECK|FOREIGN)\b/i);
});

test('Client Webhook increment is the exact five-table current model and test DDL agrees', () => {
  const increment = readFileSync(new URL('docs/database/sql/changes/2026-10-15-webhook-delivery.sql', service), 'utf8');
  const testDdl = readFileSync(new URL('paytech-platform-support-test/src/test/resources/init/ddl.sql', service), 'utf8');
  const created = [...increment.matchAll(/CREATE TABLE `(\w+)` \(([\s\S]*?)\) ENGINE=InnoDB([^;]*);/g)];
  assert.deepEqual(created.map(t => t[1]), ['t_webhook_endpoint', 't_webhook_signing_secret', 't_webhook_event',
    't_webhook_delivery', 't_webhook_attempt']);
  for (const table of created) {
    assert.equal(table[0], tables.find(t => t[1] === table[1])[0]);
    assert.ok(testDdl.includes(table[0]));
  }
  assert.doesNotMatch(increment.replace(/^--.*$/gm, ''), /\b(?:DROP|ALTER|DELETE|INSERT|UPDATE|CHECK|FOREIGN)\b/i);
});
