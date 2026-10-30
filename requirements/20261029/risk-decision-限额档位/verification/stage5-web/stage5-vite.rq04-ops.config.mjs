// REQ-04 阶段 5：rq04combo worktree（main 6eaf1bd3 + 联合查询 + REQ-04）的 ops-web 跑在 18315（18311—18314、18316 为其他会话）；/api/operations 走本机 Nginx OPERATIONS 站点（18444），同 REQ-12 阶段 5。
import react from '/Users/kenny/Desktop/Workspace/Code-MoR-rq04combo/operations-console-web/node_modules/@vitejs/plugin-react/dist/index.js';
export default {
  root: '/Users/kenny/Desktop/Workspace/Code-MoR-rq04combo/operations-console-web',
  // node_modules 是指向主工作树的符号链接：依赖预构建缓存放在 worktree 内，不写主工作树的 node_modules/.vite。
  cacheDir: '/Users/kenny/Desktop/Workspace/Code-MoR-rq04combo/operations-console-web/.vite-cache',
  plugins: [react()],
  server: {
    proxy: {
      '/api/operations': { target: 'https://127.0.0.1:18444', secure: false, changeOrigin: true, headers: { origin: 'https://127.0.0.1:18444' } },
    },
  },
};
