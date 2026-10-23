import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readEmailLink, passwordIssue, createAuthClient } from './auth.ts';

test('email secret is accepted only from the exact fragment contract', () => {
  const id='12345678-1234-4234-8234-123456789abc', secret='A'.repeat(43);
  assert.deepEqual(readEmailLink('#challengeNo='+id+'&secret='+secret), {challengeNo:id,secret});
  assert.equal(readEmailLink('#challengeNo='+id+'&secret='+secret+'&secret='+secret),null);
  assert.equal(readEmailLink('?challengeNo='+id+'&secret='+secret),null);
  assert.equal(readEmailLink('#challengeNo='+id+'&secret=short'),null);
});
test('password rules preserve whitespace and count Unicode characters', () => {
  assert.equal(passwordIssue('a'.repeat(14)), 'Password must be at least 15 characters');
  assert.equal(passwordIssue('a'.repeat(15)), null);
  assert.equal(passwordIssue('🌊'.repeat(15)), null);
  assert.equal(passwordIssue('a'.repeat(129)), "Password can't be longer than 128 characters");
});
test('concurrent restore rotates once and logout cannot restore late credentials', async () => {
  let refreshes=0, release: ((response: Response)=>void)|undefined;
  const grant={ context:{ accountId:'1',sessionId:'2',emailMasked:'a***@example.test',emailVerified:true,contextType:'ACCOUNT',tenantId:null,memberId:null,allowedActions:['LOGOUT']}, accessToken:'access-fixture',expiresAt:'2099-01-01T00:00:00.000000Z' };
  const client=createAuthClient(async (_input,init) => {
    assert.equal(init?.credentials,'same-origin');
    refreshes++;
    return new Promise<Response>(resolve => { release=resolve; });
  });
  const first=client.restore(), second=client.restore();
  assert.equal(refreshes,1);
  client.clear();
  release!(new Response(JSON.stringify(grant),{status:200,headers:{'Content-Type':'application/json'}}));
  await Promise.all([first,second]);
  assert.equal(client.current(),null);
});

test('separate tabs serialize requests that rotate the shared cookie', async () => {
  let tail = Promise.resolve(), inFlight = 0, maximum = 0;
  const lock = <T>(action: () => Promise<T>): Promise<T> => {
    const next = tail.then(action); tail = next.then(() => {}, () => {}); return next;
  };
  const grant={context:{accountId:'1',sessionId:'2',emailMasked:'a***@example.test',emailVerified:true,contextType:'ACCOUNT',tenantId:null,memberId:null,allowedActions:['LOGOUT']},accessToken:'fixture',expiresAt:'2099-01-01T00:00:00.000000Z'};
  const transport: typeof fetch = async () => {
    maximum = Math.max(maximum, ++inFlight);
    await new Promise(resolve => setImmediate(resolve));
    inFlight--;
    return new Response(JSON.stringify(grant), {status:200,headers:{'Content-Type':'application/json'}});
  };
  const first = createAuthClient(transport, lock), second = createAuthClient(transport, lock);
  await Promise.all([first.restore(), second.login('fixture@example.test', 'synthetic test password')]);
  assert.equal(maximum, 1);
});

test('protected write refreshes once without changing command body or idempotency key',async()=>{
 const writes:RequestInit[]=[];let refreshes=0;
 const grant={context:{accountId:'1',sessionId:'2',emailMasked:'a***@example.test',emailVerified:true,contextType:'ACCOUNT',tenantId:null,memberId:null,allowedActions:['ONBOARDING']},accessToken:'fixture',expiresAt:'2099-01-01T00:00:00.000000Z'};
 const client=createAuthClient(async(input,init)=>{
  if(String(input).endsWith('/refresh')){refreshes++;return Response.json(grant);}
  writes.push(init!);return writes.length===1?Response.json({detail:'expired'},{status:401}):Response.json({applicationId:'10'});
 });
 const options={method:'POST' as const,key:'a9c2dfe8-c414-4311-8715-d7c601a0ce48',body:{clientType:'INDIVIDUAL'}};
 await client.request('/onboarding/v1/applications',options);
 assert.equal(refreshes,2);assert.equal(writes.length,2);assert.deepEqual(writes[0],writes[1]);
 assert.equal(new Headers(writes[0].headers).get('Idempotency-Key'),options.key);
});
test('late business response is discarded after logout and arbitrary outbound URL is rejected',async()=>{
 let release:((response:Response)=>void)|undefined;
 const grant={context:{accountId:'1',sessionId:'2',emailMasked:'a***@example.test',emailVerified:true,contextType:'ACCOUNT',tenantId:null,memberId:null,allowedActions:['ONBOARDING']},accessToken:'fixture',expiresAt:'2099-01-01T00:00:00.000000Z'};
 const client=createAuthClient(async(input)=>String(input).endsWith('/refresh')?Response.json(grant):new Promise<Response>(resolve=>{release=resolve;}));
 await client.restore();const pending=client.request('/onboarding/v1/start');client.clear();release!(Response.json({applications:[]}));await assert.rejects(pending,/signed-in account has changed/);
 await assert.rejects(client.request('https://evil.test/onboarding/v1/start'),/Invalid request path/);
});

