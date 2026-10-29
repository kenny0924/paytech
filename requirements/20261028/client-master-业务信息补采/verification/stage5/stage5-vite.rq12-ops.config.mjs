// REQ-12 阶段 5：rq12 worktree 的 ops-web 跑在 18313（18311 仍为 wfdq worktree，停止被权限拒绝）；/api/operations 走本机 Nginx OPERATIONS 站点（18444），与 REQ-05 阶段 5 相同。
import react from '/Users/kenny/Desktop/Workspace/Code-MoR-rq12/operations-console-web/node_modules/@vitejs/plugin-react/dist/index.js';
export default {
  root: '/Users/kenny/Desktop/Workspace/Code-MoR-rq12/operations-console-web',
  // node_modules 是指向主工作树的符号链接：依赖预构建缓存放在 worktree 内，不写主工作树的 node_modules/.vite（18301 在用）。
  cacheDir: '/Users/kenny/Desktop/Workspace/Code-MoR-rq12/operations-console-web/.vite-cache',
  plugins: [react()],
  server: {
    proxy: {
      '/api/operations': { target: 'https://127.0.0.1:18444', secure: false, changeOrigin: true, headers: { origin: 'https://127.0.0.1:18444' } },
    },
  },
};
