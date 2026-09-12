const fs=require('node:fs');
const {chromium}=require('/Users/kenny/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const dir='/tmp/rco16-stage5/';const runtime=JSON.parse(fs.readFileSync(dir+'bff-runtime.json'));
const rules='/decision-rule-sets/v1/rule-sets', dict='/decision-dictionaries/v1/';
const owned=fs.existsSync(dir+'owned.json')?JSON.parse(fs.readFileSync(dir+'owned.json')):{codes:[],keys:[],dictionaries:[],releases:[]};
const persist=()=>fs.writeFileSync(dir+'owned.json',JSON.stringify(owned,null,2));
const calls=[];
function routeCode(method,path){const p=path.split('?')[0];
 if(p.includes('/authoring-context/'))return 'operations-risk-rule-authoring-context';
 if(p.startsWith(dict)){const parts=p.slice(dict.length).split('/'),type=parts[0]==='rejection-reasons'?'rejection-reason':'subject-tag';return `operations-risk-${type}-${parts[2]==='references'?'references':parts[2]==='status'?'status':parts.length===1?(method==='GET'?'list':'create'):(method==='GET'?'detail':'update')}`;}
 if(p===rules)return 'operations-risk-rule-set-'+(method==='GET'?'list':'save');
 if(p.startsWith(rules+'/'))return 'operations-risk-rule-set-'+(p.endsWith('/versions')?'versions':p.endsWith('/release-references')?'release-references':p.endsWith('/freeze')?'freeze':method==='DELETE'?'delete':'detail');
 return 'operations-risk-test-candidate-'+(p.endsWith('/validate')?'validate':method==='POST'?'create':'list');
}
async function owner(method,path,body,key){const h={'Origin':'https://operations-console.local.test','X-Request-Id':'rco16-browser-'+crypto.randomUUID(),'X-Gateway-Realm':'OPERATIONS','X-Gateway-Channel':'OPERATIONS','X-Gateway-Actor-Type':'PLATFORM_USER','X-Gateway-Actor-Subject':'10001','X-Gateway-Session-Id':'session_10001','X-Gateway-Route-Code':routeCode(method,path),'X-Gateway-Context-Type':'TENANT','X-Gateway-Tenant-Type':'INTERNAL','X-Gateway-Tenant-Id':'tenant_10001','X-Gateway-Tenant-Member-Id':'member_10001'};
 if(body!==undefined&&body!==null){h['Content-Type']='application/json';if(!path.startsWith(dict)){h['Idempotency-Key']=key||crypto.randomUUID();owned.keys.push(h['Idempotency-Key']);persist();}}
 const res=await fetch('http://127.0.0.1:'+runtime.port+path,{method,headers:h,body:body==null?undefined:typeof body==='string'?body:JSON.stringify(body)}),text=await res.text();
 const entry={method,path,status:res.status,requestId:res.headers.get('x-request-id')};calls.push(entry);
 return {status:res.status,contentType:res.headers.get('content-type'),text,json:JSON.parse(text)};
}
const resource=(id,code,type,name,path=null)=>({resourceId:String(id),resourceCode:code,resourceType:type,parentResourceId:null,displayName:name,frontendRoute:path,componentKey:path?code:null,sortOrder:id});
function resources(mode='all'){const items=[resource(1,'OPERATIONS_AUDIT','MENU','审计日志','/audit'),resource(2,'OPERATIONS_RISK_RULE_CONFIGURATION','MENU','规则配置','/risk/rule-configuration'),resource(3,'OPERATIONS_RISK_REJECTION_REASONS','MENU','拒绝原因','/risk/rejection-reasons'),resource(4,'OPERATIONS_RISK_SUBJECT_TAGS','MENU','主体标签','/risk/subject-tags')];
 for(const s of ['HISTORY','REFERENCES',...(mode==='readonly'?[]:['SAVE','FREEZE','DELETE'])])items.push(resource(items.length+10,'OPERATIONS_RISK_RULE_SET_'+s,'BUTTON',s));
 if(mode==='all')for(const type of ['REJECTION_REASON','SUBJECT_TAG'])for(const action of ['CREATE','UPDATE','STATUS'])items.push(resource(items.length+10,`OPERATIONS_RISK_${type}_${action}`,'BUTTON',action));return items;
}
async function browserSetup(mode='all',handler){const browser=await chromium.launch({headless:true,channel:'chrome'});const page=await browser.newPage({viewport:{width:1360,height:1000}});page.setDefaultTimeout(10000);const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>sessionStorage.setItem('paytech.operations.session.v1',JSON.stringify({version:1,accessToken:'synthetic-rco16-access',refreshToken:'synthetic-rco16-refresh',accessExpiresAt:Date.now()+600000,refreshExpiresAt:Date.now()+3600000})));
 await page.route('**/api/operations/**',async route=>{const req=route.request(),u=new URL(req.url()),path=u.pathname.replace('/api/operations','')+u.search;const reply=(body,status=200)=>route.fulfill({status,contentType:status===200?'application/json':'application/problem+json',body:JSON.stringify(body)});
 if(path==='/auth/v1/context')return reply({accountId:'10001',sessionId:'session_10001',accountType:'HUMAN',emailMasked:null,accountStatus:'ACTIVE',tenantId:null,memberId:null,passwordChangeRequired:false,bootstrapAdministratorRequired:false,integrationMode:'INTERNAL'});
 if(path==='/authorization/v1/navigation')return reply({resources:resources(mode)});
 if(path==='/audit/v1/events')return reply({items:[],page:1,size:10,total:0});
 if(handler&&await handler({route,req,path,reply}))return;
 const result=await owner(req.method(),path,req.postData(),req.headers()['idempotency-key']);return route.fulfill({status:result.status,contentType:result.contentType,body:result.text});});return{browser,page,errors};
}
module.exports={dir,owned,persist,calls,owner,browserSetup,rules,dict};
