import {
  existsSync,
  mkdtempSync,
  readFileSync,
  readdirSync,
  rmSync,
} from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';
import { runInNewContext } from 'node:vm';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(scriptDirectory, '..');
const buildRoot = join(repositoryRoot, 'paytech', 'build');
const foundationRoot = join(repositoryRoot, 'paytech', 'foundation');
const wrapper = join(repositoryRoot, 'paytech', 'mvnw');
const mavenSettings = process.env.PAYTECH_MAVEN_SETTINGS;
const selfTestOnly = process.argv.includes('--self-test');

const requiredFiles = [
  join(buildRoot, 'paytech-dependencies', 'pom.xml'),
  join(buildRoot, 'paytech-parent', 'pom.xml'),
  join(foundationRoot, 'pom.xml'),
  join(foundationRoot, 'paytech-messaging-api', 'pom.xml'),
  join(foundationRoot, 'paytech-messaging', 'pom.xml'),
  join(foundationRoot, 'paytech-messaging', 'catalog', 'events.yaml'),
  join(foundationRoot, 'paytech-internal-auth', 'pom.xml'),
  join(foundationRoot, 'paytech-json-canonicalization', 'pom.xml'),
  join(
    foundationRoot,
    'paytech-json-canonicalization',
    'src',
    'main',
    'java',
    'com',
    'paytech',
    'foundation',
    'jsoncanonicalization',
    'api',
    'Rfc8785JsonCanonicalizer.java',
  ),
  join(
    foundationRoot,
    'paytech-json-canonicalization',
    'src',
    'main',
    'java',
    'com',
    'paytech',
    'foundation',
    'jsoncanonicalization',
    'api',
    'JsonCanonicalizationException.java',
  ),
  join(
    foundationRoot,
    'paytech-json-canonicalization',
    'src',
    'test',
    'java',
    'com',
    'paytech',
    'foundation',
    'jsoncanonicalization',
    'auto',
    'Rfc8785JsonCanonicalizerAutoTest.java',
  ),
  join(
    foundationRoot,
    'paytech-json-canonicalization',
    'src',
    'test',
    'java',
    'com',
    'paytech',
    'foundation',
    'jsoncanonicalization',
    'auto',
    'Rfc8785JsonCanonicalizerBoundaryAutoTest.java',
  ),
  join(
    foundationRoot,
    'paytech-json-canonicalization',
    'src',
    'test',
    'java',
    'com',
    'paytech',
    'foundation',
    'jsoncanonicalization',
    'auto',
    'JsonCanonicalizationContractAutoTest.java',
  ),
  join(foundationRoot, 'paytech-http-problem', 'pom.xml'),
  join(
    foundationRoot,
    'paytech-http-problem',
    'src',
    'main',
    'java',
    'com',
    'paytech',
    'foundation',
    'httpproblem',
    'api',
    'ApiProblemResponse.java',
  ),
  join(
    foundationRoot,
    'paytech-http-problem',
    'src',
    'main',
    'java',
    'com',
    'paytech',
    'foundation',
    'httpproblem',
    'api',
    'ProblemFieldError.java',
  ),
  join(
    foundationRoot,
    'paytech-http-problem',
    'src',
    'main',
    'java',
    'com',
    'paytech',
    'foundation',
    'httpproblem',
    'api',
    'ApiProblemProfile.java',
  ),
  join(
    foundationRoot,
    'paytech-http-problem',
    'src',
    'test',
    'java',
    'com',
    'paytech',
    'foundation',
    'httpproblem',
    'auto',
    'ApiProblemResponseJsonAutoTest.java',
  ),
  join(
    foundationRoot,
    'paytech-http-problem',
    'src',
    'test',
    'java',
    'com',
    'paytech',
    'foundation',
    'httpproblem',
    'auto',
    'ApiProblemProfileAutoTest.java',
  ),
  join(
    foundationRoot,
    'paytech-caffeine-starter',
    'src',
    'main',
    'java',
    'com',
    'paytech',
    'foundation',
    'caffeine',
    'api',
    'CaffeineCacheConstants.java',
  ),
  join(
    foundationRoot,
    'paytech-caffeine-starter',
    'src',
    'main',
    'resources',
    'META-INF',
    'spring',
    'org.springframework.boot.autoconfigure.AutoConfiguration.imports',
  ),
  join(
    foundationRoot,
    'paytech-caffeine-starter',
    'src',
    'test',
    'java',
    'com',
    'paytech',
    'foundation',
    'caffeine',
    'auto',
    'autoconfigure',
    'CaffeineCacheAutoConfigurationAutoTest.java',
  ),
  join(
    foundationRoot,
    'paytech-redis-starter',
    'src',
    'main',
    'java',
    'com',
    'paytech',
    'foundation',
    'redis',
    'api',
    'RedisCacheDefinition.java',
  ),
  join(
    foundationRoot,
    'paytech-redis-starter',
    'src',
    'main',
    'java',
    'com',
    'paytech',
    'foundation',
    'redis',
    'api',
    'ResourceLock.java',
  ),
  join(
    foundationRoot,
    'paytech-redis-starter',
    'src',
    'main',
    'java',
    'com',
    'paytech',
    'foundation',
    'redis',
    'api',
    'ResourceLocks.java',
  ),
  join(
    foundationRoot,
    'paytech-redis-starter',
    'src',
    'main',
    'java',
    'com',
    'paytech',
    'foundation',
    'redis',
    'core',
    'autoconfigure',
    'RedisResourceLockAutoConfiguration.java',
  ),
  join(
    foundationRoot,
    'paytech-redis-starter',
    'src',
    'main',
    'java',
    'com',
    'paytech',
    'foundation',
    'redis',
    'core',
    'coordination',
    'FoundationRedissonClientDescriptor.java',
  ),
  join(
    foundationRoot,
    'paytech-redis-starter',
    'src',
    'main',
    'java',
    'com',
    'paytech',
    'foundation',
    'redis',
    'core',
    'autoconfigure',
    'RedisCacheAutoConfiguration.java',
  ),
  join(
    foundationRoot,
    'paytech-redis-starter',
    'src',
    'main',
    'resources',
    'META-INF',
    'spring',
    'org.springframework.boot.autoconfigure.AutoConfiguration.imports',
  ),
  join(
    foundationRoot,
    'paytech-redis-starter',
    'src',
    'test',
    'java',
    'com',
    'paytech',
    'foundation',
    'redis',
    'auto',
    'integration',
    'RedisStarterIntegrationAutoTest.java',
  ),
  join(
    foundationRoot,
    'paytech-redis-starter',
    'src',
    'test',
    'java',
    'com',
    'paytech',
    'foundation',
    'redis',
    'auto',
    'integration',
    'ResourceLockRedisIntegrationAutoTest.java',
  ),
  join(
    foundationRoot,
    'paytech-redis-starter',
    'src',
    'test',
    'java',
    'com',
    'paytech',
    'foundation',
    'redis',
    'test',
    'infrastructure',
    'RedisLocalEnvironmentPrecondition.java',
  ),
  join(
    foundationRoot,
    'paytech-redis-starter',
    'src',
    'test',
    'java',
    'com',
    'paytech',
    'foundation',
    'redis',
    'test',
    'infrastructure',
    'RedisTestNamespace.java',
  ),
  wrapper,
];

const errors = [];

function tagValue(xml, tagName) {
  const escapedTag = tagName.replace(/[.*+?^${}()|[\]\\]/gu, '\\$&');
  const match = xml.replace(/<!--[\s\S]*?-->/gu, '').match(new RegExp(
    `<${escapedTag}[ \\t\\r\\n]*(?:>([^<]*)</${escapedTag}[ \\t\\r\\n]*>|/>)`, 'u',
  ));
  return match ? (match[1] ?? '').trim() : undefined;
}

function dependencyBlocks(xml) {
  const liveXml = xml.replace(/<!--[\s\S]*?-->/gu, '');
  return [...liveXml.matchAll(/<dependency[ \t\r\n]*>([\s\S]*?)<\/dependency[ \t\r\n]*>/gu)].map(
    (match) => match[1],
  );
}

