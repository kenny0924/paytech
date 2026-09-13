"""Read-only final schema, exact image and catalog checks after bounded acceptance cleanup."""
import deploy as d
import json

model=json.loads((d.ROOT/'risk-compliance-service/docs/database/risk-compliance.pdma.json').read_text())
new_tables={'t_decision_flow','t_decision_flow_version','t_decision_flow_node','t_decision_flow_edge'}
old_tables={'t_rule_set_version','t_rule_operand','t_rule_action_parameter'}
new_columns={('t_rule_set_version','input_contract_code'),('t_rule_set_version','input_contract_version'),('t_rule_operand','field_code'),('t_rule_action_parameter','field_code')}
report={'schema':{},'containers':[]}
for table in model['entities']:
    name=table['defKey']
    if name not in new_tables|old_tables:continue
    values=d.rows('mor_risk',"SELECT column_name,column_type,is_nullable,column_default,column_comment FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='"+name+"' ORDER BY ordinal_position")
    assert [row[0] for row in values]==[f['defKey'] for f in table['fields']],name+' fields'
    comment_differences=[]
    for field,row in zip(table['fields'],values):
        type_name=field['type'].lower()
        if field['len']!='':type_name+='('+str(field['len'])+(','+str(field['scale']) if field['scale']!='' else '')+')'
        if type_name=='boolean':type_name='tinyint(1)'
        assert row[1]==type_name,(name,field['defKey'],'type')
        assert row[2]==('NO' if field['notNull'] else 'YES'),(name,field['defKey'],'nullable')
        default=field['defaultValue'] or None
        # PDManer stores the SQL literal; information_schema returns its value.
        if default and default.startswith("'") and default.endswith("'"):
            default=default[1:-1].replace("''", "'")
        assert row[3]==default,(name,field['defKey'],'default')
        if row[4]!=field['comment']:
            if name in new_tables or (name,field['defKey']) in new_columns:raise AssertionError('New comment mismatch')
            comment_differences.append(field['defKey'])
    actual={}
    for index,unique,position,column,order in d.rows('mor_risk',"SELECT index_name,1-non_unique,seq_in_index,column_name,collation FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='"+name+"' ORDER BY index_name,seq_in_index"):
        actual.setdefault(index,{'unique':bool(unique),'fields':[]})['fields'].append([column,order])
    fields={f['id']:f['defKey'] for f in table['fields']}
    expected={i['defKey']:{'unique':i['unique'],'fields':[[fields[f['fieldDefKey']],f['ascOrDesc']] for f in i['fields']]} for i in table['indexes']}
    expected['PRIMARY']={'unique':True,'fields':[[f['defKey'],'A'] for f in table['fields'] if f['primaryKey']]}
    assert expected==actual,name+' indexes'
    report['schema'][name]={'columns':len(values),'indexes':len(actual),'oldCommentDifferencesPreserved':comment_differences}
    if name in new_tables:
        assert d.rows('mor_risk',"SELECT table_comment FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='"+name+"'")[0][0]==table['comment']
report['roleVersions']=d.rows('mor_platform','SELECT role_code,version FROM t_role WHERE role_id IN (900000000000000510,900000000000000511) ORDER BY role_id')
assert report['roleVersions']==(('PLATFORM_ADMIN',15),('OPERATIONS_VIEWER',8))
report['activeRoutes']=d.rows('mor_platform',"SELECT COUNT(*) FROM t_gateway_route WHERE status='ACTIVE'")[0][0]
assert report['activeRoutes']==61
for old in d.BASELINE['containers']:
    c=json.loads(d.run(['docker','inspect','mor-test-'+old['service']+'-1']))[0]
    assert c['State']['Health']['Status']=='healthy'
    expected=d.IMAGES['images'].get(old['service'],old)['imageId'];assert c['Image']==expected
    if old['service'] not in d.IMAGES['images']:assert c['Id']==old['containerId'] and c['State']['StartedAt']==old['startedAt']
    report['containers'].append({'service':old['service'],'containerId':c['Id'],'imageId':c['Image'],'health':'healthy'})
fixture=json.loads((d.HERE/'acceptance-fixture.json').read_text())
report['syntheticFlow']=d.rows('mor_risk','SELECT status,version FROM t_decision_flow WHERE flow_id='+fixture['flowVersion']['flowId'])
report['syntheticRule']=d.rows('mor_risk','SELECT status,version FROM t_rule_set WHERE rule_set_id='+fixture['ruleRoot']['ruleSetId'])
assert report['syntheticFlow']==(('DELETED',3),) and report['syntheticRule']==(('DELETED',2),)
report['runtimeFacts']=d.rows('mor_risk','SELECT (SELECT COUNT(*) FROM t_decision_evaluation),(SELECT COUNT(*) FROM t_action_effect)')
assert [[str(x) for x in row] for row in report['runtimeFacts']]==d.BASELINE['risk']['runtimeCounts']
report['readonlyAccountAcceptance']='Not rerun in test: both existing viewer accounts DISABLED, historical third CLOSED; no user/account/role changes made for acceptance. Local stage 5 evidence remains applicable.'
(d.HERE/'final-checks.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print('7 table structures and indexes match; 48 new comments match; 11 services healthy; 61 routes; sample roots deleted; no runtime facts created')
