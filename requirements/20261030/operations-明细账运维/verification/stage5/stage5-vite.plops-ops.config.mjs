// 明细账运维阶段 5：组合工作树 Code-MoR-plops-e2e（main 9eab368c + 本需求 + payout 未提交改动）的 ops-web 私有 Vite 18317；/api/operations 走本机 Nginx OPERATIONS 站点（18444）。
import react from '/Users/kenny/Desktop/Workspace/Code-MoR-plops-e2e/operations-console-web/node_modules/@vitejs/plugin-react/dist/index.js';
export default {
  root: '/Users/kenny/Desktop/Workspace/Code-MoR-plops-e2e/operations-console-web',
  cacheDir: '/Users/kenny/Desktop/Workspace/Code-MoR-plops-e2e/operations-console-web/.vite-cache',
  plugins: [react()],
  server: {
    proxy: {
      '/api/operations': { target: 'https://127.0.0.1:18444', secure: false, changeOrigin: true, headers: { origin: 'https://127.0.0.1:18444' } },
    },
  },
};
