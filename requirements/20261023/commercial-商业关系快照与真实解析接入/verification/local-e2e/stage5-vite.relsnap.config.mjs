// AC-12 browser acceptance: serve the relsnap worktree ops-web on 18311; /api/operations goes through the local nginx
// OPERATIONS site (18444) so the Gateway sees the same entry channel, Origin and X-Real-IP as the shared 18301 instance.
import react from '/Users/kenny/Desktop/Workspace/Code-MoR-relsnap/operations-console-web/node_modules/@vitejs/plugin-react/dist/index.js';
export default {
  root: '/Users/kenny/Desktop/Workspace/Code-MoR-relsnap/operations-console-web',
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
