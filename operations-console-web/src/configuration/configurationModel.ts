import type { ManagedResource, ManagementProtection } from './types.ts';

export interface ConfigurationCommand { path: string; method: 'POST' | 'PUT' | 'PATCH'; body: string; key: string }

/** 同一次内容和目标的人工重试保留 key；改变草稿后才生成新 key。 */
export function configurationCommand(path: string, method: ConfigurationCommand['method'], payload: unknown,
  previous?: ConfigurationCommand): ConfigurationCommand {
  const body = JSON.stringify(payload);
  return { path, method, body, key: previous?.path === path && previous.method === method && previous.body === body
    ? previous.key : crypto.randomUUID() };
}

export function editable(protection: ManagementProtection | undefined, field: string, creating = false) {
  return creating || protection?.editableFields.includes(field) === true;
}

/** 搜索结果保留父链，不把子项错误提升成根节点。 */
export function visibleResourceIds(resources: ManagedResource[], keyword: string, status: string): Set<string> {
  const byId = new Map(resources.map(resource => [resource.resourceId, resource]));
  const included = new Set<string>(); const query = keyword.trim().toLocaleLowerCase();
  for (const resource of resources) {
    if (status && resource.status !== status) continue;
    if (query && !`${resource.displayName} ${resource.resourceCode}`.toLocaleLowerCase().includes(query)) continue;
    let current: ManagedResource | undefined = resource;
    const visited = new Set<string>();
    while (current && !visited.has(current.resourceId)) {
      included.add(current.resourceId); visited.add(current.resourceId);
      current = current.parentResourceId ? byId.get(current.parentResourceId) : undefined;
    }
  }
  return included;
}

export function resourceTypeLabel(resource: Pick<ManagedResource, 'resourceType' | 'componentKey'>) {
  return resource.resourceType === 'BUTTON' ? '按钮' : resource.componentKey ? '页面菜单' : '目录';
}

/** 选择父级时剔除自身及其所有后代，最终结构仍由 Platform 判断。 */
export function descendants(resources: ManagedResource[], resourceId: string): Set<string> {
  const result = new Set([resourceId]); let changed = true;
  while (changed) {
    changed = false;
    for (const resource of resources) if (resource.parentResourceId && result.has(resource.parentResourceId)
      && !result.has(resource.resourceId)) { result.add(resource.resourceId); changed = true; }
  }
  return result;
}
