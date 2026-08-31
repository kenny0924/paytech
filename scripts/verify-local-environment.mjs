import { readFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(scriptDirectory, '..');
const environmentRoot = join(repositoryRoot, 'local-environment');
const composeFile = join(environmentRoot, 'compose.yaml');
const schedulerComposeFile = join(environmentRoot, 'compose.scheduler.yaml');
const exampleEnvironment = join(environmentRoot, '.env.example');
const mysqlInitScript = join(
  environmentRoot,
  'mysql',
  'init',
  '01-create-databases-and-users.sh',
);
const xxlJobSchemaFile = join(
  environmentRoot,
  'mysql',
  'init',
  '03-xxl-job-schema.sql',
);
const xxlJobSchemaVersionFile = join(
  environmentRoot,
  'mysql',
  'init',
  '04-xxl-job-schema-version.sql',
);
const redisPasswordEnvironmentVariable = 'PAYTECH_REDIS_PASSWORD';
const retiredRedisPasswordEnvironmentVariable = ['MOR', 'REDIS', 'PASSWORD'].join('_');
const expectedEnvironmentVariables = new Map([
  ['MOR_MYSQL_ROOT_PASSWORD', 'local-root-change-me-2026'],
  ['MOR_CCT_DB_PASSWORD', 'local-cct-change-me-2026'],
  ['MOR_COMMERCE_DB_PASSWORD', 'local-commerce-change-me-2026'],
  ['MOR_PAYMENT_DB_PASSWORD', 'local-payment-change-me-2026'],
  ['MOR_RISK_DB_PASSWORD', 'local-risk-change-me-2026'],
  ['MOR_FINANCE_DB_PASSWORD', 'local-finance-change-me-2026'],
  ['MOR_PLATFORM_DB_PASSWORD', 'local-platform-change-me-2026'],
  ['MOR_VAULT_DB_PASSWORD', 'local-vault-change-me-2026'],
  ['MOR_NACOS_DB_PASSWORD', 'local-nacos-db-change-me-2026'],
  ['MOR_XXL_JOB_DB_PASSWORD', 'local-xxl-job-change-me-2026'],
  ['PAYTECH_FOUNDATION_TEST_DB_PASSWORD', 'local-foundation-test-change-me-2026'],
  [redisPasswordEnvironmentVariable, 'local-redis-change-me-2026'],
  ['MOR_RABBITMQ_USER', 'mor_local'],
  ['MOR_RABBITMQ_PASSWORD', 'local-rabbit-change-me-2026'],
  ['MOR_RABBITMQ_VHOST', '/mor-local'],
  ['MOR_NACOS_AUTH_TOKEN', 'TW9STG9jYWxBdXRoVG9rZW5DaGFuZ2VNZTIwMjYhISEh'],
  ['MOR_NACOS_AUTH_IDENTITY_KEY', 'mor-local-server'],
  ['MOR_NACOS_AUTH_IDENTITY_VALUE', 'local-identity-change-me-2026'],
  ['MOR_XXL_JOB_ACCESS_TOKEN', 'local-xxl-job-access-token-change-me-2026'],
  ['MOR_XXL_JOB_LOGIN_TOKEN_KEY', 'local-xxl-job-login-token-key-change-me-2026'],
]);
const activeGatewayContractFiles = [
  'agent-docs/TECHNOLOGY_STACK.md',
  'agent-docs/JAVA_PROJECT_STRUCTURE.md',
  'agent-docs/UNIT_TEST_GUIDE.md',
  'agent-docs/LOCAL_DEVELOPMENT_ENVIRONMENT.md',
  'agent-docs/plans/2026-08-29-operations-api-gateway-design.md',
  'agent-docs/plans/2026-08-29-platform-support-operations-authentication-requirements.md',
];
const gatewayDesignFile = join(
  repositoryRoot,
  'agent-docs',
  'plans',
  '2026-08-29-operations-api-gateway-design.md',
);
const legacyGatewayHeaderPrefix = ['X', 'MoR'].join('-');
const legacyRefreshCookieName = ['__Secure', 'mor', 'operations', 'refresh'].join('-');
const guardTierName = ['Guard', 'Tier'].join('');
const forwardedWildcardName = ['X', 'Forwarded', '*'].join('-');

const errors = [];

function parseEnvironmentTemplate(content) {
  const declarations = new Map();
  for (const line of content.split(/\r?\n/u)) {
    if (!line || line.startsWith('#')) {
      continue;
    }
    const match = line.match(/^([A-Z][A-Z0-9_]*)=(.*)$/u);
    if (!match) {
      errors.push('environment example contains an invalid declaration');
      continue;
    }
    if (declarations.has(match[1])) {
      errors.push(`environment example declares ${match[1]} more than once`);
      continue;
    }
    declarations.set(match[1], match[2]);
  }
  return declarations;
}

function section(markdown, heading) {
  const headingMarker = `## ${heading}\n`;
  const start = markdown.indexOf(headingMarker);
  if (start < 0) {
    errors.push(`Gateway design is missing section: ${heading}`);
    return '';
  }
  const contentStart = start + headingMarker.length;
  const end = markdown.indexOf('\n## ', contentStart);
  return markdown.slice(contentStart, end < 0 ? undefined : end);
}

function tableRows(markdown, expectedHeader) {
  const header = `| ${expectedHeader.join(' | ')} |`;
  const lines = markdown.split('\n');
  const headerIndex = lines.indexOf(header);
  if (headerIndex < 0 || !/^\|(?:\s*:?-+:?\s*\|)+$/u.test(lines[headerIndex + 1] ?? '')) {
    return null;
  }
  const rows = [];
  for (const line of lines.slice(headerIndex + 2)) {
    if (!line.startsWith('|')) {
      break;
    }
    rows.push(line.split('|').slice(1, -1).map((cell) => cell.trim()));
  }
  return rows;
}

function requireExactTable(name, markdown, header, expectedRows) {
  const rows = tableRows(markdown, header);
  if (rows === null || JSON.stringify(rows) !== JSON.stringify(expectedRows)) {
    errors.push(`Gateway design ${name} table drifted`);
  }
}

function requireSectionStatements(name, markdown, statements) {
  for (const statement of statements) {
    if (!markdown.includes(statement)) {
      errors.push(`Gateway design ${name} is missing a required statement`);
    }
  }
}
const composeResult = spawnSync(
  'docker',
  [
    'compose',
    '--env-file',
    exampleEnvironment,
    '-f',
    composeFile,
    '--profile',
    'base',
    'config',
    '--format',
    'json',
  ],
  { cwd: repositoryRoot, encoding: 'utf8' },
);

if (composeResult.status !== 0) {
  errors.push(`Docker Compose model is invalid:\n${composeResult.stderr}`);
} else {
  const model = JSON.parse(composeResult.stdout);
  const expectedImages = {
    mysql: 'mysql:8.4.11',
    nacos: 'nacos/nacos-server:v3.1.2-slim',
    rabbitmq: 'rabbitmq:4.2.9-management',
    redis: 'redis:8.2.1-alpine',
  };
  const expectedPorts = {
    mysql: ['127.0.0.1:13306:3306'],
    nacos: [
      '127.0.0.1:18088:8080',
      '127.0.0.1:8848:8848',
      '127.0.0.1:9848:9848',
    ],
    rabbitmq: [
      '127.0.0.1:15672:15672',
      '127.0.0.1:5672:5672',
    ],
    redis: ['127.0.0.1:16379:6379'],
  };
  const modelServices = model.services ?? {};
  const actualServices = Object.keys(modelServices).sort();
  const expectedServices = Object.keys(expectedImages).sort();

  if (model.name !== 'mor-local') {
    errors.push(`unexpected Compose project name: ${model.name}`);
  }
  if (JSON.stringify(actualServices) !== JSON.stringify(expectedServices)) {
    errors.push(`unexpected base services: ${actualServices.join(', ')}`);
  }

  for (const [serviceName, expectedImage] of Object.entries(expectedImages)) {
    const service = modelServices[serviceName];
    if (!service) {
      continue;
    }
    if (service.image !== expectedImage) {
      errors.push(`${serviceName} image drifted: ${service.image}`);
    }
    if (service.restart !== 'no') {
      errors.push(`${serviceName} must not restart automatically`);
    }
    if (JSON.stringify(service.profiles) !== JSON.stringify(['base'])) {
      errors.push(`${serviceName} must belong only to the base profile`);
    }
    if (service.container_name) {
      errors.push(`${serviceName} must not define a fixed container name`);
    }
    const actualPorts = (service.ports ?? [])
      .map((port) => `${port.host_ip}:${port.published}:${port.target}`)
      .sort();
    if (JSON.stringify(actualPorts) !== JSON.stringify(expectedPorts[serviceName].sort())) {
      errors.push(`${serviceName} has unexpected port bindings: ${actualPorts.join(', ')}`);
    }
  }

  const nacosReadinessUrl =
    'http://127.0.0.1:8848/nacos/v3/admin/core/state/readiness';
  const nacosHealthcheck = modelServices.nacos?.healthcheck?.test ?? [];
  if (!nacosHealthcheck.join(' ').includes(nacosReadinessUrl)) {
    errors.push(`Nacos health check must use the v3 server readiness API: ${nacosHealthcheck.join(' ')}`);
  }

  const redis = modelServices.redis;
  const redisEnvironment = redis?.environment ?? {};
  if (JSON.stringify(Object.keys(redisEnvironment).sort())
      !== JSON.stringify([redisPasswordEnvironmentVariable, 'TZ'].sort())) {
    errors.push('Redis Compose environment keys drifted');
  }
  const escapedRedisPasswordVariable = `$\${${redisPasswordEnvironmentVariable}}`;
  const expectedRedisCommand = [
    '/bin/sh',
    '-c',
    `exec redis-server --appendonly yes --appendfsync everysec --requirepass "${escapedRedisPasswordVariable}"`,
  ];
  const expectedRedisHealthcheck = [
    'CMD-SHELL',
    `redis-cli --no-auth-warning -a "${escapedRedisPasswordVariable}" ping | grep PONG`,
  ];
  if (JSON.stringify(redis?.command) !== JSON.stringify(expectedRedisCommand)) {
    errors.push('resolved Redis command must use only the new password variable');
  }
  if (JSON.stringify(redis?.healthcheck?.test) !== JSON.stringify(expectedRedisHealthcheck)) {
    errors.push('resolved Redis health check must use only the new password variable');
  }

  const expectedVolumes = [
    'mor-local-mysql-data',
    'mor-local-rabbitmq-data',
    'mor-local-redis-data',
  ];
  const actualVolumes = Object.values(model.volumes ?? {}).map((volume) => volume.name).sort();
  if (JSON.stringify(actualVolumes) !== JSON.stringify(expectedVolumes.sort())) {
    errors.push(`unexpected named volumes: ${actualVolumes.join(', ')}`);
  }
  if (model.networks?.mor?.name !== 'mor-local-network') {
    errors.push('Compose mor network drifted');
  }
  for (const service of Object.values(modelServices)) {
    if (JSON.stringify(Object.keys(service.networks ?? {}).sort()) !== JSON.stringify(['mor'])) {
      errors.push('base service network membership drifted');
      break;
    }
  }
}

const schedulerComposeResult = spawnSync(
  'docker',
  [
    'compose',
    '--env-file',
    exampleEnvironment,
    '-f',
    composeFile,
    '-f',
    schedulerComposeFile,
    '--profile',
    'base',
    '--profile',
    'scheduler',
    'config',
    '--format',
    'json',
  ],
  { cwd: repositoryRoot, encoding: 'utf8' },
);

if (schedulerComposeResult.status !== 0) {
  errors.push(`Docker scheduler Compose model is invalid:\n${schedulerComposeResult.stderr}`);
} else {
  const model = JSON.parse(schedulerComposeResult.stdout);
  const expectedServices = [
    'mysql',
    'nacos',
    'rabbitmq',
    'redis',
    'xxl-job-admin',
    'xxl-job-schema-init',
  ];
  const actualServices = Object.keys(model.services ?? {}).sort();
  if (JSON.stringify(actualServices) !== JSON.stringify(expectedServices.sort())) {
    errors.push(`unexpected base+scheduler services: ${actualServices.join(', ')}`);
  }

  const admin = model.services?.['xxl-job-admin'];
  if (!admin) {
    errors.push('xxl-job-admin is missing from the scheduler profile');
  } else {
    if (admin.image !== 'xuxueli/xxl-job-admin:3.4.2') {
      errors.push(`xxl-job-admin image drifted: ${admin.image}`);
    }
    if (admin.platform !== 'linux/amd64') {
      errors.push(`xxl-job-admin must declare linux/amd64: ${admin.platform}`);
    }
    if (admin.restart !== 'no') {
      errors.push('xxl-job-admin must not restart automatically');
    }
    if (admin.stop_grace_period !== '30s') {
      errors.push(`xxl-job-admin stop grace period drifted: ${admin.stop_grace_period}`);
    }
    if (JSON.stringify(admin.profiles) !== JSON.stringify(['scheduler'])) {
      errors.push('xxl-job-admin must belong only to the scheduler profile');
    }
    if (admin.container_name) {
      errors.push('xxl-job-admin must not define a fixed container name');
    }
    const ports = (admin.ports ?? [])
      .map((port) => `${port.host_ip}:${port.published}:${port.target}`);
    if (JSON.stringify(ports) !== JSON.stringify(['127.0.0.1:18090:8080'])) {
      errors.push(`xxl-job-admin has unexpected port bindings: ${ports.join(', ')}`);
    }
    const params = admin.environment?.PARAMS ?? '';
    for (const requiredParameter of [
      'jdbc:mysql://mysql:3306/xxl_job',
      '--spring.datasource.username=xxl_job',
      '--spring.datasource.password=',
      '--xxl.job.accessToken=',
      '--xxl-sso.token.key=',
    ]) {
      if (!params.includes(requiredParameter)) {
        errors.push(`xxl-job-admin PARAMS is missing: ${requiredParameter}`);
      }
    }
    const adminDependencies = admin.depends_on ?? {};
    if (adminDependencies.mysql?.condition !== 'service_healthy') {
      errors.push('xxl-job-admin must wait for healthy MySQL');
    }
    if (adminDependencies['xxl-job-schema-init']?.condition !== 'service_completed_successfully') {
      errors.push('xxl-job-admin must wait for successful XXL-Job schema initialization');
    }
    if (!(admin.entrypoint ?? []).join(' ').includes('exec java')) {
      errors.push('xxl-job-admin entrypoint must exec Java for graceful shutdown');
    }
    if (!(admin.healthcheck?.test ?? []).join(' ').includes('/dev/tcp/127.0.0.1/8080')) {
      errors.push('xxl-job-admin health check must probe its local TCP port');
    }
    if (admin.mem_limit !== '536870912') {
      errors.push(`xxl-job-admin memory limit drifted: ${admin.mem_limit}`);
    }
    if (!Object.hasOwn(admin.networks ?? {}, 'mor')) {
      errors.push('xxl-job-admin must use the isolated mor network');
    }
    const logVolume = (admin.volumes ?? []).find((volume) => volume.target === '/data/applogs');
    if (logVolume?.source !== 'xxl-job-admin-logs') {
      errors.push(`xxl-job-admin log volume drifted: ${logVolume?.source}`);
    }
    if (model.volumes?.['xxl-job-admin-logs']?.name !== 'mor-local-xxl-job-admin-logs') {
      errors.push('xxl-job-admin named log volume is missing or renamed');
    }
  }

  const schemaInit = model.services?.['xxl-job-schema-init'];
  if (!schemaInit) {
    errors.push('xxl-job-schema-init is missing from the scheduler profile');
  } else {
    if (schemaInit.image !== 'mysql:8.4.11') {
      errors.push(`xxl-job-schema-init image drifted: ${schemaInit.image}`);
    }
    if (JSON.stringify(schemaInit.profiles) !== JSON.stringify(['scheduler'])) {
      errors.push('xxl-job-schema-init must belong only to the scheduler profile');
    }
    if (schemaInit.depends_on?.mysql?.condition !== 'service_healthy') {
      errors.push('xxl-job-schema-init must wait for healthy MySQL');
    }
    if ((schemaInit.ports ?? []).length > 0) {
      errors.push('xxl-job-schema-init must not expose a host port');
    }
    const command = (schemaInit.command ?? []).join(' ');
    if (!command.includes('03-xxl-job-schema.sql')
        || !command.includes('04-xxl-job-schema-version.sql')
        || !command.includes('expected 0 or 8 vendor tables')) {
      errors.push('xxl-job-schema-init does not enforce the complete versioned schema');
    }
  }
}

const shellResult = spawnSync('bash', ['-n', mysqlInitScript], {
  cwd: repositoryRoot,
  encoding: 'utf8',
});
if (shellResult.status !== 0) {
  errors.push(`MySQL initialization script is invalid:\n${shellResult.stderr}`);
}

const mysqlInit = readFileSync(mysqlInitScript, 'utf8');
const nacosSchema = readFileSync(
  join(environmentRoot, 'mysql', 'init', '02-nacos-schema.sql'),
  'utf8',
);
const xxlJobSchema = readFileSync(xxlJobSchemaFile, 'utf8');
const xxlJobSchemaVersion = readFileSync(xxlJobSchemaVersionFile, 'utf8');
const databases = [
  'mor_cct',
  'mor_commerce',
  'mor_payment',
  'mor_risk',
  'mor_finance',
  'mor_platform',
  'mor_vault',
  'nacos_config',
  'xxl_job',
  'paytech_foundation_test',
];
for (const database of databases) {
  if (!mysqlInit.includes(`CREATE DATABASE IF NOT EXISTS ${database} `)) {
    errors.push(`missing isolated MySQL database: ${database}`);
  }
}
const databaseDeclarationCount = mysqlInit.match(/^CREATE DATABASE IF NOT EXISTS /gmu)?.length ?? 0;
if (databaseDeclarationCount !== databases.length) {
  errors.push(`unexpected MySQL database declaration count: ${databaseDeclarationCount}`);
}
const runtimeNamespaceFiles = [
  composeFile,
  schedulerComposeFile,
  exampleEnvironment,
  mysqlInitScript,
  xxlJobSchemaFile,
  xxlJobSchemaVersionFile,
];
for (const file of runtimeNamespaceFiles) {
  const content = readFileSync(file, 'utf8');
  if (/(?:code-mor-local|code_mor_|CODE_MOR_)/u.test(content)) {
    errors.push(`retired local runtime namespace remains: ${file}`);
  }
}
const environmentTemplate = readFileSync(exampleEnvironment, 'utf8');
const environmentDeclarations = parseEnvironmentTemplate(environmentTemplate);
if (JSON.stringify([...environmentDeclarations.keys()])
    !== JSON.stringify([...expectedEnvironmentVariables.keys()])) {
  errors.push('environment example declaration inventory drifted');
}
for (const [name, expectedValue] of expectedEnvironmentVariables) {
  if (environmentDeclarations.get(name) !== expectedValue) {
    errors.push(`environment example declaration drifted: ${name}`);
  }
}
if (environmentDeclarations.has(retiredRedisPasswordEnvironmentVariable)) {
  errors.push('environment example still declares the retired Redis password variable');
}
for (const variableName of ['MOR_XXL_JOB_ACCESS_TOKEN', 'MOR_XXL_JOB_LOGIN_TOKEN_KEY']) {
  const value = environmentTemplate.match(new RegExp(`^${variableName}=(.+)$`, 'mu'))?.[1];
  if (!value || value.length < 32 || /\s/u.test(value)) {
    errors.push(`${variableName} must contain at least 32 non-whitespace characters`);
  }
}
const nacosTokenMatch = environmentTemplate.match(/^MOR_NACOS_AUTH_TOKEN=(.+)$/mu);
if (!nacosTokenMatch) {
  errors.push('missing MOR_NACOS_AUTH_TOKEN value');
} else {
  const decodedToken = Buffer.from(nacosTokenMatch[1], 'base64');
  if (decodedToken.length < 32) {
    errors.push(`MOR_NACOS_AUTH_TOKEN must decode to at least 32 bytes: ${decodedToken.length}`);
  }
}
if (!nacosSchema.startsWith('-- Source: https://raw.githubusercontent.com/alibaba/nacos/3.1.2/')) {
  errors.push('Nacos schema provenance is missing or does not target 3.1.2');
}
if (!nacosSchema.includes('USE nacos_config;')) {
  errors.push('Nacos schema does not select the isolated nacos_config database');
}
const nacosTableCount = nacosSchema.match(/^CREATE TABLE /gmu)?.length ?? 0;
if (nacosTableCount !== 10) {
  errors.push(`unexpected Nacos 3.1.2 table count: ${nacosTableCount}`);
}
if (!xxlJobSchema.startsWith(
  '-- Source: https://raw.githubusercontent.com/xuxueli/xxl-job/3.4.2/doc/db/tables_xxl_job.sql',
)) {
  errors.push('XXL-Job schema provenance is missing or does not target 3.4.2');
}
if (!xxlJobSchema.includes(
  '-- Upstream tag commit: c2bbb46c9a3af8e2a69246728a452c606240b80e',
)) {
  errors.push('XXL-Job schema does not freeze the approved 3.4.2 tag commit');
}
if (!xxlJobSchema.includes('USE xxl_job;')) {
  errors.push('XXL-Job schema does not select the isolated xxl_job database');
}
const xxlJobTableCount = xxlJobSchema.match(/^CREATE TABLE /gmu)?.length ?? 0;
if (xxlJobTableCount !== 8) {
  errors.push(`unexpected XXL-Job 3.4.2 table count: ${xxlJobTableCount}`);
}
if (/xxl-job-executor-sample|demoJobHandler|GLUE_GROOVY/u.test(xxlJobSchema)) {
  errors.push('XXL-Job local schema must not preload sample executors or executable jobs');
}
if (!xxlJobSchemaVersion.includes('CREATE TABLE IF NOT EXISTS `xxl_job_schema_version`')) {
  errors.push('XXL-Job local schema version marker is missing');
}
if (!xxlJobSchemaVersion.includes("'3.4.2', 'c2bbb46c9a3af8e2a69246728a452c606240b80e'")) {
  errors.push('XXL-Job local schema version marker has drifted');
}

const activeGatewayContract = activeGatewayContractFiles.map((file) => ({
  file,
  content: readFileSync(join(repositoryRoot, file), 'utf8'),
}));
for (const document of activeGatewayContract) {
  if (!document.content.includes('2026-08-30-operations-gateway-hardening-design.md')) {
    errors.push(`active Gateway contract does not reference the hardening authority: ${document.file}`);
  }
  if (document.content.includes(legacyGatewayHeaderPrefix)
      || document.content.includes(legacyRefreshCookieName)) {
    errors.push(`active Gateway contract retains legacy wire vocabulary: ${document.file}`);
  }
  if (document.content.includes(guardTierName)
      || document.content.includes(forwardedWildcardName)
      || /strip(?:Prefix| Prefix)/iu.test(document.content)
      || /previous.{0,40}HMAC|HMAC.{0,40}previous|dual.{0,40}HMAC|HMAC.{0,40}dual/iu.test(document.content)) {
    errors.push(`active Gateway contract retains a superseded Guard or forwarding model: ${document.file}`);
  }
}

const gatewayDesign = readFileSync(gatewayDesignFile, 'utf8');
const routingSection = section(gatewayDesign, '1. Boundary and routing');
requireExactTable('route contract', routingSection,
  ['Route', 'External contract', 'Explicit BFF path', 'Access and refresh-cookie input'], [
    ['Login', '`/api/operations/auth/v1/login`', '`/auth/v1/login`', 'anonymous; no refresh cookie'],
    ['Refresh', '`/api/operations/auth/v1/refresh`', '`/auth/v1/refresh`', 'anonymous; exactly one refresh cookie'],
    ['Logout', '`/api/operations/auth/v1/logout`', '`/auth/v1/logout`', 'JWT; exactly one refresh cookie'],
  ]);
requireSectionStatements('routing', routingSection, [
  'Matching is exact `HttpMethod + rawPath`, requires an empty query, and each Catalog item owns its downstream path.',
  'There is no wildcard route or prefix-stripping rule.',
  'no `GET` route or default future Guard profile exists now.',
]);

const protocolSection = section(gatewayDesign, '2. Browser and trusted-header contract');
requireExactTable('trusted-header removal', protocolSection,
  ['Removed trusted name'], [
    ['`X-Request-Id`'],
    ['`X-Gateway-Actor-Subject`'],
    ['`X-Gateway-Session-Id`'],
    ['`X-Gateway-Actor-Type`'],
    ['`X-Gateway-Channel`'],
    ['`Forwarded`'],
    ['`X-Forwarded-For`'],
    ['`X-Forwarded-Host`'],
    ['`X-Forwarded-Port`'],
    ['`X-Forwarded-Proto`'],
    ['`X-Forwarded-Prefix`'],
    ['`X-Forwarded-Ssl`'],
    ['`X-Forwarded-Server`'],
    ['`X-Real-IP`'],
  ]);
requireExactTable('route header allowlist', protocolSection,
  ['Route', 'Browser request headers', 'BFF external values', 'Trusted BFF values'], [
    ['Login', '`Content-Type`', '`Origin`, `Content-Type`', '`X-Request-Id`'],
    ['Refresh', '`Content-Type`, `X-CSRF-Token`', '`Origin`, `Content-Type`, `X-CSRF-Token`, one refresh cookie', '`X-Request-Id`'],
    ['Logout', '`Authorization`, `Content-Type`, `X-CSRF-Token`', '`Origin`, `Content-Type`, `X-CSRF-Token`, one refresh cookie', '`X-Request-Id` and four `X-Gateway-*` identity headers'],
  ]);
requireSectionStatements('browser protocol', protocolSection, [
  'Ingress removes names by case-insensitive whole-name equality only.',
  'The refresh cookie is `Secure`, `HttpOnly`, `SameSite=Strict`, path `/api/operations/auth/v1`, and has no `Domain`.',
  'Browser Fetch uses credentials `include`; CORS allows an exact Origin and `Access-Control-Allow-Credentials: true`.',
]);

const guardSection = section(gatewayDesign, '3. Guard and body limits');
requireSectionStatements('Guard/body', guardSection, [
  'Rate limiting occurs before immediate Concurrency acquisition; there is no wait or queue.',
  'A later Concurrency rejection, JWT rejection, body rejection or downstream failure does not refund an already-consumed Rate token.',
  'Every acquired lease releases on success, error or cancellation.',
  'Guard uses the `v2` namespace, Redis server time plus a monotonic positive policy revision, and only the current Guard HMAC key.',
  'All current routes have a `16 KiB` actual body cap and a `5 s` total aggregation deadline.',
  'Validated bytes replay to BFF with exact `Content-Length` and no `Transfer-Encoding`; BFF sees no body before validation.',
  'Pooled buffers are released and temporary arrays are best-effort cleared on complete, error and cancellation.',
]);

const transportSection = section(gatewayDesign, '4. JWT/JWKS and transport envelope');
requireSectionStatements('JWKS/transport', transportSection, [
  'Gateway accepts only `RS256`, with `kid` matching `^[A-Za-z0-9._:-]{1,128}$`; invalid input causes no JWKS call.',
  'Refresh uses one global in-flight operation and one global five-second cooldown, never per-`kid` state.',
  'A definitive unknown after success is `401`; a required failed/cooldown refresh is `503`.',
  'JWKS uses constrained HTTPS with no userinfo, query, fragment or redirect; connect is `2 s`, response `3 s`, body `64 KiB`, at most 16 valid RSA-2048 keys, and cache age five minutes.',
  'Production/default ingress is `PUBLIC_TLS`, limited to TLS 1.3/1.2 and the approved seven suites, with HTTP/2 disabled.',
  'Request line is `8 KiB`, aggregate headers `32 KiB`, parsed Authorization and Cookie are each `8 KiB`, Origin and each CORS control header are `2 KiB`, header/read-idle deadline is `5 s`, connection cap is 1000, backlog is 512, and keepalive requests are capped at 1000.',
  'BFF transport uses a fixed pool of 1000, acquire/connect timeouts of `2 s`, a complete-chain timeout of `10 s`, no redirects and no Reactor retry.',
  'Finite type-based classification maps BFF TLS/protocol/invalid response to `502`, connect/pool/DNS to `503`, and complete timeout to `504`, without exception details.',
]);

if (errors.length > 0) {
  process.stderr.write(`${errors.join('\n')}\n`);
  process.exit(1);
}

process.stdout.write('Local base/scheduler Compose models and MySQL initialization verified.\n');
