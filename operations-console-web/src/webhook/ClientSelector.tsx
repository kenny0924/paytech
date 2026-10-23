import { useEffect, useState } from 'react';
import { Select } from 'antd';
import { session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { useT } from '../i18n/react';
import { clientAccessOk, clientAccessText, clientSearchDelay, decodeClient, pageOf, webhookAdminPath } from './webhook';
import type { WebhookClient } from './webhook';

const clientPage = pageOf(decodeClient);
const searchSize = 10;

/** Client 选择：输入停顿 300ms 后按名称或 Client ID 搜索；下拉显示端点数与平台访问提示。 */
export function ClientSelector({ value, onChange }: { value: WebhookClient | null; onChange: (client: WebhookClient) => void }) {
  const notify = useErrorNotification(); const t = useT('webhook');
  const [open, setOpen] = useState(false); const [keyword, setKeyword] = useState('');
  const [items, setItems] = useState<WebhookClient[]>([]); const [total, setTotal] = useState(0); const [loading, setLoading] = useState(false);
  useEffect(() => {
    if (!open) return;
    const controller = new AbortController();
    const timer = setTimeout(() => {
      setLoading(true);
      const query = new URLSearchParams({ page: '1', size: String(searchSize), ...(keyword.trim() ? { keyword: keyword.trim() } : {}) });
      session.request(`${webhookAdminPath}/clients?${query}`, { signal: controller.signal }, clientPage)
        .then(result => { if (!controller.signal.aborted) { setItems(result.items); setTotal(result.total); } })
        .catch(error => { if (!controller.signal.aborted) notify(error, t('clientSelector.searchFailed')); })
        .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    }, keyword ? clientSearchDelay : 0);
    return () => { clearTimeout(timer); controller.abort(); };
  // t 随语言包刷新换新，只用于失败通知标题，不应触发重新查询
  }, [open, keyword, notify]);
  const shown = value && !items.some(item => item.clientId === value.clientId) ? [value, ...items] : items;
  return <Select className="webhook-client-select" value={value?.clientId} loading={loading} placeholder={t('clientSelector.placeholder')}
    open={open} onOpenChange={next => { setOpen(next); if (!next) setKeyword(''); }}
    showSearch={{ filterOption: false, searchValue: keyword, onSearch: text => setKeyword(text.slice(0, 255)) }}
    notFoundContent={loading ? t('clientSelector.searching') : t('clientSelector.notFound')}
    options={shown.map(client => ({ value: client.clientId, label: `${client.clientName} · ${client.clientId}`, client }))}
    optionRender={option => { const client = (option.data as { client: WebhookClient }).client; const access = clientAccessOk(client) ? null : clientAccessText(client);
      return <div className="webhook-client-option"><div>{client.clientName}</div>
        <div className="cell-meta">{client.clientId} · {client.endpointCount ? t('clientSelector.endpointCount', { count: client.endpointCount }) : t('clientSelector.noEndpoints')}
          {access && <span className="webhook-warn"> · {access}</span>}</div></div>; }}
    popupRender={menu => <>{menu}{total > items.length
      && <div className="webhook-select-footer">{t('clientSelector.footer', { count: items.length })}</div>}</>}
    onChange={(_, option) => { const client = (option as { client?: WebhookClient } | undefined)?.client; if (client) onChange(client); }} />;
}
