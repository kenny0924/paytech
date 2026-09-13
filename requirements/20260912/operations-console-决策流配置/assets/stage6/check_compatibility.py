"""Compile projected test catalogs with the actual deployed Platform/Gateway JARs offline."""
from preflight import ROOT, REQ, OUT, registration
from pathlib import Path
import copy, hashlib, json, re, subprocess, zipfile

WORK = Path('/tmp/rco17-stage6')
WORK.mkdir(exist_ok=True)
old = ROOT/'requirements/20260911/operations-console-风控规则集历史与字典管理'
fields = {
    'targets':('t_gateway_target','target_id target_service_code discovery_service_name channel status'),
    'routes':('t_gateway_route','route_id route_code channel http_method external_path_template downstream_path_template target_id security_profile_code authorization_mode permission_id required_scope_code request_cookie_mode response_cookie_mode body_limit_bytes body_receive_timeout_millis response_timeout_millis status'),
    'permissions':('t_permission','permission_id permission_code'),
    'headers':('t_gateway_route_header','route_id header_name header_use'),
    'queryParameters':('t_gateway_route_query_param','route_id parameter_name'),
    'guardRules':('t_gateway_guard_rule','route_id rule_type dimension capacity refill_tokens refill_period_millis concurrency_limit')}
catalog = json.loads((OUT/'preflight.json').read_text())['platform']['catalog']
def camel(name):
    return re.sub('_([a-z])', lambda m:m[1].upper(), name)
def project(source):
    return {key:[{camel(col):row[col] for col in cols.split()} for row in source.get(table,[])] for key,(table,cols) in fields.items()}
projection = project(catalog)
scenarios = {'before':copy.deepcopy(projection)}
for row in projection['routes']:
    if row['routeCode'].startswith('operations-risk-'): row['status']='DISABLED'
scenarios['maintenance'] = copy.deepcopy(projection)
for key, rows in project(registration()).items(): projection[key].extend(rows)
scenarios['registered-disabled'] = copy.deepcopy(projection)
for row in projection['routes']:
    if row['routeCode'].startswith('operations-risk-'): row['status']='ACTIVE'
scenarios['enabled'] = copy.deepcopy(projection)
for name,data in scenarios.items(): (WORK/(name+'.json')).write_text(json.dumps(data)+'\n')
reports = {}
for app,helper in [('platform-support-service',old/'assets/stage6/preparation-scripts/CheckPlatformSnapshot.java'),('api-gateway',old/'assets/stage6-final/CheckGatewayCandidate.java')]:
    jar = WORK/(app+'-deployed.jar')
    subprocess.run(['docker','cp','mor-test-'+app+'-1:/app/application.jar',str(jar)], check=True, capture_output=True)
    dest = WORK/(app+'-expanded')
    with zipfile.ZipFile(jar) as z: z.extractall(dest)
    boot = dest/'BOOT-INF'
    cp = ':'.join([str(boot/'classes'),*(str(p) for p in sorted((boot/'lib').glob('*.jar')))])
    suffix = '.json' if app=='platform-support-service' else '.json.snapshot.json'
    result = subprocess.run(['/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/java','--class-path',cp,str(helper),*(str(WORK/(name+suffix)) for name in scenarios)], text=True, capture_output=True)
    (WORK/(app+'-compiler.log')).write_text(result.stdout+result.stderr)
    assert result.returncode==0, app+': inspect local compiler log'
    counts = [int(line) for line in result.stdout.splitlines() if line.isdigit()]
    assert counts==[51,27,27,61], (app,counts)
    reports[app]={'deployedJarSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'routeCounts':dict(zip(scenarios,counts)),'helper':str(helper.relative_to(ROOT))}
(OUT/'compatibility.json').write_text(json.dumps({'scope':'Actual deployed compilers, offline projection only; no service replacement or SQL writes', 'compilers':reports},indent=2)+'\n')
print(json.dumps(reports))
