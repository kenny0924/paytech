import { useMemo, useRef, useState } from 'react';
import { AutoComplete } from 'antd';
import { session } from '../api/session';
import { decodeCasePage, reviewApi } from '../clientReview/clientReview';
import { browserStorage, recentClients } from './commercialConsole';

interface Props { value?: string; onChange?: (value: string) => void; placeholder?: string; allowClear?: boolean }
interface ApprovedClient { clientId: string; caseNo: string }

/**
 * Client ID 输入：手输 + 本机最近使用（最多 10 个）+ 审核通过的 Client（入驻审核任务 status=APPROVED 的第一页 50 条，只有 clientId 与任务号）
 * <li>运营侧没有按名称搜索 Client 的接口（遗留给 client-master），选项只帮助回填 ID；审核列表无权限或失败时只显示最近使用。</li>
 * <li>使用后由调用方 rememberClient 记录。</li>
 */
export function ClientIdInput({ value, onChange, placeholder = '输入或选择 Client ID', allowClear = true }: Props) {
  const [approved, setApproved] = useState<ApprovedClient[]>([]);
  const loaded = useRef(false);
  const recent = useMemo(() => recentClients(browserStorage()), []);
  const loadApproved = () => {
    if (loaded.current) return;
    loaded.current = true;
    session.request(`${reviewApi}/cases?status=APPROVED&page=1&size=50`, {}, decodeCasePage)
      .then(page => setApproved([...new Map(page.items.map(item => [item.clientId, { clientId: item.clientId, caseNo: item.caseNo }])).values()]))
      .catch(() => setApproved([]));
  };
  const keyword = (value ?? '').trim();
  const match = (clientId: string, extra = '') => !keyword || clientId.includes(keyword) || extra.includes(keyword);
  const options = [
    { label: '最近使用', options: recent.filter(id => match(id)).map(id => ({ value: id, label: <span className="mono">{id}</span> })) },
    { label: '审核通过的 Client', options: approved.filter(item => match(item.clientId, item.caseNo) && !recent.includes(item.clientId))
      .map(item => ({ value: item.clientId, label: <span><span className="mono">{item.clientId}</span> · {item.caseNo}</span> })) },
  ].filter(group => group.options.length > 0);
  return <AutoComplete value={value} onChange={next => onChange?.(String(next ?? ''))} onFocus={loadApproved} options={options}
    placeholder={placeholder} allowClear={allowClear} maxLength={19} />;
}
