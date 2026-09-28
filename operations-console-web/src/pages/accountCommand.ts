import type { Account } from '../api/types.ts';

export type AccountAction = 'create' | 'roles' | 'disable' | 'enable';
export interface AccountCommand { path: string; method: 'POST' | 'PUT'; payload: string; key: string }

/** 状态命令不带隐藏表单字段；手动重试只复用同一目标、方法及请求体的幂等键。 */
export function accountCommand(action: AccountAction, account: Account | undefined,
  values: Record<string, unknown>, previous: AccountCommand | null): AccountCommand {
  const path = action === 'create' ? '/iam/v1/accounts'
    : `/iam/v1/accounts/${encodeURIComponent(account!.accountId)}/${action}`;
  const method = action === 'roles' ? 'PUT' : 'POST';
  const body = action === 'create' ? values : action === 'roles'
    ? { ...values, expectedMemberVersion: account!.memberVersion }
    : { expectedVersion: account!.version, reason: String(values.reason ?? '').trim() };
  const payload = JSON.stringify(body);
  const key = previous?.path === path && previous.method === method && previous.payload === payload
    ? previous.key : crypto.randomUUID();
  return { path, method, payload, key };
}
