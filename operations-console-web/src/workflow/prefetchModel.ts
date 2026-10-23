/**
 * 我的待办的实例详情请求调度：Gateway 实例详情路由按用户限制并发（AUTHENTICATED_PRINCIPAL CONCURRENCY=2），
 * 选中项、批量补齐与普通预取统一进入 {@link DetailQueue}：同时最多 1 个在途，选中项优先，同一实例去重，429 重试一次。
 */

/** 每次最多预取的实例数。 */
export const PREFETCH_LIMIT = 5;

/** 需要预取的实例：去重，跳过选中项与已缓存，按出现顺序取前 limit 个。 */
export function planPrefetch(candidates: readonly string[], selected: string | null, cached: ReadonlySet<string>,
  limit = PREFETCH_LIMIT): string[] {
  const result: string[] = [];
  for (const id of candidates) {
    if (result.length >= limit) break;
    if (id === selected || cached.has(id) || result.includes(id)) continue;
    result.push(id);
  }
  return result;
}

/**
 * 批量模式的按需补齐顺序：先补勾选项，再补其他审批待办；去重，跳过已缓存与本轮已失败的实例。不设数量上限，
 * 仍由 DetailQueue 一次一个执行。
 */
export function planBatchFill(checked: readonly string[], others: readonly string[], cached: ReadonlySet<string>,
  failed: ReadonlySet<string>): string[] {
  const result: string[] = [];
  for (const id of [...checked, ...others]) {
    if (cached.has(id) || failed.has(id) || result.includes(id)) continue;
    result.push(id);
  }
  return result;
}

/** 请求优先级：数字越小越先执行。 */
export const DETAIL_PRIORITY = { SELECTED: 0, BATCH: 1, PREFETCH: 2 } as const;

/** 取消排队请求时给等待方的错误；调用方据此静默忽略。 */
export class DetailRequestCancelled extends Error {
  constructor() { super('Instance detail request cancelled'); this.name = 'DetailRequestCancelled'; }
}

/** 429 默认退避毫秒数；拿不到 Retry-After 时使用。 */
export const DEFAULT_RETRY_MILLIS = 1000;

/** 退避上限，避免服务端给出过长等待时页面长时间无响应。 */
export const MAX_RETRY_MILLIS = 10_000;

/**
 * 解析 Retry-After：非负整数秒或 HTTP 日期；缺失或非法时用默认退避，结果限制在 [0, MAX_RETRY_MILLIS]。
 */
export function retryAfterMillis(header: string | null | undefined, now = Date.now()): number {
  const value = header?.trim();
  let millis = DEFAULT_RETRY_MILLIS;
  if (value && /^\d+$/.test(value)) millis = Number(value) * 1000;
  else if (value && Number.isFinite(Date.parse(value))) millis = Date.parse(value) - now;
  return Math.min(MAX_RETRY_MILLIS, Math.max(0, millis));
}

interface Waiter<T> { tag: string; resolve: (value: T) => void; reject: (error: unknown) => void }
interface Entry<T> { id: string; priority: number; seq: number; waiters: Waiter<T>[] }

export interface DetailQueueOptions {
  /** 返回 429 的退避毫秒数；不是 429 返回 null（不重试）。 */
  retryDelay: (error: unknown) => number | null;
  /** 可注入的等待函数（测试用）。 */
  sleep?: (millis: number) => Promise<void>;
}

/**
 * 实例详情串行队列：同时最多 1 个在途；按优先级再按入队顺序执行；同一实例合并为一次请求并取最高优先级；
 * 选中项插队只排到在途请求之后，不中止它；429 按退避重试一次，仍失败才拒绝。
 * cancel(tag) 只移除该标记的等待方，在途请求继续完成并交给其他等待方。
 */
export class DetailQueue<T> {
  private readonly queued = new Map<string, Entry<T>>();
  private inFlight: Entry<T> | null = null;
  private seq = 0;
  private readonly sleep: (millis: number) => Promise<void>;
  private readonly load: (id: string) => Promise<T>;
  private readonly options: DetailQueueOptions;

  constructor(load: (id: string) => Promise<T>, options: DetailQueueOptions) {
    this.load = load; this.options = options;
    this.sleep = options.sleep ?? (millis => new Promise(resolve => setTimeout(resolve, millis)));
  }

  /** 请求实例详情；tag 用于成组取消。 */
  request(id: string, priority: number, tag: string): Promise<T> {
    return new Promise<T>((resolve, reject) => {
      const waiter = { tag, resolve, reject };
      if (this.inFlight?.id === id) { this.inFlight.waiters.push(waiter); return; }
      const entry = this.queued.get(id);
      if (entry) { entry.waiters.push(waiter); entry.priority = Math.min(entry.priority, priority); return; }
      this.queued.set(id, { id, priority, seq: this.seq++, waiters: [waiter] });
      void this.pump();
    });
  }

  /** 取消某标记的全部等待方；没有剩余等待方的排队项移除，在途请求不中止。 */
  cancel(tag: string): void {
    for (const entry of [...this.queued.values(), ...(this.inFlight ? [this.inFlight] : [])]) {
      const removed = entry.waiters.filter(waiter => waiter.tag === tag);
      entry.waiters = entry.waiters.filter(waiter => waiter.tag !== tag);
      removed.forEach(waiter => waiter.reject(new DetailRequestCancelled()));
      if (entry !== this.inFlight && !entry.waiters.length) this.queued.delete(entry.id);
    }
  }

  /** 排队中的实例，按执行顺序（测试与诊断用）。 */
  pending(): string[] { return this.ordered().map(entry => entry.id); }

  /** 当前在途实例。 */
  running(): string | null { return this.inFlight?.id ?? null; }

  private ordered(): Entry<T>[] {
    return [...this.queued.values()].sort((left, right) => left.priority - right.priority || left.seq - right.seq);
  }

  private async pump(): Promise<void> {
    if (this.inFlight) return;
    const next = this.ordered()[0];
    if (!next) return;
    this.queued.delete(next.id); this.inFlight = next;
    let outcome: { ok: true; value: T } | { ok: false; error: unknown };
    try { outcome = { ok: true, value: await this.loadWithRetry(next.id) }; }
    catch (error) { outcome = { ok: false, error }; }
    this.inFlight = null;
    for (const waiter of next.waiters) {
      if (outcome.ok) waiter.resolve(outcome.value); else waiter.reject(outcome.error);
    }
    void this.pump();
  }

  private async loadWithRetry(id: string): Promise<T> {
    try { return await this.load(id); }
    catch (error) {
      const delay = this.options.retryDelay(error);
      if (delay === null) throw error;
      await this.sleep(delay);
      return this.load(id);
    }
  }
}
