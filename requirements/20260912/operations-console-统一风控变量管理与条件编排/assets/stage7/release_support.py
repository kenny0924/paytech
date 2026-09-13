from pathlib import Path
import datetime,decimal,hashlib,json,subprocess,sys
import pymysql
ROOT=Path(__file__).resolve().parents[5]
REQ=Path(__file__).resolve().parents[2]
OUT=Path(__file__).resolve().parent
sys.path.insert(0,str(ROOT/'test-environment/scripts'))
from initialize import environment,risk_operations_rollout_lock

def run(args,timeout=60):
 r=subprocess.run(args,capture_output=True,text=True,timeout=timeout)
 if r.returncode: raise RuntimeError('Command failed; inspect local log: '+str(args[:3]))
 return r.stdout.strip()
def connect(db):return pymysql.connect(host='127.0.0.1',port=23306,user='root',password=environment()['MOR_MYSQL_ROOT_PASSWORD'],database=db,charset='utf8mb4',autocommit=False,connect_timeout=10,read_timeout=30,write_timeout=30)
def query(db,sql,args=None):
 with connect(db) as c:
  with c.cursor() as q:q.execute(sql,args);return q.fetchall()
def encode(v):
 if isinstance(v,(datetime.datetime,decimal.Decimal)):return str(v)
 if isinstance(v,bytes):return v.hex()
 raise TypeError(type(v).__name__)
def save(name,value):(OUT/name).write_text(json.dumps(value,ensure_ascii=False,indent=2,default=encode)+'\n')
def sha(path):return hashlib.sha256(Path(path).read_bytes()).hexdigest()
def containers():
 ids=run(['docker','ps','-q','--filter','label=com.docker.compose.project=mor-test']).splitlines()
 return [{ 'name':d['Name'].lstrip('/'),'id':d['Id'],'image':d['Image'],'startedAt':d['State']['StartedAt'],'health':d['State'].get('Health',{}).get('Status')} for d in json.loads(run(['docker','inspect',*ids]))]
def statements(source):
 result=[];buf=[];quote=None;i=0
 while i<len(source):
  ch=source[i]
  if quote:
   buf.append(ch)
   if ch=='\\' and i+1<len(source):i+=1;buf.append(source[i])
   elif ch==quote:
    if i+1<len(source) and source[i+1]==quote:i+=1;buf.append(source[i])
    else:quote=None
  elif source[i:i+2]=='--' and (i+2==len(source) or source[i+2].isspace()):
   while i<len(source) and source[i]!='\n':i+=1
   buf.append('\n');continue
  elif ch in "'\"`":quote=ch;buf.append(ch)
  elif ch==';':
   t=''.join(buf).strip();buf=[]
   if t:result.append(t)
  else:buf.append(ch)
  i+=1
 assert quote is None
 if ''.join(buf).strip():result.append(''.join(buf).strip())
 return result
