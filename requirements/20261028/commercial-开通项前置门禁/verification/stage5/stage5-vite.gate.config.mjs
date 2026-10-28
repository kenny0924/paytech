// REQ-01 阶段 5：worktree 的 ops-web 跑在 18311；/api/operations 走本机 Nginx OPERATIONS 站点（18444），
// 让 Gateway 看到与共享 18301 实例相同的入口渠道、Origin 与 X-Real-IP（同 20261023 relsnap 阶段 5 做法）。
import react from '/Users/kenny/Desktop/Workspace/Code-MoR-gate/operations-console-web/node_modules/@vitejs/plugin-react/dist/index.js';
export default {
  root: '/Users/kenny/Desktop/Workspace/Code-MoR-gate/operations-console-web',
  plugins: [react()],
  server: {
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
