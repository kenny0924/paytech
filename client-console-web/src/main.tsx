import React, { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Link, Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import { createAuthClient, readEmailLink, RequestFailure } from './auth.ts';
import type { AccountContext } from './auth.ts';
import './style.css';
import {OnboardingPage} from './OnboardingPage.tsx';
import {WorkspacePage} from './WorkspacePage.tsx';
import {LoginForm} from './LoginForm.tsx';
import {TextField, focusField} from './TextField.tsx';
import {authIssues, authFailure} from './auth-form.ts';
import type {AuthKind, AuthIssues} from './auth-form.ts';

const client = createAuthClient();
const emailLink = readEmailLink(window.location.hash);
// 链接秘密只留在本页内存；刷新页面需重新打开原邮件。
if (window.location.hash || window.location.search) window.history.replaceState(null, '', window.location.pathname);
type Kind = AuthKind;
const content: Record<Kind, [string, string]> = {
  login: ['欢迎回来', '登录您的 PAYTECH 客户账号。'],
  register: ['创建账号', '使用邮箱注册，开始您的客户入驻。'],
  forgot: ['找回密码', '输入注册邮箱，我们会发送密码重置链接。'],
  reset: ['设置新密码', '设置新密码后，请重新登录您的账号。'],
  verify: ['验证您的邮箱', '确认后，我们将验证该链接对应的邮箱。'],
  sent: ['请查收邮件', '如果该邮箱符合条件，您会收到一封验证邮件。'],
};
function ErrorMessage({error}: {error: Error | null}) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => { if (error) ref.current?.focus(); }, [error]);
  if (!error) return null;
  const fields = error instanceof RequestFailure ? Object.entries(error.fields).filter(([field]) =>
    ['email', 'password', 'newPassword'].includes(field)) : [];
  return <div className="notice error" role="alert" tabIndex={-1} ref={ref}>
    {error.message}
    {fields.map(([field, message]) => <small key={field}><a href={'#' + (field === 'newPassword' ? 'password' : field)}
      onClick={event => {event.preventDefault(); document.getElementById(field === 'newPassword' ? 'password' : field)?.focus();}}>
      {field === 'email' ? '邮箱' : '密码'}：{message}</a></small>)}
    {error instanceof RequestFailure && error.requestId && <small>请求编号：{error.requestId}</small>}
  </div>;
}
function AuthPage({kind}: {kind: Kind}) {
  const navigate = useNavigate(), [email, setEmail] = useState(''), [password, setPassword] = useState(''), [confirm, setConfirm] = useState('');
  const [issues, setIssues] = useState<AuthIssues>({}), [message,setMessage]=useState('');
  const [busy, setBusy] = useState(false), [done, setDone] = useState(false), [cooldown, setCooldown] = useState(0);
  const guard = useRef(false), form=useRef<HTMLFormElement>(null);
  useEffect(() => { if (!cooldown) return; const timer = window.setTimeout(() => setCooldown(c => Math.max(0,c-1)), 1000); return () => clearTimeout(timer); }, [cooldown]);
  const linkMissing = (kind === 'verify' || kind === 'reset') && !emailLink;
  const emailField = ['register','forgot','sent'].includes(kind);
  const passwordFields = kind==='register' || kind==='reset';
  function change(field: keyof AuthIssues, value: string) {
    if (field==='email') {setEmail(value);setDone(false);}
    else if (field==='password') setPassword(value); else setConfirm(value);
    setIssues(previous=>({...previous,[field]:undefined,...(field==='password'?{confirm:undefined}:{})}));
    setMessage('');
  }
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (guard.current || cooldown > 0) return;
    setMessage('');
    const next=authIssues(kind,{email,password,confirm});
    setIssues(next);
    if (Object.keys(next).length) {
      const id=next.email?'email':next.password?'password':'confirm-password';
      focusField(form.current?.elements.namedItem(id) as HTMLInputElement | null);
      return;
    }
    guard.current = true; setBusy(true);
    try {
      if (kind === 'register') {
        const reply = await client.call<{resendAfterSeconds:number}>('register',{email:email.trim(),password});
        setPassword(''); setConfirm(''); setDone(true); setCooldown(reply.resendAfterSeconds);
      } else if (kind === 'forgot' || kind === 'sent') {
        const reply = await client.call<{resendAfterSeconds:number}>(kind === 'forgot' ? 'password-reset-requests' : 'email-verification-requests',{email:email.trim()});
        setDone(true); setCooldown(reply.resendAfterSeconds);
      } else if (kind === 'verify' && emailLink) {
        await client.call('email-verifications', emailLink); setDone(true);
      } else if (kind === 'reset' && emailLink) {
        await client.call('password-resets', {...emailLink,newPassword:password}); setPassword(''); setConfirm(''); client.clear(); setDone(true);
      }
    } catch (failure) { const feedback=authFailure(failure,kind);setIssues(feedback.fields);setMessage(feedback.message); }
    finally { setBusy(false); guard.current = false; }
  }
  const finished = done && ['register','verify','reset'].includes(kind);
  let title = content[kind][0], intro = content[kind][1];
  if (finished) { title = kind === 'register' ? '请查收验证邮件' : kind === 'verify' ? '邮箱验证成功' : '密码已重置'; intro = kind === 'register' ? '如果该邮箱符合条件，验证链接已发送。请检查收件箱或垃圾邮件。' : '现在可以使用邮箱和密码登录。'; }
  return <main className="auth-shell"><section className="auth" aria-labelledby="page-title">
    <Link className="brand" to="/login" aria-label="PAYTECH 首页">PAYTECH</Link>
    <header><h1 id="page-title">{title}</h1><p>{intro}</p></header>
    {kind === 'login' ? <LoginForm login={client.login} onSuccess={()=>navigate('/onboarding', {replace:true})}/> : finished ? <div className="result"><div className="check" aria-hidden="true">✓</div>
      {kind === 'register' ? <><Link className="primary link-button" to="/check-email">重新发送验证邮件</Link><Link to="/login">返回登录</Link></> : <Link className="primary link-button" to="/login">前往登录</Link>}
    </div> : linkMissing ? <div className="result"><p className="notice">链接缺失或无效，请重新申请邮件并打开其中的链接。</p>
      <Link className="primary link-button" to={kind === 'verify' ? '/check-email' : '/forgot-password'}>重新申请邮件</Link></div> :
    <form ref={form} noValidate onSubmit={submit} aria-busy={busy}>
      {emailField && <TextField id="email" label="邮箱" type="email" required maxLength={320} disabled={busy}
        value={email} onValueChange={value=>change('email',value)} autoComplete="email" autoCapitalize="none" spellCheck={false}
        placeholder="输入您的邮箱" error={issues.email}
        message={!passwordFields ? message || (done ? '请求已受理，请查收邮件。' : undefined) : undefined}
        messageTone={message ? 'error' : 'success'}/>}
      {passwordFields && <TextField id="password" label={kind==='reset'?'新密码':'密码'} type="password" required
        value={password} onValueChange={value=>change('password',value)} disabled={busy} autoComplete="new-password"
        error={issues.password} hint="15—128 个字符，可包含空格。"/>}
      {passwordFields && <TextField id="confirm-password" label="确认密码" type="password" required
        value={confirm} onValueChange={value=>change('confirm',value)} disabled={busy} autoComplete="new-password"
        error={issues.confirm} message={message}/>}
      {!emailField && !passwordFields && <p className="form-feedback" role="status">{message}</p>}
      <button className="primary" type="submit" disabled={busy || cooldown>0}>
        {busy?'处理中…':cooldown>0?cooldown+' 秒后可重新发送':({login:'登录',register:'创建账号',forgot:'发送重置邮件',reset:'保存新密码',verify:'验证邮箱',sent:'发送验证邮件'}[kind])}
      </button>
      <nav className="auth-links"><Link to="/login">返回登录</Link></nav>
    </form>}
    <footer>PAYTECH Client Console</footer>
  </section></main>;
}
function AccountPage() {
  const grant = useSyncExternalStore(client.subscribe, client.current), navigate=useNavigate();
  const [context,setContext] = useState<AccountContext|null>(null), [error,setError] = useState<Error|null>(null), [busy,setBusy]=useState(false);
  useEffect(()=>{let alive=true; client.context().then(value=>{if(alive)setContext(value);}).catch(failure=>{
    if(!alive)return;
    if(failure instanceof RequestFailure && failure.status===401){client.clear();navigate('/login',{replace:true});}
    else setError(failure);
  });return()=>{alive=false;};},[navigate]);
  async function logout(){if(busy)return;setBusy(true);setError(null);try{await client.logout();navigate('/login',{replace:true});}catch(failure){setError(failure as Error);}finally{setBusy(false);}}
  return <main className="auth-shell"><section className="auth"><Link className="brand" to="/account">PAYTECH</Link>
    <header><h1>账号信息</h1><p>{context?'您已成功登录。':'正在读取您的账号…'}</p></header><ErrorMessage error={error}/>
    {context && <dl className="account"><dt>邮箱</dt><dd>{context.emailMasked}</dd><dt>邮箱状态</dt><dd>已验证</dd></dl>}
    {context && <Link className="primary link-button" to="/onboarding">我的入驻申请</Link>}
    {(grant || context) && <button className="primary" onClick={logout} disabled={busy}>{busy?'正在退出…':'退出登录'}</button>}
    {error && !grant && <Link className="primary link-button" to="/login">返回登录</Link>}
  </section></main>;
}
function App(){return <BrowserRouter><Routes>
  {([['/login','login'],['/register','register'],['/forgot-password','forgot'],['/reset-password','reset'],['/verify-email','verify'],['/check-email','sent']] as const).map(([path,kind])=><Route key={path} path={path} element={<AuthPage key={kind} kind={kind}/>}/>)}
  <Route path="/onboarding" element={<OnboardingPage client={client}/>}/><Route path="/onboarding/applications/:applicationId" element={<OnboardingPage client={client}/>}/>
  <Route path="/workspace" element={<WorkspacePage client={client}/>}/>
  <Route path="/workspace/developer/api-keys" element={<WorkspacePage key="api-keys" client={client} section="api-keys"/>}/>
  <Route path="/workspace/developer/webhooks" element={<WorkspacePage key="webhooks" client={client} section="webhooks"/>}/>
  <Route path="/account" element={<AccountPage/>}/><Route path="*" element={<Navigate to="/login" replace/>}/>
</Routes></BrowserRouter>;}
const root = createRoot(document.getElementById('root')!);
root.render(<App/>);
if (import.meta.hot) import.meta.hot.dispose(() => root.unmount());
