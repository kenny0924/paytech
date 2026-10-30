// tax 阶段 5：tax worktree 的 ops-web 跑在 127.0.0.1:18315（18311—18314 已被其他会话占用）；/api/operations 走本机 Nginx OPERATIONS 站点（18444），
// 与 20261028 commercial-开通项前置门禁阶段 5 同一做法。node_modules 为指向主工作树的临时符号链接（两边 package-lock.json 一致），
// Vite 缓存放会话临时目录，不写主工作树的 node_modules/.vite。
import react from '/Users/kenny/Desktop/Workspace/Code-MoR/operations-console-web/node_modules/@vitejs/plugin-react/dist/index.js';
export default {
  root: '/Users/kenny/Desktop/Workspace/Code-MoR-tax/operations-console-web',
  cacheDir: '/private/tmp/claude-501/-Users-kenny-Desktop-Workspace-Code-MoR-design-discuss/efbf8c50-ce10-40da-924c-05707194ea2e/scratchpad/vite-cache',
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    port: 18315,
    strictPort: true,
    proxy: {
      '/api/operations': {
        target: 'https://127.0.0.1:18444',
        secure: false,
        changeOrigin: true,
        headers: { origin: 'https://127.0.0.1:18444' },
      },
    },
  },
};
