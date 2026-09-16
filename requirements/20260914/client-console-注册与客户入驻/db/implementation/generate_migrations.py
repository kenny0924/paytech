"""Generate reviewable owner-local DDL from the frozen review source and current schema.

Never connects to a database. Environment SHOW CREATE/empty-closure verification is
required before manual execution. Stage 3 review generators remain frozen.
"""
from pathlib import Path
import hashlib
import json
import re
import subprocess

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[4]
REVIEW = json.loads((HERE.parent / 'target-field-projection.json').read_text())
TABLE_RE = re.compile(r'CREATE TABLE `(\w+)` \(([\s\S]*?)\) ENGINE=InnoDB([^;]*);')


def create_tables(sql):
    return {match[1]: match for match in TABLE_RE.finditer(sql)}


def freeze_executed_cct(output):
    """Keep an applied migration and its historical target fingerprint immutable."""
    receipt_path = HERE.parents[1] / 'assets/stage4/cct-onboarding-table-execution-20260915.json'
    if not receipt_path.exists():
        return None
    receipt = json.loads(receipt_path.read_text())
    assert receipt['status'] == 'APPLIED_AND_STRUCTURALLY_VERIFIED'
    manifest = json.loads((HERE / 'migration-manifest.json').read_text())['systems']['CCT']
    applied = (HERE / manifest['ddl']).read_bytes()
    expected = receipt['ddlFileSha256']
    assert hashlib.sha256(applied).hexdigest() == expected == manifest['ddlSha256'], 'Applied CCT SQL drift'
    assert hashlib.sha256(output.encode()).hexdigest() == expected, 'Use a new migration for changed CCT tables'
    assert manifest['sourceModelSha256'] == receipt['sourceModelSha256'], 'Applied CCT source drift'
    return manifest


def generate(write=True):
    evidence = {'status': 'canonical DDL candidate; not executed; environment preflight required', 'systems': {}}
    outputs = {}
    for owner, repo, stem in [('CCT', 'client-contract-service', 'client-contract'), ('Risk', 'risk-compliance-service', 'risk-compliance')]:
        relative = f'docs/database/{stem}.pdma.json'
        old_bytes = subprocess.check_output(['git', '-C', str(ROOT / repo), 'show', f'HEAD:{relative}'])
        # Fail instead of silently regenerating a different migration after the baseline changes.
        assert hashlib.sha256(old_bytes).hexdigest() == REVIEW['systems'][owner]['sha256'], owner
        old = {e['defKey']: e for e in json.loads(old_bytes)['entities']}
        new_bytes = (ROOT / repo / relative).read_bytes()
        current_sql = (ROOT / repo / f'docs/database/sql/{stem}-schema.sql').read_text()
        current = create_tables(current_sql)
        statements = ['-- REVIEW CANDIDATE. Do not execute before environment-specific approval.',
                      '-- Stop writers; back up; compare actual SHOW CREATE with the baseline;',
                      '-- require the complete owner-local reference closure to be empty.',
                      '-- No rollback by deleting history. See ../../migration-plan.md.',
                      '-- Do not run with mysql --force. DDL auto-commits.', 'SET NAMES utf8mb4;']
        names = []
        for table in REVIEW['systems'][owner]['tables']:
            name = table['name']; names.append(name)
            target = current[name]
            if name not in old:
                statements.append(target[0]); continue
            source = old[name]
            target_columns = re.findall(r'^  `(\w+)` (.*?)(?:,)?$', target[2], re.M)
            indexes = re.findall(r'^  ((?:UNIQUE KEY|KEY) `(\w+)` [^\n]*?)(?:,)?$', target[2], re.M)
            changes = [f'DROP INDEX `{i["defKey"]}`' for i in source['indexes']]
            before = {f['defKey'] for f in source['fields']}
            after = {c[0] for c in target_columns}
            rename = {'relation_id': 'relation_version_id'} if name == 't_party_relation' else {}
            changes += [f'DROP COLUMN `{c}`' for c in sorted(before - after - set(rename.values()))]
            previous = None
            for column, definition in target_columns:
                position = f' AFTER `{previous}`' if previous else ' FIRST'
                if column in rename:
                    command = f'CHANGE COLUMN `{rename[column]}` `{column}`'
                else:
                    command = ('MODIFY COLUMN' if column in before else 'ADD COLUMN') + f' `{column}`'
                changes.append(command + ' ' + definition + position)
                previous = column
            changes += ['ADD ' + definition for definition, _ in indexes]
            comment = re.search(r"COMMENT=('(?:[^']|'')*')$", target[3]).group(1)
            changes.append('COMMENT = ' + comment)
            statements.append(f'ALTER TABLE `{name}`\n  ' + ',\n  '.join(changes) + ';')
        filename = stem + '-empty-closure.sql'
        output = '\n\n'.join(statements) + '\n'
        outputs[filename] = output
        evidence['systems'][owner] = {'sourceModelSha256': hashlib.sha256(old_bytes).hexdigest(),
            'targetModelSha256': hashlib.sha256(new_bytes).hexdigest(), 'tables': names,
            'ddl': filename, 'ddlSha256': hashlib.sha256(output.encode()).hexdigest()}
        if owner == 'CCT':
            frozen = freeze_executed_cct(output)
            if frozen:
                evidence['systems'][owner] = frozen
    platform = (ROOT / 'platform-support-service/docs/database/sql/platform-support-schema.sql').read_text()
    tables = create_tables(platform)
    additions = ['t_authentication_challenge', 't_file_upload', 't_file_scan_policy', 't_file_scan_requirement',
                 't_file_object', 't_file_owner_ref', 't_file_scan_result']
    outputs['platform-support-additions.sql'] = '-- REVIEW CANDIDATE. Existing tables must be absent or compared exactly; never overwrite them.\nSET NAMES utf8mb4;\n\n' + '\n\n'.join(tables[n][0] for n in additions) + '\n'
    outputs['migration-manifest.json'] = json.dumps(evidence, indent=2) + '\n'
    if write:
        for filename, output in outputs.items():
            (HERE / filename).write_text(output)
    return outputs


if __name__ == '__main__':
    generate()
