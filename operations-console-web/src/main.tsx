import { createRoot } from 'react-dom/client';
import { ConfigProvider, App as AntApp } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import { createBrowserRouter, RouterProvider } from 'react-router-dom';
import { App } from './App';
import './styles.css';

const router = createBrowserRouter([{ path: '*', element: <App /> }]);

createRoot(document.getElementById('root')!).render(
  <ConfigProvider locale={zhCN} theme={{ token: {
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
  </ConfigProvider>,
);
