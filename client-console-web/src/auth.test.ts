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
  assert.equal(passwordIssue('a'.repeat(14)), '密码至少需要 15 个字符');
  assert.equal(passwordIssue('a'.repeat(15)), null);
  assert.equal(passwordIssue('🌊'.repeat(15)), null);
  assert.equal(passwordIssue('a'.repeat(129)), '密码不能超过 128 个字符');
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
 await client.restore();const pending=client.request('/onboarding/v1/start');client.clear();release!(Response.json({applications:[]}));await assert.rejects(pending,/登录账号已变更/);
 await assert.rejects(client.request('https://evil.test/onboarding/v1/start'),/请求路径无效/);
});

test('refresh changing the account cannot execute an existing account command',async()=>{
 const context={accountId:'1',sessionId:'2',emailMasked:'a***@example.test',emailVerified:true,contextType:'ACCOUNT',tenantId:null,memberId:null,allowedActions:['ONBOARDING']};let refresh=0,writes=0;
 const client=createAuthClient(async(input)=>{if(String(input).endsWith('/refresh'))return Response.json({context:{...context,accountId:++refresh===1?'1':'3'},accessToken:'fixture',expiresAt:'2000-01-01T00:00:00.000000Z'});writes++;return Response.json({});});
 await client.restore();await assert.rejects(client.request('/onboarding/v1/applications',{method:'POST',body:{clientType:'INDIVIDUAL'}}),/账号已变更/);assert.equal(writes,0);
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
