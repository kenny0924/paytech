"""Manual real ingress acceptance; credentials remain in the private runtime directory."""
import json,uuid,urllib.request,urllib.error,time
from pathlib import Path
runtime=Path('/tmp/plt-client-api-stage5');platform=json.loads((runtime/'platform.json').read_text());origin=json.loads((runtime/'web.json').read_text())['origin'];results=[]
def call(name,method,path,token=None,body=None,expected=200,key=None):
 headers={'Origin':origin,'Content-Type':'application/json'}
 if token:headers['Authorization']='Bearer '+token
 if body is not None:headers['Idempotency-Key']=key or uuid.uuid4().hex
 request=urllib.request.Request(origin+'/api/operations'+path,method=method,headers=headers,data=None if body is None else json.dumps(body).encode())
 for attempt in range(15):
  try:response=urllib.request.urlopen(request,timeout=15)
  except urllib.error.HTTPError as e:response=e
  if response.status!=429:break
  delay=min(30,max(1,int(response.headers.get('Retry-After','6'))))
  response.read();time.sleep(delay)
 data=response.read();value=json.loads(data) if data else None
 assert response.status==expected,(name,response.status,value.get('code') if isinstance(value,dict) else None)
 if expected>=400:assert value['requestId']==response.headers['X-Request-Id']
 results.append({'name':name,'method':method,'path':path,'status':response.status})
 return value

def fixture(kind):
 with urllib.request.urlopen(f"http://127.0.0.1:{platform['controlPort']}/fixture?{kind}") as r:return json.load(r)['code']
def login(kind):return call('login-'+kind,'POST','/auth/v1/login',body={'loginIdentifier':platform[kind],'password':platform['password']})['accessToken']
if __name__=='__main__':
 admin,reader,denied=[login(k) for k in ['admin','reader','denied']]
 credentials=json.loads((runtime/'credentials.json').read_text());cid=credentials[0]['credentialId']
 C='/api-credential-admin/v1/credentials';S='/api-scope-admin/v1/scopes'
 for identity,token in [('admin',admin),('reader',reader)]:
  page=call(identity+'-credentials','GET',C,token);assert page['total']==3
  detail=call(identity+'-credential-detail','GET',C+'/'+cid,token);assert detail['credentialId']==cid
  grants=call(identity+'-grants','GET',C+'/'+cid+'/scopes',token);assert grants['total']==2
  assert all(c['credential'] not in json.dumps([page,detail,grants]) for c in credentials)
 call('denied-credential','GET',C,denied,expected=403)
 call('reader-revoke-denied','POST',C+'/'+cid+'/revoke',reader,{'expectedVersion':1,'reason':'只读人员不能撤销'},expected=403)
 call('denied-scope','GET',S,denied,expected=403)
 call('route-only-scope-options','GET','/api-scope-admin/v1/scope-options?keyword=orders',denied)
 code=fixture('SCOPE');body={'scopeCode':code,'displayName':'综合验收目录','description':'用于真实调用链验证','reason':'本机验收'};key=uuid.uuid4().hex
 call('reader-create-denied','POST',S,reader,body,expected=403)
 created=call('scope-create','POST',S,admin,body,key=key)
 assert created==call('scope-create-replay','POST',S,admin,body,key=key)
 call('scope-detail','GET',S+'/'+code+'?referenceKind=NONE',admin)
 call('scope-edit','PUT',S+'/'+code,admin,{'displayName':'综合验收目录已更新','description':None,'expectedVersion':1,'reason':'清空说明'})
 call('scope-conflict','PUT',S+'/'+code,admin,{'displayName':'旧版本','description':None,'expectedVersion':1,'reason':'冲突'},expected=409)
 for kind in ['ROUTE','CREDENTIAL']:
  v=call('scope-reference-'+kind,'GET',S+'/orders.read?referenceKind='+kind+'&page=1&size=10',admin)
  assert v['references']['kind']==kind
 call('reference-permission-remains-independent','GET',S+'/orders.read?referenceKind=ROUTE',denied,expected=403)
 call('object-audit','GET','/audit/v1/events?resourceType=API_SCOPE&resourceId='+code,admin)
 before=call('scope-current-count','GET',S,admin)['total']
 needed=max(0,14-before)
 for i in range(needed):
  sc=fixture('SCOPE');call('prepare-page-'+str(i),'POST',S,admin,{'scopeCode':sc,'displayName':f'分页验收目录 {i+1:02}','reason':'本机页面分页验收'})
 page=call('scope-page-two','GET',S+'?page=2&size=10',admin);assert page['total']==before+needed and len(page['items'])==min(10,page['total']-10)
 call('scope-filter-literal','GET',S+'?keyword=orders',admin)
 call('invalid-query','GET',C+'?unknown=1',admin,expected=400)
 runtime.joinpath('integration.json').write_text(json.dumps({'checks':len(results),'results':results,'browserScopeCode':code},ensure_ascii=False,indent=2)+'\n')
 print(json.dumps({'checks':len(results),'status':'PASS','browserScopeCode':code},ensure_ascii=False))
