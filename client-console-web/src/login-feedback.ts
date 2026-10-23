import {RequestFailure} from './auth.ts';
import {authIssues} from './auth-form.ts';
import {translator} from './i18n/index.ts';

const tc = translator('common'), ta = translator('auth');

export type LoginIssues = {email?: string; password?: string};
export type LoginFeedback = {message: string; invalidCredentials: boolean};

export function loginIssues(email: string, password: string): LoginIssues {
  // 登录沿用已有凭据，不在此套用注册时的新密码强度规则或裁剪密码。
  return authIssues('login',{email,password,confirm:''});
}

export function loginFailure(error: unknown): LoginFeedback {
  if (error instanceof RequestFailure) {
    if (error.status===401) return {message:ta('login.invalid_credentials'), invalidCredentials:true};
    if (error.status===0) return {message:tc('error.network'), invalidCredentials:false};
    if (error.status===429) return {message:tc('error.rate_limited'), invalidCredentials:false};
    if (error.status>=500) return {message:ta('login.unavailable'), invalidCredentials:false};
  }
  return {message:ta('login.failed'), invalidCredentials:false};
}
