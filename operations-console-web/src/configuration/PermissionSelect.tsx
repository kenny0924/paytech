import { useEffect, useRef, useState } from 'react';
import { Select } from 'antd';
import { session } from '../api/session';
import type { PermissionOption, PagingResponseBody } from './types';
import { useErrorNotification } from '../feedback/useErrorNotification';

/** 服务端分页搜索全局 ACTIVE 权限，同时保留当前停用绑定的显示。 */
export function PermissionSelect({ value, onChange, usage, currentLabel, disabled }: {
  value?: string; onChange?: (value: string) => void; usage: 'GATEWAY_ROUTE' | 'CONSOLE_RESOURCE'; currentLabel?: string; disabled?: boolean;
}) {
  const notify = useErrorNotification(); const [keyword, setKeyword] = useState(''); const [page, setPage] = useState(1);
  const [options, setOptions] = useState<PermissionOption[]>([]); const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0); const pending = useRef(false);
  useEffect(() => {
    if (disabled) return;
    const controller = new AbortController();
    const timer = setTimeout(() => {
      pending.current = true; setLoading(true);
      const query = new URLSearchParams({ usage, page: String(page), size: '100', ...(keyword.trim() ? { keyword: keyword.trim() } : {}) });
      session.request<PagingResponseBody<PermissionOption>>(`/permission-admin/v1/permission-options?${query}`, { signal: controller.signal })
        .then(result => { if (!controller.signal.aborted) { setTotal(result.total); setOptions(previous => page === 1 ? result.items :
          [...new Map([...previous, ...result.items].map(item => [item.permissionId, item])).values()]); } })
        .catch(error => { if (!controller.signal.aborted) notify(error, '权限选项加载失败'); })
        .finally(() => { if (!controller.signal.aborted) { pending.current = false; setLoading(false); } });
    }, keyword ? 250 : 0);
    return () => { clearTimeout(timer); controller.abort(); pending.current = false; };
  }, [usage, keyword, page, disabled, notify]);
  const display = options.map(option => ({ value: option.permissionId, label: option.permissionCode }));
  if (value && !display.some(option => option.value === value)) display.unshift({ value, label: currentLabel || value });
  return <Select value={value} onChange={onChange} disabled={disabled} showSearch filterOption={false} loading={loading}
    placeholder="搜索权限编码" options={display} onSearch={text => { setPage(1); setKeyword(text); }}
    onPopupScroll={event => { const target = event.target as HTMLElement;
      if (!pending.current && options.length < total && target.scrollHeight - target.scrollTop - target.clientHeight < 32) setPage(v => v + 1);
    }} />;
}
