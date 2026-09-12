from pathlib import Path
import json,subprocess,hashlib,re,datetime,sys,xml.etree.ElementTree as E
root=Path('/Users/kenny/Desktop/Workspace/Code-MoR');w=Path('/tmp/rco16-stage6');req=root/'requirements/20260911/operations-console-风控规则集历史与字典管理';out=req/'assets/stage6';base=json.loads((w/'preflight.json').read_text())
containers=[]
for old in base['containers']:
 c=json.loads(subprocess.check_output(['docker','inspect','mor-test-'+old['service']+'-1'],text=True))[0]
 assert c['Id']==old['containerId'] and c['Image']==old['imageId'] and c['State']['StartedAt']==old['startedAt']
 if old['service'] in ['risk-service','operations-console-bff','operations-console-web']:
  assert subprocess.check_output(['docker','image','inspect','--format','{{.Id}}',old['imageTag']],text=True).strip()==old['imageId']
 containers.append(old['service'])
sys.path.insert(0,str(root/'test-environment/scripts'));from initialize import mysql
def q(db,sql):return [s.split('\t') for s in mysql('START TRANSACTION READ ONLY;'+sql+'COMMIT;',database=db,timeout_seconds=30).splitlines()]
assert q('mor_risk','SELECT rule_set_id,decision_point_id,current_rule_set_version_id,status,version FROM t_rule_set ORDER BY rule_set_id;')==base['risk']['roots']
assert q('mor_risk','SELECT rule_set_version_id,version_no,status,content_format,content_hash FROM t_rule_set_version ORDER BY rule_set_version_id;')==base['risk']['versions']
assert q('mor_risk',"SELECT COUNT(*),SUM(table_name IN ('t_rule_reject_reason','t_rule_subject_tag')) FROM information_schema.tables WHERE table_schema=DATABASE();")==[['72','0']]
assert q('mor_platform',"SELECT (SELECT COUNT(*) FROM t_gateway_route),(SELECT COUNT(*) FROM t_console_resource WHERE channel='OPERATIONS' AND status='ACTIVE'),(SELECT version FROM t_role WHERE role_id=900000000000000510);")==[['37','22','9']]
links=[];missing=[]
for p in req.glob('*.md'):
 for target in re.findall(r'\]\(([^)]+)\)',p.read_text()):
  if '://' in target or target.startswith('#'):continue
  target=target.split('#',1)[0]
  if target:links.append(target)
  if target and not (p.parent/target).exists():missing.append(str(p.relative_to(root))+':'+target)
assert not missing,missing
verified=json.loads((req/'assets/stage5/verification.json').read_text());files=0
for repo,app in verified['applications'].items():
 for f,sha in app.get('filesSha256',{}).items():assert hashlib.sha256((root/repo/f).read_bytes()).hexdigest()==sha;files+=1
assert not subprocess.check_output(['git','status','--porcelain'],cwd=root/'api-gateway',text=True).strip(),'Gateway source changed'
for repo in ['risk-compliance-service','operations-console-bff','operations-console-web','platform-support-service']:
 subprocess.run(['git','diff','--check'],cwd=root/repo,check=True,capture_output=True)
subprocess.run(['git','diff','--check','--',str(req.relative_to(root)),'agent-docs/CURRENT_IMPLEMENTATION.md'],cwd=root,check=True,capture_output=True)
subprocess.run([str(root/'test-environment/compose.sh'),'config','--quiet'],check=True,capture_output=True)
report={'checkedAt':datetime.datetime.now(datetime.timezone.utc).isoformat(),'unchangedContainers':containers,'developmentTagsUnchanged':True,'riskTableCount':72,'newDictionaryTableCount':0,'rootVersionHashesUnchanged':True,'platformRoutes':37,'activeOperationsResources':22,'adminRoleVersion':9,'sourceFilesMatchedStage5':files,'gatewayWorkingTreeUnmodified':True,'requirementMarkdownLinksChecked':len(links),'diffChecksPassed':True,'composeQuietValidationPassed':True,'scope':'Read-only test verification. No test SQL, deployment, restart, commit, push or remote artifact publication.'}
(out/'final-checks.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps(report,ensure_ascii=False))
