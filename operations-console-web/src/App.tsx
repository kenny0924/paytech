import { Suspense, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Layout, Result, Spin, Tag } from 'antd';
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { ApiError, session } from './api/session';
import type { ConsoleResource, NavigationResponseBody, SessionContextResponseBody } from './api/types';
import { Login } from './auth/Login';
import { RequiredAction } from './auth/RequiredAction';
import { useOperationsTools } from './auth/webmcp';
import { useErrorNotification } from './feedback/useErrorNotification';
import { buildMenuTree, menuPages, pageRoutePaths } from './navigation/menu';
import { menuTrail } from './navigation/menuTrail';
import { useTrailTitleHeading } from './navigation/useTrailTitleHeading';
import { NavigationCollapseControl } from './navigation/NavigationCollapseControl';
import { getBrowserLocalStorage, loadNavigationCollapsed, saveNavigationCollapsed } from './navigation/navigationPreference';
import { SidebarMenu } from './navigation/SidebarMenu';

import { ConsolePage } from './navigation/ConsolePage';
export function App() {
  const notifyError = useErrorNotification();
  const [context, setContext] = useState<SessionContextResponseBody | null>(null);
  const [initializing, setInitializing] = useState(true);
  const [initializationFailed, setInitializationFailed] = useState(false);
  const [initializationAttempt, setInitializationAttempt] = useState(0);
  const [loginMessage, setLoginMessage] = useState('');
  useEffect(() => {
    let active = true;
    setInitializing(true); setInitializationFailed(false);
    session.onExpired = () => setContext(null);
    session.context().then(value => { if (active) setContext(value); }).catch(error => {
      if (active && !(error instanceof ApiError && error.status === 401)) {
        setInitializationFailed(true);
        notifyError(error, '会话恢复失败');
      }
    }).finally(() => { if (active) setInitializing(false); });
    return () => { active = false; session.onExpired = () => {}; };
  }, [initializationAttempt, notifyError]);
  if (initializing) return <div className="full-loading"><Spin size="large" tip="正在检查登录…"><div /></Spin></div>;
  if (initializationFailed) return <div className="full-loading"><Result status="warning" title="暂时无法确认登录状态"
    subTitle="请稍后重试。" extra={<Button type="primary" onClick={() => setInitializationAttempt(value => value + 1)}>重试</Button>} /></div>;
  if (!context) return <Login onLogin={setContext} initialMessage={loginMessage} />;
  if (context.passwordChangeRequired || context.bootstrapAdministratorRequired) {
    return <RequiredAction context={context} onComplete={() => { setContext(null); setLoginMessage('操作已完成，请使用新密码或新管理员账号登录。'); }} />;
  }
  return <Console context={context} />;
}

function Console({ context }: { context: SessionContextResponseBody }) {
  const notifyError = useErrorNotification();
  const location = useLocation();
  const navigate = useNavigate();
  const [resources, setResources] = useState<ConsoleResource[]>([]);
  const [loading, setLoading] = useState(true);
  const [navigationFailed, setNavigationFailed] = useState(false);
  const [navigationLoaded, setNavigationLoaded] = useState(false);
  const [reload, setReload] = useState(0);
  const [loggingOut, setLoggingOut] = useState(false);
  const navigationPreferenceStorage = useMemo(getBrowserLocalStorage, []);
  const [userCollapsed, setUserCollapsed] = useState(() => loadNavigationCollapsed(navigationPreferenceStorage));
  const [viewportCollapsed, setViewportCollapsed] = useState(false);
  const collapsed = userCollapsed || viewportCollapsed;
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setNavigationFailed(false);
    session.request<NavigationResponseBody>('/authorization/v1/navigation', { signal: controller.signal })
      .then(result => {
        if (controller.signal.aborted) return;
        if (!Array.isArray(result?.resources)) throw new ApiError(502, 'INVALID_RESPONSE');
        setResources(result.resources);
        setNavigationLoaded(true);
      }).catch(error => {
        if (!controller.signal.aborted) {
          setNavigationFailed(true);
          notifyError(error, '菜单加载失败');
        }
      }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  // 导航属于当前登录会话；切页只切换路由，避免重复鉴权查询和卸载页面。
  }, [reload, notifyError]);
  const menuTree = useMemo(() => buildMenuTree(resources), [resources]);
  const menus = useMemo(() => menuPages(menuTree), [menuTree]);
  const trail = useMemo(() => menuTrail(menuTree, location.pathname), [menuTree, location.pathname]);
  useTrailTitleHeading(trail.at(-1)?.displayName);
  useOperationsTools(menus, navigate);
  async function logout() {
    setLoggingOut(true);
    try { await session.logout(); }
    catch (error) { notifyError(error, '退出会话未确认'); }
    finally { setLoggingOut(false); }
  }
  function toggleNavigation() {
    const next = !userCollapsed;
    setUserCollapsed(next);
    saveNavigationCollapsed(navigationPreferenceStorage, next);
  }
  return <Layout className="console-layout">
    <Layout.Sider className="console-sidebar" width={200} breakpoint="lg" collapsedWidth={56}
      collapsed={collapsed} onBreakpoint={setViewportCollapsed} trigger={null}>
      <div className="brand console-brand"><span className="brand-mark">P</span><span className="brand-name">PAYTECH</span></div>
      <SidebarMenu tree={menuTree} pathname={location.pathname} collapsed={collapsed} onNavigate={path => { void navigate(path); }} />
      {!viewportCollapsed && <NavigationCollapseControl collapsed={userCollapsed} onToggle={toggleNavigation} />}
    </Layout.Sider>
    <Layout>
      <Layout.Header className="console-header"><nav className="console-trail" aria-label="当前位置"><span className="workspace-title">运营平台</span>
        {trail.map((item, index) => <span key={item.resourceId} className={index === trail.length - 1 ? 'console-trail-current' : 'console-trail-item'}>{item.displayName}</span>)}</nav>
        <div className="header-account">{context.integrationMode !== 'PRODUCTION' && <Tag className="environment-tag">开发环境</Tag>}
          <span>{context.emailMasked || '管理员'}</span><Button type="text" onClick={() => void logout()} loading={loggingOut}>退出</Button></div>
      </Layout.Header>
      <Layout.Content className="console-content">
        {navigationLoaded && navigationFailed && <Alert type="warning" showIcon title="权限刷新失败，当前页面已保留。请重试确认最新权限。"
          action={<Button onClick={() => setReload(value => value + 1)}>重试</Button>} />}
        {!navigationLoaded && navigationFailed ? <Result status="warning" title="暂时无法加载菜单"
          extra={<Button onClick={() => setReload(value => value + 1)}>重试</Button>} />
          : !navigationLoaded && loading ? <div className="content-loading"><Spin /></div>
          : <Suspense fallback={<div className="content-loading"><Spin /></div>}>
            <Routes>
              <Route path="/" element={menus[0] ? <Navigate to={menus[0].frontendRoute!} replace /> : <NoAccess />} />
              {menus.flatMap(menu => pageRoutePaths(menu).map(path => <Route key={`${menu.resourceId}:${path}`} path={path} element={<ConsolePage componentKey={menu.componentKey!}
                resources={resources} actorId={context.accountId} locationKey={location.key} onAuthorizationChanged={() => setReload(value => value + 1)} />} />))}
              <Route path="*" element={<NoAccess />} />
            </Routes>
          </Suspense>}
      </Layout.Content>
    </Layout>
  </Layout>;
}
function NoAccess() { return <Result status="403" title="暂无访问权限" subTitle="如需使用此功能，请联系平台管理员分配权限。" />; }
