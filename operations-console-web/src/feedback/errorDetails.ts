import { ApiError } from '../api/session.ts';
import { translator } from '../i18n/index.ts';

const t = translator('common');

export interface ErrorDetails {
  code: string;
  message: string;
  requestId: string;
}

/** ApiError 已在接口边界处理安全消息；未知异常不展示原始内容。 */
export function errorDetails(error: unknown): ErrorDetails | null {
  if (error instanceof Error && error.name === 'AbortError') return null;
  if (error instanceof ApiError) {
    if (error.code === 'SESSION_CHANGED') return null;
    return { code: error.code || 'REQUEST_FAILED', message: error.message, requestId: error.requestId.trim() };
  }
  return { code: 'REQUEST_FAILED', message: t('error.requestFailed'), requestId: '' };
}

export function errorNotificationKey(details: ErrorDetails): string {
  return JSON.stringify(details.requestId
    ? ['request', details.requestId, details.code]
    : ['message', details.code, details.message]);
}

/** 复制内容与卡片展示一致，不包含状态、堆栈、请求体或调用方标题。 */
export function copyableErrorDetails(details: ErrorDetails): string {
  return JSON.stringify({ code: details.code, message: details.message, requestId: details.requestId || t('error.notReturned') }, null, 2);
}
