import { useEffect } from 'react';
import { flushSync } from 'react-dom';
import type { NavigateFunction } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';

type ToolRegistry = { registerTool(tool: {
  name: string; title: string; description: string; inputSchema: object;
  annotations: { readOnlyHint: boolean; untrustedContentHint: boolean };
  execute(input: unknown): unknown;
}, options: { signal: AbortSignal }): void | Promise<void> };

/** 仅提供与当前可见菜单相同的导航入口，不暴露凭据或绕过服务端授权。 */
export function useOperationsTools(menus: ConsoleResource[], navigate: NavigateFunction) {
  useEffect(() => {
    const registry = (document as Document & { modelContext?: ToolRegistry }).modelContext;
    if (!registry?.registerTool || menus.length === 0) return;
    const lifecycle = new AbortController();
    const paths = menus.map(menu => menu.frontendRoute!);
    try {
      void Promise.resolve(registry.registerTool({
        name: 'open_operations_page', title: '打开 Operations 页面',
        description: '打开当前用户菜单中已有权限的人员管理、角色管理或审计日志页面。只导航，不创建或修改数据。',
        inputSchema: { type: 'object', properties: { path: { type: 'string', enum: paths } }, required: ['path'], additionalProperties: false },
        annotations: { readOnlyHint: false, untrustedContentHint: true },
        execute(input: unknown) {
          if (!input || typeof input !== 'object' || Object.keys(input).length !== 1 || !('path' in input)
            || typeof input.path !== 'string' || !paths.includes(input.path)) throw new Error('页面不存在或没有访问权限');
          const path = input.path;
          flushSync(() => { void navigate(path); });
          return { path, status: 'opened' };
        },
      }, { signal: lifecycle.signal })).catch(() => lifecycle.abort());
    } catch { lifecycle.abort(); }
    return () => lifecycle.abort();
  }, [menus, navigate]);
}
