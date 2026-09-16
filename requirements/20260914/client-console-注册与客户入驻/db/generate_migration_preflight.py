"""Generate owner-local read-only inventory, never connect to a database."""
from pathlib import Path
import hashlib
import json

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]
projection = json.loads((HERE / 'target-field-projection.json').read_text())
models = {}
sources = {}
for owner, service, model_name in [
    ('CCT', 'client-contract-service', 'client-contract'),
    ('Risk', 'risk-compliance-service', 'risk-compliance'),
    ('Platform', 'platform-support-service', 'platform-support')]:
    path = ROOT / service / 'docs/database' / (model_name + '.pdma.json')
    models[owner] = {e['defKey']: e for e in json.loads(path.read_text())['entities']}
    sources[owner] = dict(path=str(path.relative_to(ROOT)), sha256=hashlib.sha256(path.read_bytes()).hexdigest())

cct_names = [t['name'] for t in projection['systems']['CCT']['tables']]
cct_names += ['t_party', 't_client', 't_client_business_classification',
              't_partner_application', 't_operating_entity_application', 't_agreement_party']
risk_names = [name for name in models['Risk']
              if name == 't_due_diligence_case'
              or name.startswith('t_qualification_')
              or name.startswith('t_dd_') and not name.startswith('t_dd_program')]
platform_names = [x['name'] for x in json.loads((HERE / 'platform-additions-inventory.json').read_text())['tables']]

def count_query(name):
    return "SELECT '%s' AS table_name, COUNT(*) AS row_count FROM `%s`;" % (name, name)

def metadata(names):
    literal = ','.join("'%s'" % n for n in names)
    return [
        "SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE,COLUMN_DEFAULT,COLUMN_COMMENT "
        "FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN (%s) "
        "ORDER BY TABLE_NAME,ORDINAL_POSITION;" % literal,
        "SELECT TABLE_NAME,INDEX_NAME,NON_UNIQUE,SEQ_IN_INDEX,COLUMN_NAME "
        "FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN (%s) "
        "ORDER BY TABLE_NAME,INDEX_NAME,SEQ_IN_INDEX;" % literal,
    ]

header = ['-- READ ONLY. Review against the selected Owner database; no cross-database queries.',
          '-- Missing tables/columns are a baseline mismatch, not zero rows. Stop on any SQL error.',
          'SELECT DATABASE() AS selected_database;']
checks = {}
for owner, names, filename in [('CCT', cct_names, 'cct-migration-preflight.sql'),
                               ('Risk', risk_names, 'risk-migration-preflight.sql')]:
    assert all(n in models[owner] for n in names)
    lines = header + metadata(names) + ['-- Exact row counts for the conservative empty-data gate.']
    lines += [count_query(n) for n in names]
    references = []
    for name in names:
        for f in models[owner][name]['fields']:
            col = f['defKey']
            target = None
            if owner == 'CCT' and col in {'profile_id', 'base_party_profile_id', 'target_party_profile_id', 'party_profile_id'} and name != 't_party_profile':
                target = ('t_party_profile', 'profile_id')
            elif owner == 'CCT' and col in {'business_profile_id', 'base_business_profile_id', 'target_business_profile_id'} and name != 't_client_business_profile':
                target = ('t_client_business_profile', 'business_profile_id')
            elif owner == 'CCT' and name == 't_party_profile_identifier' and col == 'identity_key_id':
                target = ('t_party_identity_key', 'identity_key_id')
            elif owner == 'Risk' and not f.get('primaryKey'):
                if col in {'case_id', 'parent_case_id', 'child_case_id'}:
                    target = ('t_due_diligence_case', 'case_id')
                elif col == 'case_subject_id':
                    target = ('t_dd_case_subject', 'case_subject_id')
                elif col == 'requirement_id':
                    target = ('t_dd_requirement', 'requirement_id')
                elif col in {'decision_id', 'accepted_decision_id'}:
                    target = ('t_qualification_decision', 'decision_id')
            if target:
                target_table, target_col = target
                lines.append("SELECT '%s.%s' AS reference_name,COUNT(*) AS missing_parent_count "
                    "FROM `%s` child LEFT JOIN `%s` parent ON child.`%s`=parent.`%s` "
                    "WHERE child.`%s` IS NOT NULL AND parent.`%s` IS NULL;" %
                    (name,col,name,target_table,col,target_col,col,target_col))
                references.append(dict(table=name,column=col,targetTable=target_table,targetColumn=target_col))
    if owner == 'Risk':
        lines += [
            "SELECT source_namespace,source_application_id,role_type,role_id,qualification_type,COUNT(*) AS duplicate_count "
            "FROM t_due_diligence_case WHERE case_origin='APPLICATION' "
            "GROUP BY source_namespace,source_application_id,role_type,role_id,qualification_type HAVING COUNT(*)>1;",
            "SELECT COUNT(*) AS missing_application_source FROM t_due_diligence_case "
            "WHERE case_origin='APPLICATION' AND (source_application_id IS NULL OR source_namespace IS NULL);",
        ]
    (HERE / filename).write_text('\n\n'.join(lines) + '\n')
    checks[owner] = dict(emptyGateTables=names, localReferences=references)

(HERE / 'platform-migration-preflight.sql').write_text('\n\n'.join(header + [
    '-- Metadata only: absent additions yield no metadata; existing tables require exact comparison.',
] + metadata(platform_names)) + '\n')
(HERE / 'migration-inventory.json').write_text(json.dumps(dict(
    status='READ_ONLY_REVIEW_NOT_EXECUTED', sources=sources, owners=checks,
    platformAdditionTables=platform_names,
    boundary='Candidate local reference inventory; not proof that cross-owner or historical references are absent. '
             'Existing data requires an environment-specific compatibility plan; never choose MAX(version).'),
    ensure_ascii=False, indent=2) + '\n')
print(json.dumps({owner:len(value['emptyGateTables']) for owner,value in checks.items()}))
