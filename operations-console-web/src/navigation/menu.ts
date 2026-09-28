import type { ConsoleResource } from '../api/types.ts';

import { pageCapabilities } from './pageCapabilities.ts';
const componentRoutes: Record<string, string> = Object.fromEntries(pageCapabilities.map(page => [page.componentKey, page.frontendRoute]));

export interface ConsoleMenu extends ConsoleResource { children: ConsoleMenu[] }

/** 只展示服务端授权资源及本地已部署页面，不补回缺失的父级或权限。 */
export function buildMenuTree(resources: ConsoleResource[]): ConsoleMenu[] {
  const siblings = new Map<string | null, ConsoleResource[]>();
  for (const resource of resources) {
    if (resource.resourceType !== 'MENU') continue;
    const group = siblings.get(resource.parentResourceId) ?? [];
    group.push(resource);
    siblings.set(resource.parentResourceId, group);
  }
  function children(parentId: string | null, ancestors: Set<string>): ConsoleMenu[] {
    return (siblings.get(parentId) ?? [])
      .toSorted((left, right) => left.sortOrder - right.sortOrder || left.resourceId.localeCompare(right.resourceId))
      .flatMap(resource => {
        if (ancestors.has(resource.resourceId)) return [];
        const directory = resource.frontendRoute === null && resource.componentKey === null;
        if (!directory && (!resource.componentKey || componentRoutes[resource.componentKey] !== resource.frontendRoute)) return [];
        const descendants = children(resource.resourceId, new Set([...ancestors, resource.resourceId]));
        if (directory && descendants.length === 0) return [];
        return [{ ...resource, children: descendants }];
      });
  }
  return children(null, new Set());
}

export function menuPages(tree: ConsoleMenu[]): ConsoleMenu[] {
  return tree.flatMap(menu => [...(menu.frontendRoute ? [menu] : []), ...menuPages(menu.children)]);
}

export function menuAncestors(tree: ConsoleMenu[], pathname: string): string[] {
  for (const menu of tree) {
    if (menu.frontendRoute === pathname) return [];
    if (menuPages(menu.children).some(page => page.frontendRoute === pathname)) {
      return [menu.resourceId, ...menuAncestors(menu.children, pathname)];
    }
  }
  return [];
}

/** 页面内部仅登记已实现的详情路径，其他页面继续精确匹配服务端菜单路径。 */
export function pageRoutePaths(menu: ConsoleResource): string[] {
  if (!menu.frontendRoute) return [];
  return menu.componentKey === 'OPERATIONS_TENANT_ACCESS'
    ? [menu.frontendRoute, `${menu.frontendRoute}/:tenantId`, `${menu.frontendRoute}/:tenantId/members/:memberId`]
    : [menu.frontendRoute];
}
