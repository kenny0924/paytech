import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
const dir='/tmp/rco19-stage5', dist='/Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web/dist';
const bff=JSON.parse(fs.readFileSync(dir+'/bff-runtime.json'));
let mode='all', catalogFailure=false;const calls=[];
function routeCode(method,p){
 if(p.startsWith('/decision-variables/v1/')){if(p.endsWith('/authoring-context'))return 'operations-risk-variable-manage-context';let parts=p.slice('/decision-variables/v1/variables'.length).split('/').filter(Boolean);return 'operations-risk-variable-'+(parts.length===0?(method==='GET'?'list':'create'):parts[1]==='members'?(method==='GET'?'members':'members-replace'):parts[1]==='references'?'references':method==='GET'?'detail':method==='DELETE'?'delete':'update');}
 if(p.startsWith('/decision-flows/v1/')){const q=p.slice('/decision-flows/v1/');return 'operations-risk-flow-'+(q.startsWith('authoring-context')?'authoring-context':q.startsWith('rule-set-versions')?'rule-set-reference':q.startsWith('legacy')?'legacy-point':q==='flows'?(method==='GET'?'list':'save'):q==='flows/validate'?'validate':q.endsWith('/versions')?'history':q.includes('/versions/')?'version':method==='DELETE'?'delete':'detail');}
 if(p.startsWith('/decision-rule-sets/v1/'))return 'operations-risk-'+(p.includes('/authoring-context/')?'rule-authoring-context':p.endsWith('/versions')?'rule-set-versions':p.endsWith('/release-references')?'rule-set-release-references':p.endsWith('/freeze')?'rule-set-freeze':p==='/decision-rule-sets/v1/rule-sets'?(method==='GET'?'rule-set-list':'rule-set-save'):method==='DELETE'?'rule-set-delete':'rule-set-detail');
 if(p.startsWith('/decision-dictionaries/v1/')){const parts=p.slice('/decision-dictionaries/v1/'.length).split('/'),kind=parts[0]==='rejection-reasons'?'rejection-reason':'subject-tag';return 'operations-risk-'+kind+'-'+(parts[2]==='references'?'references':parts[2]==='status'?'status':parts.length===1?(method==='GET'?'list':'create'):method==='GET'?'detail':'update');}
 return p.includes('login')?'operations-login':p.includes('context')?'operations-context':'operations-navigation';
}
function resources(){const r=[];function add(code,name,url){r.push({resourceId:String(r.length+1),resourceCode:code,resourceType:url?'MENU':'BUTTON',parentResourceId:null,displayName:name,frontendRoute:url??null,componentKey:url?code:null,sortOrder:r.length});}
 for(const [code,name,url]of [['OPERATIONS_RISK_VARIABLES','变量管理','/risk/variables'],['OPERATIONS_RISK_RULE_CONFIGURATION','规则配置','/risk/rule-configuration'],['OPERATIONS_RISK_DECISION_FLOWS','决策流','/risk/decision-flows'],['OPERATIONS_RISK_SUBJECT_TAGS','主体标签','/risk/subject-tags'],['OPERATIONS_RISK_REJECTION_REASONS','拒绝原因','/risk/rejection-reasons']])add(code,name,url);
 for(const code of ['OPERATIONS_RISK_RULE_SET_HISTORY','OPERATIONS_RISK_RULE_SET_REFERENCES',...(mode==='read'?[]:['OPERATIONS_RISK_VARIABLE_MANAGE']),...(mode==='all'?['OPERATIONS_RISK_VARIABLE_DELETE','OPERATIONS_RISK_RULE_SET_SAVE','OPERATIONS_RISK_RULE_SET_DELETE','OPERATIONS_RISK_DECISION_FLOW_SAVE','OPERATIONS_RISK_DECISION_FLOW_DELETE']:[])])add(code,code);
 return {resources:r};}
const server=http.createServer(async(req,res)=>{try{const u=new URL(req.url,'http://127.0.0.1');
 const reply=(status,body)=>{res.writeHead(status,{'Content-Type':status>=400?'application/problem+json':'application/json','Cache-Control':'no-store'});res.end(JSON.stringify(body));};
 if(u.pathname==='/test-control'){mode=u.searchParams.get('mode')??mode;catalogFailure=u.searchParams.get('catalogFailure')==='true';await fetch('http://127.0.0.1:'+bff.controlPort+'/permissions?mode='+mode);return reply(200,{mode,catalogFailure});}
 if(u.pathname==='/test-evidence')return reply(200,{calls,platform:await(await fetch('http://127.0.0.1:'+bff.controlPort+'/evidence')).json()});
 if(u.pathname.startsWith('/api/operations/')){const p=u.pathname.slice('/api/operations'.length);
 if(p==='/authorization/v1/navigation')return reply(200,resources());
 if(catalogFailure&&p==='/decision-variables/v1/variables')return reply(503,{type:'about:blank',title:'Unavailable',detail:'本机验收目录故障',code:'SERVICE_UNAVAILABLE',requestId:crypto.randomUUID(),timestamp:new Date().toISOString()});
 const headers={'Origin':'https://operations-console.local.test','X-Request-Id':crypto.randomUUID(),'Content-Type':'application/json','X-Gateway-Realm':'OPERATIONS','X-Gateway-Channel':'OPERATIONS','X-Gateway-Route-Code':routeCode(req.method,p)};
 if(!p.endsWith('/login')&&!p.endsWith('/refresh'))Object.assign(headers,{'X-Gateway-Actor-Type':'PLATFORM_USER','X-Gateway-Actor-Subject':'10001','X-Gateway-Session-Id':'session_10001','X-Gateway-Context-Type':'TENANT','X-Gateway-Tenant-Type':'INTERNAL','X-Gateway-Tenant-Id':'tenant_10001','X-Gateway-Tenant-Member-Id':'member_10001'});
 if(req.headers['idempotency-key'])headers['Idempotency-Key']=req.headers['idempotency-key'];
 const chunks=[];for await(const c of req)chunks.push(c);const body=Buffer.concat(chunks);
 const upstream=await fetch('http://127.0.0.1:'+bff.port+p+u.search,{method:req.method,headers,body:body.length?body:undefined});const bytes=Buffer.from(await upstream.arrayBuffer());
 calls.push({method:req.method,path:p+u.search,status:upstream.status,routeCode:headers['X-Gateway-Route-Code']});
 res.writeHead(upstream.status,{'Content-Type':upstream.headers.get('content-type')??'application/json','Cache-Control':'no-store'});return res.end(bytes);}
 const file=u.pathname.startsWith('/assets/')?path.join(dist,u.pathname):path.join(dist,'index.html');if(!file.startsWith(dist+path.sep)||!fs.existsSync(file)){res.writeHead(404);return res.end();}
 const ext=path.extname(file);res.writeHead(200,{'Content-Type':({'.js':'text/javascript','.css':'text/css','.svg':'image/svg+xml','.html':'text/html'}[ext]??'application/octet-stream'),'Cache-Control':'no-store'});res.end(fs.readFileSync(file));
 }catch(e){res.writeHead(502,{'Content-Type':'application/problem+json'});res.end(JSON.stringify({code:'LOCAL_HARNESS_ERROR',detail:e.message}));}});
server.listen(0,'127.0.0.1',()=>{fs.writeFileSync(dir+'/web-runtime.json',JSON.stringify({port:server.address().port,pid:process.pid,dist}));console.log('local Web ready',server.address().port);});
