// REQ-12 阶段 5：rq12 worktree 的 client-console-web 跑在 18312；/api/client 走本机 Nginx CLIENT 站点（18443），入口渠道与 Origin 同门户。
import react from '/Users/kenny/Desktop/Workspace/Code-MoR-rq12/client-console-web/node_modules/@vitejs/plugin-react/dist/index.js';
export default {
  root: '/Users/kenny/Desktop/Workspace/Code-MoR-rq12/client-console-web',
  // node_modules 是指向 Code-MoR-onboard 的符号链接：依赖预构建缓存放在本 worktree 内。
  cacheDir: '/Users/kenny/Desktop/Workspace/Code-MoR-rq12/client-console-web/.vite-cache',
  plugins: [react()],
  server: {
    // 字体等依赖经符号链接解析到 Code-MoR-onboard 的 node_modules，需放行（否则 /@fs 403）。
    fs: { allow: ['/Users/kenny/Desktop/Workspace/Code-MoR-rq12/client-console-web', '/Users/kenny/Desktop/Workspace/Code-MoR-onboard/client-console-web/node_modules'] },
    proxy: {
      '/api/client': { target: 'https://127.0.0.1:18443', secure: false, changeOrigin: true, headers: { origin: 'https://127.0.0.1:18443' } },
    },
  },
};
