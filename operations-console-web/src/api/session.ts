import type { SessionContextResponseBody, TokenGrantResponseBody } from './types.ts';

const problemRequestId = /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/;
const legacyRequestId = /^[A-Za-z0-9._:-]{1,128}$/;
const canonicalProblemTimestamp = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{6}Z$/;
const legacyInstant = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?Z$/;
const maximumJsonLength = 1_000_000;
const maximumJsonDepth = 64;
const statusTitles = new Map<number, string>([
  [400, 'Bad Request'], [401, 'Unauthorized'], [402, 'Payment Required'], [403, 'Forbidden'],
  [404, 'Not Found'], [405, 'Method Not Allowed'], [406, 'Not Acceptable'],
  [408, 'Request Timeout'], [409, 'Conflict'], [410, 'Gone'],
  [411, 'Length Required'], [412, 'Precondition Failed'], [413, 'Content Too Large'],
  [414, 'URI Too Long'], [415, 'Unsupported Media Type'], [416, 'Requested range not satisfiable'],
  [417, 'Expectation Failed'], [418, "I'm a teapot"], [421, 'Misdirected Request'],
  [422, 'Unprocessable Content'], [423, 'Locked'], [424, 'Failed Dependency'], [425, 'Too Early'],
  [426, 'Upgrade Required'], [428, 'Precondition Required'], [429, 'Too Many Requests'],
  [431, 'Request Header Fields Too Large'], [451, 'Unavailable For Legal Reasons'],
  [500, 'Internal Server Error'], [501, 'Not Implemented'], [502, 'Bad Gateway'],
  [503, 'Service Unavailable'], [504, 'Gateway Timeout'], [505, 'HTTP Version not supported'],
  [506, 'Variant Also Negotiates'], [507, 'Insufficient Storage'], [508, 'Loop Detected'], [509, 'Bandwidth Limit Exceeded'],
  [510, 'Not Extended'], [511, 'Network Authentication Required'],
]);

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}
function nonBlankString(value: unknown): value is string {
  return typeof value === 'string' && value.trim().length > 0;
}
function isRealUtcInstant(value: string) {
  const parsed = Date.parse(value);
  return Number.isFinite(parsed) && new Date(parsed).toISOString().slice(0, 19) === value.slice(0, 19);
}
function isTokenCharacter(value: string | undefined) {
  return value !== undefined && /[!#$%&'*+.^_`|~0-9A-Za-z-]/.test(value);
}
function isOptionalWhitespace(value: string | undefined) {
  return value === ' ' || value === '\t';
}
function responseMediaType(value: string | null): 'problem' | 'legacy' | null {
  if (value === null) return null;
  let position = 0;
  const skipWhitespace = () => { while (isOptionalWhitespace(value[position])) position++; };
  const token = () => {
    const start = position;
    while (isTokenCharacter(value[position])) position++;
    return position === start ? null : value.slice(start, position);
  };
  skipWhitespace();
  const type = token();
  if (type === null || value[position++] !== '/') return null;
  const subtype = token();
  if (subtype === null) return null;
  const mediaType = `${type}/${subtype}`.toLowerCase();
  const parameters = new Set<string>();
  skipWhitespace();
  while (position < value.length) {
    if (value[position++] !== ';') return null;
    skipWhitespace();
    const name = token();
    if (name === null || parameters.has(name.toLowerCase())) return null;
    parameters.add(name.toLowerCase());
    skipWhitespace();
    if (value[position++] !== '=') return null;
    skipWhitespace();
    if (value[position] === '"') {
      position++;
      let closed = false;
      while (position < value.length) {
        const character = value[position++];
        if (character === '"') { closed = true; break; }
        if (character === '\\') {
          const escaped = value[position++];
          if (escaped === undefined || (escaped !== ' ' && escaped !== '\t' && !/[\x21-\x7e]/.test(escaped))) return null;
        } else if (character === '\r' || character === '\n' || character === undefined
          || (character !== '\t' && !/[\x20-\x7e]/.test(character))) return null;
      }
      if (!closed) return null;
    } else if (token() === null) return null;
    skipWhitespace();
  }
  if (mediaType === 'application/problem+json') return 'problem';
  return mediaType === 'application/json' ? 'legacy' : null;
}
function diagnosticRequestId(value: string | null) {
  return value !== null && problemRequestId.test(value) ? value : '';
}
function hasUniqueJsonObjectKeys(text: string) {
  if (!text || text.length > maximumJsonLength) return false;
  let position = 0;
  const whitespace = () => { while (/\s/.test(text[position] ?? '')) position++; };
  const string = (): string | null => {
    if (text[position++] !== '"') return null;
    let decoded = '';
    while (position < text.length) {
      const character = text[position++];
      if (character === '"') return decoded;
      if (character === '\\') {
        const escaped = text[position++];
        if (escaped === '"' || escaped === '\\' || escaped === '/') decoded += escaped;
        else if (escaped === 'b') decoded += '\b';
        else if (escaped === 'f') decoded += '\f';
        else if (escaped === 'n') decoded += '\n';
        else if (escaped === 'r') decoded += '\r';
        else if (escaped === 't') decoded += '\t';
        else if (escaped === 'u') {
          const hex = text.slice(position, position + 4);
          if (!/^[0-9A-Fa-f]{4}$/.test(hex)) return null;
          decoded += String.fromCharCode(Number.parseInt(hex, 16)); position += 4;
        } else return null;
      } else if (character.charCodeAt(0) < 0x20) return null;
      else decoded += character;
    }
    return null;
  };
  const value = (depth: number): boolean => {
    if (depth > maximumJsonDepth) return false;
    whitespace();
    const character = text[position];
    if (character === '{') {
      position++; whitespace();
      const keys = new Set<string>();
      if (text[position] === '}') { position++; return true; }
      while (true) {
        const key = string();
        if (key === null || keys.has(key)) return false;
        keys.add(key); whitespace();
        if (text[position++] !== ':') return false;
        if (!value(depth + 1)) return false;
        whitespace();
        if (text[position] === '}') { position++; return true; }
        if (text[position++] !== ',') return false;
        whitespace();
      }
    }
    if (character === '[') {
      position++; whitespace();
      if (text[position] === ']') { position++; return true; }
      while (true) {
        if (!value(depth + 1)) return false;
        whitespace();
        if (text[position] === ']') { position++; return true; }
        if (text[position++] !== ',') return false;
        whitespace();
      }
    }
    if (character === '"') return string() !== null;
    for (const literal of ['true', 'false', 'null']) {
      if (text.startsWith(literal, position)) { position += literal.length; return true; }
    }
    const number = text.slice(position).match(/^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[Ee][+-]?\d+)?/);
    if (number === null) return false;
    position += number[0].length;
    return true;
  };
  if (!value(0)) return false;
  whitespace();
  return position === text.length;
}
function parseJsonValue(text: string): unknown {
  if (!hasUniqueJsonObjectKeys(text)) throw new Error('invalid JSON');
  return JSON.parse(text) as unknown;
}
function parseJsonObject(text: string): Record<string, unknown> | null {
  try { const value = parseJsonValue(text); return isRecord(value) ? value : null; }
  catch { return null; }
}
function validErrors(value: unknown) {
  return Array.isArray(value) && value.length > 0 && value.every(item => isRecord(item)
    && Object.keys(item).length === 2 && Object.hasOwn(item, 'field') && Object.hasOwn(item, 'message')
    && nonBlankString(item.field) && nonBlankString(item.message));
}
const emptyFieldErrors = Object.freeze([]) as readonly { readonly field: string; readonly message: string }[];
function copyFieldErrors(value: unknown) {
  if (!validErrors(value)) return emptyFieldErrors;
  const errors = value as readonly { field: string; message: string }[];
  return Object.freeze(errors.map(item => Object.freeze({ field: item.field, message: item.message })));
}
function readProblem(status: number, headerRequestId: string, value: Record<string, unknown>) {
  if (value.type !== 'about:blank' || value.title !== statusTitles.get(status)
    || !nonBlankString(value.detail) || !nonBlankString(value.code)
    || !nonBlankString(value.requestId) || !problemRequestId.test(value.requestId)
    || value.requestId !== headerRequestId || !nonBlankString(value.timestamp)
    || !canonicalProblemTimestamp.test(value.timestamp) || !isRealUtcInstant(value.timestamp)
    || Object.hasOwn(value, 'status') || Object.hasOwn(value, 'instance') || Object.hasOwn(value, 'message')
    || (Object.hasOwn(value, 'errors') && !validErrors(value.errors))) return null;
  return { code: value.code, detail: value.detail, requestId: value.requestId, fieldErrors: copyFieldErrors(value.errors) };
}
function readLegacy(value: Record<string, unknown>) {
  if (Object.keys(value).length !== 4 || !nonBlankString(value.code) || !nonBlankString(value.message)
    || !nonBlankString(value.requestId) || !legacyRequestId.test(value.requestId)
    || !nonBlankString(value.timestamp) || !legacyInstant.test(value.timestamp) || !isRealUtcInstant(value.timestamp)) return null;
  return { code: value.code, message: value.message, requestId: value.requestId };
}

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly requestId: string;
  readonly fieldErrors: readonly { readonly field: string; readonly message: string }[];
  constructor(status: number, code: string, requestId = '', responseMessage?: unknown,
    fieldErrors: readonly { readonly field: string; readonly message: string }[] = emptyFieldErrors) {
    const businessFailure = (status === 400 && (code === 'BUSINESS_CHECK_FAILED' || code === 'INVALID_REQUEST'))
      || (status === 409 && code === 'CONCURRENT_REQUEST');
    const businessMessage = businessFailure && typeof responseMessage === 'string' && responseMessage.trim()
      ? responseMessage : undefined;
    const message = businessMessage ?? (status === 401 ? '登录已失效，请重新登录。'
      : status === 403 ? '你没有执行此操作的权限。'
      : status === 409 ? '数据已发生变化，请刷新后重试。'
      : status === 429 ? '操作过于频繁，请稍后再试。'
      : status >= 500 ? '服务暂时不可用，请稍后重试。'
      : '操作未完成，请检查输入后重试。');
    super(message); this.status = status; this.code = code; this.requestId = requestId; this.fieldErrors = copyFieldErrors(fieldErrors);
  }
}

