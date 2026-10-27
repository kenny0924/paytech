import type { ConsoleResource } from '../api/types.ts';
import { tOr } from '../i18n/index.ts';
import type { Translate } from '../i18n/index.ts';

import { pageCapabilities } from './pageCapabilities.ts';
const componentRoutes: Record<string, string> = Object.fromEntries(pageCapabilities.map(page => [page.componentKey, page.frontendRoute]));

export interface ConsoleMenu extends ConsoleResource { children: ConsoleMenu[] }

/**
 * 菜单、面包屑与页面标题共用的资源名称：`nav.resource.<resourceCode>` 有翻译时用翻译，
 * 否则回退 Platform 返回的 display_name（新资源未建 key 时仍可用）。`t` 为 `useT('nav')`。
 */
export function resourceLabel(t: Translate, resource: Pick<ConsoleResource, 'resourceCode' | 'displayName'>): string {
  return tOr(t, 'resource.' + resource.resourceCode, resource.displayName);
}

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
  switch (menu.componentKey) {
    case 'OPERATIONS_TENANT_ACCESS':
      return [menu.frontendRoute, `${menu.frontendRoute}/:tenantId`, `${menu.frontendRoute}/:tenantId/members/:memberId`];
    case 'OPERATIONS_CLIENT_ONBOARDING_REVIEW':
      return [menu.frontendRoute, `${menu.frontendRoute}/:caseId`];
    case 'OPERATIONS_NOTIFICATION_TEMPLATES':
      return [menu.frontendRoute, `${menu.frontendRoute}/:templateCode`];
    case 'OPERATIONS_EXTERNAL_STANDARD_APIS':
      return [menu.frontendRoute, `${menu.frontendRoute}/:standardApiCode`];
    case 'OPERATIONS_OPERATING_ENTITIES':
      return [menu.frontendRoute, `${menu.frontendRoute}/:entityNo`];
    case 'OPERATIONS_LOCAL_PARTNERS':
      return [menu.frontendRoute, `${menu.frontendRoute}/:localPartnerId`];
    case 'OPERATIONS_CLIENTS':
      return [menu.frontendRoute, `${menu.frontendRoute}/:clientId`];
    case 'OPERATIONS_COMMERCIAL_AGREEMENTS':
      return [menu.frontendRoute, `${menu.frontendRoute}/:agreementId`];
    case 'OPERATIONS_COMMERCIAL_TERMS':
      return [menu.frontendRoute, `${menu.frontendRoute}/versions/:versionId`, `${menu.frontendRoute}/compare/:baseVersionId/:targetVersionId`];
    case 'OPERATIONS_CHANNEL_CHANNELS':
      return [menu.frontendRoute, `${menu.frontendRoute}/:channelId`, `${menu.frontendRoute}/code/:channelCode`, `${menu.frontendRoute}/code/:channelCode/protocol/:section/:childCode`,
        `${menu.frontendRoute}/:channelId/protocol/:section/:childCode`, `${menu.frontendRoute}/:channelId/verify/:section/:childCode`];
    case 'OPERATIONS_CHANNEL_MIDS':
      return [menu.frontendRoute, `${menu.frontendRoute}/:midId`];
    case 'OPERATIONS_CHANNEL_COST_PLANS':
      return [menu.frontendRoute, `${menu.frontendRoute}/:planCode`, `${menu.frontendRoute}/:planCode/versions/:costVer`];
    case 'OPERATIONS_FINANCE_RECON_CLEARING':
      return [menu.frontendRoute, `${menu.frontendRoute}/:clearingAccountId`];
    case 'OPERATIONS_FINANCE_RECON_CASH':
      return [menu.frontendRoute, `${menu.frontendRoute}/:cashAccountId`];
    case 'OPERATIONS_FINANCE_RECON_STATEMENTS':
      return [menu.frontendRoute, `${menu.frontendRoute}/:statementId`];
    case 'OPERATIONS_FINANCE_RECON_BANK':
      return [menu.frontendRoute, `${menu.frontendRoute}/statements/:bankStatementId`, `${menu.frontendRoute}/lines/:bankStatementLineId`];
    case 'OPERATIONS_FINANCE_RECON_EXCEPTIONS':
      return [menu.frontendRoute, `${menu.frontendRoute}/:exceptionId`];
    case 'OPERATIONS_WORKFLOW_INSTANCES':
      return [menu.frontendRoute, `${menu.frontendRoute}/:instanceId`];
    case 'OPERATIONS_WORKFLOW_DEFINITIONS':
      return [menu.frontendRoute, `${menu.frontendRoute}/:definitionId`, `${menu.frontendRoute}/:definitionId/versions/:versionNo`];
    case 'OPERATIONS_WORKFLOW_ACTION_TEMPLATES':
      return [menu.frontendRoute, `${menu.frontendRoute}/:templateId`];
    case 'OPERATIONS_QUERY_TEMPLATES':
      return [menu.frontendRoute, `${menu.frontendRoute}/:templateId`];
    default:
      return [menu.frontendRoute];
  }
}
