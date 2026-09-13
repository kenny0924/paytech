"""Explicitly authorized one-time RCO-17 test rollout. Never an application initializer."""
from pathlib import Path
import dataclasses, datetime, decimal, hashlib, json, os, re, subprocess, sys, time, urllib.request
import pymysql

HERE=Path(__file__).resolve().parent
REQ=HERE.parents[1]
ROOT=HERE.parents[4]
sys.path.insert(0,str(ROOT/'test-environment/scripts'))
sys.path.insert(0,str(REQ/'assets/stage6'))
from initialize import environment, risk_operations_rollout_lock
from gateway_snapshot_metrics import decode_prometheus
from gateway_snapshot_wait import ComposeFetcher
from preflight import registration

PACKAGE=json.loads((REQ/'assets/stage6/release-manifest.json').read_text())
BASELINE=json.loads((REQ/'assets/stage6/preflight.json').read_text())
IMAGES=json.loads((REQ/'assets/stage6/images.json').read_text())
COMPOSE=str(ROOT/'test-environment/compose.sh')
PLATFORM_SQL=REQ/'db/platform-support-service/test-release'
REPORT={'authorization':'User replied 对 to the exact stage 6 release package: backups, test SQL, 3 services and bounded synthetic acceptance. No Git/shared publication.', 'events':[], 'status':'IN_PROGRESS'}

def sha(data):return hashlib.sha256(data).hexdigest()
def event(name,**data):
    REPORT['events'].append({'at':datetime.datetime.now(datetime.timezone.utc).isoformat(),'step':name,**data})
    (HERE/'deployment.json').write_text(json.dumps(REPORT,ensure_ascii=False,indent=2)+'\n')
    print(name,flush=True)
def run(args,timeout=60):
    result=subprocess.run(args,capture_output=True,text=True,timeout=timeout)
    if result.returncode:raise RuntimeError('Command failed: '+str(args[:3])+'; output withheld')
    return result.stdout.strip()
def connect(database):
    return pymysql.connect(host='127.0.0.1',port=23306,user='root',password=environment()['MOR_MYSQL_ROOT_PASSWORD'],database=database,charset='utf8mb4',autocommit=False,connect_timeout=10,read_timeout=30,write_timeout=30)
def rows(database,sql):
    with connect(database) as c:
        with c.cursor() as cursor:
            cursor.execute('START TRANSACTION READ ONLY')
            cursor.execute(sql);return cursor.fetchall()
def normalize(value):
    if isinstance(value,datetime.datetime):return value.isoformat(sep=' ',timespec='microseconds')
    if isinstance(value,decimal.Decimal):return str(value)
    if isinstance(value,bytes):return value.hex()
    return value
def snapshot(count):
    deadline=time.monotonic()+60
    while True:
        value=decode_prometheus(ComposeFetcher()('http://127.0.0.1:19080/actuator/prometheus',12))
        if value.valid and value.refresh_result=='SUCCESS' and value.failure_reason=='NONE' and not value.last_valid_in_use and value.operations_route_count==count:
            event('Gateway loaded '+str(count)+' routes',snapshot=dataclasses.asdict(value));return
        if time.monotonic()>=deadline:raise RuntimeError('Snapshot did not reach '+str(count))
        time.sleep(2)
def statements(source):
    # These reviewed plain SQL files contain no procedures or DELIMITER commands.
    result=[];buffer=[];quote=None;i=0
    while i<len(source):
        ch=source[i]
        if quote:
            buffer.append(ch)
            if ch=='\\' and i+1<len(source):i+=1;buffer.append(source[i])
            elif ch==quote:
                if i+1<len(source) and source[i+1]==quote:i+=1;buffer.append(source[i])
                else:quote=None
        elif source[i:i+2]=='--' and (i+2==len(source) or source[i+2].isspace()):
            while i<len(source) and source[i]!='\n':i+=1
            buffer.append('\n');continue
        elif ch in "'\"`":quote=ch;buffer.append(ch)
        elif ch==';':
            text=''.join(buffer).strip();buffer=[]
            if text:result.append(text)
        else:buffer.append(ch)
        i+=1
    assert quote is None
    if ''.join(buffer).strip():result.append(''.join(buffer).strip())
    return result
def checked(path):
    raw=path.read_bytes();assert sha(raw)==PACKAGE['fileSha256'][str(path.relative_to(ROOT))],str(path)
    return raw.decode()