test('refresh changing the account cannot execute an existing account command',async()=>{
 const context={accountId:'1',sessionId:'2',emailMasked:'a***@example.test',emailVerified:true,contextType:'ACCOUNT',tenantId:null,memberId:null,allowedActions:['ONBOARDING']};let refresh=0,writes=0;
 const client=createAuthClient(async(input)=>{if(String(input).endsWith('/refresh'))return Response.json({context:{...context,accountId:++refresh===1?'1':'3'},accessToken:'fixture',expiresAt:'2000-01-01T00:00:00.000000Z'});writes++;return Response.json({});});
 await client.restore();await assert.rejects(client.request('/onboarding/v1/applications',{method:'POST',body:{clientType:'INDIVIDUAL'}}),/account has changed/);assert.equal(writes,0);
});

test('anonymous session restoration fails before any onboarding data request',async()=>{
 const paths:string[]=[];
 const client=createAuthClient(async(input)=>{
  paths.push(String(input));
  return Response.json({detail:'请先登录',code:'CLIENT_SESSION_INVALID'},{status:401});
 });
 await assert.rejects(client.request('/onboarding/v1/applications/100'),error=>
  error instanceof Error && 'status' in error && error.status===401);
 assert.equal(client.current(),null);
 assert.deepEqual(paths,['/api/client/auth/v1/refresh']);
});

