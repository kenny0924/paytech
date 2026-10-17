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
const sharedInfrastructureTables = ['t_consumed_event'];
// Retained services moved without re-cutting: every CREATE TABLE in the reviewed schema SQL belongs to the service.
const retainedServiceSchemas = [
  {
    service: 'platform-support',
    database: 'mor_platform',
    schema: join(repositoryRoot, 'paytech', 'platform-support', 'docs', 'database', 'sql', 'platform-support-schema.sql'),
  },
];

// Transitional placements (design/03 §1.2 card row, task card 05 §5.5): tables owned by one service that still live in another
// service's database until a later step. They extend the host service's R4 whitelist and the listed modules' R11 exemption,
// but are not counted again in the §4.2 physical table totals.
const transitionalPlacements = [
  {
    host: 'payment',
    database: 'mor_payment',
    owner: 'card',
    untilStep: 12,
    modules: ['paytech-payment-card'],
    note: '第 12 步前位于 mor_payment：卡域代码与卡表仍在 payment 进程与库内（design/05），第 12 步整体迁到 card / mor_card。',
  },
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
const add = (service, database, table) => {
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

const transitionalOutput = [];
for (const placement of transitionalPlacements) {
  const ownerTables = [...(ownership.get(placement.owner)?.values() ?? [])].flatMap((tables) => [...tables])
    .filter((table) => table !== 't_compensation_event' && !sharedInfrastructureTables.includes(table)).sort();
  if (ownerTables.length === 0) fail(`transitional owner ${placement.owner} has no tables`);
  transitionalOutput.push(
    `  # ${placement.note}`,
    `  - host: ${placement.host}`,
    `    database: ${placement.database}`,
    `    owner: ${placement.owner}`,
    `    untilStep: ${placement.untilStep}`,
    `    modules: [${placement.modules.join(', ')}]`,
    '    tables:',
    ...ownerTables.map((table) => `      - ${table}`),
  );
}

const output = [
  '# Generated by scripts/generate-table-ownership.mjs; do not edit by hand.',
  `# Source: ${relative(repositoryRoot, designDocument)} §2 and §4.2.`,
  `# ${sourceRows} source tables, ${actualCounts.size} databases, ${total} physical tables.`,
  ...retainedServiceSchemas.map((retained) => `# Retained: ${retained.service} ${retained.database} from ${relative(repositoryRoot, retained.schema)}.`),
  `# ${retainedTables} retained-service tables added on top of design 03 (not part of the §4.2 count).`,
  '# Structure: services.<service>.<database> = tables owned (or copied / per-database infrastructure);',
  '# transitionalPlacements = owner tables hosted in another service database until a later step (not counted twice).',
  'sharedInfrastructureTables:',
  ...sharedInfrastructureTables.map((table) => `  - ${table}`),
];
output.push('transitionalPlacements:', ...transitionalOutput, 'services:');
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
  process.stdout.write(`table-ownership.yaml is current (${total} physical tables + ${retainedTables} retained).\n`);
} else {
  writeFileSync(outputFile, content);
  process.stdout.write(`wrote ${relative(repositoryRoot, outputFile)} (${total} physical tables + ${retainedTables} retained).\n`);
}

function fail(message) {
  process.stderr.write(`${message}\n`);
  process.exit(1);
}
