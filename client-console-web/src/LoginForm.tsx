import {useRef, useState} from 'react';
import type {FormEvent} from 'react';
import {Link} from 'react-router-dom';
import {loginFailure, loginIssues} from './login-feedback.ts';
import type {LoginFeedback, LoginIssues} from './login-feedback.ts';
import {TextField, focusField} from './TextField.tsx';
import {useT} from './i18n/react.tsx';

export function LoginForm({login, onSuccess}: {
  login: (email: string, password: string) => Promise<void>;
  onSuccess: () => void;
}) {
  const [email, setEmail]=useState(''), [password, setPassword]=useState('');
  const [busy, setBusy]=useState(false);
  const [issues, setIssues]=useState<LoginIssues>({});
  const [feedback, setFeedback]=useState<LoginFeedback | null>(null);
  const emailInput=useRef<HTMLInputElement>(null), passwordInput=useRef<HTMLInputElement>(null);
  const guard=useRef(false), t=useT('auth');

  function change(field: 'email' | 'password', value: string) {
    if (field==='email') setEmail(value); else setPassword(value);
    setIssues(previous => ({...previous, [field]:undefined}));
    setFeedback(null);
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
      setFeedback(loginFailure(error));
    } finally {
      guard.current=false; setBusy(false);
    }
  }

  const emailInvalid=!!issues.email || !!feedback?.invalidCredentials;
  const passwordInvalid=!!issues.password || !!feedback?.invalidCredentials;
  return <form className="login-form" noValidate onSubmit={submit} aria-busy={busy}>
    <TextField inputRef={emailInput} id="email" label={t('field.email')} type="email" required maxLength={320}
      disabled={busy} value={email} onValueChange={value=>change('email',value)}
      autoComplete="email" autoCapitalize="none" spellCheck={false} placeholder={t('field.email_placeholder')}
      error={issues.email} invalid={emailInvalid} describedBy={feedback?.invalidCredentials ? 'password-feedback' : undefined}/>
    <TextField inputRef={passwordInput} id="password" label={t('field.password')} type="password" required
      disabled={busy} value={password} onValueChange={value=>change('password',value)}
      autoComplete="current-password" error={issues.password} invalid={passwordInvalid} message={feedback?.message}/>
    <div className="right"><Link to="/forgot-password">{t('login.forgot')}</Link></div>
    <button className="primary" type="submit" disabled={busy}>{busy ? t('login.signing_in') : t('submit.login')}</button>
    <nav className="auth-links"><Link to="/register">{t('login.no_account')}</Link></nav>
  </form>;
}
