import type { ConsoleMenu } from './menu.ts';

/** 顶部路径：从一级目录到当前页面的菜单链；详情子路径按最长前缀归到所属页面，未命中返回空数组。 */
export function menuTrail(tree: readonly ConsoleMenu[], pathname: string): ConsoleMenu[] {
  let best: ConsoleMenu[] = [];
  const visit = (menus: readonly ConsoleMenu[], trail: ConsoleMenu[]) => {
    for (const menu of menus) {
      const next = [...trail, menu];
      const route = menu.frontendRoute;
      if (route && (pathname === route || pathname.startsWith(`${route}/`)) && route.length > (best.at(-1)?.frontendRoute?.length ?? -1)) best = next;
      visit(menu.children, next);
    }
  };
  visit(tree, []);
  return best;
}