function checkedDependencyBlocks(xml, context) {
  // This is a deliberately narrow POM profile, not an XML parser. Unsupported
  // representations must fail closed instead of looking like absent coordinates.
  const liveXml = xml.replace(/<!--[\s\S]*?-->/gu, '');
  const blocks = dependencyBlocks(liveXml);
  const delimiters = [...liveXml.matchAll(/<\/?dependency(?=[ \t\r\n/>]|$)[^>]*>?/gu)];
  const validDelimiters = delimiters.length === blocks.length * 2
    && delimiters.every((match, index) => (index % 2 === 0
      ? /^<dependency[ \t\r\n]*>$/u : /^<\/dependency[ \t\r\n]*>$/u).test(match[0]));
  if (!validDelimiters) errors.push(`unsupported dependency syntax/coordinate: ${context}`);

  const whitespace = '[ \\t\\r\\n]*';
  const coordinateText = '[A-Za-z0-9_.-]+';
  const excludedCoordinate = (name) => `<${name}${whitespace}>${whitespace}`
    + `${coordinateText}${whitespace}</${name}${whitespace}>`;
  const exclusion = `<exclusion${whitespace}>${whitespace}`
    + `${excludedCoordinate('groupId')}${whitespace}${excludedCoordinate('artifactId')}`
    + `${whitespace}</exclusion${whitespace}>${whitespace}`;
  const exclusions = new RegExp(
    `<exclusions${whitespace}>${whitespace}(?:${exclusion})*</exclusions${whitespace}>`, 'gu',
  );
  const allowedFields = new Set([
    'groupId', 'artifactId', 'version', 'type', 'classifier', 'scope', 'systemPath', 'optional',
  ]);
  const dependencies = [];
  for (const block of blocks) {
    const fields = [];
    const residue = block.replace(exclusions, '').replace(
      /<([A-Za-z][A-Za-z0-9]*)[ \t\r\n]*(?:>([^<>&]*)<\/\1[ \t\r\n]*>|\/>)/gu,
      (element, name, value) => {
        fields.push([name, (value ?? '').trim()]);
        return '';
      },
    );
    const uniqueFields = new Set(fields.map(([name]) => name));
    const validCoordinates = ['groupId', 'artifactId'].every((name) => {
      const matches = fields.filter(([field]) => field === name);
      return matches.length === 1 && /^[A-Za-z0-9_.-]+$/u.test(matches[0][1]);
    });
    if (residue.trim() !== '' || uniqueFields.size !== fields.length
        || fields.some(([name]) => !allowedFields.has(name)) || !validCoordinates) {
      errors.push(`unsupported dependency syntax/coordinate: ${context}`);
    }
    // Preserve direct-field provenance: consumers must never re-read descendants
    // from the raw block after its exclusions have been removed for validation.
    dependencies.push({ ...Object.fromEntries(fields), hasLocalVersion: hasLiveDependencyVersion(block) });
  }
  return dependencies;
}

function hasLiveDependencyVersion(block) {
  return /<version(?=[ \t\r\n/>])/u.test(block.replace(/<!--[\s\S]*?-->/gu, ''));
}

