import type { DeleteRoleRequestBody, RoleListItem } from '../api/types.ts';
import { deletionCommand } from '../api/deletion.ts';
import type { DeletionCommand } from '../api/deletion.ts';

/** 同一角色版本与原因的手动重试保留幂等键，不在失败后自动重新发起删除。 */
export function roleDeletionCommand(role: RoleListItem, reason: string, previous: DeletionCommand | null): DeletionCommand {
  const body: DeleteRoleRequestBody = { expectedVersion: role.version, reason: reason.trim() };
  return deletionCommand(`/iam/v1/roles/${encodeURIComponent(role.roleId)}`, body, previous);
}
