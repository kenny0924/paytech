import {useRef, useState} from 'react';
import type {FormEvent} from 'react';
import {Link} from 'react-router-dom';
import {loginFailure, loginIssues} from './login-feedback.ts';
import type {LoginFeedback, LoginIssues} from './login-feedback.ts';
import {TextField, focusField} from './TextField.tsx';

export function LoginForm({login, onSuccess}: {
  login: (email: string, password: string) => Promise<void>;
  onSuccess: () => void;
}) {
  const [email, setEmail]=useState(''), [password, setPassword]=useState('');
  const [busy, setBusy]=useState(false);
  const [issues, setIssues]=useState<LoginIssues>({});
  const [feedback, setFeedback]=useState<LoginFeedback | null>(null);
  const emailInput=useRef<HTMLInputElement>(null), passwordInput=useRef<HTMLInputElement>(null);
  const guard=useRef(false);

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
    <TextField inputRef={emailInput} id="email" label="邮箱" type="email" required maxLength={320}
      disabled={busy} value={email} onValueChange={value=>change('email',value)}
      autoComplete="email" autoCapitalize="none" spellCheck={false} placeholder="输入您的邮箱"
      error={issues.email} invalid={emailInvalid} describedBy={feedback?.invalidCredentials ? 'password-feedback' : undefined}/>
    <TextField inputRef={passwordInput} id="password" label="密码" type="password" required
      disabled={busy} value={password} onValueChange={value=>change('password',value)}
      autoComplete="current-password" error={issues.password} invalid={passwordInvalid} message={feedback?.message}/>
    <div className="right"><Link to="/forgot-password">忘记密码？</Link></div>
    <button className="primary" type="submit" disabled={busy}>{busy ? '正在登录…' : '登录'}</button>
    <nav className="auth-links"><Link to="/register">还没有账号？创建账号</Link></nav>
  </form>;
}
