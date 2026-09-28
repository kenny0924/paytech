import { useEffect, useState } from 'react';
import { Menu } from 'antd';
import type { MenuProps } from 'antd';
import { menuAncestors, menuPages } from './menu';
import type { ConsoleMenu } from './menu';

function menuItems(tree: ConsoleMenu[], root = true): MenuProps['items'] {
  return tree.map(menu => ({
    key: menu.resourceId, label: menu.displayName,
    icon: root ? <svg aria-hidden="true" className="menu-icon" viewBox="0 0 24 24" fill="none"
      stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
      <rect x="3" y="3" width="7" height="7" rx="1.5" /><rect x="14" y="3" width="7" height="7" rx="1.5" />
      <rect x="3" y="14" width="7" height="7" rx="1.5" /><rect x="14" y="14" width="7" height="7" rx="1.5" />
    </svg> : undefined,
    children: menu.children.length ? menuItems(menu.children, false) : undefined,
  }));
}

export function SidebarMenu({ tree, pathname, collapsed, onNavigate }: {
  tree: ConsoleMenu[]; pathname: string; collapsed: boolean; onNavigate: (path: string) => void;
}) {
  const [openKeys, setOpenKeys] = useState<string[]>([]);
  const [popupKeys, setPopupKeys] = useState<string[]>([]);
  const pages = menuPages(tree);
  const selected = pages.find(page => page.frontendRoute === pathname || page.componentKey === 'OPERATIONS_TENANT_ACCESS' && pathname.startsWith(`${page.frontendRoute}/`));
  useEffect(() => {
    setOpenKeys(previous => [...new Set([...previous, ...menuAncestors(tree, pathname)])]);
  }, [tree, pathname]);
  useEffect(() => { setPopupKeys([]); }, [collapsed, pathname]);
  return <Menu theme="light" mode="inline" inlineIndent={16} items={menuItems(tree)}
    selectedKeys={selected ? [selected.resourceId] : []} openKeys={collapsed ? popupKeys : openKeys}
    onOpenChange={collapsed ? setPopupKeys : setOpenKeys}
    onClick={({ key }) => {
      const page = pages.find(item => item.resourceId === key);
      if (page?.frontendRoute) onNavigate(page.frontendRoute);
    }} />;
}
