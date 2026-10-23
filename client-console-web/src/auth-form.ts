import {passwordIssue, RequestFailure} from './auth.ts';
import {translator} from './i18n/index.ts';

const tc = translator('common'), ta = translator('auth');

export type AuthKind = 'login' | 'register' | 'forgot' | 'reset' | 'verify' | 'sent';
export type AuthIssues = {email?: string; password?: string; confirm?: string};

export function emailIssue(email: string): string | undefined {
  const address=email.trim();
  if (!address) return ta('email.required');
  if (address.length>320 || !/^[^\s@]+@[^\s@.]+(?:\.[^\s@.]+)+$/.test(address)) return ta('email.invalid');
}

export function authIssues(kind: AuthKind, values: {email: string; password: string; confirm: string}): AuthIssues {
  const fields: AuthIssues={};
  if (['login','register','forgot','sent'].includes(kind)) {
    const issue=emailIssue(values.email);
    if (issue) fields.email=issue;
  }
  if (kind==='login') {
    if (!values.password) fields.password=ta('password.required');
  } else if (kind==='register' || kind==='reset') {
    const issue=values.password ? passwordIssue(values.password) : ta('password.required');
    if (issue) fields.password=issue;
    if (!values.confirm) fields.confirm=ta('password.confirm_required');
    else if (values.password!==values.confirm) fields.confirm=ta('password.mismatch');
  }
  return fields;
}

export function authFailure(error: unknown, kind: AuthKind): {fields: AuthIssues; message: string} {
  const fields: AuthIssues={};
  if (error instanceof RequestFailure) {
    if (error.status===0) return {fields,message:tc('error.network')};
    if (error.status===429) return {fields,message:tc('error.rate_limited')};
    if (error.status>=500) return {fields,message:tc('error.unavailable')};
    if (error.code==='CLIENT_EMAIL_LINK_INVALID') return {fields,message:ta('link.expired')};
    // 只映射当前页面可修改的字段，不显示原始诊断、内部字段或账号存在性。
    if (error.fields.email && ['login','register','forgot','sent'].includes(kind)) fields.email=ta('email.invalid');
    if ((error.fields.password || error.fields.newPassword) && ['register','reset'].includes(kind)) fields.password=ta('password.rejected');
  }
  return {fields,message:Object.keys(fields).length ? '' : tc('error.generic')};
}
