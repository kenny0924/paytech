import { useEffect, useRef, useState } from 'react';
import { Button, Select, Space } from 'antd';
import { session } from '../api/session';
import { requestPage } from '../api/paging';
import { useErrorNotification } from '../feedback/useErrorNotification';
interface ScopeOption { scopeCode: string; displayName: string }
/** 目录由 Platform 管理，旧绑定仅保留原值，不能手写新 Scope。 */
export function ScopeSelect({ value, onChange, disabled, originalValue }: { value?: string; onChange?: (value: string) => void; disabled?: boolean; originalValue?: string | null }) {
  const [keyword, setKeyword] = useState(''); const [page, setPage] = useState(1); const [options, setOptions] = useState<ScopeOption[]>([]);
  const [loading, setLoading] = useState(false); const [failed, setFailed] = useState(false); const [reload, setReload] = useState(0); const [total, setTotal] = useState(0);
  const [selected, setSelected] = useState<ScopeOption | null>();
  const pending = useRef(false); const notify = useErrorNotification();
  useEffect(() => {
    setSelected(undefined);
    if (!value) return;
    const abort = new AbortController();
    // 查询当前编码的名称，不把“没有出现在当前选项页”误判成“未登记”。
    requestPage<ScopeOption>('/api-scope-admin/v1/scope-options', { page: 1, size: 100 }, abort.signal, session, { keyword: value })
      .then(result => {
        if (abort.signal.aborted) return;
        const match = result.items.find(option => option.scopeCode === value);
        setSelected(match ?? (result.total === result.items.length ? null : undefined));
      })
      .catch(() => { /* 名称无法确认时保留原编码；主选项查询继续提供局部重试。 */ });
    return () => abort.abort();
  }, [value, reload]);
  useEffect(() => {
    if (disabled) return;
    const abort = new AbortController(); pending.current = true; setLoading(true); setFailed(false);
    const timer = setTimeout(() => {
      requestPage<ScopeOption>('/api-scope-admin/v1/scope-options', { page, size: 50 }, abort.signal, session, keyword.trim() ? { keyword: keyword.trim() } : {})
        .then(result => { if (!abort.signal.aborted) { setOptions(previous => page === 1 ? result.items : [...new Map([...previous, ...result.items].map(o => [o.scopeCode, o])).values()]); setTotal(result.total); } })
        .catch(e => { if (!abort.signal.aborted) { setFailed(true); notify(e, 'Scope 选项加载失败'); } })
        .finally(() => { if (!abort.signal.aborted) { pending.current = false; setLoading(false); } });
    }, keyword ? 250 : 0);
    return () => { clearTimeout(timer); abort.abort(); pending.current = false; };
  }, [keyword, page, disabled, reload, notify]);
  const display = options.map(o => ({ value: o.scopeCode, label: `${o.scopeCode} · ${o.displayName}` }));
  if (value && !display.some(o => o.value === value)) display.unshift({ value, label: selected?.scopeCode === value
    ? `${value} · ${selected.displayName}` : `${value}${value === originalValue ? selected === null ? ' · 未登记' : ' · 原绑定' : ''}` });
  return <Space.Compact block><Select style={{ width: '100%' }} value={value} onChange={onChange} showSearch filterOption={false} disabled={disabled} loading={loading}
    placeholder="搜索 Scope 编码或名称" options={display} notFoundContent={failed ? '加载失败' : loading ? '加载中…' : '暂无匹配 Scope'}
    onSearch={text => { setOptions([]); setPage(1); setKeyword(text); }} onPopupScroll={event => { const target = event.target as HTMLElement;
      if (!pending.current && !failed && options.length < total && target.scrollHeight - target.scrollTop - target.clientHeight < 32) setPage(p => p + 1); }} />
    {failed && <Button onClick={() => setReload(v => v + 1)}>重试</Button>}</Space.Compact>;
}