def platform_transaction(name):
    path=PLATFORM_SQL/name;sql=checked(path);checks=[]
    expected=json.loads((PLATFORM_SQL/'manifest.json').read_text())['stages'][name]
    with connect('mor_platform') as c:
        try:
            with c.cursor() as cursor:
                updates=inserted=0
                for statement in statements(sql):
                    assert not statement.upper().startswith('COMMIT')
                    cursor.execute(statement)
                    if statement.startswith('UPDATE '):assert cursor.rowcount==1;updates+=1
                    elif statement.startswith('INSERT INTO '):inserted+=cursor.rowcount
                    elif statement.startswith('SELECT '):
                        values=cursor.fetchall();assert len(values)==1 and values[0][1]==1;checks.extend(values)
                assert updates==expected['updates'] and inserted==expected['insertedRows']
                if name=='02-registration.sql':
                    for table,values in registration().items():
                        key=next(iter(values[0]))
                        for value in values:
                            cols=[col for col in value if col not in ('create_time','update_time')]
                            cursor.execute('SELECT '+','.join(cols)+' FROM '+table+' WHERE '+key+'=%s',(value[key],))
                            assert cursor.fetchall()==(tuple(value[col] for col in cols),),table
            c.commit()
        except BaseException:c.rollback();raise
    event('Committed '+name,updates=updates,inserted=inserted,casChecks=len(checks))
def ddl(path):
    with connect('mor_risk') as c:
        c.autocommit(True)
        with c.cursor() as cursor:
            for n,statement in enumerate(statements(checked(path)),1):
                assert statement.startswith(('CREATE TABLE','ALTER TABLE'))
                cursor.execute(statement)
                event('Applied DDL statement',file=str(path.relative_to(REQ)),statement=n)
def risk_precheck():
    result=[]
    with connect('mor_risk') as c:
        with c.cursor() as cursor:
            cursor.execute('START TRANSACTION READ ONLY')
            for statement in statements(checked(REQ/'db/risk-compliance-service/simplification/precheck.sql')):
                cursor.execute(statement);result.extend(cursor.fetchall())
    assert [[str(v) for v in row] for row in result]==BASELINE['risk']['precheck']
    return dict(result)
def preserved():
    report={}
    for table in ['t_rule_set','t_decision_point_version','t_decision_point_input_field','t_decision_point_rule_set','t_decision_release','t_command_idempotency']:
        with connect('mor_risk') as c:
            with c.cursor() as cursor:
                cursor.execute('START TRANSACTION READ ONLY');cursor.execute('SELECT * FROM '+table+' ORDER BY 1')
                data=[[normalize(value) for value in row] for row in cursor.fetchall()]
                report[table]={'count':len(data),'sha256':sha(json.dumps(data,ensure_ascii=False).encode())}
    return report
def history(candidate):
    args=['python3',str(REQ/'assets/stage6/read_history.py')]+(['--candidate'] if candidate else [])
    run(args)
    name='candidate-history.json' if candidate else 'old-history.json'
    data=json.loads((REQ/'assets/stage6'/name).read_text())
    (HERE/('migrated-history.json' if candidate else 'before-history.json')).write_text(json.dumps(data,indent=2)+'\n')
    event('New Reader matches baseline' if candidate else 'Old Reader baseline captured',rules=len(data['rules']),releases=len(data['releases']))
def backfill():
    base=REQ/'db/risk-compliance-service/simplification';checks=[]
    with connect('mor_risk') as c:
        try:
            with c.cursor() as cursor:
                for path in [base/'dml.sql',base/'postcheck.sql']:
                    for statement in statements(checked(path)):
                        cursor.execute(statement)
                        if statement.startswith('SELECT '):checks.extend(cursor.fetchall())
                counts=dict(checks)
                assert counts.pop('backfilled_rule_versions')==33
                assert counts.pop('backfilled_context_operands')==418
                assert counts.pop('backfilled_context_parameters')==11
                assert all(v==0 for v in counts.values()) and len(counts)==6
            c.commit()
        except BaseException:c.rollback();raise
    event('Risk backfill committed after same-transaction checks',metrics=dict(checks))
def backup():
    directory=Path.home()/'Code-MoR-backups'/('rco17-test-'+datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ'))
    directory.mkdir(mode=0o700)
    tables=list(BASELINE['platform']['catalog'])
    objects=[('risk-schema.sql','mor_risk',[],True,74),('risk-migrating-tables.sql','mor_risk',['t_rule_set_version','t_rule_operand','t_rule_action_parameter'],False,3),('platform-control-plane.sql','mor_platform',tables,False,9)]
    records=[]
    for name,db,selected,only_schema,count in objects:
        command='MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump --protocol=socket -uroot --default-character-set=utf8mb4 --no-tablespaces --single-transaction --skip-lock-tables --set-gtid-purged=OFF --hex-blob "$@"'
        args=['docker','exec','mor-test-mysql-1','sh','-c',command,'mysqldump']+(['--no-data'] if only_schema else [])+[db,*selected]
        result=subprocess.run(args,capture_output=True,timeout=60)
        assert result.returncode==0 and result.stdout.count(b'CREATE TABLE')==count,'Backup failed'
        path=directory/name
        with os.fdopen(os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600),'wb') as f:f.write(result.stdout)
        assert path.stat().st_size>0
        records.append({'file':str(path),'bytes':path.stat().st_size,'sha256':sha(path.read_bytes()),'tables':count})
    REPORT['backupDirectory']=str(directory)
    event('Scoped backups completed',files=records)
