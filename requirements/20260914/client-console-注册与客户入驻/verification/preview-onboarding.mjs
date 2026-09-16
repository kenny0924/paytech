/** Test-only browser protocol Simulator. No database, credentials, real rules, or public listener. */
import http from 'node:http';
import {randomBytes} from 'node:crypto';
import {readFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
const dist=fileURLToPath(new URL('../../../../client-console-web/dist/',import.meta.url));
const now='2026-09-16T08:00:00.000000Z';
const context={accountId:'1',sessionId:'2',emailMasked:'d***@example.test',emailVerified:true,contextType:'ACCOUNT',tenantId:null,memberId:null,allowedActions:['ONBOARDING','LOGOUT']};
// 仅用于 loopback 交互预览；独立名称与内存状态，绝不替代正式 Secure Refresh Cookie。
const sessions=new Map();let nextSession=10;
const sessionDuration=60*60*1000;
const portArgument=process.argv.slice(2);
if(portArgument.length>1 || (portArgument.length===1 && !/^--port=[1-9][0-9]{0,4}$/.test(portArgument[0])))throw Error('Use --port=<loopback port> or omit for a random port');
const requestedPort=portArgument.length?Number(portArgument[0].slice(7)):0;
if(requestedPort>65535)throw Error('Invalid preview port');
const cookieName=()=> 'client-preview-session-'+server.address().port;
const sessionCookie=(value,seconds)=>`${cookieName()}=${value}; HttpOnly; SameSite=Strict; Path=/api/client/auth/v1; Max-Age=${seconds}`;
function currentSession(value){const session=sessions.get(value);if(!session)return null;if(session.expiresAt<=Date.now()){sessions.delete(value);return null;}return session;}
function cookieSession(req){const values=(req.headers.cookie??'').split(';').map(v=>v.trim()).filter(v=>v.startsWith(cookieName()+'='));return values.length===1?currentSession(values[0].slice(cookieName().length+1)):null;}
function accessSession(req){const authorization=req.headers.authorization;if(!authorization?.startsWith('Bearer '))return null;const token=authorization.slice(7);return [...sessions.values()].find(s=>s.accessToken===token && s.expiresAt>Date.now())??null;}
function grant(session){return {context:{...context,sessionId:session.id},accessToken:session.accessToken,expiresAt:new Date(session.expiresAt).toISOString()};}
const applications=new Map();const receipts=new Map();let next=100,failPerson=true;
function create(type){const applicationId=String(next++),subjectPartyId=String(next++);const app={applicationId,applicationNo:'DEMO-'+applicationId,subjectPartyId,clientId:String(next++),clientType:type,status:'DRAFT',savedAt:now,editable:true,values:{},people:[],relations:[],documents:[],materialSubjects:[]};applications.set(applicationId,app);return app;}
const organization=create('ORGANIZATION');organization.values={organization:{legalName:'示例工作室',tradeName:'Studio Demo',incorporationCountry:'AE',organizationType:'有限责任公司',incorporationDate:'2025-01-15'},address:{addressLine1:'Demo Building',city:'Dubai',countryCode:'AE'},business:{businessDescription:'仅用于本地交互验证的合成资料。'}};
const person={partyId:'301',editable:true,values:{individual:{givenName:'测试',familyName:'人员',residenceCountry:'AE'}}};organization.people.push(person);organization.relations.push({relationId:'302',relatedPartyId:'301',relationType:'DIRECTOR',ownershipPercentage:null,effectiveFrom:'2026-01-01',effectiveTo:null,status:'DRAFT',editable:true});
const individual=create('INDIVIDUAL');individual.values={individual:{givenName:'示例',familyName:'个人'}};individual.status='APPROVED';individual.editable=false;
const requirement={requirementCode:'REGISTRATION',documentType:'REGISTRATION_CERTIFICATE',requiredFlag:true};
function page(app){return {...app,materialSubjects:[{subjectPartyId:app.subjectPartyId,requirementsStatus:app.values[app.clientType==='ORGANIZATION'?'organization':'individual']?.[app.clientType==='ORGANIZATION'?'incorporationCountry':'residenceCountry']?'AVAILABLE':'COUNTRY_REQUIRED',requirements:[requirement]},...app.people.filter(p=>app.relations.some(r=>r.relatedPartyId===p.partyId&&r.status!=='REMOVED')).map(p=>({subjectPartyId:p.partyId,requirementsStatus:'AVAILABLE',requirements:[{requirementCode:'IDENTITY',documentType:'PASSPORT',requiredFlag:true}]}))]};}
const server=http.createServer(async(req,res)=>{
 const url=new URL(req.url,'http://127.0.0.1');const route=url.pathname;let bytes='';for await(const chunk of req){bytes+=chunk;if(bytes.length>524288){res.writeHead(413).end();return;}}
 const reply=(body,status=200)=>{res.writeHead(status,{'Content-Type':'application/json','Cache-Control':'no-store','Referrer-Policy':'no-referrer'});res.end(status===204?'':JSON.stringify(body));};
 const problem=(status,detail)=>{
  const [title,code]=status===401?['Unauthorized','CLIENT_SESSION_INVALID']:status===400?['Bad Request','INVALID_PARAMETER']:status===405?['Method Not Allowed','METHOD_NOT_ALLOWED']:['Service Unavailable','SERVICE_UNAVAILABLE'];
  res.setHeader('X-Request-Id','browser-fixture');
  return reply({type:'about:blank',title,detail,code,requestId:'browser-fixture',timestamp:new Date().toISOString()},status);
 };
 let json;try{json=bytes?JSON.parse(bytes):null;}catch{return problem(400,'请求格式无效');}
 if(route==='/api/client/auth/v1/login'){
  if(req.method!=='POST')return problem(405,'请使用 POST 登录');
  if(json?.email!=='demo@example.test'||json?.password!=='Preview-only-2026!')return problem(401,'演示邮箱或密码错误');
  for(const [key,session] of sessions)if(session.expiresAt<=Date.now())sessions.delete(key);
  const session={id:String(nextSession++),refreshToken:randomBytes(32).toString('base64url'),accessToken:randomBytes(32).toString('base64url'),expiresAt:Date.now()+sessionDuration};
  sessions.set(session.refreshToken,session);res.setHeader('Set-Cookie',sessionCookie(session.refreshToken,sessionDuration/1000));return reply(grant(session));
 }
 if(route==='/api/client/auth/v1/refresh'){
  if(req.method!=='POST')return problem(405,'请使用 POST 恢复会话');
  const session=cookieSession(req);if(!session)return problem(401,'请先登录');return reply(grant(session));
 }
 if(route==='/api/client/auth/v1/logout'){
  if(req.method!=='POST')return problem(405,'请使用 POST 退出');
  const session=cookieSession(req);if(!session||accessSession(req)!==session)return problem(401,'当前会话不可用，请重新登录');
  sessions.delete(session.refreshToken);res.setHeader('Set-Cookie',sessionCookie('',0));return reply(null,204);
 }
 if(route==='/api/client/auth/v1/context'){
  const session=accessSession(req);if(!session)return problem(401,'请先登录');return reply(grant(session).context);
 }
 if((route.startsWith('/api/client/onboarding/')||route.startsWith('/api/client/onboarding-files/'))&&!accessSession(req))return problem(401,'请先登录');
 if(route==='/api/client/onboarding/v1/form-options'){
  const type=url.searchParams.get('clientType'),country=url.searchParams.get('countryCode');
  const organizationType={code:'SYNTHETIC_AE_ORG',label:'合成组织类型（仅测试）'};
  return reply({clientType:type,countryCode:country,catalogStatus:type==='ORGANIZATION'?(country===null?'COUNTRY_REQUIRED':country==='AE'?'AVAILABLE':'UNAVAILABLE'):'AVAILABLE',
   organizationTypes:type==='ORGANIZATION'&&country==='AE'?[organizationType]:[],businessModels:[{code:'SYNTHETIC_MODEL',label:'合成经营模式（仅测试）'}],
   identifierTypes:[{code:'SYNTHETIC_ID',label:type==='ORGANIZATION'?'合成注册证明（仅测试）':'合成个人证明（仅测试）',issuers:[{scopeKey:'SYNTHETIC_GB_AUTHORITY',label:'合成英国签发机构',issuingCountry:'GB'},{scopeKey:'GLOBAL',label:'合成全球机构',issuingCountry:null}]}],
   requirementsStatus:country?'AVAILABLE':'COUNTRY_REQUIRED',requirements:country?[requirement]:[]});
 }
 if(route==='/api/client/onboarding/v1/start')return reply({applications:[...applications.values()].map(a=>({applicationId:a.applicationId,applicationNo:a.applicationNo,clientType:a.clientType,status:a.status,active:a.editable})),hasMore:false,activeApplicationId:organization.applicationId});
 const parts=route.split('/');const app=applications.get(parts[6]);
 if(route.startsWith('/api/client/onboarding/v1/applications/')&&app){
  if(req.method==='GET')return reply(page(app));
  const key=req.headers['idempotency-key'];if(receipts.has(key))return reply(receipts.get(key));
  let receipt={applicationId:app.applicationId};
  if(parts[7]==='draft'){for(const [group,values] of Object.entries(json.values))Object.assign(app.values[group]??={},values);}
  else if(parts[7]==='people'){
   if(req.method==='POST'){const partyId=String(next++),relationId=String(next++);app.people.push({partyId,editable:true,values:json.person.values});app.relations.push({relationId,relatedPartyId:partyId,...json.relation,status:'DRAFT',editable:true});receipt={...receipt,partyId,relationId};}
   else {const person=app.people.find(p=>p.partyId===parts[8]);for(const [group,values] of Object.entries(json.values))Object.assign(person.values[group]??={},values);}
  }else if(parts[7]==='relations'){
   if(req.method==='POST')app.relations.push({relationId:String(next++),...json,status:'DRAFT',editable:true});
   else {const relation=app.relations.find(r=>r.relationId===parts[8]);if(req.method==='DELETE'){relation.status='REMOVED';relation.editable=false;}else Object.assign(relation,json.values);}
  }else if(parts[7]==='documents'){
   if(req.method==='DELETE')app.documents=app.documents.filter(d=>d.documentId!==parts[8]);
  }
  receipts.set(key,receipt);
  if(parts[7]==='people'&&req.method==='POST'&&failPerson){failPerson=false;return problem(503,'本地验证：人员已创建，但第一次响应丢失。请重试原操作。');}
  return reply(receipt,req.method==='DELETE'?204:req.method==='POST'?201:200);
 }
 if(route.startsWith('/api/'))return problem(503,'本地界面验证未提供此依赖。');
 try {const target=route.startsWith('/assets/')?path.join(dist,route):path.join(dist,'index.html');if(!target.startsWith(dist))throw Error();const data=await readFile(target);res.writeHead(200,{'Content-Type':target.endsWith('.js')?'text/javascript':target.endsWith('.css')?'text/css':'text/html','Cache-Control':'no-store'});res.end(data);}catch{res.writeHead(404).end();}
});
server.listen(requestedPort,'127.0.0.1',()=>console.log(JSON.stringify({url:'http://127.0.0.1:'+server.address().port+'/login',fixtureOnly:true})));
for(const signal of ['SIGINT','SIGTERM'])process.on(signal,()=>server.close(()=>process.exit()));