export type SuccessDecoder<T> = (value: unknown) => T;
export interface RequestClient { request<T>(path: string, options?: RequestInit, decode?: SuccessDecoder<T>): Promise<T>; }

const sessionEndingCommands = new Set(['/auth/v1/password', '/auth/v1/bootstrap/administrator']);
const sessionStorageKey = 'paytech.operations.session.v1';
type SessionStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>;
interface SavedSession {
  version: 1;
  accessToken: string; refreshToken: string;
  accessExpiresAt: number; refreshExpiresAt: number;
}

/** 当前标签页恢复会话；单飞轮换与会话版本隔离并发响应。 */
export class OperationsSession implements RequestClient {
  private accessToken = '';
  private refreshToken = '';
  private accessExpiresAt = 0;
  private refreshExpiresAt = 0;
  private revision = 0;
  private logoutRevision: number | null = null;
  private refreshFlight: Promise<void> | null = null;
  private readonly baseUrl: string;
  private readonly storage?: SessionStorage;
  onExpired: () => void = () => {};

  constructor(baseUrl = '/api/operations', storage?: SessionStorage) {
    this.baseUrl = baseUrl;
    this.storage = storage;
    this.restore();
  }

  private restore() {
    if (!this.storage) return;
    try {
      const raw = this.storage.getItem(sessionStorageKey);
      if (raw === null) return;
      const saved: SavedSession = JSON.parse(raw);
      if (saved?.version !== 1 || typeof saved.accessToken !== 'string' || !saved.accessToken
        || typeof saved.refreshToken !== 'string' || !saved.refreshToken
        || !Number.isSafeInteger(saved.accessExpiresAt) || saved.accessExpiresAt <= 0
        || !Number.isSafeInteger(saved.refreshExpiresAt) || saved.refreshExpiresAt <= Date.now()) {
        this.forgetSavedSession(); return;
      }
      this.accessToken = saved.accessToken; this.refreshToken = saved.refreshToken;
      this.accessExpiresAt = saved.accessExpiresAt; this.refreshExpiresAt = saved.refreshExpiresAt;
    } catch { this.forgetSavedSession(); }
  }

