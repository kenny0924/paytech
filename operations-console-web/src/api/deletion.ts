export interface DeletionCommand { path: string; payload: string; key: string }

/** 手动重试仅复用完全相同的删除目标与请求体对应的幂等键。 */
export function deletionCommand(path: string, body: object, previous: DeletionCommand | null): DeletionCommand {
  const payload = JSON.stringify(body);
  const key = previous?.path === path && previous.payload === payload ? previous.key : crypto.randomUUID();
  return { path, payload, key };
}
