import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import {fileURLToPath} from 'node:url';

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const productionJavaRoot = path.join(repositoryRoot, 'paytech-api-gateway-app/src/main/java');
const productionResourceRoot = path.join(repositoryRoot, 'paytech-api-gateway-app/src/main/resources');
const testRoot = path.join(repositoryRoot, 'paytech-api-gateway-test/src/test');
const applicationYaml = path.join(productionResourceRoot, 'application.yml');
const readme = path.join(repositoryRoot, 'README.md');
const violations = [];

function requireNonemptyFile(file, description) {
    if (!fs.existsSync(file) || !fs.statSync(file).isFile() || fs.statSync(file).size === 0) {
        violations.push(`${description} is missing or empty: ${path.relative(repositoryRoot, file)}`);
        return false;
    }
    return true;
}

function filesUnder(root, extensions) {
    if (!fs.existsSync(root)) {
        violations.push(`${path.relative(repositoryRoot, root)} is missing`);
        return [];
    }
    const result = [];
    const visit = current => {
        for (const entry of fs.readdirSync(current, {withFileTypes: true})) {
            const resolved = path.join(current, entry.name);
            if (entry.isDirectory()) {
                visit(resolved);
                continue;
            }
            if (entry.isFile() && extensions.some(extension => entry.name.endsWith(extension))) {
                result.push(resolved);
            }
        }
    };
    visit(root);
    return result.sort();
}

function stripJavaComments(source) {
    let result = '';
    let state = 'code';
    for (let index = 0; index < source.length; index += 1) {
        const current = source[index];
        const next = source[index + 1];
        if (state === 'line-comment') {
            if (current === '\n') {
                state = 'code';
                result += current;
            } else {
                result += ' ';
            }
            continue;
        }
        if (state === 'block-comment') {
            if (current === '*' && next === '/') {
                result += '  ';
                index += 1;
                state = 'code';
            } else {
                result += current === '\n' ? '\n' : ' ';
            }
            continue;
        }
        if (state === 'string') {
            result += current;
            if (current === '\\' && next !== undefined) {
                result += next;
                index += 1;
            } else if (current === '"') {
                state = 'code';
            }
            continue;
        }
        if (state === 'character') {
            result += current;
            if (current === '\\' && next !== undefined) {
                result += next;
                index += 1;
            } else if (current === "'") {
                state = 'code';
            }
            continue;
        }
        if (current === '/' && next === '/') {
            result += '  ';
            index += 1;
            state = 'line-comment';
        } else if (current === '/' && next === '*') {
            result += '  ';
            index += 1;
            state = 'block-comment';
        } else {
            result += current;
            if (current === '"') {
                state = 'string';
            } else if (current === "'") {
                state = 'character';
            }
        }
    }
    return result;
}

function stripJavaStrings(source) {
    let result = '';
    let state = 'code';
    for (let index = 0; index < source.length; index += 1) {
        const current = source[index];
        const next = source[index + 1];
        if (state === 'string' || state === 'character') {
            const closing = state === 'string' ? '"' : "'";
            if (current === '\\' && next !== undefined) {
                result += '  ';
                index += 1;
            } else {
                result += current === '\n' ? '\n' : ' ';
                if (current === closing) {
                    state = 'code';
                }
            }
            continue;
        }
        result += current;
        if (current === '"') {
            state = 'string';
        } else if (current === "'") {
            state = 'character';
        }
    }
    return result;
}

function addViolation(file, message) {
    violations.push(`${path.relative(repositoryRoot, file)}: ${message}`);
}

function checkPattern(file, source, pattern, message) {
    if (pattern.test(source)) {
        addViolation(file, message);
    }
}

function verifyReadmeBffBoundary(file) {
    if (!requireNonemptyFile(file, 'Gateway README')) {
        return;
    }
    const source = fs.readFileSync(file, 'utf8');
    checkPattern(file, source, /(?:127\.0\.0\.1:)?18081/,
        'README must not identify the local Nexus port as an Operations BFF');
    checkPattern(file, source, /OPERATIONS_BFF_URI|gateway\.operations\.bff-uri/,
        'README must use Nacos discovery rather than a fixed Operations BFF URI');
    if (!/PLATFORM_SUPPORT_SERVICE_ID/.test(source) || !/NACOS_SERVER_ADDR/.test(source)
            || !/lb:\/\/operations-console-bff/.test(source)) {
        addViolation(file, 'README must document Nacos discovery for Platform and Operations BFF');
    }
}

