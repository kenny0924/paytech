import { Suspense, lazy, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Layout, Result, Spin, Tag } from 'antd';
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { ApiError, session } from './api/session';
import type { ConsoleResource, NavigationResponseBody, SessionContextResponseBody } from './api/types';
import { Login } from './auth/Login';
import { RequiredAction } from './auth/RequiredAction';
import { useOperationsTools } from './auth/webmcp';
import { useErrorNotification } from './feedback/useErrorNotification';
import { LanguageSwitcher } from './i18n/LanguageSwitcher';
import { useT } from './i18n/react';
import { buildMenuTree, legacyRedirects, menuPages, pageRoutePaths, resourceLabel } from './navigation/menu';
import { menuTrail } from './navigation/menuTrail';
import { useTrailTitleHeading } from './navigation/useTrailTitleHeading';
import { NavigationCollapseControl } from './navigation/NavigationCollapseControl';
import { getBrowserLocalStorage, loadNavigationCollapsed, saveNavigationCollapsed } from './navigation/navigationPreference';
import { SidebarMenu } from './navigation/SidebarMenu';

import { ConsolePage } from './navigation/ConsolePage';

/** 原「外部 API › 接入方」地址（channelWorkspace.legacyIntegrationRoute）：接入方并入渠道后按编码跳转到渠道详情的同名页签。 */
const LegacyIntegrationRedirect = lazy(() => import('./channel/ChannelResolve').then(m => ({ default: m.LegacyIntegrationRedirect })));
export function App() {
  const notifyError = useErrorNotification();
  const t = useT('auth'), tCommon = useT('common');
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
        notifyError(error, t('session.restoreFailed'));
      }
    }).finally(() => { if (active) setInitializing(false); });
    return () => { active = false; session.onExpired = () => {}; };
  // 只随重试重新恢复会话；t 在语言包到达时会换新，不应重复请求。
  }, [initializationAttempt, notifyError]);
  if (initializing) return <div className="full-loading"><Spin size="large" tip={t('session.checking')}><div /></Spin></div>;
  if (initializationFailed) return <div className="full-loading"><Result status="warning" title={t('session.unavailableTitle')}
    subTitle={t('session.unavailableHint')} extra={<Button type="primary" onClick={() => setInitializationAttempt(value => value + 1)}>{tCommon('action.retry')}</Button>} /></div>;
  if (!context) return <Login onLogin={setContext} initialMessage={loginMessage} />;
  if (context.passwordChangeRequired || context.bootstrapAdministratorRequired) {
    return <RequiredAction context={context} onComplete={() => { setContext(null); setLoginMessage(t('session.actionCompleted')); }} />;
  }
  return <Console context={context} />;
}

function Console({ context }: { context: SessionContextResponseBody }) {
  const notifyError = useErrorNotification();
  const t = useT('nav'), tAuth = useT('auth'), tCommon = useT('common');
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
          notifyError(error, t('menu.loadFailed'));
        }
      }).finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  // 导航属于当前登录会话；切页只切换路由，避免重复鉴权查询和卸载页面。
  }, [reload, notifyError]);
  const menuTree = useMemo(() => buildMenuTree(resources), [resources]);
  const menus = useMemo(() => menuPages(menuTree), [menuTree]);
  const trail = useMemo(() => menuTrail(menuTree, location.pathname), [menuTree, location.pathname]);
  const current = trail.at(-1);
  useTrailTitleHeading(current ? resourceLabel(t, current) : undefined);
  useOperationsTools(menus, navigate);
  async function logout() {
    setLoggingOut(true);
    try { await session.logout(); }
    catch (error) { notifyError(error, tAuth('logout.unconfirmed')); }
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
      <Layout.Header className="console-header"><nav className="console-trail" aria-label={t('header.trail')}><span className="workspace-title">{t('header.workspace')}</span>
        {trail.map((item, index) => <span key={item.resourceId} className={index === trail.length - 1 ? 'console-trail-current' : 'console-trail-item'}>{resourceLabel(t, item)}</span>)}</nav>
        <div className="header-account">{context.integrationMode !== 'PRODUCTION' && <Tag className="environment-tag">{t('header.environment')}</Tag>}
          <LanguageSwitcher />
          <span>{context.emailMasked || t('header.administrator')}</span><Button type="text" onClick={() => void logout()} loading={loggingOut}>{tAuth('logout.button')}</Button></div>
      </Layout.Header>
      <Layout.Content className="console-content">
        {navigationLoaded && navigationFailed && <Alert type="warning" showIcon title={t('header.permissionRefreshFailed')}
          action={<Button onClick={() => setReload(value => value + 1)}>{tCommon('action.retry')}</Button>} />}
        {!navigationLoaded && navigationFailed ? <Result status="warning" title={t('menu.unavailable')}
          extra={<Button onClick={() => setReload(value => value + 1)}>{tCommon('action.retry')}</Button>} />
          : !navigationLoaded && loading ? <div className="content-loading"><Spin /></div>
          : <Suspense fallback={<div className="content-loading"><Spin /></div>}>
            <Routes>
              <Route path="/" element={menus[0] ? <Navigate to={menus[0].frontendRoute!} replace /> : <NoAccess />} />
              {menus.flatMap(menu => pageRoutePaths(menu).map(path => <Route key={`${menu.resourceId}:${path}`} path={path} element={<ConsolePage componentKey={menu.componentKey!}
                resources={resources} actorId={context.accountId} locationKey={location.key} onAuthorizationChanged={() => setReload(value => value + 1)} />} />))}
              {legacyRedirects(menus).map(redirect => <Route key={redirect.path} path={redirect.path} element={<Navigate to={redirect.to} replace />} />)}
              <Route path="/external-api/integrations/*" element={menus.some(menu => menu.componentKey === 'OPERATIONS_CHANNEL_CHANNELS')
                ? <LegacyIntegrationRedirect /> : <NoAccess />} />
              <Route path="*" element={<NoAccess />} />
            </Routes>
          </Suspense>}
      </Layout.Content>
    </Layout>
  </Layout>;
}
function NoAccess() {
  const t = useT('nav');
  return <Result status="403" title={t('noAccess.title')} subTitle={t('noAccess.hint')} />;
}