  private save() {
    if (!this.storage || !this.refreshToken) return;
    const saved: SavedSession = { version: 1, accessToken: this.accessToken, refreshToken: this.refreshToken,
      accessExpiresAt: this.accessExpiresAt, refreshExpiresAt: this.refreshExpiresAt };
    try { this.storage.setItem(sessionStorageKey, JSON.stringify(saved)); }
    catch { this.forgetSavedSession(); }
  }

  private forgetSavedSession() {
    // 浏览器禁用存储时仍允许当前页面使用内存会话；不留下可恢复的旧轮换凭据。
    try { this.storage?.removeItem(sessionStorageKey); } catch { /* 当前浏览器存储不可访问。 */ }
  }

  private async exchange<T>(path: string, options: RequestInit = {}, decode?: SuccessDecoder<T>): Promise<T> {
    const headers = new Headers(options.headers);
    if (options.method && options.method !== 'GET') {
      headers.set('Content-Type', 'application/json');
    }
    const response = await fetch(this.baseUrl + path, {
      ...options, credentials: 'omit', cache: 'no-store', redirect: 'error', headers,
    });
    const requestId = diagnosticRequestId(response.headers.get('X-Request-Id'));
    if (!response.ok) {
      const mediaType = responseMediaType(response.headers.get('Content-Type'));
      if (mediaType === 'problem') {
        const problem = readProblem(response.status, requestId, parseJsonObject(await response.text()) ?? {});
        if (problem) throw new ApiError(response.status, problem.code, problem.requestId, problem.detail, problem.fieldErrors);
      } else if (mediaType === 'legacy') {
        const legacy = readLegacy(parseJsonObject(await response.text()) ?? {});
        if (legacy) throw new ApiError(response.status, legacy.code, legacy.requestId, legacy.message);
      }
      throw new ApiError(response.status, 'REQUEST_FAILED', requestId);
    }
    if (response.status === 204 || response.headers.get('content-length') === '0') {
      try { return decode ? decode(undefined) : undefined as T; }
      catch { throw new ApiError(502, 'INVALID_RESPONSE', requestId); }
    }
    const text = await response.text();
    try {
      const value = text ? parseJsonValue(text) : undefined;
      return decode ? decode(value) : value as T;
    }
    catch { throw new ApiError(502, 'INVALID_RESPONSE', requestId); }
  }