function commandAliases(source, typeNames, factoryMethod) {
    const aliases = new Set();
    const typeAlternation = typeNames.join('|');
    const typedDeclaration = new RegExp(
        `\\b(?:${typeAlternation})\\b(?:\\s*<[^;{}]+>)?\\s+([A-Za-z_$][\\w$]*)\\b`, 'g');
    for (const match of source.matchAll(typedDeclaration)) {
        aliases.add(match[1]);
    }
    const factoryAssignment = new RegExp(
        `\\b([A-Za-z_$][\\w$]*)\\s*=\\s*[^;]{0,240}?\\.\\s*${factoryMethod}\\s*\\(\\s*\\)`, 'g');
    for (const match of source.matchAll(factoryAssignment)) {
        aliases.add(match[1]);
    }
    return aliases;
}

function escapeRegExp(value) {
    return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function checkRedisCommandAliases(file, source) {
    const keyAliases = commandAliases(source,
        ['ReactiveKeyCommands', 'RedisKeyCommands'], 'keyCommands');
    const serverAliases = commandAliases(source,
        ['ReactiveServerCommands', 'RedisServerCommands'], 'serverCommands');
    const lettuceTypes = [
        'RedisCommands',
        'RedisClusterCommands',
        'RedisAdvancedClusterCommands',
        'RedisAsyncCommands',
        'RedisReactiveCommands',
        'RedisAdvancedClusterAsyncCommands',
        'RedisAdvancedClusterReactiveCommands',
        'RedisKeyAsyncCommands',
        'RedisServerAsyncCommands',
        'RedisKeyReactiveCommands',
        'RedisServerReactiveCommands'
    ];
    const lettuceAliases = new Set([
        ...commandAliases(source, lettuceTypes, 'sync'),
        ...commandAliases(source, lettuceTypes, 'async'),
        ...commandAliases(source, lettuceTypes, 'reactive')
    ]);
    for (const alias of keyAliases) {
        checkPattern(file, source, new RegExp(`\\b${escapeRegExp(alias)}\\s*\\.\\s*keys\\s*\\(`),
            'Redis KEYS command through a key-command alias is forbidden');
    }
    for (const alias of serverAliases) {
        checkPattern(file, source,
            new RegExp(`\\b${escapeRegExp(alias)}\\s*\\.\\s*(?:flushdb|flushall)(?:Async)?\\s*\\(`, 'i'),
            'Redis FLUSHDB/FLUSHALL command through a server-command alias is forbidden');
    }
    for (const alias of lettuceAliases) {
        checkPattern(file, source,
            new RegExp(`\\b${escapeRegExp(alias)}\\s*\\.\\s*(?:keys|(?:flushdb|flushall)(?:Async)?)\\s*\\(`,
                'i'),
            'Lettuce KEYS/FLUSHDB/FLUSHALL command is forbidden');
    }
}

const redisCommandReceiver = /(?:keyCommands|serverCommands)\s*\(\s*\)\s*\.\s*(?:keys|(?:flushdb|flushall)(?:Async)?)\s*\(/i;
const lettuceCommandReceiver = /\.\s*(?:sync|async|reactive)\s*\(\s*\)\s*\.\s*(?:keys|(?:flushdb|flushall)(?:Async)?)\s*\(/i;
const redisCommandLiteral = /(?:execute|dispatch|of)\s*\(\s*["']\s*(?:KEYS|FLUSHDB|FLUSHALL)\b/i;
const redisCommandConstant = /\b(?:CommandType|ProtocolKeyword)\s*\.\s*(?:KEYS|FLUSHDB|FLUSHALL)\b/i;
const embeddedLuaDestructiveCommand = /redis\s*\.\s*(?:call|pcall)\s*\(\s*\\?(["'])\s*(?:KEYS|FLUSHDB|FLUSHALL)\s*\\?\1/i;
const externalProcessApi = /\b(?:ProcessBuilder|Runtime)\b|\.\s*exec\s*\(/;
const wildcardOperationsRoute = /["']\/api\/operations[^"'\r\n]*(?:\*|\{[^}]+})[^"'\r\n]*["']/;

const productionJavaFiles = filesUnder(productionJavaRoot, ['.java']);
if (productionJavaFiles.length === 0) {
    violations.push('Gateway production Java source evidence is empty');
}
for (const file of productionJavaFiles) {
    const rawSource = fs.readFileSync(file, 'utf8');
    const commentlessSource = stripJavaComments(rawSource);
    const codeSource = stripJavaStrings(commentlessSource);
    checkPattern(file, codeSource,
        /\.(?:block|blockFirst|blockLast|blockOptional|subscribe|subscribeWith|toIterable|toStream)\s*\(/,
        'production reactive code must not block or manually subscribe');
    checkPattern(file, codeSource,
        /(?:org\.springframework\.web\.servlet|jakarta\.servlet|javax\.servlet|\bDispatcherServlet\b|@(?:Rest)?Controller\b)/,
        'Spring MVC or Servlet API is forbidden');
    checkPattern(file, codeSource,
        /\b(?:ReactiveRedisTemplate|ReactiveStringRedisTemplate|RedisTemplate|StringRedisTemplate|RedisConnection|RedisConnectionFactory|RedisOperations|RedisCallback|RedisKeyCommands|RedisServerCommands|RedisCommands|RedisClusterCommands)\b/,
        'Redis template or blocking Redis connection API is forbidden');
    checkPattern(file, codeSource, /\.\s*sync\s*\(/,
        'synchronous Lettuce-style API access is forbidden');
    checkPattern(file, codeSource, redisCommandReceiver, 'Redis KEYS/FLUSHDB/FLUSHALL command is forbidden');
    checkPattern(file, codeSource, lettuceCommandReceiver,
        'Lettuce KEYS/FLUSHDB/FLUSHALL command is forbidden');
    checkPattern(file, commentlessSource, redisCommandLiteral, 'Redis KEYS/FLUSHDB/FLUSHALL command literal is forbidden');
    checkPattern(file, commentlessSource, embeddedLuaDestructiveCommand,
        'Java-embedded Lua must not execute Redis KEYS/FLUSHDB/FLUSHALL');
    checkPattern(file, codeSource, redisCommandConstant,
        'Redis KEYS/FLUSHDB/FLUSHALL command constant is forbidden');
    checkPattern(file, commentlessSource, wildcardOperationsRoute, 'wildcard Operations route is forbidden');
    checkPattern(file, codeSource,
        /\b(?:SslContextBuilder|TrustManagerFactory|X509TrustManager|KeyManagerFactory)\b|\.\s*secure\s*\(/,
        'Gateway production code must not configure application or JWKS TLS');
    checkPattern(file, commentlessSource, /\btrustAnchor\b/,
        'Gateway production code must not own a JWKS Trust Anchor');
    checkRedisCommandAliases(file, codeSource);
}

const productionResourceFiles = filesUnder(productionResourceRoot, ['.yml', '.yaml', '.lua']);
for (const file of productionResourceFiles) {
    const source = fs.readFileSync(file, 'utf8');
    if (file.endsWith('.lua')) {
        checkPattern(file, source,
            /redis\.(?:call|pcall)\s*\(\s*(["'])(?:KEYS|FLUSHDB|FLUSHALL)\1/i,
            'Lua must not execute Redis KEYS/FLUSHDB/FLUSHALL');
    }
    if (file.endsWith('.yml') || file.endsWith('.yaml')) {
        checkPattern(file, source, /\/api\/operations\S*(?:\*|\{[^}]+})/,
            'wildcard Operations route is forbidden');
    }
}

const testJavaFiles = filesUnder(testRoot, ['.java']);
if (testJavaFiles.length === 0) {
    violations.push('Gateway test Java source evidence is empty');
}
for (const file of testJavaFiles) {
    const rawSource = fs.readFileSync(file, 'utf8');
    const commentlessSource = stripJavaComments(rawSource);
    const codeSource = stripJavaStrings(commentlessSource);
    checkPattern(file, codeSource,
        /(?:org\.mockito|\bMockito\b|@(?:Mock|MockBean|MockitoBean)\b|\bwhen\s*\()/,
        'Mockito and method-level behavior stubs are forbidden');
    checkPattern(file, codeSource, externalProcessApi,
        'tests must not start external processes, including Docker or Docker Compose');
    checkPattern(file, codeSource, redisCommandReceiver, 'tests must not execute Redis KEYS/FLUSHDB/FLUSHALL');
    checkPattern(file, codeSource, lettuceCommandReceiver,
        'tests must not execute Lettuce KEYS/FLUSHDB/FLUSHALL');
    checkPattern(file, commentlessSource, redisCommandLiteral,
        'tests must not execute Redis KEYS/FLUSHDB/FLUSHALL command literals');
    checkPattern(file, commentlessSource, embeddedLuaDestructiveCommand,
        'test Java-embedded Lua must not execute Redis KEYS/FLUSHDB/FLUSHALL');
    checkPattern(file, codeSource, redisCommandConstant,
        'tests must not execute Redis KEYS/FLUSHDB/FLUSHALL command constants');
    checkRedisCommandAliases(file, codeSource);
}

const testResourceFiles = filesUnder(testRoot, ['.yml', '.yaml', '.lua']);
const testPemFiles = filesUnder(testRoot, ['.pem']);
for (const file of testPemFiles) {
    addViolation(file, 'Gateway tests must not retain application or JWKS TLS PEM fixtures');
}
for (const file of testResourceFiles.filter(candidate => candidate.endsWith('.lua'))) {
    checkPattern(file, fs.readFileSync(file, 'utf8'),
        /redis\.(?:call|pcall)\s*\(\s*(["'])(?:KEYS|FLUSHDB|FLUSHALL)\1/i,
        'test Lua must not execute Redis KEYS/FLUSHDB/FLUSHALL');
}

function stripYamlComment(line) {
    let quote = null;
    for (let index = 0; index < line.length; index += 1) {
        const current = line[index];
        if (quote !== null) {
            if (current === '\\' && quote === '"') {
                index += 1;
            } else if (current === "'" && quote === "'" && line[index + 1] === "'") {
                index += 1;
            } else if (current === quote) {
                quote = null;
            }
            continue;
        }
        if (current === '"' || current === "'") {
            quote = current;
        } else if (current === '#') {
            return line.slice(0, index);
        }
    }
    return line;
}

function maskYamlQuotedContent(line) {
    let result = '';
    let quote = null;
    for (let index = 0; index < line.length; index += 1) {
        const current = line[index];
        const next = line[index + 1];
        if (quote !== null) {
            result += ' ';
            if (current === '\\' && quote === '"' && next !== undefined) {
                result += ' ';
                index += 1;
            } else if (current === "'" && quote === "'" && next === "'") {
                result += ' ';
                index += 1;
            } else if (current === quote) {
                quote = null;
            }
            continue;
        }
        if (current === '"' || current === "'") {
            quote = current;
            result += ' ';
        } else {
            result += current;
        }
    }
    return {source: result, unterminatedQuote: quote};
}

function unsupportedYamlSyntax(line) {
    const masked = maskYamlQuotedContent(line);
    if (masked.unterminatedQuote !== null) {
        return 'uses a multiline or unterminated quoted scalar';
    }
    const syntax = masked.source.replace(/\$\{[^{}\r\n]+\}/g, match => ' '.repeat(match.length));
    if (/[{}\[\]]/.test(syntax)) {
        return 'uses a YAML flow collection';
    }
    if (/^\s*<<\s*:/.test(syntax)) {
        return 'uses a YAML merge key';
    }
    if (/(?:^|[\s,:])[&*][A-Za-z0-9_-]+/.test(syntax)) {
        return 'uses a YAML anchor or alias';
    }
    if (/(?:^|[\s,:])!(?:!|<|[A-Za-z0-9_-])/.test(syntax)) {
        return 'uses an explicit YAML tag';
    }
    if (/:\s*[>|][+-]?(?:\s|$)/.test(syntax)) {
        return 'uses a multiline block scalar';
    }
    if (/^\s*(?:-|\?)(?:\s|$)/.test(syntax) || /^\s*%/.test(syntax)) {
        return 'uses an unsupported YAML sequence, explicit key, or directive';
    }
    return null;
}

function parseYamlScalars(file) {
    const values = new Map();
    const ancestors = [];
    const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/);
    for (const [lineIndex, originalLine] of lines.entries()) {
        const line = stripYamlComment(originalLine);
        if (line.trim() === '') {
            continue;
        }
        if (/^\s*(?:---|\.\.\.)\s*$/.test(line)) {
            ancestors.length = 0;
            continue;
        }
        if (/\t/.test(line.match(/^\s*/)[0])) {
            addViolation(file, `line ${lineIndex + 1} uses a YAML tab indentation`);
            continue;
        }
        const unsupported = unsupportedYamlSyntax(line);
        if (unsupported !== null) {
            addViolation(file, `line ${lineIndex + 1} ${unsupported}`);
            continue;
        }
        const indent = line.match(/^ */)[0].length;
        const match = line.match(/^\s*([^:#][^:]*?):(?:\s+(.*?))?\s*$/);
        if (match === null) {
            addViolation(file, `line ${lineIndex + 1} uses unsupported YAML syntax`);
            continue;
        }
        const key = match[1].trim().replace(/^(["'])(.*)\1$/, '$2');
        const scalar = match[2];
        while (ancestors.length > 0 && ancestors.at(-1).indent >= indent) {
            ancestors.pop();
        }
        const yamlPath = [...ancestors.map(ancestor => ancestor.key), key].join('.');
        if (scalar === undefined || scalar === '') {
            ancestors.push({indent, key});
            continue;
        }
        const occurrences = values.get(yamlPath) ?? [];
        occurrences.push({value: scalar.trim(), line: lineIndex + 1});
        values.set(yamlPath, occurrences);
    }
    return values;
}

const requiredFalsePaths = [
    'spring.data.redis.repositories.enabled',
    'spring.cloud.gateway.server.webflux.discovery.locator.enabled',
    'spring.cloud.gateway.server.webflux.redis-route-definition-repository.enabled',
    'spring.cloud.gateway.server.webflux.global-filter.local-response-cache.enabled',
    'spring.cloud.gateway.server.webflux.filter.request-rate-limiter.enabled',
    'spring.cloud.gateway.server.webflux.filter.retry.enabled',
    'spring.cloud.gateway.server.webflux.forwarded.enabled',
    'spring.cloud.gateway.server.webflux.x-forwarded.enabled',
    'spring.cloud.gateway.server.webflux.metrics.path-tags.enabled',
    'spring.cloud.gateway.server.webflux.metrics.tags.path.enabled'
];

function verifyDisabledGatewayFeatures(file, requireEveryPath) {
    const yamlValues = parseYamlScalars(file);
    for (const requiredPath of requiredFalsePaths) {
        const scalars = yamlValues.get(requiredPath);
        if (scalars === undefined) {
            if (requireEveryPath) {
                addViolation(file, `${requiredPath} must be explicitly configured as false`);
            }
            continue;
        }
        for (const scalar of scalars) {
            if (!/^false$/i.test(scalar.value)) {
                addViolation(file,
                    `${requiredPath} must be the YAML boolean false (line ${scalar.line}, found ${scalar.value})`);
            }
        }
    }
}

const redisTimeoutPaths = [
    'spring.data.redis.connect-timeout',
    'spring.data.redis.timeout'
];

function durationMillis(value) {
    const scalar = value.replace(/^(?:"(.*)"|'(.*)')$/, (_, doubleQuoted, singleQuoted) =>
        doubleQuoted ?? singleQuoted);
    const simple = scalar.match(/^([0-9]+(?:\.[0-9]+)?)(ns|us|ms|s|m|h|d)?$/i);
    if (simple !== null) {
        const multipliers = {ns: 0.000001, us: 0.001, ms: 1, s: 1000, m: 60000, h: 3600000, d: 86400000};
        return Number(simple[1]) * multipliers[(simple[2] ?? 'ms').toLowerCase()];
    }
    const iso = scalar.match(/^PT(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?$/i);
    if (iso === null || iso.slice(1).every(part => part === undefined)) {
        return Number.NaN;
    }
    return Number(iso[1] ?? 0) * 3600000 + Number(iso[2] ?? 0) * 60000 + Number(iso[3] ?? 0) * 1000;
}

function verifyRedisTimeouts(file, requireBaseValues) {
    const yamlValues = parseYamlScalars(file);
    for (const propertyPath of redisTimeoutPaths) {
        const scalars = yamlValues.get(propertyPath) ?? [];
        if (requireBaseValues && scalars.length !== 1) {
            addViolation(file, `${propertyPath} must be declared exactly once in the base production document`);
            continue;
        }
        if (!requireBaseValues && scalars.length > 0) {
            addViolation(file, `${propertyPath} must not be overridden by a production profile or YAML document`);
            continue;
        }
        for (const scalar of scalars) {
            const millis = durationMillis(scalar.value);
            if (!Number.isFinite(millis) || millis <= 0 || millis > 2000) {
                addViolation(file, `${propertyPath} must be positive and at most 2 seconds `
                    + `(line ${scalar.line}, found ${scalar.value})`);
            } else if (requireBaseValues && millis !== 2000) {
                addViolation(file, `${propertyPath} must be exactly 2 seconds in the base production document `
                    + `(line ${scalar.line}, found ${scalar.value})`);
            }
        }
    }
}

function verifyReadmeTransportBoundary(file, configurationFile) {
    if (!requireNonemptyFile(file, 'Gateway README')) {
        return;
    }
    const source = fs.readFileSync(file, 'utf8');
    const yamlValues = parseYamlScalars(configurationFile);
    const requiredDefaults = new Map([
        ['server.address', '${GATEWAY_BIND_ADDRESS}'],
        ['server.http2.enabled', 'false']
    ]);
    for (const [propertyPath, expectedValue] of requiredDefaults) {
        const values = yamlValues.get(propertyPath) ?? [];
        if (values.length !== 1 || values[0].value !== expectedValue) {
            addViolation(configurationFile,
                `${propertyPath} must remain the documented internal HTTP default (${expectedValue})`);
        }
    }
    const forbiddenYamlPaths = [
        'gateway.transport.mode',
        'gateway.operations.security.trust-anchor'
    ];
    for (const propertyPath of forbiddenYamlPaths) {
        if ((yamlValues.get(propertyPath) ?? []).length > 0) {
            addViolation(configurationFile, `${propertyPath} is forbidden for the internal HTTP deployment`);
        }
    }
    for (const propertyPath of yamlValues.keys()) {
        if (propertyPath === 'server.ssl' || propertyPath.startsWith('server.ssl.')) {
            addViolation(configurationFile, `${propertyPath} is forbidden on the internal HTTP listener`);
        }
    }
    for (const forbidden of [
        'GATEWAY_TLS_CERTIFICATE',
        'GATEWAY_TLS_PRIVATE_KEY',
        'OPERATIONS_JWKS_TRUST_ANCHOR',
        'PUBLIC_TLS',
        'LOOPBACK_CLEARTEXT'
    ]) {
        if (source.includes(forbidden)) {
            addViolation(file, `${forbidden} must not remain in the current internal HTTP runbook`);
        }
    }
    if (/OPERATIONS_(?:BFF|JWKS)_URI\s*=\s*https:\/\//u.test(source)) {
        addViolation(file, 'internal BFF and JWKS examples must not use HTTPS');
    }
    for (const requiredLine of [
        'proxy_set_header X-Real-IP $remote_addr;',
        'proxy_set_header X-Forwarded-For $remote_addr;'
    ]) {
        if (!source.includes(requiredLine)) {
            addViolation(file, `README must contain the exact Nginx contract line: ${requiredLine}`);
        }
    }
    if (source.includes('`X-Forwarded-*`')) {
        addViolation(file, 'README must document the finite exact proxy-header list, not a wildcard');
    }
    for (const proxyHeader of [
        'Forwarded',
        'X-Forwarded-For',
        'X-Forwarded-Host',
        'X-Forwarded-Port',
        'X-Forwarded-Prefix',
        'X-Forwarded-Proto',
        'X-Forwarded-Server',
        'X-Forwarded-Ssl',
        'X-Real-IP'
    ]) {
        if (!source.includes(`\`${proxyHeader}\``)) {
            addViolation(file, `README must document exact proxy-header removal: ${proxyHeader}`);
        }
    }
    if (!/only from the private Nginx network/iu.test(source)
            || !/prevent\s+all direct public or client access/iu.test(source)) {
        addViolation(file, 'README must forbid direct Gateway access and limit the listener to private Nginx');
    }
    if (!/curl[\s\S]{0,180}https:\/\/operations-console\.local\.test\/api\/operations/u.test(source)) {
        addViolation(file, 'public Operations curl examples must target the Nginx HTTPS origin');
    }
}

if (requireNonemptyFile(applicationYaml, 'Gateway application configuration')) {
    verifyDisabledGatewayFeatures(applicationYaml, true);
    verifyRedisTimeouts(applicationYaml, true);
}
verifyReadmeBffBoundary(readme);
verifyReadmeTransportBoundary(readme, applicationYaml);
for (const file of productionResourceFiles.filter(candidate => {
    const name = path.basename(candidate);
    return candidate !== applicationYaml && /^application(?:-.*)?\.ya?ml$/.test(name);
})) {
    verifyDisabledGatewayFeatures(file, false);
    verifyRedisTimeouts(file, false);
}

if (violations.length > 0) {
    console.error('Gateway boundary verification failed:');
    for (const violation of violations) {
        console.error(`- ${violation}`);
    }
    process.exit(1);
}

console.log(`Gateway boundaries verified: ${productionJavaFiles.length} production Java files, `
    + `${productionResourceFiles.length} production resource files, ${testJavaFiles.length} test Java files, `
    + `${testResourceFiles.length} test resource files.`);
