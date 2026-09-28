import { session } from './session.ts';
import type { RequestClient, SuccessDecoder } from './session.ts';
import type { PagingResponseBody } from './types.ts';

export const pageSizeOptions = [10, 20, 50, 100, 200];
export const defaultPaging = { page: 1, size: 10 };
export interface PagingRequestBody { page: number; size: number }
export type PageDecoder<T> = SuccessDecoder<PagingResponseBody<T>>;

/** 固定页面路径和解码器，供 Hook 的筛选、翻页及刷新复用同一响应校验链。 */
export function createPagingLoader<T>(path: string, decodePage?: PageDecoder<T>, client: RequestClient = session) {
  return (paging: PagingRequestBody, signal: AbortSignal, filters: Readonly<Record<string, string>>) =>
    requestPage(path, paging, signal, client, filters, decodePage);
}

export function changePaging(current: PagingRequestBody, page: number, size: number): PagingRequestBody {
  return { page: size === current.size ? page : 1, size };
}

function hasValidPageRange<T>(result: PagingResponseBody<T>, paging: PagingRequestBody): boolean {
  if (!Number.isSafeInteger(paging.page) || paging.page < 1
    || !Number.isSafeInteger(paging.size) || paging.size < 1) return false;
  const offset = (paging.page - 1) * paging.size;
  if (!Number.isSafeInteger(offset)) return false;
  if (result.items.length === 0) return offset >= result.total;
  const end = offset + result.items.length;
  return Number.isSafeInteger(end) && end <= result.total;
}

async function requestPageAtExpectedTotal<T>(path: string, paging: PagingRequestBody, signal: AbortSignal | undefined,
  client: RequestClient, filters: Readonly<Record<string, string>>, decodePage: PageDecoder<T> | undefined,
  expectedTotal: number | undefined, itemsBeforePage: number): Promise<PagingResponseBody<T>> {
  signal?.throwIfAborted();
  const query = new URLSearchParams(filters);
  query.set('page', String(paging.page)); query.set('size', String(paging.size));
  const result = await client.request<PagingResponseBody<T>>(`${path}?${query}`, { signal }, value => {
    const result = decodePage ? decodePage(value) : value as PagingResponseBody<T>;
    const offset = (paging.page - 1) * paging.size;
    const expectedItems = Number.isSafeInteger(offset) ? Math.min(paging.size, Math.max(result?.total - offset, 0)) : -1;
    if (!result || !Array.isArray(result.items) || result.page !== paging.page || result.size !== paging.size
      || !Number.isSafeInteger(result.total) || result.total < 0 || result.items.length !== expectedItems
      || !hasValidPageRange(result, paging) || (expectedTotal !== undefined && result.total !== expectedTotal)
      || (expectedTotal !== undefined && itemsBeforePage + result.items.length !== result.total && result.items.length < paging.size)) {
      throw new Error('invalid page');
    }
    return result;
  });
  signal?.throwIfAborted();
  return result;
}
export async function requestPage<T>(path: string, paging: PagingRequestBody, signal?: AbortSignal,
  client: RequestClient = session, filters: Readonly<Record<string, string>> = {}, decodePage?: PageDecoder<T>): Promise<PagingResponseBody<T>> {
  return requestPageAtExpectedTotal(path, paging, signal, client, filters, decodePage, undefined, 0);
}

/** 选项选择需要完整集合；中途失败或列表总数变化时不返回部分结果。 */
export async function requestAllPages<T>(path: string, signal: AbortSignal, client: RequestClient = session,
  filters: Readonly<Record<string, string>> = {}, decodePage?: PageDecoder<T>): Promise<T[]> {
  const size = 200;
  const first = await requestPageAtExpectedTotal(path, { page: 1, size }, signal, client, filters, decodePage, undefined, 0);
  const items = [...first.items];
  for (let page = 2; items.length < first.total; page++) {
    const result = await requestPageAtExpectedTotal(path, { page, size }, signal, client, filters, decodePage, first.total, items.length);
    items.push(...result.items);
  }
  return items;
}
