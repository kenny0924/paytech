import React, { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter, Link, Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import { createAuthClient, readEmailLink, RequestFailure } from './auth.ts';
import type { AccountContext } from './auth.ts';
import '@fontsource/ibm-plex-sans/latin-400.css';
import '@fontsource/ibm-plex-sans/latin-500.css';
import '@fontsource/ibm-plex-sans/latin-600.css';
import '@fontsource/ibm-plex-mono/latin-400.css';
import './style.css';
import './onboarding/onboarding.css';
import {StartFlow, StartList} from './onboarding/StartFlow.tsx';
import {ApplicationRoot} from './onboarding/ApplicationRoot.tsx';
import {WorkspacePage} from './WorkspacePage.tsx';
import {LoginForm} from './LoginForm.tsx';
import {TextField, focusField} from './TextField.tsx';
import {authIssues, authFailure} from './auth-form.ts';
import {InvitationPage} from './InvitationPage.tsx';
import {afterLoginPath, captureInvitationLink, invitationAcceptPath} from './invitation.ts';
import type {AuthKind, AuthIssues} from './auth-form.ts';
import {i18n} from './i18n/index.ts';
import {I18nProvider, useT} from './i18n/react.tsx';
import {LocaleSwitcher} from './LocaleSwitcher.tsx';

const client = createAuthClient();
function sessionStore(): Storage | null { try { return window.sessionStorage; } catch { return null; } }
const store = sessionStore();
// 邀请链接须在清理 URL 前读取，存入本标签页 sessionStorage，供登录 / 注册往返后继续接受。
const invitationLink = captureInvitationLink(window.location.pathname, window.location.hash, store);
// 其他邮件链接秘密只留在本页内存；刷新页面需重新打开原邮件。
const emailLink = invitationLink ? null : readEmailLink(window.location.hash);
// `?lang=` 与 `?release=` 须在清理 URL 前读取；manifest 读取失败不阻塞渲染。
void i18n.start({search: window.location.search, navigatorLanguages: navigator.languages ?? [navigator.language]});
if (window.location.hash || window.location.search) window.history.replaceState(null, '', window.location.pathname);
type Kind = AuthKind;
function ErrorMessage({error}: {error: Error | null}) {
  const ref = useRef<HTMLDivElement>(null), t = useT('auth'), tc = useT('common');
  useEffect(() => { if (error) ref.current?.focus(); }, [error]);
  if (!error) return null;
  const fields = error instanceof RequestFailure ? Object.entries(error.fields).filter(([field]) =>
    ['email', 'password', 'newPassword'].includes(field)) : [];
  return <div className="notice error" role="alert" tabIndex={-1} ref={ref}>
    {error.message}
    {fields.map(([field, message]) => <small key={field}><a href={'#' + (field === 'newPassword' ? 'password' : field)}
      onClick={event => {event.preventDefault(); document.getElementById(field === 'newPassword' ? 'password' : field)?.focus();}}>
      {t('field.issue', {field: t(field === 'email' ? 'field.email' : 'field.password'), message})}</a></small>)}
    {error instanceof RequestFailure && error.requestId && <small>{tc('request_id', {requestId: error.requestId})}</small>}
  </div>;
}
/** 认证页外壳（C1）：品牌左上、语言右上，左对齐标题 + 单列 400px 表单；无卡片框。 */
function AuthShell({children, home = '/login'}: {children: React.ReactNode; home?: string}) {
  const tc = useT('common');
  return <div className="auth-shell">
    <header className="auth-bar"><Link className="brand" to={home} aria-label={tc('brand.home_label')}>Paytech</Link><LocaleSwitcher/></header>
    <main className="auth" id="main">{children}</main>
  </div>;
}
function AuthPage({kind}: {kind: Kind}) {
  const navigate = useNavigate(), [email, setEmail] = useState(''), [password, setPassword] = useState(''), [confirm, setConfirm] = useState('');
  const [issues, setIssues] = useState<AuthIssues & {terms?: string}>({}), [message,setMessage]=useState('');
  const [busy, setBusy] = useState(false), [done, setDone] = useState(false), [cooldown, setCooldown] = useState(0), [terms, setTerms] = useState(false);
  const guard = useRef(false), form=useRef<HTMLFormElement>(null), t = useT('auth'), tc = useT('common');
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
  async function resend() {
    if (guard.current || cooldown > 0) return;
    guard.current = true; setBusy(true); setMessage('');
    try { const reply = await client.call<{resendAfterSeconds:number}>('email-verification-requests',{email:email.trim()}); setCooldown(reply.resendAfterSeconds); }
    catch (failure) { setMessage(authFailure(failure,'sent').message); }
    finally { setBusy(false); guard.current = false; }
  }
  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (guard.current || cooldown > 0) return;
    setMessage('');
    const next: AuthIssues & {terms?: string} = authIssues(kind,{email,password,confirm});
    if (kind === 'register' && !terms) next.terms = t('register.terms_required');
    setIssues(next);
    if (Object.keys(next).length) {
      const id=next.email?'email':next.password?'password':next.confirm?'confirm-password':'terms';
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
  // 注册成功即进入"查收验证邮件"：说明发往哪里、何时可重发、可更换邮箱。
  if (kind === 'register' && done) return <AuthShell><header><h1>{t('register.done_title')}</h1>
    <p>{t('register.sent_to', {email: email.trim()})}</p></header>
    <div className="result">
      <p>{t('register.not_received')}</p>
      {message && <div className="notice error" role="alert">{message}</div>}
      <button className="secondary resend" type="button" onClick={() => void resend()} disabled={busy || cooldown > 0} aria-live="polite">
        {cooldown > 0 ? t('action.resend_in', {seconds: cooldown}) : t('register.resend')}</button>
      <button className="text-button" type="button" onClick={() => {setDone(false); setCooldown(0);}}>{t('register.change_email')}</button>
    </div></AuthShell>;
  // 验证 / 重置成功：彩色圆点 + 一行状态 + 标题，不放大对勾。
  if (done && (kind === 'verify' || kind === 'reset')) return <AuthShell><header>
    <p className="status-line"><span className="status-dot done strong">{t(kind + '.done_status')}</span></p>
    <h1>{t(kind + '.done_title')}</h1><p>{t(kind === 'verify' ? 'verify.done_intro' : 'done.sign_in_intro')}</p></header>
    <div className="result"><Link className="primary" to="/login">{t('action.go_to_login')}</Link></div></AuthShell>;
  return <AuthShell>
    <header><h1>{t(kind + '.title')}</h1><p>{t(kind + '.intro')}</p></header>
    {kind === 'login' ? <LoginForm login={client.login} onSuccess={()=>navigate(afterLoginPath(store), {replace:true})}/> : linkMissing ? <div className="result">
      <div className="notice error">{t('link.missing')}</div>
      <Link className="primary" to={kind === 'verify' ? '/check-email' : '/forgot-password'}>{t('link.request_again')}</Link></div> :
    <form ref={form} noValidate onSubmit={submit} aria-busy={busy}>
      {message && !emailField && <div className="notice error" role="alert">{message}</div>}
      {emailField && <TextField id="email" label={t('field.email')} type="email" required maxLength={320} disabled={busy}
        value={email} onValueChange={value=>change('email',value)} autoComplete="email" autoCapitalize="none" spellCheck={false}
        error={issues.email}
        message={!passwordFields ? message || (done ? t('request.accepted') : undefined) : message || undefined}
        messageTone={message ? 'error' : 'success'}/>}
      {passwordFields && <TextField id="password" label={kind==='reset'?t('field.new_password'):t('field.password')} type="password" required
        value={password} onValueChange={value=>change('password',value)} disabled={busy} autoComplete="new-password"
        error={issues.password} hint={t('field.password_hint')}/>}
      {passwordFields && <TextField id="confirm-password" label={t('field.confirm_password')} type="password" required
        value={confirm} onValueChange={value=>change('confirm',value)} disabled={busy} autoComplete="new-password"
        error={issues.confirm}/>}
      {kind === 'register' && <div className="field">
        <label className="checkline"><input type="checkbox" id="terms" name="terms" checked={terms} disabled={busy} aria-invalid={!!issues.terms}
          onChange={event => {setTerms(event.target.checked); setIssues(previous => ({...previous, terms: undefined}));}}/>{t('register.terms')}</label>
        {issues.terms && <p className="ob-error" role="alert">{issues.terms}</p>}</div>}
      <button className="primary" type="submit" disabled={busy || cooldown>0}>
        {busy?tc('status.processing'):cooldown>0?t('action.resend_in',{seconds:cooldown}):t('submit.'+kind)}
      </button>
      <p className="auth-links">{kind === 'register' ? <>{t('register.have_account')} <Link to="/login">{t('register.sign_in')}</Link></>
        : <Link to="/login">{t('action.back_to_login')}</Link>}</p>
    </form>}
  </AuthShell>;
}
function AccountPage() {
  const grant = useSyncExternalStore(client.subscribe, client.current), navigate=useNavigate();
  const [context,setContext] = useState<AccountContext|null>(null), [error,setError] = useState<Error|null>(null), [busy,setBusy]=useState(false);
  const t = useT('auth');
  useEffect(()=>{let alive=true; client.context().then(value=>{if(alive)setContext(value);}).catch(failure=>{
    if(!alive)return;
    if(failure instanceof RequestFailure && failure.status===401){client.clear();navigate('/login',{replace:true});}
    else setError(failure);
  });return()=>{alive=false;};},[navigate]);
  async function logout(){if(busy)return;setBusy(true);setError(null);try{await client.logout();navigate('/login',{replace:true});}catch(failure){setError(failure as Error);}finally{setBusy(false);}}
  return <AuthShell home="/account">
    <header><h1>{t('account.title')}</h1><p>{context?t('account.signed_in'):t('account.loading')}</p></header><ErrorMessage error={error}/>
    {context && <dl className="account"><dt>{t('field.email')}</dt><dd>{context.emailMasked}</dd><dt>{t('account.email_status')}</dt><dd>{t('account.verified')}</dd></dl>}
    <div className="result">
      {context && <Link className="primary" to="/onboarding">{t('account.my_applications')}</Link>}
      {(grant || context) && <button className="secondary" onClick={logout} disabled={busy}>{busy?t('action.signing_out'):t('action.sign_out')}</button>}
      {error && !grant && <Link className="primary" to="/login">{t('action.back_to_login')}</Link>}
    </div>
  </AuthShell>;
}
function App(){return <I18nProvider><BrowserRouter><Routes>
  {([['/login','login'],['/register','register'],['/forgot-password','forgot'],['/reset-password','reset'],['/verify-email','verify'],['/check-email','sent']] as const).map(([path,kind])=><Route key={path} path={path} element={<AuthPage key={kind} kind={kind}/>}/>)}
  <Route path="/onboarding" element={<StartList client={client}/>}/>
  <Route path="/onboarding/new" element={<StartFlow client={client}/>}/>
  <Route path="/onboarding/applications/:applicationId/*" element={<ApplicationRoot client={client}/>}/>
  <Route path="/workspace" element={<WorkspacePage client={client}/>}/>
  <Route path="/workspace/developer/api-keys" element={<WorkspacePage key="api-keys" client={client} section="api-keys"/>}/>
  <Route path="/workspace/developer/webhooks" element={<WorkspacePage key="webhooks" client={client} section="webhooks"/>}/>
  <Route path="/workspace/team" element={<WorkspacePage key="team" client={client} section="team"/>}/>
  <Route path={invitationAcceptPath} element={<InvitationPage client={client} store={store} memoryLink={invitationLink}/>}/>
  <Route path="/account" element={<AccountPage/>}/><Route path="*" element={<Navigate to="/login" replace/>}/>
</Routes></BrowserRouter></I18nProvider>;}
const root = createRoot(document.getElementById('root')!);
root.render(<App/>);
if (import.meta.hot) import.meta.hot.dispose(() => root.unmount());
