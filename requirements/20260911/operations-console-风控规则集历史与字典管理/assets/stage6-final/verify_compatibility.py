"""读取 test 控制面并使用已部署 Platform / 候选 Gateway 制品离线验证，不改变运行环境。"""
from pathlib import Path
import copy, hashlib, json, re, subprocess, sys, zipfile

ROOT = Path(__file__).resolve().parents[5]
REQ = Path(__file__).resolve().parents[2]
WORK = Path('/tmp/rco16-stage6-final')
sys.path.insert(0, str(ROOT / 'test-environment/scripts'))
from initialize import mysql

fields = {
    'targets':('t_gateway_target','target_id target_service_code discovery_service_name channel status'),
    'routes':('t_gateway_route','route_id route_code channel http_method external_path_template downstream_path_template target_id security_profile_code authorization_mode permission_id required_scope_code request_cookie_mode response_cookie_mode body_limit_bytes body_receive_timeout_millis response_timeout_millis status'),
    'permissions':('t_permission','permission_id permission_code'),
    'headers':('t_gateway_route_header','route_id header_name header_use'),
    'queryParameters':('t_gateway_route_query_param','route_id parameter_name'),
    'guardRules':('t_gateway_guard_rule','route_id rule_type dimension capacity refill_tokens refill_period_millis concurrency_limit')}


def camel(name):
    return re.sub('_([a-z])', lambda m:m[1].upper(), name)


queries = []
for key, (table, names) in fields.items():
    pairs = ','.join("'" + camel(col) + "'," + col for col in names.split())
    queries.append("SELECT JSON_OBJECT('kind','" + key + "','row',JSON_OBJECT(" + pairs + ')) FROM ' + table + ';')
projection = {k:[] for k in fields}
for line in mysql('START TRANSACTION READ ONLY;\n' + '\n'.join(queries) + '\nCOMMIT;', timeout_seconds=30).splitlines():
    row = json.loads(line); projection[row['kind']].append(row['row'])
assert len(projection['routes']) == 37
scenarios = {'before':copy.deepcopy(projection)}
for route in projection['routes']:
    if route['routeCode'].startswith('operations-risk-'): route['status'] = 'DISABLED'
scenarios['maintenance'] = copy.deepcopy(projection)
registration = json.loads((REQ / 'db/platform-support-service/registration.json').read_text())
for key, (table, names) in fields.items():
    if table not in registration['tables']: continue
    block = registration['tables'][table]
    for values in block['rows']:
        row = dict(zip(block['columns'], values))
        projection[key].append({camel(col):int(row[col]) if isinstance(row[col],str) and re.fullmatch(r'\d{18}',row[col]) else row[col] for col in names.split()})
scenarios['registered-disabled'] = copy.deepcopy(projection)
for route in projection['routes']:
    if route['routeCode'].startswith('operations-risk-'): route['status'] = 'ACTIVE'
scenarios['enabled'] = copy.deepcopy(projection)
for name, data in scenarios.items(): (WORK / (name + '.json')).write_text(json.dumps(data, indent=2) + '\n')
reports = {}
for label, jar, helper in [
    ('deployedPlatform', WORK / 'platform-support-service-deployed.jar', REQ / 'assets/stage6/preparation-scripts/CheckPlatformSnapshot.java'),
    ('candidateGateway', ROOT / 'api-gateway/api-gateway-web/target/api-gateway-web-1.0.0-SNAPSHOT-exec.jar', REQ / 'assets/stage6-final/CheckGatewayCandidate.java'),
    ('deployedGatewayRecovery', WORK / 'api-gateway-deployed.jar', REQ / 'assets/stage6/preparation-scripts/CheckGatewaySnapshot.java')]:
    dest = WORK / (label + '-expanded'); dest.mkdir(exist_ok=True)
    with zipfile.ZipFile(jar) as z: z.extractall(dest)
    boot = dest / 'BOOT-INF'
    cp = ':'.join([str(boot/'classes'), *(str(p) for p in sorted((boot/'lib').glob('*.jar')))])
    suffix = '.json' if label == 'deployedPlatform' else '.json.snapshot.json'
    names = list(scenarios)[:3] if label == 'deployedGatewayRecovery' else list(scenarios)
    result = subprocess.run(['/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home/bin/java', '--class-path', cp, str(helper), *(str(WORK/(n+suffix)) for n in names)], text=True, capture_output=True)
    (WORK / (label + '-compiler.log')).write_text(result.stdout + result.stderr)
    assert result.returncode == 0, label + ': inspect private local compiler log'
    counts = [int(line) for line in result.stdout.splitlines() if line.isdigit()]
    assert counts == ([37,27,27] if label == 'deployedGatewayRecovery' else [37,27,27,51]), (label,counts)
    reports[label] = {'jarSha256':hashlib.sha256(jar.read_bytes()).hexdigest(), 'routeCounts':dict(zip(names,counts))}
report = {'scope':'Read-only actual test catalog, activation projected in memory; deployed Platform and actual candidate Gateway compiler, no runtime deployment or SQL execution.', 'compilers':reports,
    'snapshotSha256':{n:hashlib.sha256((WORK/(n+'.json.snapshot.json')).read_bytes()).hexdigest() for n in scenarios}}
(REQ/'assets/stage6-final/compatibility.json').write_text(json.dumps(report,indent=2)+'\n')
print(json.dumps(report,indent=2))