function javaImports(source, context) {
  // Scan comments and literals together so comment markers inside a literal (or
  // quotes inside a comment) cannot change how subsequent imports are recognized.
  const code = source.replace(
    /\/\/[^\r\n]*|\/\*[\s\S]*?\*\/|"""(?:\\[\s\S]|(?!""")[^\\])*"""|"(?:\\[\s\S]|[^"\\])*"|'(?:\\[\s\S]|[^'\\])*'/gu,
    (literal) => literal.replace(/[^\r\n]/gu, ' '),
  );
  const imports = [];
  for (const match of code.matchAll(/(?<![\p{ID_Continue}$])import\s+(?:static\s+)?([^;]+);/gu)) {
    const target = match[1].trim();
    if (!/^(?:[A-Za-z_$][A-Za-z0-9_$]*[ \t\r\n\f]*\.[ \t\r\n\f]*)+(?:[A-Za-z_$][A-Za-z0-9_$]*|\*)$/u.test(target)) {
      errors.push(`unsupported import target syntax: ${context}`);
      continue;
    }
    imports.push(target.replace(/[ \t\r\n\f]+/gu, ''));
  }
  return imports;
}

function collectNamespaceFiles(directory) {
  const files = [];
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    if (entry.name === 'target') {
      continue;
    }
    const entryPath = join(directory, entry.name);
    if (entry.isDirectory()) {
      files.push(...collectNamespaceFiles(entryPath));
      continue;
    }
    if (/\.(java|xml|imports|ya?ml|sql)$/u.test(entry.name) || entry.name === 'pom.xml') {
      files.push(entryPath);
    }
  }
  return files;
}

for (const file of requiredFiles) {
  try {
    readFileSync(file);
  } catch {
    errors.push(`missing required Java foundation file: ${file}`);
  }
}

if (errors.length === 0 && !selfTestOnly) {
  const dependencyBom = readFileSync(
    join(buildRoot, 'paytech-dependencies', 'pom.xml'),
    'utf8',
  );

  const approvedImports = [
    ['org.springframework.boot', 'spring-boot-dependencies', 'spring-boot.version', '4.0.8'],
    ['org.springframework.cloud', 'spring-cloud-dependencies', 'spring-cloud.version', '2025.1.3'],
    [
      'com.alibaba.cloud',
      'spring-cloud-alibaba-dependencies',
      'spring-cloud-alibaba.version',
      '2025.1.0.0',
    ],
  ];

  const managedDependencies = checkedDependencyBlocks(dependencyBom, 'paytech-dependencies/pom.xml');
  for (const [groupId, artifactId, versionProperty, approvedVersion] of approvedImports) {
    const matchingImports = managedDependencies.filter(
      (dependency) => dependency.groupId === groupId && dependency.artifactId === artifactId,
    );
    const declaredVersion = tagValue(dependencyBom, versionProperty);
    const importBlock = matchingImports[0];
    const validImport = matchingImports.length === 1
      && importBlock.version === `\${${versionProperty}}`
      && importBlock.type === 'pom'
      && importBlock.scope === 'import';
    if (declaredVersion !== approvedVersion || !validImport) {
      errors.push(
        `approved BOM import is missing or has drifted: ${groupId}:${artifactId}:${approvedVersion}`,
      );
    }
  }

  const modulePoms = [
    'paytech-common-core/pom.xml',
    'paytech-http-problem/pom.xml',
    'paytech-json-canonicalization/pom.xml',
    'paytech-http-client-support/pom.xml',
    'paytech-mybatis-support/pom.xml',
    'paytech-snowflake-id/pom.xml',
    'paytech-mvc-web-starter/pom.xml',
    'paytech-caffeine-starter/pom.xml',
    'paytech-redis-starter/pom.xml',
    'paytech-compensation-event/pom.xml',
    'paytech-xxl-job-starter/pom.xml',
    'paytech-messaging-api/pom.xml',
    'paytech-messaging/pom.xml',
    'paytech-internal-auth/pom.xml',
  ];
  const foundationModel = readFileSync(join(foundationRoot, 'pom.xml'), 'utf8');
  for (const relativePom of modulePoms) {
    const moduleName = relativePom.replace('/pom.xml', '');
    const moduleDeclaration = `<module>${moduleName}</module>`;
    const declarationCount = foundationModel.split(moduleDeclaration).length - 1;
    if (declarationCount !== 1) {
      errors.push(
        `foundation reactor module must be declared exactly once (${moduleName}); found ${declarationCount}`,
      );
    }
  }

  for (const artifactId of [
    'paytech-caffeine-starter',
    'paytech-compensation-event',
    'paytech-http-problem',
    'paytech-json-canonicalization',
    'paytech-redis-starter',
    'paytech-xxl-job-starter',
    'paytech-messaging-api',
    'paytech-messaging',
    'paytech-internal-auth',
  ]) {
    const dependencies = managedDependencies.filter(
      (dependency) => dependency.groupId === 'com.paytech' && dependency.artifactId === artifactId,
    );
    if (dependencies.length !== 1 || dependencies[0].version !== '${paytech.version}') {
      errors.push(`${artifactId} is missing from paytech-dependencies version governance`);
    }
  }

  const liveDependencyBom = dependencyBom.replace(/<!--[\s\S]*?-->/gu, '');
  // Count every exact-name start before accepting the one supported value form;
  // attributes, self-closing or unfinished openings must not disappear here.
  const javaJsonCanonicalizationVersionOpenings = [
    ...liveDependencyBom.matchAll(
      /<java-json-canonicalization\.version(?=[ \t\r\n/>]|$)/gu,
    ),
  ];
  const javaJsonCanonicalizationVersionElements = [
    ...liveDependencyBom.matchAll(
      /<java-json-canonicalization\.version[ \t\r\n]*>([^<]*)<\/java-json-canonicalization\.version[ \t\r\n]*>/gu,
    ),
  ];
  const javaJsonCanonicalizationVersion = javaJsonCanonicalizationVersionElements.length === 1
    ? javaJsonCanonicalizationVersionElements[0][1].trim()
    : undefined;
  const javaJsonCanonicalizationDependencies = managedDependencies.filter(
    (dependency) => dependency.groupId === 'io.github.erdtman'
      && dependency.artifactId === 'java-json-canonicalization',
  );
  if (javaJsonCanonicalizationVersionOpenings.length !== 1
      || javaJsonCanonicalizationVersionElements.length !== 1
      || javaJsonCanonicalizationVersion !== '1.1'
      || javaJsonCanonicalizationDependencies.length !== 1
      || javaJsonCanonicalizationDependencies[0].version
        !== '${java-json-canonicalization.version}') {
    errors.push(
      'java-json-canonicalization approved-version drift: expected exactly one '
        + 'io.github.erdtman:java-json-canonicalization:${java-json-canonicalization.version} '
        + 'managed dependency with property value 1.1',
    );
  }

  const xxlJobVersion = tagValue(dependencyBom, 'xxl-job.version');
  const xxlJobDependencies = managedDependencies.filter(
    (dependency) => dependency.groupId === 'com.xuxueli' && dependency.artifactId === 'xxl-job-core',
  );
  if (xxlJobVersion !== '3.4.2' || xxlJobDependencies.length !== 1
      || xxlJobDependencies[0].version !== '${xxl-job.version}') {
    errors.push('xxl-job-core is missing approved 3.4.2 version governance');
  }

  const redissonVersion = tagValue(dependencyBom, 'redisson.version');
  const redissonDependencies = managedDependencies.filter(
    (dependency) => dependency.groupId === 'org.redisson' && dependency.artifactId === 'redisson',
  );
  const retiredRedissonStarter = managedDependencies.some(
    (dependency) => dependency.groupId === 'org.redisson'
      && dependency.artifactId === 'redisson-spring-boot-starter',
  );
  if (redissonVersion !== '4.6.1' || redissonDependencies.length !== 1
      || redissonDependencies[0].version !== '${redisson.version}') {
    errors.push('Redisson Core is missing approved 4.6.1 version governance');
  }
  if (retiredRedissonStarter) {
    errors.push('redisson-spring-boot-starter must not be managed because it can replace Lettuce');
  }

  const expectedGroupId = '<groupId>com.paytech</groupId>';
  const mavenModelFiles = [
    join(buildRoot, 'paytech-dependencies', 'pom.xml'),
    join(buildRoot, 'paytech-parent', 'pom.xml'),
    join(foundationRoot, 'pom.xml'),
    ...modulePoms.map((relativePom) => join(foundationRoot, relativePom)),
  ];
  for (const modelFile of mavenModelFiles) {
    const model = readFileSync(modelFile, 'utf8');
    if (!model.includes(expectedGroupId)) {
      errors.push(`PayTech Maven groupId is missing: ${modelFile}`);
    }
    if (model.includes('code-mor-')) {
      errors.push(`retired code-mor Maven identifier remains: ${modelFile}`);
    }
    if (model.includes('<artifactId>mor-')) {
      errors.push(`retired mor Maven artifact remains: ${modelFile}`);
    }
  }

  const expectedArtifactIds = [
    [join(buildRoot, 'paytech-dependencies', 'pom.xml'), 'paytech-dependencies'],
    [join(buildRoot, 'paytech-parent', 'pom.xml'), 'paytech-parent'],
    [join(foundationRoot, 'pom.xml'), 'paytech-foundation'],
    [
      join(foundationRoot, 'paytech-json-canonicalization', 'pom.xml'),
      'paytech-json-canonicalization',
    ],
    [join(foundationRoot, 'paytech-common-core', 'pom.xml'), 'paytech-common-core'],
    [join(foundationRoot, 'paytech-http-problem', 'pom.xml'), 'paytech-http-problem'],
    [join(foundationRoot, 'paytech-snowflake-id', 'pom.xml'), 'paytech-snowflake-id'],
    [join(foundationRoot, 'paytech-mvc-web-starter', 'pom.xml'), 'paytech-mvc-web-starter'],
    [join(foundationRoot, 'paytech-caffeine-starter', 'pom.xml'), 'paytech-caffeine-starter'],
    [join(foundationRoot, 'paytech-redis-starter', 'pom.xml'), 'paytech-redis-starter'],
    [join(foundationRoot, 'paytech-compensation-event', 'pom.xml'), 'paytech-compensation-event'],
    [join(foundationRoot, 'paytech-xxl-job-starter', 'pom.xml'), 'paytech-xxl-job-starter'],
    [join(foundationRoot, 'paytech-messaging-api', 'pom.xml'), 'paytech-messaging-api'],
    [join(foundationRoot, 'paytech-messaging', 'pom.xml'), 'paytech-messaging'],
    [join(foundationRoot, 'paytech-internal-auth', 'pom.xml'), 'paytech-internal-auth'],
  ];
  for (const [modelFile, artifactId] of expectedArtifactIds) {
    const model = readFileSync(modelFile, 'utf8');
    if (!model.includes(`<artifactId>${artifactId}</artifactId>`)) {
      errors.push(`expected Maven artifactId is missing (${artifactId}): ${modelFile}`);
    }
  }

  const jsonCanonicalizationPom = readFileSync(
    join(foundationRoot, 'paytech-json-canonicalization', 'pom.xml'),
    'utf8',
  );
  const jsonCanonicalizationDependencies = checkedDependencyBlocks(
    jsonCanonicalizationPom, 'paytech-json-canonicalization/pom.xml',
  );
  const jsonCanonicalizationProductionDependencies = jsonCanonicalizationDependencies.filter(
    (dependency) => dependency.scope !== 'test',
  );
  const expectedJsonCanonicalizationProductionDependencies = [
    'io.github.erdtman:java-json-canonicalization',
    'tools.jackson.core:jackson-core',
  ];
  const actualJsonCanonicalizationProductionDependencies =
    jsonCanonicalizationProductionDependencies
      .map((dependency) => `${dependency.groupId}:${dependency.artifactId}`)
      .sort();
  if (actualJsonCanonicalizationProductionDependencies.length
        !== expectedJsonCanonicalizationProductionDependencies.length
      || actualJsonCanonicalizationProductionDependencies.some(
        (coordinate, index) => coordinate
          !== expectedJsonCanonicalizationProductionDependencies[index],
      )) {
    errors.push(
      'paytech-json-canonicalization production dependencies must equal exactly: '
        + expectedJsonCanonicalizationProductionDependencies.join(', '),
    );
  }
  for (const dependency of jsonCanonicalizationProductionDependencies) {
    if (dependency.hasLocalVersion) {
      errors.push(
        'paytech-json-canonicalization production dependency must omit its local version: '
          + `${dependency.groupId}:${dependency.artifactId}`,
      );
    }
  }

  const jsonCanonicalizationSourceRoot = join(
    foundationRoot,
    'paytech-json-canonicalization',
    'src',
    'main',
  );
  for (const sourceFile of collectNamespaceFiles(jsonCanonicalizationSourceRoot)) {
    if (!sourceFile.endsWith('.java')) {
      continue;
    }
    const content = readFileSync(sourceFile, 'utf8');
    // Intentionally reject all raw Unicode-escape spellings, even ineligible ones,
    // and Java-ignorable characters before masking. This source profile avoids a
    // partial implementation of Java's pre-lexical Unicode translation rules.
    if (/\\u+[0-9A-Fa-f]{4}/u.test(content)
        || /[\u0000-\u0008\u000e-\u001b\u007f-\u009f\p{Cf}]/u.test(content)) {
      errors.push(`unsupported Unicode source syntax: ${sourceFile}`);
      continue;
    }
    for (const importedType of javaImports(content, sourceFile)) {
      const isAllowedJdkImport = importedType.startsWith('java.')
        && !importedType.startsWith('java.sql.');
      const isAllowedModuleImport = importedType.startsWith(
        'com.paytech.foundation.jsoncanonicalization.',
      );
      const isAllowedPrivateLibraryImport = importedType.startsWith('tools.jackson.core.')
        || importedType.startsWith('org.erdtman.jcs.');
      if (!isAllowedJdkImport && !isAllowedModuleImport && !isAllowedPrivateLibraryImport) {
        errors.push(
          `paytech-json-canonicalization production source import is outside the approved allowlist (${importedType}): ${sourceFile}`,
        );
      }
    }
  }

  for (const moduleName of [
    'paytech-common-core',
    'paytech-http-problem',
    'paytech-http-client-support',
    'paytech-mybatis-support',
    'paytech-snowflake-id',
    'paytech-mvc-web-starter',
    'paytech-caffeine-starter',
    'paytech-redis-starter',
    'paytech-compensation-event',
    'paytech-xxl-job-starter',
    // -core modules depend on messaging-api; payloadHash canonicalization lives in paytech-messaging (-app only).
    'paytech-messaging-api',
  ]) {
    const modulePom = readFileSync(join(foundationRoot, moduleName, 'pom.xml'), 'utf8');
    const declaresJsonCanonicalization = checkedDependencyBlocks(modulePom, `${moduleName}/pom.xml`).some(
      (dependency) => dependency.groupId === 'com.paytech'
        && dependency.artifactId === 'paytech-json-canonicalization',
    );
    if (declaresJsonCanonicalization) {
      errors.push(
        `high-fan-out Foundation module must not declare paytech-json-canonicalization: ${moduleName}`,
      );
    }
  }

  const httpProblemPom = readFileSync(
    join(foundationRoot, 'paytech-http-problem', 'pom.xml'),
    'utf8',
  );
  const httpProblemDependencies = dependencyBlocks(httpProblemPom);
  const httpProblemProductionDependencies = httpProblemDependencies.filter(
    (block) => tagValue(block, 'scope') !== 'test',
  );
  // ApiProblemProfile reuses the framework-free ApiHeaderNames constants from paytech-common-core.
  const allowedHttpProblemProductionDependencies = [
    'com.fasterxml.jackson.core:jackson-annotations',
    'com.paytech:paytech-common-core',
  ];
  const actualHttpProblemProductionDependencies = httpProblemProductionDependencies
    .map((block) => `${tagValue(block, 'groupId')}:${tagValue(block, 'artifactId')}`)
    .sort();
  if (actualHttpProblemProductionDependencies.join(',')
      !== allowedHttpProblemProductionDependencies.join(',')) {
    errors.push(
      'paytech-http-problem production dependencies must equal exactly: '
        + allowedHttpProblemProductionDependencies.join(', '),
    );
  }
  const httpProblemSourceRoot = join(foundationRoot, 'paytech-http-problem', 'src', 'main');
  for (const sourceFile of collectNamespaceFiles(httpProblemSourceRoot)) {
    const content = readFileSync(sourceFile, 'utf8');
    if (/org\.springframework|jakarta\.servlet|javax\.servlet/u.test(content)) {
      errors.push(`paytech-http-problem production source has a framework dependency: ${sourceFile}`);
    }
  }

  for (const namespaceFile of collectNamespaceFiles(foundationRoot)) {
    const content = readFileSync(namespaceFile, 'utf8');
    if (content.includes('com.codemor')) {
      errors.push(`retired com.codemor namespace remains: ${namespaceFile}`);
    }
    if (/(?:CodeMor|CM-COMMON)/u.test(content)) {
      errors.push(`retired CodeMor/CM technical identifier remains: ${namespaceFile}`);
    }
    if (/\bMor[A-Z]/u.test(content)) {
      errors.push(`retired Mor Java identifier remains: ${namespaceFile}`);
    }
  }

  const compensationSourceRoot = join(
    foundationRoot,
    'paytech-compensation-event',
    'src',
  );
  const retiredCompensationIdentifiers = [
    /\bMor[A-Z]/u,
    /mor\.compensation/u,
    /mor-compensation-(?:worker|handler|rabbit)/u,
    /CompensationExecutionType|execution_type/u,
    /sourceVersion|schemaVersion|source_version|schema_version/u,
    /CanonicalEventEnvelope|payloadHash|payload_hash/u,
    /RabbitMqEventRoute|RabbitMqCompensationPublisher/u,
  ];
  for (const sourceFile of collectNamespaceFiles(compensationSourceRoot)) {
    const content = readFileSync(sourceFile, 'utf8');
    if (retiredCompensationIdentifiers.some((pattern) => pattern.test(content))) {
      errors.push(`retired compensation identifier remains: ${sourceFile}`);
    }
  }

  const compensationPom = readFileSync(
    join(foundationRoot, 'paytech-compensation-event', 'pom.xml'),
    'utf8',
  );
  const compensationDependencies = dependencyBlocks(compensationPom);
  for (const [groupId, artifactId] of [
    ['com.paytech', 'paytech-xxl-job-starter'],
    ['com.xuxueli', 'xxl-job-core'],
  ]) {
    const hasDependency = compensationDependencies.some(
      (block) => tagValue(block, 'groupId') === groupId
        && tagValue(block, 'artifactId') === artifactId,
    );
    if (!hasDependency) {
      errors.push(`paytech-compensation-event dependency is missing: ${groupId}:${artifactId}`);
    }
  }

  const compensationProperties = readFileSync(
    join(
      compensationSourceRoot,
      'main',
      'java',
      'com',
      'paytech',
      'foundation',
      'compensation',
      'core',
      'autoconfigure',
      'CompensationEventProperties.java',
    ),
    'utf8',
  );
  if (!compensationProperties.includes('private int scannerBatchSize = 100;')
      || !compensationProperties.includes('private int coreSize = 10;')
      || !compensationProperties.includes('private int maximumSize = 20;')
      || !compensationProperties.includes('private int queueCapacity = 80;')
      || !compensationProperties.includes('Duration.ofSeconds(60)')
      || !compensationProperties.includes('private int maximumConcurrency = 20;')
      || !compensationProperties.includes('ignoreUnknownFields = false')
      || compensationProperties.includes('class Pool {')
      || compensationProperties.includes('getPool()')) {
    errors.push('paytech-compensation-event executor defaults or independent configuration groups have drifted');
  }
  const compensationAutoConfiguration = readFileSync(
    join(
      compensationSourceRoot,
      'main',
      'java',
      'com',
      'paytech',
      'foundation',
      'compensation',
      'core',
      'autoconfigure',
      'CompensationEventAutoConfiguration.java',
    ),
    'utf8',
  );
  if (!compensationAutoConfiguration.includes(
    'COORDINATOR_EXECUTOR_BEAN = "compensationCoordinatorExecutor"',
  )
      || !compensationAutoConfiguration.includes(
        'HANDLER_EXECUTOR_BEAN = "compensationHandlerExecutor"',
      )
      || !compensationAutoConfiguration.includes('properties.getCoordinator()')
      || !compensationAutoConfiguration.includes('properties.getHandler().getMaximumConcurrency()')
      || !compensationAutoConfiguration.includes('environment.getProperty("spring.application.name")')
      || !compensationAutoConfiguration.includes('namedDaemonThreadFactory("compensation-coordinator-")')
      || compensationAutoConfiguration.includes('compensationWorkerExecutor')
      || compensationAutoConfiguration.includes('namedDaemonThreadFactory("compensation-worker-")')) {
    errors.push('paytech-compensation-event coordinator and Handler executors are not independently named');
  }

  const xxlJobStarterPom = readFileSync(
    join(foundationRoot, 'paytech-xxl-job-starter', 'pom.xml'),
    'utf8',
  );
  const xxlJobStarterDependencies = dependencyBlocks(xxlJobStarterPom);
  const compensationDependency = xxlJobStarterDependencies.some(
    (block) => tagValue(block, 'groupId') === 'com.paytech'
      && tagValue(block, 'artifactId') === 'paytech-compensation-event',
  );
  if (compensationDependency) {
    errors.push('paytech-xxl-job-starter must not depend on paytech-compensation-event');
  }
  for (const [groupId, artifactId] of [
    ['com.xuxueli', 'xxl-job-core'],
    ['org.slf4j', 'slf4j-api'],
    ['org.springframework.boot', 'spring-boot'],
  ]) {
    const hasDependency = xxlJobStarterDependencies.some(
      (block) => tagValue(block, 'groupId') === groupId
        && tagValue(block, 'artifactId') === artifactId,
    );
    if (!hasDependency) {
      errors.push(`paytech-xxl-job-starter dependency is missing: ${groupId}:${artifactId}`);
    }
  }

  const xxlJobSourceRoot = join(foundationRoot, 'paytech-xxl-job-starter', 'src');
  for (const sourceFile of collectNamespaceFiles(xxlJobSourceRoot)) {
    const content = readFileSync(sourceFile, 'utf8');
    if (/com\.paytech\.foundation\.compensation|CompensationEvent/u.test(content)) {
      errors.push(`generic paytech-xxl-job-starter references compensation: ${sourceFile}`);
    }
  }

  const compensationXxlJobHandler = join(
    foundationRoot,
    'paytech-compensation-event',
    'src',
    'main',
    'java',
    'com',
    'paytech',
    'foundation',
    'compensation',
    'ext',
    'job',
    'CompensationEventXxlJobHandler.java',
  );
  if (!existsSync(compensationXxlJobHandler)) {
    errors.push('paytech-compensation-event does not own its XXL-Job scan handler under ext/job');
  } else {
    const handlerSource = readFileSync(compensationXxlJobHandler, 'utf8');
    if (!handlerSource.includes('package com.paytech.foundation.compensation.ext.job;')) {
      errors.push('compensation XXL-Job scan handler package is not ext/job');
    }
  }

  const retiredCompensationXxlJobHandler = join(
    foundationRoot,
    'paytech-compensation-event',
    'src',
    'main',
    'java',
    'com',
    'paytech',
    'foundation',
    'compensation',
    'core',
    'execution',
    'CompensationEventXxlJobHandler.java',
  );
  if (existsSync(retiredCompensationXxlJobHandler)) {
    errors.push('external XXL-Job scan handler must not remain under core/execution');
  }

  const caffeineStarterPom = readFileSync(
    join(foundationRoot, 'paytech-caffeine-starter', 'pom.xml'),
    'utf8',
  );
  const caffeineStarterDependencies = dependencyBlocks(caffeineStarterPom);
  for (const [groupId, artifactId] of [
    ['com.paytech', 'paytech-common-core'],
    ['com.github.ben-manes.caffeine', 'caffeine'],
    ['org.springframework.boot', 'spring-boot-starter-cache'],
  ]) {
    const hasDependency = caffeineStarterDependencies.some(
      (block) => tagValue(block, 'groupId') === groupId
        && tagValue(block, 'artifactId') === artifactId,
    );
    if (!hasDependency) {
      errors.push(`paytech-caffeine-starter dependency is missing: ${groupId}:${artifactId}`);
    }
  }
  const caffeineSourceRoot = join(foundationRoot, 'paytech-caffeine-starter', 'src');
  const caffeineProperties = readFileSync(
    join(
      caffeineSourceRoot,
      'main',
      'java',
      'com',
      'paytech',
      'foundation',
      'caffeine',
      'core',
      'autoconfigure',
      'CaffeineCacheProperties.java',
    ),
    'utf8',
  );
  if (!caffeineProperties.includes('@ConfigurationProperties(prefix = "caffeine")')
      || !caffeineProperties.includes('maximumSize = 10_000')
      || !caffeineProperties.includes('Duration.ofMinutes(10)')
      || !caffeineProperties.includes('Duration.ofMillis(1)')) {
    errors.push('paytech-caffeine-starter does not enforce safe bounded defaults');
  }
  const caffeineCacheConstants = readFileSync(
    join(
      caffeineSourceRoot,
      'main',
      'java',
      'com',
      'paytech',
      'foundation',
      'caffeine',
      'api',
      'CaffeineCacheConstants.java',
    ),
    'utf8',
  );
  if (!caffeineCacheConstants.includes(
    'public static final String CACHE_MANAGER_BEAN_NAME = "caffeineCacheManager";',
  )) {
    errors.push('paytech-caffeine-starter does not expose its CacheManager Bean name as a public constant');
  }
  const caffeineAutoConfiguration = readFileSync(
    join(
      caffeineSourceRoot,
      'main',
      'java',
      'com',
      'paytech',
      'foundation',
      'caffeine',
      'core',
      'autoconfigure',
      'CaffeineCacheAutoConfiguration.java',
    ),
    'utf8',
  );
  if (!caffeineAutoConfiguration.includes('@EnableCaching')
      || !caffeineAutoConfiguration.includes('@Bean(name = CaffeineCacheConstants.CACHE_MANAGER_BEAN_NAME)')
      || !caffeineAutoConfiguration.includes(
        '@ConditionalOnMissingBean(name = CaffeineCacheConstants.CACHE_MANAGER_BEAN_NAME)',
      )
      || caffeineAutoConfiguration.includes('@Bean(name = {"cacheManager", "caffeineCacheManager"})')
      || !caffeineAutoConfiguration.includes('.maximumSize(properties.getMaximumSize())')
      || !caffeineAutoConfiguration.includes('.expireAfterWrite(properties.getExpireAfterWrite())')
      || !caffeineAutoConfiguration.includes('.recordStats()')
      || !caffeineAutoConfiguration.includes('setAllowNullValues(false)')) {
    errors.push('paytech-caffeine-starter auto configuration is missing required cache boundaries');
  }
  const caffeineAutoTest = readFileSync(
    join(
      caffeineSourceRoot,
      'test',
      'java',
      'com',
      'paytech',
      'foundation',
      'caffeine',
      'auto',
      'autoconfigure',
      'CaffeineCacheAutoConfigurationAutoTest.java',
    ),
    'utf8',
  );
  if (!caffeineAutoTest.includes('@Cacheable(cacheNames = "order-cache", sync = true)')
      || !caffeineAutoTest.includes('getCache("unknown-cache")).isNull()')
      || !caffeineAutoTest.includes('doesNotHaveBean("cacheManager")')
      || !caffeineAutoTest.includes('test_caffeineCacheManagerCoexistsWithHostDefaultCacheManager')
      || !caffeineAutoTest.includes('nativeCache.stats().hitCount()')
      || caffeineAutoTest.includes('Testcontainers')) {
    errors.push('paytech-caffeine-starter auto test does not verify its real local-cache behavior');
  }

  const redisStarterPom = readFileSync(
    join(foundationRoot, 'paytech-redis-starter', 'pom.xml'),
    'utf8',
  );
  const redisStarterDependencies = dependencyBlocks(redisStarterPom);
  for (const [groupId, artifactId] of [
    ['com.paytech', 'paytech-common-core'],
    ['org.redisson', 'redisson'],
    ['org.springframework.boot', 'spring-boot-starter-data-redis'],
    ['tools.jackson.core', 'jackson-databind'],
  ]) {
    const hasDependency = redisStarterDependencies.some(
      (block) => tagValue(block, 'groupId') === groupId
        && tagValue(block, 'artifactId') === artifactId,
    );
    if (!hasDependency) {
      errors.push(`paytech-redis-starter dependency is missing: ${groupId}:${artifactId}`);
    }
  }
  const forbiddenRedisDependencies = redisStarterDependencies.filter(
    (block) => tagValue(block, 'groupId') === 'org.redisson'
      && /redisson-spring-(?:boot-starter|data-\d+)/u.test(tagValue(block, 'artifactId') ?? ''),
  );
  if (forbiddenRedisDependencies.length > 0) {
    errors.push('paytech-redis-starter must use Redisson Core without a Redisson connection factory');
  }

  const redisStarterSourceRoot = join(foundationRoot, 'paytech-redis-starter', 'src');
  for (const sourceFile of collectNamespaceFiles(redisStarterSourceRoot)) {
    const content = readFileSync(sourceFile, 'utf8');
    if (/RedissonConnectionFactory|redisson-spring-boot-starter/u.test(content)) {
      errors.push(`paytech-redis-starter can replace the Lettuce connection factory: ${sourceFile}`);
    }
    if (/org\.testcontainers|GenericContainer|DockerClientFactory/u.test(content)) {
      errors.push(`paytech-redis-starter test can create external Redis infrastructure: ${sourceFile}`);
    }
  }
  const redisNamespaceProperties = readFileSync(
    join(
      redisStarterSourceRoot,
      'main',
      'java',
      'com',
      'paytech',
      'foundation',
      'redis',
      'core',
      'autoconfigure',
      'RedisNamespaceProperties.java',
    ),
    'utf8',
  );
  if (!redisNamespaceProperties.includes('@ConfigurationProperties(prefix = "redis")')
      || !redisNamespaceProperties.includes('spring.application.name')) {
    errors.push('paytech-redis-starter does not resolve the default service key prefix from spring.application.name');
  }
  const redisKeyNamespaceAutoConfiguration = readFileSync(
    join(
      redisStarterSourceRoot,
      'main',
      'java',
      'com',
      'paytech',
      'foundation',
      'redis',
      'core',
      'autoconfigure',
      'RedisKeyNamespaceAutoConfiguration.java',
    ),
    'utf8',
  );
  if (!redisKeyNamespaceAutoConfiguration.includes('@Bean(name = {"stringRedisTemplate", "redisTemplate"})')) {
    errors.push('paytech-redis-starter can expose the default JDK-serialized RedisTemplate');
  }
  const namespacedStringRedisTemplate = readFileSync(
    join(
      redisStarterSourceRoot,
      'main',
      'java',
      'com',
      'paytech',
      'foundation',
      'redis',
      'core',
      'template',
      'NamespacedStringRedisTemplate.java',
    ),
    'utf8',
  );
  if (!namespacedStringRedisTemplate.includes('setKeySerializer(new RedisKeyPrefixSerializer(keyPrefix))')
      || !namespacedStringRedisTemplate.includes('Cursor<String> scan(ScanOptions options)')) {
    errors.push('paytech-redis-starter does not namespace StringRedisTemplate key serialization and scans');
  }
  const redisJsonTemplate = readFileSync(
    join(
      redisStarterSourceRoot,
      'main',
      'java',
      'com',
      'paytech',
      'foundation',
      'redis',
      'api',
      'RedisJsonTemplate.java',
    ),
    'utf8',
  );
  if (!redisJsonTemplate.includes('Class<T> valueType')
      || !redisJsonTemplate.includes('TypeReference<T> valueType')
      || !redisJsonTemplate.includes('writeValueAsString(value)')
      || !redisJsonTemplate.includes('RedisJsonObjectMapperFactory.create()')
      || redisJsonTemplate.includes('enableDefaultTyping')
      || redisJsonTemplate.includes('RedisSerializer.json()')) {
    errors.push('paytech-redis-starter JSON template is not explicitly typed plain JSON');
  }
  const redisCacheDefinition = readFileSync(
    join(
      redisStarterSourceRoot,
      'main',
      'java',
      'com',
      'paytech',
      'foundation',
      'redis',
      'api',
      'RedisCacheDefinition.java',
    ),
    'utf8',
  );
  if (!redisCacheDefinition.includes(
    'record RedisCacheDefinition<T>(String cacheName, Class<T> valueType, Duration ttl)',
  )) {
    errors.push('paytech-redis-starter does not expose an explicitly typed cache definition API');
  }
  const redisCacheAutoConfiguration = readFileSync(
    join(
      redisStarterSourceRoot,
      'main',
      'java',
      'com',
      'paytech',
      'foundation',
      'redis',
      'core',
      'autoconfigure',
      'RedisCacheAutoConfiguration.java',
    ),
    'utf8',
  );
  if (!redisCacheAutoConfiguration.includes('@EnableCaching')
      || !redisCacheAutoConfiguration.includes('@Bean(name = {"cacheManager", "redisCacheManager"})')
      || !redisCacheAutoConfiguration.includes('@Primary')
      || !redisCacheAutoConfiguration.includes('ObjectProvider<RedisCacheDefinition<?>>')) {
    errors.push('paytech-redis-starter does not expose Redis as the typed default CacheManager');
  }
  const redisCacheManagerFactory = readFileSync(
    join(
      redisStarterSourceRoot,
      'main',
      'java',
      'com',
      'paytech',
      'foundation',
      'redis',
      'core',
      'cache',
      'RedisCacheManagerFactory.java',
    ),
    'utf8',
  );
  if (!redisCacheManagerFactory.includes('new TypedRedisJsonSerializer(')
      || !redisCacheManagerFactory.includes('BatchStrategies.scan(1_000)')
      || !redisCacheManagerFactory.includes('.disableCreateOnMissingCache()')
      || !redisCacheManagerFactory.includes('.transactionAware()')
      || !redisCacheManagerFactory.includes('.enableStatistics()')
      || redisCacheManagerFactory.includes('RedisSerializer.java(')
      || redisCacheManagerFactory.includes('RedisSerializer.json()')) {
    errors.push('paytech-redis-starter typed CacheManager boundaries are incomplete');
  }
  const typedRedisSerializerTest = readFileSync(
    join(
      redisStarterSourceRoot,
      'test',
      'java',
      'com',
      'paytech',
      'foundation',
      'redis',
      'auto',
      'serialization',
      'TypedRedisJsonSerializerAutoTest.java',
    ),
    'utf8',
  );
  if (!typedRedisSerializerTest.includes('serializesPlainJsonAndRestoresDeclaredType')
      || !typedRedisSerializerTest.includes('rejectsNullValueExplicitly')
      || !typedRedisSerializerTest.includes('.doesNotContain("@class", "_type"')) {
    errors.push('paytech-redis-starter typed JSON serializer test is incomplete');
  }
  const redisIntegrationTest = readFileSync(
    join(
      redisStarterSourceRoot,
      'test',
      'java',
      'com',
      'paytech',
      'foundation',
      'redis',
      'auto',
      'integration',
      'RedisStarterIntegrationAutoTest.java',
    ),
    'utf8',
  );
  const redisLocalEnvironmentPrecondition = readFileSync(
    join(
      redisStarterSourceRoot,
      'test',
      'java',
      'com',
      'paytech',
      'foundation',
      'redis',
      'test',
      'infrastructure',
      'RedisLocalEnvironmentPrecondition.java',
    ),
    'utf8',
  );
  if (!redisLocalEnvironmentPrecondition.includes('HOST = "127.0.0.1"')
      || !redisLocalEnvironmentPrecondition.includes('PORT = 16_379')
      || !redisLocalEnvironmentPrecondition.includes('EXPECTED_VERSION = "8.2.1"')
      || !redisLocalEnvironmentPrecondition.includes('System.getenv("PAYTECH_REDIS_PASSWORD")')
      || !redisLocalEnvironmentPrecondition.includes('PAYTECH_REDIS_PASSWORD is missing')
      || !redisLocalEnvironmentPrecondition.includes('Redis version must be ')) {
    errors.push('paytech-redis-starter auto test does not fail fast against the approved local Redis 8.2.1');
  }
  if (!redisIntegrationTest.includes('RedisLocalEnvironmentPrecondition.requirePasswordAndVerify()')
      || !redisIntegrationTest.includes('spring.data.redis.host=" + RedisLocalEnvironmentPrecondition.HOST')
      || !redisIntegrationTest.includes('spring.data.redis.port=" + RedisLocalEnvironmentPrecondition.PORT')) {
    errors.push('paytech-redis-starter auto test does not reuse the repository-owned local Redis precondition');
  }
  const redisTestNamespace = readFileSync(
    join(
      redisStarterSourceRoot,
      'test',
      'java',
      'com',
      'paytech',
      'foundation',
      'redis',
      'test',
      'infrastructure',
      'RedisTestNamespace.java',
    ),
    'utf8',
  );
  if (!redisTestNamespace.includes('UUID.randomUUID()')
      || !redisTestNamespace.includes('.match(value + ":*")')
      || !redisTestNamespace.includes('raw.delete(List.copyOf(batch))')) {
    errors.push('paytech-redis-starter auto test does not isolate and SCAN-clean its Redis namespace');
  }
  if (!redisIntegrationTest.includes('verifyMultiKeyAndScan')
      || !redisIntegrationTest.includes('verifyHashFieldIsNotPrefixed')
      || !redisIntegrationTest.includes('verifyJsonValue')
      || !redisIntegrationTest.includes('verifyMixedSpringCaches')
      || !redisIntegrationTest.includes('@Cacheable(cacheNames = "redis-payment-cache"')
      || !redisIntegrationTest.includes(
        'cacheManager = CaffeineCacheConstants.CACHE_MANAGER_BEAN_NAME, sync = true',
      )
      || !redisIntegrationTest.includes('getExpire(physicalRedisKey, MILLISECONDS)')
      || !redisIntegrationTest.includes('doesNotContain("@class", "_type"')
      || !redisIntegrationTest.includes('NAMESPACE.physical(lockName)')) {
    errors.push('paytech-redis-starter auto test does not verify JSON and unified Lettuce/Redisson namespaces');
  }
  const resourceLockIntegrationTest = readFileSync(
    join(
      redisStarterSourceRoot,
      'test',
      'java',
      'com',
      'paytech',
      'foundation',
      'redis',
      'auto',
      'integration',
      'ResourceLockRedisIntegrationAutoTest.java',
    ),
    'utf8',
  );
  for (const scenario of [
    'appliesTheRealProxyAndPreservesBusinessFailure',
    'serializesTheSameKeyAndAllowsDifferentKeys',
    'coordinatesTwoIndependentApplicationContextsAndTwoJvms',
    'sortsMultipleDeclarationsDeduplicatesAliasesAndReleasesPartialAcquisition',
    'appliesOneTotalWaitBudgetAcrossRealRedisLocks',
    'doesNotReleaseTheSecondOwnerAfterTheFirstLeaseExpires',
    'restoresInterruptAndFailsClosedForUnavailableOrUnauthorizedRedis',
    'realRedisReleaseFailureDoesNotReplaceBusinessSuccessOrFailure',
  ]) {
    if (!resourceLockIntegrationTest.includes(scenario)) {
      errors.push(`paytech-redis-starter ResourceLock integration scenario is missing: ${scenario}`);
    }
  }

  const expectedPackageRoot = join(
    foundationRoot,
    'paytech-common-core',
    'src',
    'main',
    'java',
    'com',
    'paytech',
    'foundation',
  );
  if (!existsSync(expectedPackageRoot)) {
    errors.push(`PayTech Java package root is missing: ${expectedPackageRoot}`);
  }

  for (const relativePom of modulePoms) {
    const modulePom = readFileSync(join(foundationRoot, relativePom), 'utf8');
    const dependencies = dependencyBlocks(modulePom);
    if (dependencies.some(hasLiveDependencyVersion)) {
      errors.push(`managed dependency version declared in child module: ${relativePom}`);
    }
  }

  const effectiveDirectory = mkdtempSync(join(tmpdir(), 'paytech-effective-pom-'));
  const effectivePom = join(effectiveDirectory, 'effective-pom.xml');
  const javaHome = process.env.JAVA_HOME;
  const mavenResult = spawnSync(
    wrapper,
    [
      ...(mavenSettings ? ['--settings', mavenSettings] : []),
      '-q',
      '-f',
      join(foundationRoot, 'pom.xml'),
      'help:effective-pom',
      `-Doutput=${effectivePom}`,
      '-Denforcer.skip=true',
    ],
    {
      cwd: foundationRoot,
      env: { ...process.env, ...(javaHome ? { JAVA_HOME: javaHome } : {}) },
      encoding: 'utf8',
    },
  );

  if (mavenResult.status !== 0) {
    errors.push(
      `failed to resolve effective Maven model:\n${mavenResult.stdout}${mavenResult.stderr}`,
    );
  } else {
    const effectiveModel = readFileSync(effectivePom, 'utf8');
    if (!effectiveModel.includes('<maven.compiler.release>25</maven.compiler.release>')) {
      errors.push('effective Maven model does not compile with Java release 25');
    }
    if (!effectiveModel.includes('<include>**/auto/**/*Test.java</include>')) {
      errors.push('effective Surefire configuration does not select auto tests');
    }
    if (!effectiveModel.includes('<exclude>**/manual/**/*Test.java</exclude>')) {
      errors.push('effective Surefire configuration does not exclude manual tests');
    }
    const distributionManagement = effectiveModel.match(
      /<distributionManagement>[\s\S]*?<\/distributionManagement>/u,
    )?.[0];
    if (!distributionManagement) {
      errors.push('effective Maven model does not define Nexus distribution management');
    } else {
      for (const repositoryId of ['maven-releases', 'maven-snapshots']) {
        if (!distributionManagement.includes(`<id>${repositoryId}</id>`)) {
          errors.push(`effective distribution management is missing: ${repositoryId}`);
        }
      }
      if (mavenSettings) {
        for (const repositoryName of ['maven-releases', 'maven-snapshots']) {
          const expectedUrl = `http://127.0.0.1:18081/repository/${repositoryName}`;
          if (!distributionManagement.includes(`<url>${expectedUrl}</url>`)) {
            errors.push(`effective distribution management URL is missing: ${expectedUrl}`);
          }
        }
      }
    }
  }

  rmSync(effectiveDirectory, { recursive: true, force: true });
}

// Exercise the real static gates with in-memory file substitutions. No fixture POM or
// Java source is written, and the unchanged Maven subprocess runs only once normally.
function verifyStaticGateRegressions() {
  const verifierSource = readFileSync(fileURLToPath(import.meta.url), 'utf8');
  const start = verifierSource.indexOf('const errors = [];');
  const end = verifierSource.indexOf('  const effectiveDirectory =');
  if (start < 0 || end <= start) {
    throw new Error('Cannot locate verifier static section for regression checks');
  }
  const staticScript = `${verifierSource.slice(start, end)}\n}\nerrors;`;
  const childPom = join(foundationRoot, 'paytech-json-canonicalization', 'pom.xml');
  const corePom = join(foundationRoot, 'paytech-common-core', 'pom.xml');
  const bomPom = join(buildRoot, 'paytech-dependencies', 'pom.xml');
  const javaSource = join(foundationRoot, 'paytech-json-canonicalization', 'src', 'main',
    'java', 'com', 'paytech', 'foundation', 'jsoncanonicalization', 'api',
    'Rfc8785JsonCanonicalizer.java');
  const spring = '<dependency ><groupId>org.springframework</groupId>'
    + '<artifactId>spring-context</artifactId></dependency >';
  const optIn = '<dependency ><groupId>com.paytech</groupId>'
    + '<artifactId>paytech-json-canonicalization</artifactId></dependency >';
  const property = '<java-json-canonicalization.version>1.1</java-json-canonicalization.version>';
  const purityError = 'production dependencies must equal exactly';
  const versionError = 'production dependency must omit its local version';
  const optInError = 'high-fan-out Foundation module must not declare';
  const importError = 'production source import is outside the approved allowlist';
  const bomError = 'java-json-canonicalization approved-version drift';
  const dependencySyntaxError = 'unsupported dependency syntax/coordinate';
  const unicodeError = 'unsupported Unicode source syntax';
  const appendDependency = (addition) => (xml) => xml.replace(
    '</dependencies>', `${addition}</dependencies>`,
  );
  const cases = [
    ['canonical-positive', childPom, (xml) => xml, null],
    ['xml-extra-whitespace-dependency', childPom, appendDependency(spring), purityError],
    ['xml-extra-canonical-dependency', childPom,
      appendDependency(spring.replaceAll('dependency >', 'dependency>')), purityError],
    ['xml-whitespace-local-version', childPom, (xml) => xml.replace(
      '<artifactId>java-json-canonicalization</artifactId>',
      '<artifactId>java-json-canonicalization</artifactId><version >1.1</version >',
    ), versionError],
    ['xml-whitespace-opt-in', corePom, appendDependency(optIn), optInError],
    ['xml-all-tag-whitespace-positive', childPom, (xml) => xml.replace(
      /<dependencies>[\s\S]*?<\/dependencies>/u,
      (section) => section.replace(/<(\/?)(dependency|groupId|artifactId|scope)>/gu,
        '<$1$2 \t\r\n>'),
    ), null],
    ['xml-commented-dependency-positive', childPom,
      appendDependency(`<!-- ${spring} -->`), null],
    ['xml-commented-canonical-dependency-positive', childPom,
      appendDependency(`<!-- ${spring.replaceAll('dependency >', 'dependency>')} -->`), null],
    ['xml-commented-version-positive', childPom, (xml) => xml.replace(
      '<artifactId>java-json-canonicalization</artifactId>',
      '<artifactId>java-json-canonicalization</artifactId><!-- <version >1.2</version > -->',
    ), null],
    ['xml-commented-opt-in-positive', corePom,
      appendDependency(`<!-- ${optIn} -->`), null],
    ['xml-commented-canonical-version-positive', childPom, (xml) => xml.replace(
      '<artifactId>java-json-canonicalization</artifactId>',
      '<artifactId>java-json-canonicalization</artifactId><!-- <version>1.2</version> -->',
    ), null],
  ];
  for (const [name, value] of [
    ['cdata', '<version ><![CDATA[1.1]]></version >'],
    ['namespace-attribute', '<version xmlns="http://maven.apache.org/POM/4.0.0">1.1</version>'],
  ]) {
    cases.push([`xml-local-version-${name}`, childPom, (xml) => xml.replace(
      '<artifactId>java-json-canonicalization</artifactId>',
      `<artifactId>java-json-canonicalization</artifactId>${value}`,
    ), versionError]);
  }
  for (const [name, coordinate] of [
    ['cdata', '<![CDATA[paytech-json-canonicalization]]>'],
    ['entity', 'paytech-json-canonicalizatio&#110;'],
  ]) {
    cases.push([`xml-opt-in-${name}`, corePom, appendDependency(
      `<dependency><groupId>com.paytech</groupId><artifactId>${coordinate}</artifactId></dependency>`,
    ), dependencySyntaxError]);
  }
  for (const [name, dependency] of [
    ['coordinate-attribute', '<dependency><groupId>com.paytech</groupId><artifactId xmlns="http://maven.apache.org/POM/4.0.0">paytech-json-canonicalization</artifactId></dependency>'],
    ['duplicate-coordinate', '<dependency><groupId>com.paytech</groupId><artifactId>safe</artifactId><artifactId>paytech-json-canonicalization</artifactId></dependency>'],
    ['missing-coordinate', '<dependency><groupId>com.paytech</groupId></dependency>'],
    ['opening-attribute', '<dependency unsupported="true"><groupId>com.paytech</groupId><artifactId>paytech-json-canonicalization</artifactId></dependency>'],
    ['unclosed-dependency', '<dependency><groupId>com.paytech</groupId><artifactId>paytech-json-canonicalization</artifactId>'],
  ]) {
    cases.push([`xml-${name}`, corePom, appendDependency(dependency), dependencySyntaxError]);
  }
  const exclusion = (groupId, artifactId) => '<exclusions><exclusion>'
    + `<groupId>${groupId}</groupId><artifactId>${artifactId}</artifactId>`
    + '</exclusion></exclusions>';
  const harmlessExclusion = exclusion('org.example', 'safe');
  for (const [placement, body] of [
    ['leading', `${harmlessExclusion}<groupId>com.paytech</groupId><artifactId>paytech-json-canonicalization</artifactId>`],
    ['interleaved', `<groupId>com.paytech</groupId>${harmlessExclusion}<artifactId>paytech-json-canonicalization</artifactId>`],
    ['trailing', `<groupId>com.paytech</groupId><artifactId>paytech-json-canonicalization</artifactId>${harmlessExclusion}`],
  ]) {
    cases.push([`xml-direct-opt-in-${placement}-exclusions`, corePom,
      appendDependency(`<dependency>${body}</dependency>`), optInError]);
  }
  cases.push(['xml-exclusion-only-opt-in-positive', corePom, (xml) => xml.replace(
    '<groupId>tools.jackson.core</groupId>',
    `${exclusion('com.paytech', 'paytech-json-canonicalization')}<groupId>tools.jackson.core</groupId>`,
  ), null]);
  for (const [placement, body] of [
    ['leading', `${exclusion('io.github.erdtman', 'java-json-canonicalization')}<groupId>org.springframework</groupId><artifactId>spring-context</artifactId>`],
    ['interleaved', `<groupId>org.springframework</groupId>${exclusion('io.github.erdtman', 'java-json-canonicalization')}<artifactId>spring-context</artifactId>`],
  ]) {
    cases.push([`xml-direct-spring-${placement}-exclusions`, childPom, (xml) => xml.replace(
      /<dependency>\s*<groupId>io\.github\.erdtman<\/groupId>\s*<artifactId>java-json-canonicalization<\/artifactId>\s*<\/dependency>/u,
      `<dependency>${body}</dependency>`,
    ), purityError]);
  }
  cases.push(['bom-direct-jcs-leading-exclusions-positive', bomPom, (xml) => xml.replace(
    '<groupId>io.github.erdtman</groupId>',
    `${harmlessExclusion}<groupId>io.github.erdtman</groupId>`,
  ), null]);
  for (const [name, replacement, expected] of [
    ['sole-whitespace', property.replaceAll('version>', 'version >'), null],
    ['commented-example', `${property}<!-- ${property.replace('1.1', '1.2')} -->`, null],
    ['duplicate-equal', property + property, bomError],
    ['duplicate-conflicting', property + property.replace('1.1', '1.2'), bomError],
    ['duplicate-whitespace', property + property.replaceAll('version>', 'version >'), bomError],
    ['duplicate-self-closing', `${property}<java-json-canonicalization.version />`, bomError],
    ['sole-wrong-version', property.replace('1.1', '1.2'), bomError],
    ['duplicate-attributed-equal', property + property.replace('<java-json-canonicalization.version>',
      '<java-json-canonicalization.version xmlns="http://maven.apache.org/POM/4.0.0">'), bomError],
    ['duplicate-attributed-conflicting', property + property.replace('1.1', '1.2').replace('<java-json-canonicalization.version>',
      '<java-json-canonicalization.version xmlns="http://maven.apache.org/POM/4.0.0">'), bomError],
    ['sole-attributed', property.replace('<java-json-canonicalization.version>',
      '<java-json-canonicalization.version xmlns="http://maven.apache.org/POM/4.0.0">'), bomError],
    ['duplicate-attributed-self-closing', property
      + '<java-json-canonicalization.version xmlns="http://maven.apache.org/POM/4.0.0"/>', bomError],
    ['duplicate-unclosed-opening', property + '<java-json-canonicalization.version', bomError],
    ['duplicate-unclosed-attributed-opening', property
      + '<java-json-canonicalization.version xmlns="http://maven.apache.org/POM/4.0.0"', bomError],
    ['commented-attributed-positive', property + '<!-- ' + property.replace('1.1', '1.2').replace('<java-json-canonicalization.version>',
      '<java-json-canonicalization.version xmlns="http://maven.apache.org/POM/4.0.0">') + ' -->', null],
  ]) {
    cases.push([`bom-${name}`, bomPom, (xml) => xml.replace(property, replacement), expected]);
  }
  for (const [name, prefix, expected] of [
    ['second-on-line', 'import java.io.IOException; import javax.sql.DataSource;', importError],
    ['comment-leading', '/* JDBC */ import javax.sql.DataSource;', importError],
    ['static-second-on-line', 'import java.io.IOException; import static java.sql.DriverManager.getConnection;', importError],
    ['static-leading', 'import static java.sql.DriverManager.getConnection;', importError],
    ['rocketmq', 'import org.apache.rocketmq.client.producer.DefaultMQProducer;', importError],
    ['activemq', 'import org.apache.activemq.ActiveMQConnectionFactory;', importError],
    ['mysql', 'import com.mysql.cj.jdbc.MysqlDataSource;', importError],
    ['mybatis-plus', 'import com.baomidou.mybatisplus.core.mapper.BaseMapper;', importError],
    ['jdk-jdbc', 'import java.sql.Connection;', importError],
    ['allowed-jdk', 'import java.util.List; import static java.util.Collections.emptyList;', null],
    ['allowed-module', 'import com.paytech.foundation.jsoncanonicalization.api.JsonCanonicalizationException;', null],
    ['allowed-libraries', 'import tools.jackson.core.JsonFactory; import org.erdtman.jcs.JsonCanonicalizer;', null],
    ['fake-line-comment', '// import javax.sql.DataSource;', null],
    ['fake-block-comment', '/*\nimport javax.sql.DataSource;\n*/', null],
    ['fake-string', 'class Example { String value = "import javax.sql.DataSource;"; }', null],
    ['fake-text-block', 'class Example { String value = """\nimport javax.sql.DataSource;\n"""; }', null],
    ['escaped-quote-and-char', String.raw`class Example { char quote = '\''; String value = "\" import javax.sql.DataSource;"; }`, null],
  ]) {
    cases.push([`import-${name}`, javaSource, (content) => `${prefix}\n${content}`, expected]);
  }
  for (const [name, snippet] of [
    ['escaped-keyword', String.raw`\u0069mport javax.sql.DataSource;`],
    ['escaped-sql-segment', String.raw`import java.sq\u006c.Connection;`],
    ['escaped-comment-newline', String.raw`// comment\u000aimport javax.sql.DataSource;`],
    ['escaped-static-import', String.raw`\u0069mport static java.sql.DriverManager.getConnection;`],
    ['raw-ignorable', 'import java.sq\u200cl.Connection;'],
    ['escaped-literal-quotes', String.raw`class Example { String text = \u0022 import javax.sql.DataSource;\u0022; }`],
    ['multiple-u-keyword', String.raw`\uuuu0069mport javax.sql.DataSource;`],
    ['ineligible-double-backslash', String.raw`// Example: \\u0069mport javax.sql.DataSource;`],
    ['raw-ignorable-control', 'import java.sq\u0001l.Connection;'],
  ]) {
    cases.push([`source-${name}`, javaSource, (content) => content.replace(
      'package com.paytech.foundation.jsoncanonicalization.api;',
      `package com.paytech.foundation.jsoncanonicalization.api;\n${snippet}\n`,
    ), unicodeError]);
  }
  cases.push(['import-non-ascii-target', javaSource, (content) => content.replace(
    'package com.paytech.foundation.jsoncanonicalization.api;',
    'package com.paytech.foundation.jsoncanonicalization.api;\nimport java.example.Éclair;\n',
  ), 'unsupported import target syntax']);
  cases.push(['import-unqualified-target', javaSource, (content) => content.replace(
    'package com.paytech.foundation.jsoncanonicalization.api;',
    'package com.paytech.foundation.jsoncanonicalization.api;\nimport java;\n',
  ), 'unsupported import target syntax']);
  cases.push(['source-ordinary-utf8-positive', javaSource, (content) => `${content}\n`
    + 'class Utf8Example { String value = "中文 café 😀"; String block = """\n普通文本\n"""; }\n'
    + '// 普通 UTF-8 comment\n', null]);
  const fileCache = new Map();
  for (const [name, target, mutate, expectedError] of cases) {
    const actualErrors = runInNewContext(staticScript, {
      existsSync, readdirSync, join, repositoryRoot, buildRoot, foundationRoot,
      requiredFiles, selfTestOnly: false,
      readFileSync(file, encoding) {
        const key = `${file}:${encoding ?? 'buffer'}`;
        if (!fileCache.has(key)) fileCache.set(key, readFileSync(file, encoding));
        const content = fileCache.get(key);
        return file === target && typeof content === 'string' ? mutate(content) : content;
      },
    }, { timeout: 5_000 });
    const passed = expectedError === null ? actualErrors.length === 0
      : actualErrors.some((error) => error.includes(expectedError))
        && (expectedError !== unicodeError || !actualErrors.some((error) => error.includes(importError)));
    if (!passed) {
      errors.push(`static regression ${name}: expected ${expectedError ?? 'no errors'}; got ${JSON.stringify(actualErrors)}`);
    }
    if (selfTestOnly) process.stdout.write(`${passed ? 'PASS' : 'FAIL'} ${name}\n`);
  }
}

verifyStaticGateRegressions();

if (errors.length > 0) {
  process.stderr.write(`${errors.join('\n')}\n`);
  process.exit(1);
}

process.stdout.write(selfTestOnly ? 'Java foundation static regression checks verified.\n' :
  'Java foundation structure and effective Maven model verified; '
    + 'JCS 1.1, sixteen modules, exact production dependencies, and opt-in boundary verified.\n',
);