def main():
    assert not (HERE/'deployment.json').exists(),'Existing rollout record: resume the exact checkpoint instead of replaying'
    with risk_operations_rollout_lock():
        assert run(['docker','context','show'])=='desktop-linux'
        run([COMPOSE,'config','--quiet'])
        for path,value in PACKAGE['fileSha256'].items():assert sha((ROOT/path).read_bytes())==value,path
        for path,value in IMAGES['sourceFilesSha256'].items():assert sha((ROOT/path).read_bytes())==value,path
        for service,value in IMAGES['images'].items():assert run(['docker','image','inspect','--format','{{.Id}}',value['tag']])==value['imageId']
        for original in BASELINE['containers']:
            c=json.loads(run(['docker','inspect',original['containerId']]))[0]
            assert c['Image']==original['imageId'] and c['State']['Health']['Status']=='healthy'
        for db in ['mor_risk','mor_platform']:
            expected=BASELINE['risk' if db=='mor_risk' else 'platform']['identity'][0]
            assert tuple(expected)==rows(db,'SELECT DATABASE(),@@server_uuid,@@version')[0]
        assert rows('mor_platform','SELECT role_id,version FROM t_role WHERE role_id IN (900000000000000510,900000000000000511) ORDER BY role_id')==((900000000000000510,12),(900000000000000511,6))
        assert rows('mor_platform','SELECT COUNT(*) FROM t_gateway_route')[0][0]==51
        assert rows('mor_risk',"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")[0][0]==74
        risk_precheck();before=preserved();event('Approved target and migration prerequisites confirmed',preserved=before)
        backup()
        platform_transaction('01-maintenance.sql');snapshot(27)
        run([COMPOSE,'stop','--timeout','30','operations-console-web','operations-console-bff','risk-service'],110)
        assert rows('mor_risk',"SELECT COUNT(*) FROM information_schema.processlist WHERE DB='mor_risk' AND USER<>'root'")[0][0]==0
        assert preserved()==before
        event('Three apps stopped; no Risk writer connections; history unchanged')
        history(False);risk_precheck()
        ddl(REQ/'db/risk-compliance-service/ddl.sql')
        ddl(REQ/'db/risk-compliance-service/simplification/ddl.sql')
        backfill();history(True)
        ddl(REQ/'db/risk-compliance-service/simplification/finalize-ddl.sql')
        assert preserved()==before
        event('Final DDL complete; original roots, history, releases and receipts unchanged')
        platform_transaction('02-registration.sql');snapshot(27)
        for service in ['risk-service','operations-console-bff','operations-console-web']:
            item=IMAGES['images'][service]
            run(['docker','tag',item['imageId'],'mor-test/'+service+':development'])
            run([COMPOSE,'up','-d','--no-deps','--no-build','--pull','never','--force-recreate',service],60)
            deadline=time.monotonic()+240
            while True:
                c=json.loads(run(['docker','inspect','mor-test-'+service+'-1']))[0]
                assert c['Image']==item['imageId']
                if c['State'].get('Health',{}).get('Status')=='healthy':break
                if time.monotonic()>deadline or c['State']['Status'] in ['exited','dead']:raise RuntimeError(service+' not healthy')
                time.sleep(3)
            event(service+' healthy',containerId=c['Id'],imageId=c['Image'])
        platform_transaction('03-enable.sql');snapshot(61)
        assert rows('mor_risk',"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()")[0][0]==78
        assert rows('mor_platform',"SELECT COUNT(*) FROM t_console_resource WHERE channel='OPERATIONS' AND status='ACTIVE'")[0][0]==35
        with urllib.request.urlopen('http://localhost:28080',timeout=15) as response:index=sha(response.read())
        assert index==IMAGES['images']['operations-console-web']['distFilesSha256']['index.html']
        for original in BASELINE['containers']:
            if original['service'] in IMAGES['images']:continue
            c=json.loads(run(['docker','inspect',original['containerId']]))[0]
            assert c['Image']==original['imageId'] and c['State']['StartedAt']==original['startedAt']
        REPORT['status']='DEPLOYED_PENDING_BOUNDED_ACCEPTANCE'
        event('Three services deployed; 61 routes and new public HTML verified',indexSha256=index)

if __name__=='__main__':
    try:main()
    except BaseException as error:
        REPORT['status']='STOPPED_AT_FAILED_CHECKPOINT'
        event('Stopped without replaying SQL',errorType=type(error).__name__)
        raise
