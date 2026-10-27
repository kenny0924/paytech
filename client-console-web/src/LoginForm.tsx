import {useRef, useState} from 'react';
import type {FormEvent} from 'react';
import {Link} from 'react-router-dom';
import {loginFailure, loginIssues} from './login-feedback.ts';
import type {LoginFeedback, LoginIssues} from './login-feedback.ts';
import {TextField, focusField} from './TextField.tsx';
import {useT} from './i18n/react.tsx';
import {Busy} from './onboarding/ui.tsx';

/**
 * 登录表单（C1）：服务端失败写在表单顶部一行（3px 危险色竖线），凭据错误时密码框标红并清空
 * <li>"忘记密码？"放在密码标签右侧。</li>
 */
export function LoginForm({login, onSuccess}: {
  login: (email: string, password: string) => Promise<void>;
  onSuccess: () => void;
}) {
  const [email, setEmail]=useState(''), [password, setPassword]=useState('');
  const [busy, setBusy]=useState(false);
  const [issues, setIssues]=useState<LoginIssues>({});
  const [feedback, setFeedback]=useState<LoginFeedback | null>(null);
  const emailInput=useRef<HTMLInputElement>(null), passwordInput=useRef<HTMLInputElement>(null), notice=useRef<HTMLDivElement>(null);
  const guard=useRef(false), t=useT('auth');

  function change(field: 'email' | 'password', value: string) {
    if (field==='email') setEmail(value); else setPassword(value);
    setIssues(previous => ({...previous, [field]:undefined}));
  }

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (guard.current) return;
    const next=loginIssues(email, password);
    setIssues(next); setFeedback(null);
    if (next.email || next.password) {
      focusField((next.email ? emailInput : passwordInput).current);
      return;
    }
    guard.current=true; setBusy(true);
    try {
      await login(email.trim(), password);
      setPassword(''); onSuccess();
    } catch (error) {
      const result=loginFailure(error);
      setFeedback(result);
      if (result.invalidCredentials) { setPassword(''); setIssues({password:t('login.reenter_password')}); focusField(passwordInput.current); }
      else notice.current?.focus();
    } finally {
      guard.current=false; setBusy(false);
    }
  }

  return <form className="login-form" noValidate onSubmit={submit} aria-busy={busy}>
    {feedback && <div className="notice error" role="alert" tabIndex={-1} ref={notice}>{feedback.message}</div>}
    <TextField inputRef={emailInput} id="email" label={t('field.email')} type="email" required maxLength={320}
      disabled={busy} value={email} onValueChange={value=>change('email',value)}
      autoComplete="email" autoCapitalize="none" spellCheck={false}
      error={issues.email} invalid={!!feedback?.invalidCredentials}/>
    <TextField inputRef={passwordInput} id="password" label={t('field.password')} type="password" required
      disabled={busy} value={password} onValueChange={value=>change('password',value)}
      autoComplete="current-password" error={issues.password} invalid={!!feedback?.invalidCredentials}
      labelAction={<Link to="/forgot-password">{t('login.forgot')}</Link>}/>
    <button className="primary" type="submit" disabled={busy}><Busy active={busy} label={t('login.signing_in')}>{t('submit.login')}</Busy></button>
    <p className="auth-links">{t('login.no_account_prefix')} <Link to="/register">{t('login.register')}</Link></p>
  </form>;
}
