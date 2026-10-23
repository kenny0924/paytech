import type { CreateRoleRequestBody, PermissionNode, RoleResponseBody, UpdateRoleRequestBody } from '../api/types.ts';
import { ApiError } from '../api/session.ts';
import { translator } from '../i18n/index.ts';
import { resourceLabel } from '../navigation/menu.ts';

const tNav = translator('nav'), tAccess = translator('access');
/** BFF code of the structural group that holds permissions without a console resource. */
export const otherPermissionsGroup = 'OTHER_PERMISSIONS';

export interface PermissionTreeItem {
  key: string; title: string; permissionId: string | null; children: PermissionTreeItem[];
}
export interface RoleFormValues { roleCode: string; roleName: string; reason: string }
export interface RoleCommand { path: string; method: 'POST' | 'PUT'; payload: string; key: string }

/**
 * Tree title: resource nodes use the menu name (`nav.resource.<code>`, falling back to the Platform display name),
 * the "other permissions" group uses `access.permissionTree.other`, and permissions without a resource show their code.
 */
export function permissionNodeTitle(node: Pick<PermissionNode, 'code' | 'displayName'>): string {
  if (node.code === otherPermissionsGroup) return tAccess('permissionTree.other');
  return resourceLabel(tNav, { resourceCode: node.code, displayName: node.displayName });
}

export function permissionTree(nodes: PermissionNode[]): PermissionTreeItem[] {
  const keys = new Set<string>();
  function items(values: PermissionNode[]): PermissionTreeItem[] {
    if (!Array.isArray(values)) throw new ApiError(502, 'INVALID_RESPONSE');
    return values.map(node => {
      if (!node || typeof node.nodeId !== 'string' || !node.nodeId || keys.has(node.nodeId)
        || typeof node.code !== 'string' || !node.code || typeof node.displayName !== 'string'
        || (node.permissionId !== null && (typeof node.permissionId !== 'string' || !node.permissionId))) {
        throw new ApiError(502, 'INVALID_RESPONSE');
      }
      keys.add(node.nodeId);
      return { key: node.nodeId, title: permissionNodeTitle(node), permissionId: node.permissionId,
        children: items(node.children) };
    });
  }
  return items(nodes);
}

export interface PermissionSelection { checked: string[]; halfChecked: string[] }
interface BranchSelection extends PermissionSelection { total: number; selected: number }

/** 只从已有授权计算展示状态；父节点满选必须包含自身权限和全部子孙权限，不在加载时扩权。 */
export function permissionSelection(nodes: PermissionNode[], permissionIds: string[]): PermissionSelection {
  const selectedIds = new Set(permissionIds);
  function inspect(node: PermissionNode): BranchSelection {
    const children = node.children.map(inspect);
    const total = (node.permissionId === null ? 0 : 1) + children.reduce((sum, child) => sum + child.total, 0);
    const selected = (node.permissionId !== null && selectedIds.has(node.permissionId) ? 1 : 0)
      + children.reduce((sum, child) => sum + child.selected, 0);
    return {
      total, selected,
      checked: [...(total > 0 && selected === total ? [node.nodeId] : []), ...children.flatMap(child => child.checked)],
      halfChecked: [...(selected > 0 && selected < total ? [node.nodeId] : []), ...children.flatMap(child => child.halfChecked)],
    };
  }
  const roots = nodes.map(inspect);
  return { checked: roots.flatMap(node => node.checked), halfChecked: roots.flatMap(node => node.halfChecked) };
}

/** 勾选目标子树并补齐其祖先访问权限；取消只移除目标子树，保留兄弟与祖先的已有授权。 */
export function togglePermissionNode(nodes: PermissionNode[], permissionIds: string[], nodeId: string, checked: boolean): string[] {
  const selected = new Set(permissionIds);
  function findPath(items: PermissionNode[], ancestors: PermissionNode[]): PermissionNode[] | null {
    for (const node of items) {
      const path = [...ancestors, node];
      if (node.nodeId === nodeId) return path;
      const found = findPath(node.children, path);
      if (found) return found;
    }
    return null;
  }
  function updateSubtree(node: PermissionNode) {
    if (node.permissionId !== null) {
      if (checked) selected.add(node.permissionId);
      else selected.delete(node.permissionId);
    }
    node.children.forEach(updateSubtree);
  }
  const path = findPath(nodes, []);
  if (!path) throw new ApiError(502, 'INVALID_RESPONSE');
  updateSubtree(path[path.length - 1]);
  if (checked) path.forEach(node => { if (node.permissionId !== null) selected.add(node.permissionId); });
  return [...selected].sort();
}

export interface PermissionRemovalImpact {
  removedPermissionIds: string[];
  removedDescendantPermissionIds: string[];
}

/** 预览取消某节点会移除的已选权限，严格后代集用于驱动显式确认。 */
export function permissionRemovalImpact(nodes: PermissionNode[], permissionIds: string[], nodeId: string): PermissionRemovalImpact {
  function find(items: PermissionNode[]): PermissionNode | null {
    for (const node of items) {
      if (node.nodeId === nodeId) return node;
      const nested = find(node.children);
      if (nested) return nested;
    }
    return null;
  }
  function selectedIn(items: PermissionNode[], selected: ReadonlySet<string>): string[] {
    const result: string[] = [];
    function visit(node: PermissionNode) {
      if (node.permissionId !== null && selected.has(node.permissionId)) result.push(node.permissionId);
      node.children.forEach(visit);
    }
    items.forEach(visit);
    return [...new Set(result)].sort();
  }
  const target = find(nodes);
  if (!target) throw new ApiError(502, 'INVALID_RESPONSE');
  const selected = new Set(permissionIds);
  return {
    removedPermissionIds: selectedIn([target], selected),
    removedDescendantPermissionIds: selectedIn(target.children, selected),
  };
}

/** 相同命令重试保留幂等键；权限按集合规范化，编辑不传可变角色编码。 */
export function roleCommand(values: RoleFormValues, permissionIds: string[], role: RoleResponseBody | null,
  previous: RoleCommand | null): RoleCommand {
  const fields = { roleName: values.roleName.trim(), permissionIds: [...new Set(permissionIds)].sort(), reason: values.reason.trim() };
  const body: CreateRoleRequestBody | UpdateRoleRequestBody = role
    ? { ...fields, expectedVersion: role.version } : { roleCode: values.roleCode.trim(), ...fields };
  const path = role ? `/iam/v1/roles/${encodeURIComponent(role.roleId)}` : '/iam/v1/roles';
  const method = role ? 'PUT' : 'POST';
  const payload = JSON.stringify(body);
  const key = previous?.path === path && previous.method === method && previous.payload === payload
    ? previous.key : crypto.randomUUID();
  return { path, method, payload, key };
}
