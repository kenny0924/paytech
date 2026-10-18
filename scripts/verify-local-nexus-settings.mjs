import {
  existsSync,
  mkdtempSync,
  readFileSync,
  realpathSync,
  rmSync,
  statSync,
} from 'node:fs';
import { homedir, tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';
import { fileURLToPath } from 'node:url';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(scriptDirectory, '..');
const wrapper = join(repositoryRoot, 'paytech', 'mvnw');
const requestedSettings = process.argv[2];
const errors = [];

if (!requestedSettings) {
  process.stderr.write('Usage: node scripts/verify-local-nexus-settings.mjs <settings.xml>\n');
  process.exit(2);
}

const settingsFile = resolve(requestedSettings);
const defaultSettings = join(homedir(), '.m2', 'settings.xml');

if (!existsSync(settingsFile)) {
  errors.push(`Maven Settings file does not exist: ${settingsFile}`);
} else {
  const permissions = statSync(settingsFile).mode & 0o777;
  if ((permissions & 0o077) !== 0) {
    errors.push(`Maven Settings containing credentials must use mode 600: ${permissions.toString(8)}`);
  }
}

if (!existsSync(defaultSettings)) {
  errors.push(`default Maven Settings is missing: ${defaultSettings}`);
} else if (realpathSync(defaultSettings) !== realpathSync(settingsFile)) {
  errors.push(`default Maven Settings does not reference requested file: ${defaultSettings}`);
}

if (errors.length === 0 || existsSync(settingsFile)) {
  const effectiveDirectory = mkdtempSync(join(tmpdir(), 'mor-nexus-settings-'));
  const effectiveSettings = join(effectiveDirectory, 'effective-settings.xml');
  const mavenResult = spawnSync(
    wrapper,
    [
      '--settings',
      settingsFile,
      '-q',
      'help:effective-settings',
      `-Doutput=${effectiveSettings}`,
    ],
    {
      cwd: repositoryRoot,
      env: process.env,
      encoding: 'utf8',
    },
  );

  if (mavenResult.status !== 0) {
    errors.push(`failed to resolve effective Maven Settings:\n${mavenResult.stdout}${mavenResult.stderr}`);
  } else {
    const effective = readFileSync(effectiveSettings, 'utf8');
    const publicMirror = (effective.match(/<mirror>[\s\S]*?<id>maven-public<\/id>[\s\S]*?<\/mirror>/u) ?? [])[0];
    if (!publicMirror) {
      errors.push('maven-public Mirror is missing');
    } else {
      if (!publicMirror.includes('<mirrorOf>*</mirrorOf>')) {
        errors.push('maven-public Mirror must cover every repository with mirrorOf=*');
      }
      if (!publicMirror.includes('<url>http://127.0.0.1:18081/repository/maven-public</url>')) {
        errors.push('maven-public Mirror has an unexpected URL');
      }
    }

    for (const serverId of ['maven-releases', 'maven-snapshots']) {
      if (!new RegExp(`<server>[\\s\\S]*?<id>${serverId}</id>[\\s\\S]*?</server>`, 'u').test(effective)) {
        errors.push(`authenticated Nexus server is missing: ${serverId}`);
      }
    }
    if (!effective.includes('<activeProfile>development</activeProfile>')) {
      errors.push('development Maven profile is not active');
    }
  }

  rmSync(effectiveDirectory, { recursive: true, force: true });
}

if (errors.length > 0) {
  process.stderr.write(`${errors.join('\n')}\n`);
  process.exit(1);
}

process.stdout.write('Local Maven Settings routes dependency resolution and deployment through Nexus.\n');
