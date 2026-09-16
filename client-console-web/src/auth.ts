export type AccountContext = {
  accountId: string; sessionId: string; emailMasked: string; emailVerified: boolean;
  contextType: 'ACCOUNT' | 'TENANT'; tenantId: string | null; memberId: string | null; allowedActions: string[];
};
export type Grant = { context: AccountContext; accessToken: string; expiresAt: string };
export type EmailLink = { challengeNo: string; secret: string };
export type ApiOptions = { method?: 'GET' | 'POST' | 'PATCH' | 'DELETE'; body?: unknown; key?: string; signal?: AbortSignal };
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
  if (count < 15) return '密码至少需要 15 个字符';
  if (count > 128) return '密码不能超过 128 个字符';
  if (new TextEncoder().encode(normalized).length > 512) return '密码内容过长，请调整后重试';
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
    && (c.contextType === 'ACCOUNT' ? c.tenantId === null && c.memberId === null : !!c.tenantId && !!c.memberId);
}
export function createAuthClient(transport: typeof fetch = fetch, sessionLock: SessionLock = browserSessionLock) {
  let active: Grant | null = null, generation = 0, flight: Promise<Grant | null> | null = null;
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
      response = await (['login', 'refresh', 'logout'].includes(path) ? sessionLock(send) : send());
    } catch { throw new RequestFailure(0, '网络连接失败，请检查连接后重试'); }
    if (response.status === 204) return undefined as T;
    let value: unknown;
    try { value = await response.json(); } catch { throw new RequestFailure(response.status, '服务响应异常，请稍后重试'); }
    if (!response.ok) {
      const problem = value as {code?: unknown; detail?: unknown; requestId?: unknown; errors?: {field?: string; message?: string}[]};
      const message = typeof problem?.detail === 'string' && problem.detail.length <= 1024 ? problem.detail : '请求未完成，请稍后重试';
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
      if (!validGrant(value)) throw new RequestFailure(502, '登录状态响应异常，请重新登录');
      if (started !== generation) return null;
      active = value; publish(); return active;
    }).catch(error => {
      if (started === generation && error instanceof RequestFailure && error.status === 401) clear();
      throw error;
    }).finally(() => { flight = null; });
    return flight;
  }
  return {
    call, restore, clear, current: () => active,
    async request<T>(path: string, options: ApiOptions = {}): Promise<T> {
      if (!/^\/(onboarding|onboarding-files)\/v1\//.test(path) || /[\\#]/.test(path) || path.includes('..')) throw new Error('请求路径无效');
      const started = generation, expectedAccount = active?.context.accountId;
      if (!active || Date.parse(active.expiresAt) <= Date.now() + 30_000) await restore();
      if (!active || started !== generation || (expectedAccount && active.context.accountId !== expectedAccount)) throw new RequestFailure(401, '登录账号已变更，请重新登录');
      let result: T;
      try { result = await call<T>(path, options.body, true, options); }
      catch (error) {
        if (!(error instanceof RequestFailure) || error.status !== 401) throw error;
        if (started !== generation) throw error;
        await restore();
        if (!active || started !== generation || (expectedAccount && active.context.accountId !== expectedAccount)) throw new RequestFailure(401, '登录账号已变更，请重新登录');
        result = await call<T>(path, options.body, true, options);
      }
      if (started !== generation) throw new RequestFailure(401, '登录账号已变更，请重新读取资料');
      return result;
    },
    subscribe: (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener); }; },
    async login(email: string, password: string) {
      const started = ++generation;
      const value = await call<unknown>('login', {email, password});
      if (!validGrant(value)) throw new RequestFailure(502, '登录状态响应异常，请重新登录');
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
