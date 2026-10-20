#!/usr/bin/env node
// Generates src/main/resources/table-ownership.yaml from the design 03 ownership tables.
//
// Usage: node scripts/generate-table-ownership.mjs [--check]
//   --check  fail when the committed YAML differs from what the design document produces.
//
// Source: requirements/20261017/architecture-V2骨架与模块搬迁/design/03-module-and-table-ownership.md
//   section 2 (one row per source table) and section 4.2 (physical table count per target database).
// Retained services (design 03 §1.2 "业务不动") are not listed table by table in design 03; their tables come from
//   the service's own reviewed schema SQL and are excluded from the §4.2 count check.
import { readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const moduleRoot = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const repositoryRoot = resolve(moduleRoot, '..', '..', '..');
const designDocument = join(
  repositoryRoot, 'requirements', '20261017', 'architecture-V2骨架与模块搬迁', 'design',
  '03-module-and-table-ownership.md',
);
const outputFile = join(moduleRoot, 'src', 'main', 'resources', 'table-ownership.yaml');
const checkOnly = process.argv.includes('--check');

const financeSuccessors = ['ledger', 'account', 'billing', 'reconciliation', 'tax', 'payout'];
// Tables every service database owns although design 03 does not list them per database (01 §3 / 08).
const sharedInfrastructureTables = ['t_consumed_event', 't_consumed_aggregate_version'];
// Retained services moved without re-cutting: every CREATE TABLE in the reviewed schema SQL belongs to the service.
const retainedServiceSchemas = [
  {
    service: 'platform-support',
    database: 'mor_platform',
    schema: join(repositoryRoot, 'paytech', 'platform-support', 'docs', 'database', 'sql', 'platform-support-schema.sql'),
  },
];

// Physical renames executed by a migration step; design 03 lists the source names (step 07, design/04 §2-§3).
const renamedTables = new Map([
  ['t_transaction_economics', 't_economic_fact'],
  ['t_transaction_economic_component', 't_economic_fact_line'],
  ['t_transaction_economic_comp_link', 't_economic_fact_line_link'],
  // Step 11b (design/04 §3.1): the ledger posting account keeps identity only; balances move to mor_account.
  ['t_account', 't_ledger_account'],
  // 2026-10-19 commercial round 2 (design-discuss/commercial/database.md §8): the profile tables become pricing profiles.
  ['t_payment_method_profile', 't_pricing_profile'],
  ['t_payment_method_profile_cond', 't_pricing_profile_cond'],
]);
// Shared-database transition reads (not owned, not counted). Step 07 let account read mor_ledger; step 11b gives account
// its own mor_account (design/04 §3.2, §6) and removes that allowance, so no transition is registered now.
const transitionalSharedReads = [];
// Tables a later approved refactor removes from a service after design 03 §2 (applied after the §4.2 count check).
// 2026-10-19 billing fact model refactor (decision 2026-10-19-billing-fact-engine, REQ technique 2.3): the economic fact
// tables are dropped for t_billing_fact*, and the four channel cost tables move from billing to channel-connector.
const stepRemovedTables = [
  {
    service: 'billing',
    database: 'mor_billing',
    tables: ['t_economic_fact', 't_economic_fact_line', 't_economic_fact_line_link', 't_channel_cost_plan',
      't_channel_cost_plan_ver', 't_channel_cost_scope', 't_channel_cost_component'],
  },
];
// Tables introduced by a refactor step's approved design on top of design 03 §2 (not part of the §4.2 count).
const stepAddedTables = [
  // 2026-10-19 commercial six-layer product pricing model (decision 2026-10-19-commercial-six-layer-product-pricing,
  // design-discuss/commercial/database.md §2): product catalog and agreement subscription / capability tables.
  {
    service: 'commercial',
    database: 'mor_commercial',
    tables: ['t_product', 't_product_capability', 't_product_fee_event', 't_agreement_product',
      't_agreement_capability', 't_agreement_capability_limit'],
  },
  // Step 12 (design/05 §2.1, S3): one-time card material tickets in mor_card.
  { service: 'card', database: 'mor_card', tables: ['t_card_material_ticket'] },
  // Step 11b (design/04 §3.2) reshaped by the 2026-10-19 subledger refactor (decision 2026-10-18, technique 2.3):
  // account is the Owner subledger in mor_account - Owner accounts, posting streams, postings, entries, applied facts and
  // bucket movements. t_account_balance, t_balance_applied_entry and t_balance_discrepancy are dropped.
  // 2026-10-19 chart hierarchy (decision 2026-10-19 item 7): t_owner_account_no_sequence allocates readable account numbers.
  {
    service: 'account',
    database: 'mor_account',
    tables: ['t_owner_account', 't_account_posting_stream', 't_account_posting', 't_account_entry',
      't_balance_applied_fact', 't_balance_movement', 't_owner_account_no_sequence'],
  },
  // 2026-10-19 billing fact model refactor (REQ technique 2.3): mor_billing holds 11 tables - five billing fact tables,
  // the business calendar copy, the Foundation consume-idempotency pair, t_currency_spec and t_compensation_event.
  {
    service: 'billing',
    database: 'mor_billing',
    tables: ['t_billing_fact', 't_billing_fact_ref', 't_billing_fact_pricing', 't_billing_fact_line',
      't_billing_fact_line_link', 't_business_calendar', 't_business_calendar_day', 't_consumed_event',
      't_consumed_aggregate_version'],
  },
  // Same refactor: the channel cost plan, version, scope and component tables are owned by channel-connector (17 tables).
  {
    service: 'channel-connector',
    database: 'mor_channel_connector',
    tables: ['t_channel_cost_plan', 't_channel_cost_plan_ver', 't_channel_cost_scope', 't_channel_cost_component'],
  },
  // 2026-10-19 chart hierarchy (decision 2026-10-19 items 5 and 7): immutable chart account change records and the
  // per book and chart account sequence of readable ledger account numbers in mor_ledger.
  { service: 'ledger', database: 'mor_ledger', tables: ['t_chart_account_change', 't_account_no_sequence'] },
];

const lines = readFileSync(designDocument, 'utf8').split('\n');
const sectionStart = lines.findIndex((line) => line.startsWith('## 2. '));
const sectionEnd = lines.findIndex((line) => line.startsWith('## 3. '));
const countStart = lines.findIndex((line) => line.startsWith('### 4.2 '));
const countEnd = lines.findIndex((line) => line.startsWith('### 4.3 '));
if ([sectionStart, sectionEnd, countStart, countEnd].some((index) => index < 0)) {
  fail('design 03 section headings for §2 / §3 / §4.2 / §4.3 were not found');
}

const cells = (line) => line.split('|').slice(1, -1).map((cell) => cell.trim());
const databaseOf = (service) => `mor_${service.replaceAll('-', '_')}`;
const ownership = new Map();
const add = (service, database, sourceTable) => {
  const table = renamedTables.get(sourceTable) ?? sourceTable;
  if (!ownership.has(service)) ownership.set(service, new Map());
  const databases = ownership.get(service);
  if (!databases.has(database)) databases.set(database, new Set());
  databases.get(database).add(table);
};
const splitServices = (text) => text
  .replace('全部六个 Finance 后继服务', financeSuccessors.join('、'))
  .split(/、| 与 /u)
  .map((service) => service.replace(/（.*?）/gu, '').trim())
  .filter(Boolean);

let sourceRows = 0;
for (const line of lines.slice(sectionStart, sectionEnd)) {
  if (!line.startsWith('| t_')) continue;
  sourceRows += 1;
  const [table, serviceCell, databaseCell] = cells(line);
  if (serviceCell === '—') continue; // deleted table
  if (databaseCell === '每库一份' || databaseCell === '两边各一份') {
    for (const service of splitServices(serviceCell)) add(service, databaseOf(service), table);
    continue;
  }
  if (table === 't_currency_spec') {
    // Owner platform-support; read-only copies in mor_ledger and mor_billing (design 03 §3).
    add('platform-support', 'mor_platform', table);
    add('ledger', 'mor_ledger', table);
    add('billing', 'mor_billing', table);
    continue;
  }
  const service = serviceCell.replace(/（.*$/u, '').trim();
  const database = databaseCell.split('；')[0].trim();
  if (!/^[a-z][a-z-]*$/u.test(service) || !/^mor_[a-z_]+$/u.test(database)) {
    fail(`unsupported ownership row, extend the generator: ${line}`);
  }
  add(service, database, table);
}

const expectedCounts = new Map();
for (const line of lines.slice(countStart, countEnd)) {
  const row = cells(line);
  if (!line.startsWith('| mor_') || row.length !== 4) continue;
  expectedCounts.set(row[0], Number(row[3]));
}
const actualCounts = new Map();
for (const databases of ownership.values()) {
  for (const [database, tables] of databases) {
    actualCounts.set(database, (actualCounts.get(database) ?? 0) + tables.size);
  }
}
const mismatches = [...new Set([...expectedCounts.keys(), ...actualCounts.keys()])]
  .filter((database) => expectedCounts.get(database) !== actualCounts.get(database))
  .map((database) => `${database}: design ${expectedCounts.get(database)}, generated ${actualCounts.get(database)}`);
if (sourceRows !== 255) fail(`design 03 §2 should list 255 source tables, found ${sourceRows}`);
if (mismatches.length > 0) fail(`per-database table counts differ from design 03 §4.2:\n${mismatches.join('\n')}`);
const total = [...actualCounts.values()].reduce((sum, count) => sum + count, 0);

let retainedTables = 0;
for (const retained of retainedServiceSchemas) {
  const tables = [...readFileSync(retained.schema, 'utf8').matchAll(/^CREATE TABLE `(t_[a-z0-9_]+)`/gmu)]
    .map((match) => match[1]);
  if (tables.length === 0) fail(`no CREATE TABLE found in ${relative(repositoryRoot, retained.schema)}`);
  const before = ownership.get(retained.service)?.get(retained.database)?.size ?? 0;
  for (const table of tables) add(retained.service, retained.database, table);
  retainedTables += ownership.get(retained.service).get(retained.database).size - before;
}

let stepRemoved = 0;
for (const removed of stepRemovedTables) {
  const tables = ownership.get(removed.service)?.get(removed.database);
  for (const table of removed.tables) {
    if (!tables?.delete(table)) fail(`step-removed table not listed: ${removed.service} ${removed.database} ${table}`);
    stepRemoved += 1;
  }
}

let stepAdded = 0;
for (const added of stepAddedTables) {
  for (const table of added.tables) {
    if (ownership.get(added.service)?.get(added.database)?.has(table)) fail(`step-added table already listed: ${table}`);
    add(added.service, added.database, table);
    stepAdded += 1;
  }
}

const output = [
  '# Generated by scripts/generate-table-ownership.mjs; do not edit by hand.',
  `# Source: ${relative(repositoryRoot, designDocument)} §2 and §4.2.`,
  `# ${sourceRows} source tables, ${actualCounts.size} databases, ${total} physical tables.`,
  ...retainedServiceSchemas.map((retained) => `# Retained: ${retained.service} ${retained.database} from ${relative(repositoryRoot, retained.schema)}.`),
  `# ${retainedTables} retained-service tables added on top of design 03 (not part of the §4.2 count).`,
  `# ${stepRemoved} step-removed tables (approved later refactors, e.g. 2026-10-19 billing fact model; applied after the §4.2 count).`,
  `# ${stepAdded} step-added tables (approved refactor-step designs, e.g. design/05 t_card_material_ticket; not part of the §4.2 count).`,
  '# Structure: services.<service>.<database> = tables owned (or copied / per-database infrastructure);',
  `# Renamed: ${[...renamedTables].map(([from, to]) => `${from} -> ${to}`).join(', ')}.`,
  'sharedInfrastructureTables:',
  ...sharedInfrastructureTables.map((table) => `  - ${table}`),
  '# Shared-database transition reads (not owned, not counted); removed when the service gets its own database.',
  transitionalSharedReads.length === 0 ? 'transitionalSharedReads: {}' : 'transitionalSharedReads:',
  ...transitionalSharedReads.flatMap(({ service, database, tables }) => [
    `  ${service}:`,
    `    ${database}:`,
    ...tables.map((table) => `      - ${table}`),
  ]),
];
output.push('services:');
for (const service of [...ownership.keys()].sort()) {
  output.push(`  ${service}:`);
  for (const [database, tables] of [...ownership.get(service)].sort(([a], [b]) => a.localeCompare(b))) {
    output.push(`    ${database}:`);
    for (const table of [...tables].sort()) output.push(`      - ${table}`);
  }
}
const content = `${output.join('\n')}\n`;

if (checkOnly) {
  const committed = readFileSync(outputFile, 'utf8');
  if (committed !== content) fail(`${relative(repositoryRoot, outputFile)} is stale; rerun the generator`);
  process.stdout.write(`table-ownership.yaml is current (${total} physical tables + ${retainedTables} retained - ${stepRemoved} step-removed + ${stepAdded} step-added).\n`);
} else {
  writeFileSync(outputFile, content);
  process.stdout.write(`wrote ${relative(repositoryRoot, outputFile)} (${total} physical tables + ${retainedTables} retained - ${stepRemoved} step-removed + ${stepAdded} step-added).\n`);
}

function fail(message) {
  process.stderr.write(`${message}\n`);
  process.exit(1);
}
