from release_support import *
assert run(['docker','context','show'])=='desktop-linux'
run([str(ROOT/'test-environment/compose.sh'),'config','--quiet'])
cs=containers();assert len(cs)==11 and all(c['health']=='healthy' for c in cs)
assert query('mor_risk',"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='mor_risk' AND table_name IN ('t_risk_variable','t_risk_variable_member')")==((0,),)
assert query('mor_risk',"SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='mor_risk' AND ((table_name='t_rule_operand' AND column_name IN ('variable_code','numeric_unit','currency_code','currency_variable_code')) OR (table_name='t_rule_action_parameter' AND column_name='variable_code'))")==((0,),)
roles=query('mor_platform',"SELECT role_id,version FROM t_role WHERE role_code='PLATFORM_ADMIN' AND scope_key='PLATFORM' AND status='ACTIVE'");assert len(roles)==1
for table,col,code in [('t_permission','permission_code','RISK_DECISION_CONFIG_READ'),('t_console_resource','resource_code','OPERATIONS_RISK_MANAGEMENT'),('t_gateway_target','target_service_code','operations-console-bff')]:
 assert query('mor_platform',f"SELECT COUNT(*) FROM {table} WHERE {col}=%s AND status='ACTIVE'",(code,))==((1,),)
for table in ['t_permission','t_role_permission','t_console_resource','t_gateway_route','t_gateway_route_header','t_gateway_route_query_param','t_gateway_guard_rule']:
 col=query('mor_platform','SELECT COLUMN_NAME FROM information_schema.key_column_usage WHERE table_schema=%s AND table_name=%s AND constraint_name=%s',('mor_platform',table,'PRIMARY'))[0][0]
 assert query('mor_platform',f'SELECT COUNT(*) FROM {table} WHERE {col} BETWEEN 900000000000031000 AND 900000000000034999')==((0,),),table
assert query('mor_platform',"SELECT COUNT(*) FROM t_gateway_route WHERE external_path_template LIKE '/api/operations/decision-variables/%'")==((0,),)
rule=query('mor_risk',"SELECT rule_set_id,current_rule_set_version_id,version FROM t_rule_set WHERE rule_set_code='DEMO_FLOW_AMOUNT_CHECK'")
flow=query('mor_risk',"SELECT flow_id,current_flow_version_id,version FROM t_decision_flow WHERE flow_code='DEMO_CHECKOUT_BRANCH_FLOW'")
assert len(rule)==len(flow)==1
report={'at':str(datetime.datetime.now(datetime.timezone.utc)),'containers':cs,'admin':roles[0],'demoRule':rule[0],'demoFlow':flow[0],'activeRoutes':query('mor_platform',"SELECT COUNT(*) FROM t_gateway_route WHERE channel='OPERATIONS' AND status='ACTIVE'")[0][0],'activeResources':query('mor_platform',"SELECT COUNT(*) FROM t_console_resource WHERE channel='OPERATIONS' AND status='ACTIVE'")[0][0],'sqlSha256':{str(p.relative_to(REQ)):sha(p) for p in [REQ/'db/risk-compliance-service/ddl.sql',REQ/'db/risk-compliance-service/dml.sql',REQ/'db/platform-support-service/dml.sql']}}
save('preflight.json',report);print({k:v for k,v in report.items() if k not in ['containers','sqlSha256']})
