import { ApiError, session } from '../api/session.ts';

export type CommandMethod = 'POST' | 'PUT' | 'DELETE';
export interface Command { path: string; method: CommandMethod; body: string; key: string }

/** 同一目标、方法与正文的人工重试复用 Idempotency-Key（Gateway 请求幂等，2026-09-17 决定）。 */
export function localizationCommand(path: string, method: CommandMethod, payload: unknown, previous?: Command): Command {
  const body = payload === undefined ? '' : JSON.stringify(payload);
  const same = previous?.path === path && previous.method === method && previous.body === body;
  return { path, method, body, key: same ? previous.key : crypto.randomUUID() };
}

export function sendCommand<T>(command: Command, decode: (value: unknown) => T): Promise<T> {
  return session.request<T>(command.path, { method: command.method, ...(command.body ? { body: command.body } : {}),
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': command.key } }, decode);
}

/**
 * 写命令的失败分类，由页面就地展示：
 * conflict（409：并发修改、预览过期、发布期间文案变化）、rejected（400/422：业务拒绝或字段错误，带 code、detail、errors）、
 * 其余交给通用错误通知（展示 code 与 requestId）。
 */
export type CommandFailure =
  | { kind: 'conflict'; code: string; detail: string }
  | { kind: 'rejected'; code: string; detail: string; errors: readonly { field: string; message: string }[] }
  | { kind: 'other' };
export type CommandResult<T> = { ok: true; value: T } | { ok: false; failure: CommandFailure };

export function classifyFailure(error: unknown): CommandFailure {
  if (error instanceof ApiError && error.status === 409) return { kind: 'conflict', code: error.code, detail: error.detail };
  if (error instanceof ApiError && (error.status === 400 || error.status === 422)) {
    return { kind: 'rejected', code: error.code, detail: error.detail, errors: error.fieldErrors };
  }
  return { kind: 'other' };
}


/** 文件检查中（409 I18N_IMPORT_FILE_NOT_READY）的重试间隔：1、2、3、4、5、5 秒，合计约 20 秒。 */
const SCAN_RETRY_DELAYS = [1000, 2000, 3000, 4000, 5000, 5000];
export const isFileNotReady = (error: unknown) => error instanceof ApiError && error.status === 409 && error.code === 'I18N_IMPORT_FILE_NOT_READY';

/**
 * 上传后文件要先通过检查才能读取：检查中 Platform 返回 409 I18N_IMPORT_FILE_NOT_READY（卡 A 11.2）。
 * 只对该错误按递增间隔重试并回调 onWaiting；其他错误立即抛出；重试用尽后抛出最后一次的检查中错误。
 */
export async function withScanRetry<T>(call: () => Promise<T>, isAborted: () => boolean, onWaiting: () => void = () => {},
  delays = SCAN_RETRY_DELAYS, sleep = (ms: number) => new Promise(resolve => setTimeout(resolve, ms))): Promise<T> {
  for (let attempt = 0; ; attempt++) {
    if (isAborted()) throw new DOMException('aborted', 'AbortError');
    try { return await call(); }
    catch (error) {
      if (!isFileNotReady(error) || attempt >= delays.length) throw error;
      onWaiting();
      await sleep(delays[attempt]);
    }
  }
}

