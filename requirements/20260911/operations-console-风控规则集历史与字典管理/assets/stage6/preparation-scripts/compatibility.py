from preflight import root,work,req,query,registration
import json,re,subprocess,zipfile,hashlib,copy
from initialize import mysql
fields={
 'targets':('t_gateway_target','target_id target_service_code discovery_service_name channel status'),
 'routes':('t_gateway_route','route_id route_code channel http_method external_path_template downstream_path_template target_id security_profile_code authorization_mode permission_id required_scope_code request_cookie_mode response_cookie_mode body_limit_bytes body_receive_timeout_millis response_timeout_millis status'),
 'permissions':('t_permission','permission_id permission_code'),
 'headers':('t_gateway_route_header','route_id header_name header_use'),
 'queryParameters':('t_gateway_route_query_param','route_id parameter_name'),
 'guardRules':('t_gateway_guard_rule','route_id rule_type dimension capacity refill_tokens refill_period_millis concurrency_limit')}
projection={};registrations=registration()
for key,(table,names) in fields.items():
 parts=[]
 for col in names.split(): parts.extend(["'"+re.sub('_([a-z])',lambda m:m.group(1).upper(),col)+"'",col])
 projection[key]=[json.loads(line) for line in mysql('START TRANSACTION READ ONLY;SELECT JSON_OBJECT('+','.join(parts)+') FROM '+table+';COMMIT;',timeout_seconds=20).splitlines()]
scenarios={'before':copy.deepcopy(projection)}
for r in projection['routes']:
 if r['routeCode'].startswith('operations-risk-'):r['status']='DISABLED'
scenarios['maintenance']=copy.deepcopy(projection)
for key,(table,names) in fields.items():
 for row in registrations.get(table,[]): projection[key].append({re.sub('_([a-z])',lambda m:m.group(1).upper(),col):row[col] for col in names.split()})
scenarios['registered-disabled']=copy.deepcopy(projection)
for r in projection['routes']:
 if r['routeCode'].startswith('operations-risk-'):r['status']='ACTIVE'
scenarios['enabled']=copy.deepcopy(projection)
for name,data in scenarios.items(): (work/(name+'.json')).write_text(json.dumps(data,indent=2)+'\n')
java='/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/java';reports={}
for app,service in [('platform','platform-support-service'),('gateway','api-gateway')]:
 jar=work/(app+'-deployed.jar');subprocess.run(['docker','cp','mor-test-'+service+'-1:/app/application.jar',str(jar)],check=True,capture_output=True)
 dest=work/(app+'-deployed');dest.mkdir(exist_ok=True)
 with zipfile.ZipFile(jar) as z:z.extractall(dest)
 base=dest/'BOOT-INF';cp=':'.join([str(base/'classes'),*(str(p) for p in sorted((base/'lib').glob('*.jar')))])
 source=work/('CheckPlatformSnapshot.java' if app=='platform' else 'CheckGatewaySnapshot.java')
 paths=[str(work/(n+('.json' if app=='platform' else '.json.snapshot.json'))) for n in scenarios]
 result=subprocess.run([java,'--class-path',cp,str(source),*paths],text=True,capture_output=True)
 (work/(app+'-compiler.log')).write_text(result.stdout+result.stderr)
 assert result.returncode==0,app+' compiler failed; inspect log'
 counts=[int(s) for s in result.stdout.strip().splitlines() if s.isdigit()]; assert counts==[37,27,27,51],(app,counts)
 reports[app]={'deployedJarSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'routeCounts':dict(zip(scenarios,counts))}
(work/'compatibility.json').write_text(json.dumps({'scope':'Read actual test metadata and deployed JARs; compile offline only. No DB writes or service restart.','compilers':reports},indent=2)+'\n')
print(json.dumps(reports))