  private assertRevision(revision: number) {
    if (revision !== this.revision) throw new ApiError(401, 'SESSION_CHANGED');
  }
  private accept(tokens: TokenGrantResponseBody, revision: number) {
    this.assertRevision(revision);
    if (typeof tokens?.accessToken !== 'string' || !tokens.accessToken || tokens.tokenType !== 'Bearer'
      || !Number.isInteger(tokens.expiresIn) || !(tokens.expiresIn > 0 && tokens.expiresIn <= 900)
      || typeof tokens.refreshToken !== 'string' || !tokens.refreshToken
      || !Number.isInteger(tokens.refreshExpiresIn) || !(tokens.refreshExpiresIn > 0 && tokens.refreshExpiresIn <= 28800)) {
      throw new ApiError(502, 'INVALID_AUTHENTICATION_RESPONSE');
    }
    this.accessToken = tokens.accessToken;
    this.refreshToken = tokens.refreshToken;
    this.accessExpiresAt = Date.now() + tokens.expiresIn * 1000;
    this.refreshExpiresAt = Date.now() + tokens.refreshExpiresIn * 1000;
    this.save();
  }

  private resetCredentials() {
    ++this.revision;
    this.accessToken = ''; this.refreshToken = '';
    this.accessExpiresAt = 0; this.refreshExpiresAt = 0;
    this.refreshFlight = null;
    this.logoutRevision = null;
    this.forgetSavedSession();
  }

  async login(loginIdentifier: string, password: string): Promise<SessionContextResponseBody> {
    this.resetCredentials();
    const revision = this.revision;
    const tokens = await this.exchange<TokenGrantResponseBody>('/auth/v1/login', {
      method: 'POST', body: JSON.stringify({ loginIdentifier, password }),
    });
    this.accept(tokens, revision);
    return this.context();
  }

