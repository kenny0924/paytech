#!/usr/bin/env bash
set -euo pipefail
umask 077

# Python 3 is an explicit portable process-supervision prerequisite; no GNU timeout.
command -v python3 >/dev/null 2>&1 || { echo 'PREPARE: Python 3 is required for bounded process supervision.' >&2; exit 1; }
export GOTOOLCHAIN=local
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."
exec python3 - <<'PY'
import os
import pathlib
import re
import shutil
import signal
import subprocess
import sys
import tempfile
import time

root = pathlib.Path.cwd()
reactor = root.parent  # paytech/: the single Maven wrapper and reactor root.
owned = None
active = None
safe_to_remove = True

def fail(message):
    raise RuntimeError(message)

def required(name):
    value = os.environ.get(name, '')
    if not value:
        fail('PREPARE: supply ' + name + ' through the process environment; see README.md.')
    return value

def prepared_foundation():
    repository = pathlib.Path(required('VAULT_MAVEN_REPOSITORY'))
    message = ('PREPARE: prepare Foundation artifacts in VAULT_MAVEN_REPOSITORY: '
               'install the approved paytech/build parent/BOM, paytech-common-core and paytech-http-problem '
               '1.0.0-SNAPSHOT separately; '
               'verification does not install, substitute or remotely resolve them.')
    if not repository.is_absolute() or not repository.is_dir():
        fail(message)
    for artifact, extensions in (('paytech-parent', ('pom',)), ('paytech-dependencies', ('pom',)),
                                 ('paytech-common-core', ('pom', 'jar')),
                                 ('paytech-http-problem', ('pom', 'jar'))):
        for extension in extensions:
            path = repository / 'com/paytech' / artifact / '1.0.0-SNAPSHOT' / (artifact + '-1.0.0-SNAPSHOT.' + extension)
            if not path.is_file() or path.stat().st_size == 0:
                fail(message)
    return str(repository)

def stop_group(process):
    global safe_to_remove
    for sig, seconds in ((signal.SIGTERM, 10), (signal.SIGKILL, 5)):
        try:
            os.killpg(process.pid, sig)
        except ProcessLookupError:
            return
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            process.poll()
            try:
                os.killpg(process.pid, 0)
            except ProcessLookupError:
                return
            time.sleep(0.05)
    safe_to_remove = False
    fail('STOP: verification process group did not stop; private artifacts retained.')

def interrupted(signum, frame):
    raise RuntimeError('STOP: verification interrupted.')

def run(label, command, seconds=600, capture=False):
    global active
    output = owned / (label + '.log')
    with output.open('wb') as stream:
        active = subprocess.Popen(command, cwd=root, stdout=stream, stderr=subprocess.STDOUT, start_new_session=True)
        try:
            status = active.wait(timeout=seconds)
        except BaseException:
            stop_group(active)
            active = None
            raise
        if status:
            stop_group(active)
            active = None
            if label == 'maven-version':
                fail('PREPARE: prepare cached Maven 3.9.16 wrapper distribution separately; verification never installs tools.')
            fail('FAIL: ' + label + '; captured output withheld for sensitive-data safety. Check documented prerequisites.')
        active = None
    if capture:
        return output.read_text(errors='replace')
    print('PASS: ' + label, flush=True)

def scan():
    secrets = [os.environ.get(name, '') for name in ('VAULT_TEST_DB_DSN', 'VAULT_TEST_JDBC_PASSWORD')]
    secrets += ['VAULT-E2E-SENSITIVE-CANARY-', 'TEST-ONLY-NOT-A-SECRET@example.test']
    locations = [owned, root / 'paytech-vault-client/target/surefire-reports', root / 'paytech-vault-test/target/surefire-reports']
    for location in locations:
        if not location.exists():
            continue
        for path in location.rglob('*'):
            if not path.is_file() or path.is_symlink() or path.name == 'vault-server':
                continue
            content = path.read_bytes()
            if any(value and value.encode() in content for value in secrets):
                fail('FAIL: sensitive canary detected in captured output/reports; matching content is never printed.')