const tenantContext={accountId:'1',sessionId:'5',emailMasked:'a***@example.test',emailVerified:true,contextType:'TENANT',tenantId:'7',memberId:'8',allowedActions:['WORKSPACE','SELECT_WORKSPACE','ACCOUNT_CONTEXT','LOGOUT']};
const accountContext={accountId:'1',sessionId:'2',emailMasked:'a***@example.test',emailVerified:true,contextType:'ACCOUNT',tenantId:null,memberId:null,allowedActions:['ONBOARDING','SELECT_WORKSPACE','LOGOUT']};
test('selecting a workspace replaces the grant, sends only memberId and uses the session lock',async()=>{
 const sent:{url:string;init:RequestInit}[]=[];let locked=0;
 const lock=<T>(action:()=>Promise<T>)=>{locked++;return action();};
 const client=createAuthClient(async(input,init)=>{sent.push({url:String(input),init:init!});
  return String(input).endsWith('/refresh')?Response.json({context:accountContext,accessToken:'account',expiresAt:'2099-01-01T00:00:00.000000Z'})
   :Response.json({context:tenantContext,accessToken:'tenant',expiresAt:'2099-01-01T00:00:00.000000Z'});},lock);
 await client.restore();
 const context=await client.selectWorkspace('8');
 assert.equal(context.contextType,'TENANT');assert.equal(client.current()?.accessToken,'tenant');
 const call=sent.at(-1)!;
 assert.equal(call.url,'/api/client/auth/v1/workspace-context');
 assert.equal(call.init.body,JSON.stringify({memberId:'8'}));
 assert.equal(new Headers(call.init.headers).get('Authorization'),'Bearer account');
 assert.equal(locked,2);
});
test('switching context invalidates in-flight business requests',async()=>{
 let release:((response:Response)=>void)|undefined;
 const client=createAuthClient(async(input)=>{const url=String(input);
  if(url.endsWith('/refresh'))return Response.json({context:accountContext,accessToken:'account',expiresAt:'2099-01-01T00:00:00.000000Z'});
  if(url.endsWith('/workspace-context'))return Response.json({context:tenantContext,accessToken:'tenant',expiresAt:'2099-01-01T00:00:00.000000Z'});
  return new Promise<Response>(resolve=>{release=resolve;});});
 await client.restore();
 const pending=client.request('/onboarding/v1/start');
 await client.selectWorkspace('8');
 release!(Response.json({applications:[]}));
 await assert.rejects(pending,/signed-in account has changed/);
});
test('back to account returns an account grant; failed switch keeps the original grant',async()=>{

 const client=createAuthClient(async(input)=>{const url=String(input);
  if(url.endsWith('/refresh'))return Response.json({context:tenantContext,accessToken:'tenant',expiresAt:'2099-01-01T00:00:00.000000Z'});
  if(url.endsWith('/workspace-context'))return Response.json({code:'CLIENT_WORKSPACE_UNAVAILABLE',detail:'This workspace is temporarily unavailable'},{status:403});
  return Response.json({context:accountContext,accessToken:'account',expiresAt:'2099-01-01T00:00:00.000000Z'});});
 await client.restore();
 await assert.rejects(client.selectWorkspace('9'),(error:unknown)=>error instanceof Error&&'code' in error&&error.code==='CLIENT_WORKSPACE_UNAVAILABLE');
 assert.equal(client.current()?.accessToken,'tenant');
 const context=await client.backToAccount();
 assert.equal(context.contextType,'ACCOUNT');assert.equal(client.current()?.accessToken,'account');
});
test('refresh fallback to account context is surfaced once',async()=>{
 const client=createAuthClient(async()=>Response.json({context:{...accountContext,fallbackReason:'TENANT_SUSPENDED'},accessToken:'account',expiresAt:'2099-01-01T00:00:00.000000Z'}));
 await client.restore();
 assert.equal(client.current()?.context.contextType,'ACCOUNT');
 assert.equal(client.takeFallback(),'TENANT_SUSPENDED');
 assert.equal(client.takeFallback(),null);
});
test('workspace paths are allowed and malformed fallback reasons are rejected',async()=>{
 const urls:string[]=[];
 const client=createAuthClient(async(input)=>{urls.push(String(input));
  return String(input).endsWith('/refresh')?Response.json({context:tenantContext,accessToken:'tenant',expiresAt:'2099-01-01T00:00:00.000000Z'}):Response.json({});});
 await client.request('/workspace/v1/home');
 assert.ok(urls.includes('/api/client/workspace/v1/home'));
 const bad=createAuthClient(async()=>Response.json({context:{...accountContext,fallbackReason:'<script>'},accessToken:'x',expiresAt:'2099-01-01T00:00:00.000000Z'}));
 await assert.rejects(bad.restore(),/Unexpected sign-in response/);
});
test('team and invitation paths use the protected helper and preview is sent without credentials',async()=>{
 const calls:{url:string;init:RequestInit}[]=[];
 const client=createAuthClient(async(input,init)=>{calls.push({url:String(input),init:init!});
  return String(input).endsWith('/refresh')?Response.json({context:tenantContext,accessToken:'tenant',expiresAt:'2099-01-01T00:00:00.000000Z'}):Response.json({items:[]});});
 await client.request('/team/v1/members?page=1&size=20');
 await client.request('/invitations/v1/details',{method:'POST',body:{challengeNo:'c',secret:'s'}});
 const team=calls.find(call=>call.url==='/api/client/team/v1/members?page=1&size=20');
 assert.equal(new Headers(team!.init.headers).get('Authorization'),'Bearer tenant');
 assert.ok(calls.some(call=>call.url==='/api/client/invitations/v1/details'));
 const anonymous=createAuthClient(async(input,init)=>{calls.push({url:String(input),init:init!});return Response.json({emailMasked:'d***@example.test'});});
 calls.length=0;
 await anonymous.publicRequest('/invitations/v1/preview',{challengeNo:'c',secret:'s'});
 assert.deepEqual(calls.map(call=>call.url),['/api/client/invitations/v1/preview']);
 assert.equal(new Headers(calls[0].init.headers).get('Authorization'),null);
 assert.equal(calls[0].init.method,'POST');
 assert.equal(calls[0].init.body,JSON.stringify({challengeNo:'c',secret:'s'}));
 assert.throws(()=>anonymous.publicRequest('/invitations/v1/acceptance' as '/invitations/v1/preview',{}),/Invalid request path/);
 await assert.rejects(client.request('/invitations-admin/v1/x'),/Invalid request path/);
});
