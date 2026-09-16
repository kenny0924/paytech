import {passwordIssue, RequestFailure} from './auth.ts';

export type AuthKind = 'login' | 'register' | 'forgot' | 'reset' | 'verify' | 'sent';
export type AuthIssues = {email?: string; password?: string; confirm?: string};

export function emailIssue(email: string): string | undefined {
  const address=email.trim();
  if (!address) return '请输入邮箱地址';
  if (address.length>320 || !/^[^\s@]+@[^\s@.]+(?:\.[^\s@.]+)+$/.test(address)) return '请输入有效的邮箱地址';
}

export function authIssues(kind: AuthKind, values: {email: string; password: string; confirm: string}): AuthIssues {
  const fields: AuthIssues={};
  if (['login','register','forgot','sent'].includes(kind)) {
    const issue=emailIssue(values.email);
    if (issue) fields.email=issue;
  }
  if (kind==='login') {
    if (!values.password) fields.password='请输入密码';
  } else if (kind==='register' || kind==='reset') {
    const issue=values.password ? passwordIssue(values.password) : '请输入密码';
    if (issue) fields.password=issue;
    if (!values.confirm) fields.confirm='请再次输入密码';
    else if (values.password!==values.confirm) fields.confirm='两次输入的密码不一致';
  }
  return fields;
}

export function authFailure(error: unknown, kind: AuthKind): {fields: AuthIssues; message: string} {
  const fields: AuthIssues={};
  if (error instanceof RequestFailure) {
    if (error.status===0) return {fields,message:'网络连接失败，请检查后重试'};
    if (error.status===429) return {fields,message:'尝试次数过多，请稍后再试'};
    if (error.status>=500) return {fields,message:'服务暂时不可用，请稍后重试'};
    if (error.code==='CLIENT_EMAIL_LINK_INVALID') return {fields,message:'链接已失效，请重新申请邮件'};
    // 只映射当前页面可修改的字段，不显示原始诊断、内部字段或账号存在性。
    if (error.fields.email && ['login','register','forgot','sent'].includes(kind)) fields.email='请输入有效的邮箱地址';
    if ((error.fields.password || error.fields.newPassword) && ['register','reset'].includes(kind)) fields.password='密码不符合要求，请调整后重试';
  }
  return {fields,message:Object.keys(fields).length ? '' : '暂时无法完成操作，请稍后重试'};
}
