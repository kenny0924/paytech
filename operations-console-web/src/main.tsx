import { createRoot } from 'react-dom/client';
import { ConfigProvider, App as AntApp } from 'antd';
import enUS from 'antd/locale/en_US';
import zhCN from 'antd/locale/zh_CN';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { App } from './App';
import { OPS_NAMESPACES, RUNTIME_DATA_NAMESPACES } from './i18n/namespaces';
import { I18nGate, useLocale } from './i18n/react';
import './styles.css';

const router = createBrowserRouter([{ path: '*', element: <App /> }]);

/** antd 与 dayjs 语言随当前界面语言；切换语言会整页刷新，因此每次加载只确定一次。 */
function Root() {
  const { locale } = useLocale();
  const chinese = locale === 'zh-CN';
  dayjs.locale(chinese ? 'zh-cn' : 'en');
  return <ConfigProvider locale={chinese ? zhCN : enUS} theme={{ token: {
    colorPrimary: '#315dca', colorText: '#272b32', colorTextSecondary: '#69717d',
    colorBgLayout: '#ffffff', colorBorder: '#dfe3e8', colorBorderSecondary: '#edf0f2',
    borderRadius: 5, fontSize: 12, controlHeight: 32,
    fontFamily: '-apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif',
  }, components: {
    Button: { fontWeight: 500, defaultShadow: 'none', primaryShadow: 'none' },
    Menu: { itemHeight: 34, itemBorderRadius: 5, itemSelectedBg: '#edf2ff', itemSelectedColor: '#315dca' },
    Table: { headerBg: '#f8f9fb', headerColor: '#626b78', headerBorderRadius: 0, cellPaddingBlock: 10, cellPaddingInline: 14 },
  } }}>
    <AntApp notification={{ placement: 'topRight', top: 72, maxCount: 3, stack: false,
      classNames: { list: 'operations-notification-list' },
    }}><RouterProvider router={router} /></AntApp>
  </ConfigProvider>;
}

// 根部一次加载全部命名空间（有本地缓存时直接用缓存，不等网络），登录页与控制台都在 Gate 内；
// 含只在运行时发布的 refdata，避免页面中首次使用时再加载导致当前页重新挂载。
const ROOT_NAMESPACES = [...OPS_NAMESPACES, ...RUNTIME_DATA_NAMESPACES];
createRoot(document.getElementById('root')!).render(
  <I18nGate namespaces={ROOT_NAMESPACES} fallback={<div className="full-loading" />}><Root /></I18nGate>,
);
