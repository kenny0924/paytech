import {RequestFailure} from './auth.ts';
import {authIssues} from './auth-form.ts';

export type LoginIssues = {email?: string; password?: string};
export type LoginFeedback = {message: string; invalidCredentials: boolean};

export function loginIssues(email: string, password: string): LoginIssues {
  // 登录沿用已有凭据，不在此套用注册时的新密码强度规则或裁剪密码。
  return authIssues('login',{email,password,confirm:''});
}

export function loginFailure(error: unknown): LoginFeedback {
  if (error instanceof RequestFailure) {
    if (error.status===401) return {message:'邮箱或密码不正确，请重新输入', invalidCredentials:true};
    if (error.status===0) return {message:'网络连接失败，请检查后重试', invalidCredentials:false};
    if (error.status===429) return {message:'尝试次数过多，请稍后再试', invalidCredentials:false};
    if (error.status>=500) return {message:'登录服务暂时不可用，请稍后重试', invalidCredentials:false};
  }
  return {message:'暂时无法登录，请稍后重试', invalidCredentials:false};
}
