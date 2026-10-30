import type { ConsoleResource } from '../api/types.ts';
import { tOr } from '../i18n/index.ts';
import type { Translate } from '../i18n/index.ts';

import { pageCapabilities } from './pageCapabilities.ts';
const componentRoutes: Record<string, string> = Object.fromEntries(pageCapabilities.map(page => [page.componentKey, page.frontendRoute]));
const componentNames: Record<string, string> = Object.fromEntries(pageCapabilities.map(page => [page.componentKey, page.displayName]));

/**
 * 前端合并的页面：Platform 仍分别登记两个菜单资源与权限（注册数据不变），控制台只展示合并后的一个页面
 * <li>币种页并入"参考数据"页（2026-10-27）：币种是第一个页签；任一菜单有权限就显示"参考数据"，旧地址跳到对应页签。</li>
 */
export const mergedPages = [
  { from: 'OPERATIONS_CURRENCY_SPECS', into: 'OPERATIONS_REFERENCE_DATA', legacyRoute: '/currency-specs', target: '/reference-data?tab=currencies' },
] as const;

/**
 * 按合并规则改写授权菜单：被并入的菜单不再单独出现；只有它有权限时，改写为目标页面（沿用其位置与排序）
 * <li>按钮与其他资源不变，页面仍按原资源判断能看到的页签和可用动作。</li>
 */
export function mergeMenus(resources: ConsoleResource[]): ConsoleResource[] {
  const menuWith = (componentKey: string) => resources.find(r => r.resourceType === 'MENU' && r.componentKey === componentKey);
  let result = resources;
  for (const merge of mergedPages) {
    const from = menuWith(merge.from);
    if (!from) continue;
    result = menuWith(merge.into)
      ? result.filter(r => r !== from)
      : result.map(r => r !== from ? r : { ...r, resourceCode: merge.into, componentKey: merge.into, frontendRoute: componentRoutes[merge.into],
        displayName: componentNames[merge.into] });
  }
  return result;
}

/** 已合并页面的旧地址 → 合并后页面的对应页签；只登记目标页面在授权菜单中的跳转。 */
export function legacyRedirects(menus: readonly ConsoleResource[]): { path: string; to: string }[] {
  return mergedPages.filter(merge => menus.some(menu => menu.componentKey === merge.into)).map(merge => ({ path: merge.legacyRoute, to: merge.target }));
}

export interface ConsoleMenu extends ConsoleResource { children: ConsoleMenu[] }

/**
 * 菜单、面包屑与页面标题共用的资源名称：`nav.resource.<resourceCode>` 有翻译时用翻译，
 * 否则回退 Platform 返回的 display_name（新资源未建 key 时仍可用）。`t` 为 `useT('nav')`。
 */
export function resourceLabel(t: Translate, resource: Pick<ConsoleResource, 'resourceCode' | 'displayName'>): string {
  return tOr(t, 'resource.' + resource.resourceCode, resource.displayName);
}

/** 只展示服务端授权资源及本地已部署页面，不补回缺失的父级或权限；先按 mergedPages 合并页面。 */
export function buildMenuTree(resources: ConsoleResource[]): ConsoleMenu[] {
  const siblings = new Map<string | null, ConsoleResource[]>();
  for (const resource of mergeMenus(resources)) {
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
    case 'OPERATIONS_RISK_UNDERWRITING':
      return [menu.frontendRoute, `${menu.frontendRoute}/new`, `${menu.frontendRoute}/:underwritingId`];
    case 'OPERATIONS_RISK_LIMIT_TIERS':
      return [menu.frontendRoute, `${menu.frontendRoute}/:clientId`];
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
