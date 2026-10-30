// 联合查询阶段 5：jq-m worktree 的 ops-web 跑在 18316；/api/operations 走本机 Nginx OPERATIONS 站点（18444），与 REQ-05（18311）相同的上游。
import react from '/Users/kenny/Desktop/Workspace/Code-MoR-jq-m/operations-console-web/node_modules/@vitejs/plugin-react/dist/index.js';
export default {
  root: '/Users/kenny/Desktop/Workspace/Code-MoR-jq-m/operations-console-web',
  // node_modules 是指向主工作树的符号链接：依赖预构建缓存放在 worktree 内。
  cacheDir: '/Users/kenny/Desktop/Workspace/Code-MoR-jq-m/operations-console-web/.vite-cache',
  plugins: [react()],
  server: {
    host: '127.0.0.1', port: 18316, strictPort: true,
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
