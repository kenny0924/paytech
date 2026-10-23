import { translator } from './i18n/index.ts';

const tc = translator('common'), ta = translator('auth');

export type AccountContext = {
  accountId: string; sessionId: string; emailMasked: string; emailVerified: boolean;
  contextType: 'ACCOUNT' | 'TENANT'; tenantId: string | null; memberId: string | null; allowedActions: string[];
  /** 租户会话刷新时工作区或成员已失效、改签账号上下文的公开原因码；其余情况为空。 */
  fallbackReason?: string | null;
};
export type Grant = { context: AccountContext; accessToken: string; expiresAt: string };
export type EmailLink = { challengeNo: string; secret: string };
export type ApiOptions = { method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'; body?: unknown; key?: string; signal?: AbortSignal };
export type ApiRequest = <T>(path: string, options?: ApiOptions) => Promise<T>;
type SessionLock = <T>(action: () => Promise<T>) => Promise<T>;
const browserSessionLock: SessionLock = action => typeof navigator !== 'undefined' && navigator.locks
  ? navigator.locks.request('paytech-client-session', action) : action();
export class RequestFailure extends Error {
  status: number; requestId: string; fields: Record<string, string>; code: string;
  constructor(status: number, message: string, requestId = '', fields: Record<string, string> = {}, code = '') {
    super(message); this.status = status; this.requestId = requestId; this.fields = fields; this.code = code;
  }
}
export function readEmailLink(fragment: string): EmailLink | null {
  if (!fragment.startsWith('#')) return null;
  const params = new URLSearchParams(fragment.slice(1));
  if (Array.from(params.keys()).length !== 2 || params.getAll('challengeNo').length !== 1 || params.getAll('secret').length !== 1) return null;
  const challengeNo = params.get('challengeNo')!, secret = params.get('secret')!;
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(challengeNo)
    || !/^[A-Za-z0-9_-]{43}$/.test(secret)) return null;
  return { challengeNo, secret };
}
export function passwordIssue(password: string): string | null {
  const normalized = password.normalize('NFC'), count = Array.from(normalized).length;
  if (count < 15) return ta('password.too_short', {min: 15});
  if (count > 128) return ta('password.too_long', {max: 128});
  if (new TextEncoder().encode(normalized).length > 512) return ta('password.too_large');
  return null;
}
function validGrant(value: unknown): value is Grant {
  if (!value || typeof value !== 'object') return false;
  const g = value as Grant, c = g.context;
  return typeof g.accessToken === 'string' && g.accessToken.length > 0 && typeof g.expiresAt === 'string'
    && Number.isFinite(Date.parse(g.expiresAt)) && !!c && /^[1-9][0-9]*$/.test(c.accountId)
    && /^[1-9][0-9]*$/.test(c.sessionId) && typeof c.emailMasked === 'string' && c.emailVerified === true
    && ['ACCOUNT', 'TENANT'].includes(c.contextType) && Array.isArray(c.allowedActions)
    && c.allowedActions.every(a => typeof a === 'string')
    && (c.fallbackReason === undefined || c.fallbackReason === null || (typeof c.fallbackReason === 'string' && /^[A-Z_]{1,64}$/.test(c.fallbackReason)))
    && (c.contextType === 'ACCOUNT' ? c.tenantId === null && c.memberId === null : !!c.tenantId && !!c.memberId);
}
export function createAuthClient(transport: typeof fetch = fetch, sessionLock: SessionLock = browserSessionLock) {
  let active: Grant | null = null, generation = 0, flight: Promise<Grant | null> | null = null, fallback: string | null = null;
  const listeners = new Set<() => void>();
  const publish = () => listeners.forEach(listener => listener());
  const clear = () => { generation++; active = null; publish(); };
  async function call<T>(path: string, body?: unknown, protectedRequest = false, options?: ApiOptions): Promise<T> {
    const headers: Record<string, string> = { Accept: 'application/json' };
    if (body !== undefined) headers['Content-Type'] = 'application/json';
    if (options?.key) headers['Idempotency-Key'] = options.key;
    if (protectedRequest && active) headers.Authorization = 'Bearer ' + active.accessToken;
    let response: Response;
    try {
      const send = () => transport(options ? '/api/client' + path : '/api/client/auth/v1/' + path, {
        method: options?.method ?? (body === undefined ? 'GET' : 'POST'), signal: options?.signal, headers, credentials: 'same-origin',
        body: body === undefined ? undefined : JSON.stringify(body), cache: 'no-store', redirect: 'error',
      });
      // HttpOnly Cookie 跨标签共享，所有会更换它的请求使用同一 Origin 锁。
      response = await (['login', 'refresh', 'logout', 'workspace-context', 'account-context'].includes(path) ? sessionLock(send) : send());
    } catch { throw new RequestFailure(0, tc('error.network_connection')); }
    if (response.status === 204) return undefined as T;
    let value: unknown;
    try { value = await response.json(); } catch { throw new RequestFailure(response.status, tc('error.bad_response')); }
    if (!response.ok) {
      const problem = value as {code?: unknown; detail?: unknown; requestId?: unknown; errors?: {field?: string; message?: string}[]};
      const message = typeof problem?.detail === 'string' && problem.detail.length <= 1024 ? problem.detail : tc('error.request_incomplete');
      const fields: Record<string,string> = {};
      if (Array.isArray(problem?.errors)) for (const error of problem.errors) {
        if (typeof error.field === 'string' && typeof error.message === 'string') fields[error.field] = error.message;
      }
      throw new RequestFailure(response.status, message, typeof problem?.requestId === 'string' ? problem.requestId : '', fields, typeof problem?.code === 'string' ? problem.code : '');
    }
    return value as T;
  }
  function restore(): Promise<Grant | null> {
    if (flight) return flight;
    const started = generation;
    flight = call<unknown>('refresh', {}).then(value => {
      if (!validGrant(value)) throw new RequestFailure(502, ta('session.bad_grant'));
      if (started !== generation) return null;
      if (value.context.fallbackReason) fallback = value.context.fallbackReason;
      active = value; publish(); return active;
    }).catch(error => {
      if (started === generation && error instanceof RequestFailure && error.status === 401) clear();
      throw error;
    }).finally(() => { flight = null; });
    return flight;
  }
  /**
   * 切换账号 / 工作区上下文：先使进行中的请求失效，再用新 Grant 整体替换
   * <li>旧 Refresh Cookie 由服务端在同一响应中替换；失败时保留原 Grant，由页面重新读取列表。</li>
   */
  async function switchContext(path: 'workspace-context' | 'account-context', body: Record<string, string>) {
    if (!active || Date.parse(active.expiresAt) <= Date.now() + 30_000) await restore();
    const started = ++generation;
    const value = await call<unknown>(path, body, true);
    if (!validGrant(value)) throw new RequestFailure(502, ta('session.bad_grant'));
    if (started === generation) { active = value; publish(); }
    return value.context;
  }
  return {
    call, restore, clear, current: () => active,
    selectWorkspace: (memberId: string) => switchContext('workspace-context', {memberId}),
    backToAccount: () => switchContext('account-context', {}),
    /** 读取并清除最近一次刷新降级的原因码，供开始页提示一次。 */
    takeFallback: () => { const reason = fallback; fallback = null; return reason; },
    async request<T>(path: string, options: ApiOptions = {}): Promise<T> {
      if (!/^\/(onboarding|onboarding-files|workspace|developer|team|invitations)\/v1\//.test(path) || /[\\#]/.test(path) || path.includes('..')) throw new Error(tc('error.invalid_path'));
      const started = generation, expectedAccount = active?.context.accountId;
      if (!active || Date.parse(active.expiresAt) <= Date.now() + 30_000) await restore();
      if (!active || started !== generation || (expectedAccount && active.context.accountId !== expectedAccount)) throw new RequestFailure(401, ta('session.account_changed'));
      let result: T;
      try { result = await call<T>(path, options.body, true, options); }
      catch (error) {
        if (!(error instanceof RequestFailure) || error.status !== 401) throw error;
        if (started !== generation) throw error;
        await restore();
        if (!active || started !== generation || (expectedAccount && active.context.accountId !== expectedAccount)) throw new RequestFailure(401, ta('session.account_changed'));
        result = await call<T>(path, options.body, true, options);
      }
      if (started !== generation) throw new RequestFailure(401, ta('session.account_changed_reload'));
      return result;
    },
    /**
     * 公开接口（不带登录凭证、不刷新会话）
     * <li>只允许邀请预览这一条固定路径。</li>
     */
    publicRequest<T>(path: '/invitations/v1/preview', body: unknown): Promise<T> {
      if (path !== '/invitations/v1/preview') throw new Error(tc('error.invalid_path'));
      return call<T>(path, body, false, {method: 'POST', body});
    },
    subscribe: (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener); }; },
    async login(email: string, password: string) {
      const started = ++generation;
      const value = await call<unknown>('login', {email, password});
      if (!validGrant(value)) throw new RequestFailure(502, ta('session.bad_grant'));
      if (started === generation) { active = value; publish(); }
    },
    async logout() { await call('logout', {}, true); clear(); },
    async context() {
      if (!active || Date.parse(active.expiresAt) <= Date.now() + 30_000) await restore();
      try { return await call<AccountContext>('context', undefined, true); }
      catch (error) {
        if (!(error instanceof RequestFailure) || error.status !== 401) throw error;
        await restore(); return call<AccountContext>('context', undefined, true);
      }
    },
  };
}