try:
    if os.environ.get('OTEL_RESOURCE_ATTRIBUTES', ''):
        fail('PREPARE: OTEL_RESOURCE_ATTRIBUTES must be empty; verification does not rewrite it.')
    repository = prepared_foundation()
    go = os.environ.get('GO_BIN') or shutil.which('go')
    if not go or not pathlib.Path(go).is_file():
        fail('PREPARE: provide Go 1.27.1 on PATH or GO_BIN; no tool installation is performed.')
    java = pathlib.Path(required('JAVA_HOME')) / 'bin/java'
    if not java.is_file():
        fail('PREPARE: JAVA_HOME must select JDK 25.')
    if not os.access(reactor / 'mvnw', os.X_OK) or not (reactor / '.mvn/wrapper/maven-wrapper.properties').is_file():
        fail('PREPARE: restore the paytech/ Maven 3.9.16 wrapper.')
    if not shutil.which('git'):
        fail('PREPARE: git is required.')
    sqlc = os.environ.get('SQLC_BIN') or shutil.which('sqlc')
    golangci = os.environ.get('GOLANGCI_LINT_BIN') or shutil.which('golangci-lint')
    if not sqlc or not golangci:
        fail('PREPARE: provide sqlc v1.31.1 and golangci-lint v2.14.0 on PATH or SQLC_BIN/GOLANGCI_LINT_BIN; no tool installation is performed.')
    if shutil.disk_usage(root).free < 15 * 1024**3:
        fail('PREPARE: at least 15 GiB free disk is required; no user data is deleted.')
    password = required('VAULT_TEST_JDBC_PASSWORD')
    if required('VAULT_TEST_JDBC_URL') != 'jdbc:mysql://127.0.0.1:13306/mor_vault' or required('VAULT_TEST_JDBC_USER') != 'vault_app':
        fail('PREPARE: JDBC must use vault_app at the documented loopback mor_vault URL.')
    if required('VAULT_TEST_DB_DSN') != 'vault_app:' + password + '@tcp(127.0.0.1:13306)/mor_vault?parseTime=true&loc=UTC':
        fail('PREPARE: Go DSN must use the same scoped local Vault credentials and documented options.')
    owned = pathlib.Path(tempfile.mkdtemp(prefix='vault-verify-'))
    for sig in (signal.SIGINT, signal.SIGTERM):
        signal.signal(sig, interrupted)
    if run('go-version', [go, 'version'], 15, True).split()[2] != 'go1.27.1':
        fail('PREPARE: exactly Go 1.27.1 is required (GOTOOLCHAIN=local).')
    version = run('java-version', [str(java), '-version'], 15, True)
    if not re.search(r'\bversion "25[.\-"]', version):
        fail('PREPARE: JDK 25 is required.')
    os.environ['VAULT_VERIFY_CACHED_MAVEN_ONLY'] = 'true'
    maven = [str(reactor / 'mvnw'), '-B', '-ntp', '--no-snapshot-updates', '-Dmaven.repo.local=' + repository]
    if 'Apache Maven 3.9.16' not in run('maven-version', maven + ['--version'], 60, True):
        fail('PREPARE: Maven wrapper must run exactly Maven 3.9.16.')
    run('mysql-schema-preflight', [go, 'test', './internal/data', '-run', '^TestIntegrationSchemaTablesPresent$', '-count=1', '-timeout=30s'], 90)
    run('openapi-golden', [go, 'test', './test/auto/contract', './test/auto/biz', '-count=1', '-timeout=60s'], 180)
    gofiles = [str(path.relative_to(root)) for path in root.rglob('*.go') if '.git' not in path.parts]
    gofmt = str(pathlib.Path(go).resolve().parent / 'gofmt')
    if run('gofmt', [gofmt, '-l'] + gofiles, 30, True).strip():
        fail('FAIL: gofmt changes are required; verification never edits source files.')
    print('PASS: gofmt', flush=True)
    run('sqlc-diff', [sqlc, 'diff'], 60)
    run('golangci-lint', [golangci, 'run', './...'], 300)
    run('go-vet', [go, 'vet', './...'])
    run('go-test', [go, 'test', './...', '-count=1', '-timeout=120s'])
    run('go-race', [go, 'test', '-race', './...', '-count=1', '-timeout=120s'])
    binary = owned / 'vault-server'
    run('go-build', [go, 'build', '-o', str(binary), './cmd/vault-server'])
    os.environ['VAULT_TEST_SERVER_BINARY'] = str(binary)
    run('maven-clean-verify', maven + ['clean', 'verify'], 900)
    scan()
    print('PASS: sensitive-canary-scan', flush=True)
    run('git-diff-check', ['git', 'diff', '--check'], 30)
    print('PASS: full automatic Vault gate (manual Nacos is separate).', flush=True)
except BaseException as error:
    if active is not None:
        stop_group(active)
    try:
        if owned is not None:
            scan()
    except RuntimeError as leak:
        print(str(leak), file=sys.stderr)
    print(str(error) if isinstance(error, RuntimeError) else 'FAIL: bounded verification could not complete.', file=sys.stderr)
    sys.exit(1)
finally:
    if owned is not None and safe_to_remove:
        shutil.rmtree(owned)  # Only the exact mkdtemp root this invocation created.
PY