  refresh(): Promise<void> {
    if (this.logoutRevision === this.revision) return Promise.reject(new ApiError(401, 'SESSION_CHANGED'));
    if (this.refreshFlight) return this.refreshFlight;
    if (!this.refreshToken) return Promise.reject(new ApiError(401, 'OPERATIONS_SESSION_INVALID'));
    if (this.refreshExpiresAt <= Date.now()) {
      this.clear();
      return Promise.reject(new ApiError(401, 'OPERATIONS_SESSION_INVALID'));
    }
    const revision = this.revision;
    // 轮换结果到达前不恢复旧代，避免整页刷新后重放已被服务端消费的 Refresh Token。
    this.forgetSavedSession();
    const flight = this.exchange<TokenGrantResponseBody>('/auth/v1/refresh', {
      method: 'POST', body: JSON.stringify({ refreshToken: this.refreshToken }),
    }).then(tokens => this.accept(tokens, revision)).catch((error: unknown) => {
      this.assertRevision(revision);
      if (revision === this.revision && error instanceof ApiError && error.status === 401) this.clear();
      if (revision === this.revision && error instanceof ApiError && error.status === 429) this.save();
      throw error;
    });
    this.refreshFlight = flight;
    void flight.finally(() => { if (this.refreshFlight === flight) this.refreshFlight = null; }).catch(() => {});
    return flight;
  }

  private async fresh() {
    if (!this.accessToken || this.accessExpiresAt <= Date.now() + 15_000) await this.refresh();
  }

  private authorized(options: RequestInit, token: string): RequestInit {
    const headers = new Headers(options.headers);
    headers.set('Authorization', `Bearer ${token}`);
    return { ...options, headers };
  }

  private async retryQuery<T>(path: string, options: RequestInit, revision: number, decode?: SuccessDecoder<T>): Promise<T> {
    try {
      const result = await this.exchange<T>(path, this.authorized(options, this.accessToken), decode);
      this.assertRevision(revision);
      return result;
    } catch (error) {
      this.assertRevision(revision);
      // 新一代凭据仍被拒绝时结束会话；限流和临时服务错误不清理凭据。
      if (error instanceof ApiError && error.status === 401) this.clear();
      throw error;
    }
  }

  async request<T>(path: string, options: RequestInit = {}, decode?: SuccessDecoder<T>): Promise<T> {
    const revision = this.revision;
    await this.fresh(); this.assertRevision(revision);
    const token = this.accessToken;
    try {
      const result = await this.exchange<T>(path, this.authorized(options, token), decode);
      this.assertRevision(revision);
      if (sessionEndingCommands.has(path)) this.clear();
      return result;
    } catch (error) {
      this.assertRevision(revision);
      // 仅查询允许刷新后重试一次；写操作由调用者保留同一幂等键重试。
      if (error instanceof ApiError && error.status === 401 && (!options.method || options.method === 'GET')) {
        if (this.accessToken === token) await this.refresh();
        this.assertRevision(revision);
        return this.retryQuery<T>(path, options, revision, decode);
      }
      throw error;
    }
  }

  context() { return this.request<SessionContextResponseBody>('/auth/v1/context'); }
  clear() {
    this.resetCredentials();
    this.onExpired();
  }
  async logout() {
    const revision = this.revision;
    const ready = this.refreshFlight ?? this.fresh();
    // 等待已在执行的轮换，但不允许退出期间再消费下一代 Refresh Token。
    if (revision === this.revision) this.logoutRevision = revision;
    try {
      await ready;
      this.assertRevision(revision);
      const accessToken = this.accessToken; const refreshToken = this.refreshToken;
      // 用户已退出本页，远端撤销结果不能恢复旧凭据或清除后来建立的新会话。
      this.clear();
      const clearedRevision = this.revision;
      await this.exchange('/auth/v1/logout', {
        method: 'POST', body: JSON.stringify({ refreshToken }), headers: { Authorization: `Bearer ${accessToken}` },
      }).catch((error: unknown) => {
        this.assertRevision(clearedRevision);
        throw error;
      });
    } finally { if (revision === this.revision) this.clear(); }
  }
}

function browserSessionStorage(): SessionStorage | undefined {
  try { return typeof window === 'undefined' ? undefined : window.sessionStorage; }
  catch { return undefined; }
}
export const session = new OperationsSession('/api/operations', browserSessionStorage());
