import {test} from 'node:test';
import assert from 'node:assert/strict';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {fileURLToPath} from 'node:url';

const script=fileURLToPath(new URL('./preview-onboarding.mjs',import.meta.url));
const loginBody={email:'demo@example.test',password:'Preview-only-2026!'};

// 每个用例启动独立 loopback 模拟器，避免登录状态或草稿互相污染。
async function fixture(t) {
  const child=spawn(process.execPath,[script],{stdio:['ignore','pipe','pipe']});
  t.after(async()=>{if(child.exitCode===null){const ended=once(child,'exit');child.kill('SIGTERM');await ended;}});
  let stdout='';
  const origin=await new Promise((resolve,reject)=>{
    const timeout=setTimeout(()=>reject(Error('Preview did not start')),4000);
    child.once('error',error=>{clearTimeout(timeout);reject(error);});
    child.once('exit',()=>{clearTimeout(timeout);reject(Error('Preview exited before listening'));});
    child.stdout.on('data',chunk=>{
      stdout+=chunk;
      if(stdout.includes('\n')){clearTimeout(timeout);resolve(new URL(JSON.parse(stdout.split('\n')[0]).url).origin);}
    });
  });
  const request=(path,options={})=>fetch(origin+path,{redirect:'error',...options});
  const login=async()=>{
    const response=await request('/api/client/auth/v1/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(loginBody)});
    assert.equal(response.status,200);
    const cookie=response.headers.get('set-cookie');
    assert.ok(cookie?.includes('HttpOnly'));
    return {cookie:cookie.split(';')[0],grant:await response.json()};
  };
  return {request,login};
}

test('anonymous refresh, context, application reads and writes return 401',async t=>{
  const {request}=await fixture(t);
  for(const [method,path] of [
    ['POST','/api/client/auth/v1/refresh'],['GET','/api/client/auth/v1/context'],
    ['GET','/api/client/onboarding/v1/start'],['GET','/api/client/onboarding/v1/applications/100'],
    ['GET','/api/client/onboarding/v1/form-options?clientType=ORGANIZATION'],
    ['PATCH','/api/client/onboarding/v1/applications/100/draft'],['POST','/api/client/onboarding-files/v1/uploads']
  ]) {
    const response=await request(path,{method});
    assert.equal(response.status,401,path);
    const body=await response.json();assert.equal(body.code,'CLIENT_SESSION_INVALID');assert.equal(body.title,'Unauthorized');
    assert.equal(response.headers.get('cache-control'),'no-store');
  }
});

test('only explicit correct demo login establishes a session',async t=>{
  const {request,login}=await fixture(t);
  const bad=await request('/api/client/auth/v1/login',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({...loginBody,password:'wrong'})});
  assert.equal(bad.status,401);assert.equal(bad.headers.get('set-cookie'),null);
  const {grant}=await login();
  const response=await request('/api/client/onboarding/v1/applications/100',{headers:{Authorization:'Bearer '+grant.accessToken}});
  assert.equal(response.status,200);assert.equal((await response.json()).applicationId,'100');
});

test('refresh requires this browser session cookie, cookie alone cannot read business data',async t=>{
  const {request,login}=await fixture(t);const {cookie,grant}=await login();
  assert.equal((await request('/api/client/auth/v1/refresh',{method:'POST'})).status,401);
  assert.equal((await request('/api/client/onboarding/v1/start',{headers:{Cookie:cookie}})).status,401);
  assert.equal((await request('/api/client/onboarding/v1/start',{headers:{Authorization:'Bearer browser-synthetic-fixture'}})).status,401);
  const restored=await request('/api/client/auth/v1/refresh',{method:'POST',headers:{Cookie:cookie}});
  assert.equal(restored.status,200);assert.equal((await restored.json()).context.sessionId,grant.context.sessionId);
});

test('logout revokes access and refresh, including another tab holding the old token',async t=>{
  const {request,login}=await fixture(t);const {cookie,grant}=await login();
  const headers={Cookie:cookie,Authorization:'Bearer '+grant.accessToken};
  const logout=await request('/api/client/auth/v1/logout',{method:'POST',headers});
  assert.equal(logout.status,204);assert.match(logout.headers.get('set-cookie'),/Max-Age=0/);
  assert.equal((await request('/api/client/onboarding/v1/start',{headers})).status,401);
  assert.equal((await request('/api/client/auth/v1/refresh',{method:'POST',headers})).status,401);
  assert.equal((await request('/api/client/auth/v1/context',{headers})).status,401);
});

test('logging out one session does not authorize or revoke another browser session',async t=>{
  const {request,login}=await fixture(t);const first=await login(),second=await login();
  assert.notEqual(first.grant.context.sessionId,second.grant.context.sessionId);
  assert.notEqual(first.grant.accessToken,second.grant.accessToken);
  assert.equal((await request('/api/client/auth/v1/logout',{method:'POST',headers:{Cookie:first.cookie,Authorization:'Bearer '+second.grant.accessToken}})).status,401);
  assert.equal((await request('/api/client/auth/v1/logout',{method:'POST',headers:{Cookie:first.cookie,Authorization:'Bearer '+first.grant.accessToken}})).status,204);
  assert.equal((await request('/api/client/auth/v1/context',{headers:{Authorization:'Bearer '+second.grant.accessToken}})).status,200);
});
