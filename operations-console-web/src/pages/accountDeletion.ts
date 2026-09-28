import type { Account, DeleteAccountRequestBody } from '../api/types.ts';
import { deletionCommand } from '../api/deletion.ts';
import type { DeletionCommand } from '../api/deletion.ts';

export function canDeleteAccount(account: Account, actorId: string): boolean {
  return account.accountId !== actorId && (account.status === 'ACTIVE' || account.status === 'DISABLED');
}

/** 人员删除同时约束账号和成员版本，身份与关联由服务端在同一事务内处理。 */
export function accountDeletionCommand(account: Account, reason: string, previous: DeletionCommand | null): DeletionCommand {
  const body: DeleteAccountRequestBody = {
    expectedVersion: account.version, expectedMemberVersion: account.memberVersion, reason: reason.trim(),
  };
  return deletionCommand(`/iam/v1/accounts/${encodeURIComponent(account.accountId)}`, body, previous);
}
