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

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(scriptDirectory, '..');
const buildRoot = join(repositoryRoot, 'paytech-build');
const foundationRoot = join(repositoryRoot, 'paytech-foundation');
const wrapper = join(foundationRoot, 'mvnw');
const mavenSettings = process.env.PAYTECH_MAVEN_SETTINGS;

const requiredFiles = [
  join(buildRoot, 'pom.xml'),
  join(buildRoot, 'paytech-dependencies', 'pom.xml'),
  join(buildRoot, 'paytech-parent', 'pom.xml'),
  join(foundationRoot, 'pom.xml'),
  wrapper,
];

const errors = [];

function tagValue(xml, tagName) {
  const match = xml.match(new RegExp(`<${tagName}>([^<]+)</${tagName}>`, 'u'));
  return match?.[1]?.trim();
}

function dependencyBlocks(xml) {
  return [...xml.matchAll(/<dependency>([\s\S]*?)<\/dependency>/gu)].map(
    (match) => match[1],
  );
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

if (errors.length === 0) {
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

  const managedDependencies = dependencyBlocks(dependencyBom);
  for (const [groupId, artifactId, versionProperty, approvedVersion] of approvedImports) {
    const matchingImports = managedDependencies.filter(
      (block) => tagValue(block, 'groupId') === groupId
        && tagValue(block, 'artifactId') === artifactId,
    );
    const declaredVersion = tagValue(dependencyBom, versionProperty);
    const importBlock = matchingImports[0];
    const validImport = matchingImports.length === 1
      && tagValue(importBlock, 'version') === `\${${versionProperty}}`
      && tagValue(importBlock, 'type') === 'pom'
      && tagValue(importBlock, 'scope') === 'import';
    if (declaredVersion !== approvedVersion || !validImport) {
      errors.push(
        `approved BOM import is missing or has drifted: ${groupId}:${artifactId}:${approvedVersion}`,
      );
    }
  }

  const modulePoms = [
    'paytech-common-core/pom.xml',
    'paytech-snowflake-id/pom.xml',
    'paytech-mvc-web-starter/pom.xml',
    'paytech-compensation-event/pom.xml',
    'paytech-xxl-job-starter/pom.xml',
  ];
  const foundationModel = readFileSync(join(foundationRoot, 'pom.xml'), 'utf8');
  for (const relativePom of modulePoms) {
    const moduleName = relativePom.replace('/pom.xml', '');
    if (!foundationModel.includes(`<module>${moduleName}</module>`)) {
      errors.push(`foundation reactor module is missing: ${moduleName}`);
    }
  }

  for (const artifactId of ['paytech-compensation-event', 'paytech-xxl-job-starter']) {
    const dependencies = managedDependencies.filter(
      (block) => tagValue(block, 'groupId') === 'com.paytech'
        && tagValue(block, 'artifactId') === artifactId,
    );
    if (dependencies.length !== 1 || tagValue(dependencies[0], 'version') !== '${paytech.version}') {
      errors.push(`${artifactId} is missing from paytech-dependencies version governance`);
    }
  }

  const xxlJobVersion = tagValue(dependencyBom, 'xxl-job.version');
  const xxlJobDependencies = managedDependencies.filter(
    (block) => tagValue(block, 'groupId') === 'com.xuxueli'
      && tagValue(block, 'artifactId') === 'xxl-job-core',
  );
  if (xxlJobVersion !== '3.4.2' || xxlJobDependencies.length !== 1
      || tagValue(xxlJobDependencies[0], 'version') !== '${xxl-job.version}') {
    errors.push('xxl-job-core is missing approved 3.4.2 version governance');
  }

  const expectedGroupId = '<groupId>com.paytech</groupId>';
  const mavenModelFiles = [
    join(buildRoot, 'pom.xml'),
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
    [join(buildRoot, 'pom.xml'), 'paytech-build'],
    [join(buildRoot, 'paytech-dependencies', 'pom.xml'), 'paytech-dependencies'],
    [join(buildRoot, 'paytech-parent', 'pom.xml'), 'paytech-parent'],
    [join(foundationRoot, 'pom.xml'), 'paytech-foundation'],
    [join(foundationRoot, 'paytech-common-core', 'pom.xml'), 'paytech-common-core'],
    [join(foundationRoot, 'paytech-snowflake-id', 'pom.xml'), 'paytech-snowflake-id'],
    [join(foundationRoot, 'paytech-mvc-web-starter', 'pom.xml'), 'paytech-mvc-web-starter'],
    [join(foundationRoot, 'paytech-compensation-event', 'pom.xml'), 'paytech-compensation-event'],
    [join(foundationRoot, 'paytech-xxl-job-starter', 'pom.xml'), 'paytech-xxl-job-starter'],
  ];
  for (const [modelFile, artifactId] of expectedArtifactIds) {
    const model = readFileSync(modelFile, 'utf8');
    if (!model.includes(`<artifactId>${artifactId}</artifactId>`)) {
      errors.push(`expected Maven artifactId is missing (${artifactId}): ${modelFile}`);
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
    const dependencies = modulePom.match(/<dependencies>[\s\S]*?<\/dependencies>/g) ?? [];
    if (dependencies.some((section) => /<version>/.test(section))) {
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

if (errors.length > 0) {
  process.stderr.write(`${errors.join('\n')}\n`);
  process.exit(1);
}

process.stdout.write('Java foundation structure and effective Maven model verified.\n');
