import { useEffect, useState } from 'react';
import { Alert, Descriptions, Drawer, Spin, Table, Tag } from 'antd';
import { ApiError, session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import {
  decodeAccountPosting, financeErrorTarget, financePaths, localTime, postingSourceLabels, postingTotals, sideLabels, subledgerErrorTargets,
} from './finance';
import type { AccountPosting, AccountPostingEntry } from './finance';
import { Amount } from './FinanceParts';

/**
 * 入账批次抽屉（画板 15）：批次头、来源事实或桶移动、全部流水行与借贷合计
 * <li>highlightEntryId 为总账分录行或账户流水跳转来的流水，高亮显示；「账户流水」进入该流水所属 Owner 账户。</li>
 */
export function SubledgerPostingDrawer({ postingId, highlightEntryId, minorUnits, onOpenAccount, onClose, onAuthorizationChanged }: {
  postingId: string; highlightEntryId: string | null; minorUnits: ReadonlyMap<string, number>;
  onOpenAccount: (posting: AccountPosting, entry: AccountPostingEntry) => void; onClose: () => void; onAuthorizationChanged: () => void;
}) {
  const notify = useErrorNotification();
  const [posting, setPosting] = useState<AccountPosting>(); const [failure, setFailure] = useState<string | null>(null);
  useEffect(() => {
    const abort = new AbortController();
    setPosting(undefined); setFailure(null);
    session.request(financePaths.accountPosting(postingId), { signal: abort.signal }, value => decodeAccountPosting(value, postingId))
      .then(value => { if (!abort.signal.aborted) setPosting(value); })
      .catch(error => {
        if (abort.signal.aborted) return;
        if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
        const hint = financeErrorTarget(error, [], subledgerErrorTargets)?.message;
        setFailure(hint ?? '入账批次暂时无法加载');
        if (!hint) notify(error, '入账批次加载失败');
      });
    return () => abort.abort();
    // 回调由上层每次渲染传入，只在批次变化时重新加载。
  }, [postingId, notify]);
  const minorUnit = posting ? minorUnits.get(posting.currency) : undefined;
  const amount = (value: string) => <Amount value={value} minorUnit={minorUnit} />;
  const totals = posting ? postingTotals(posting.entries) : null;
  return <Drawer open size={640} rootClassName="finance-drawer" onClose={onClose} destroyOnHidden
    title={`入账批次${posting ? ` · 序号 ${posting.postingSequence}` : ''}`}>
    {failure ? <Alert type="warning" showIcon title={failure} /> : !posting || !totals ? <Spin /> : <>
      <Descriptions column={1} size="small" className="finance-detail-head" items={[
        { key: 'id', label: '批次主键', children: <span className="mono">{posting.postingId}</span> },
        { key: 'stream', label: '主体', children: `${posting.entityType} · ${posting.entityId} / ${posting.currency} · 分片 ${posting.shardNo}` },
        { key: 'sequence', label: '批次序号', children: posting.postingSequence },
        { key: 'time', label: '入账时间', children: localTime(posting.postedTime) },
        { key: 'source', label: '来源类型', children: `${posting.sourceType}（${postingSourceLabels[posting.sourceType]}）` },
        { key: 'published', label: '发布时间', children: posting.publishedTime ? localTime(posting.publishedTime) : '尚未发布' },
      ]} />
      {posting.sourceType === 'ECONOMIC_FACT' ? <>
        <h3 className="finance-subtitle">来源事实</h3>
        <Descriptions column={1} size="small" items={[
          { key: 'fact', label: '事实 ID', children: <span className="mono">{posting.factId}</span> },
          { key: 'factType', label: '事实类型', children: posting.factType ?? '—' },
          { key: 'factVersion', label: '事实版本', children: `v${posting.factVersion}` },
          { key: 'supersedes', label: '替代事实', children: posting.supersedesFactId ? <span className="mono">{posting.supersedesFactId}</span> : '—' },
          { key: 'apply', label: '应用状态', children: posting.factApplyStatus ?? '—' },
          { key: 'financeEntity', label: '服务费财务主体', children: posting.financeEntityId ?? '—' },
        ]} />
      </> : <>
        <h3 className="finance-subtitle">桶移动</h3>
        <Descriptions column={1} size="small" items={[
          { key: 'movement', label: '移动主键', children: posting.movementId ? <span className="mono">{posting.movementId}</span> : '—' },
          { key: 'movementType', label: '移动来源', children: posting.movementSourceType ?? '—' },
          { key: 'movementRef', label: '来源引用', children: posting.movementSourceRef ?? '—' },
        ]} />
      </>}
      <section className="table-panel finance-entry-lines">
        <Table<AccountPostingEntry> rowKey="entryId" size="small" tableLayout="fixed" dataSource={posting.entries} pagination={false} scroll={{ x: 600 }}
          rowClassName={row => row.entryId === highlightEntryId ? 'finance-row-selected' : ''}
          locale={{ emptyText: posting.factApplyStatus === 'SKIPPED' ? '事实已跳过，没有流水' : '该批次没有 Owner 流水' }} columns={[
            { title: '行号', dataIndex: 'lineNo', width: 56 },
            { title: '科目', render: (_, row) => <span className="finance-code">{row.accountCode}</span> },
            { title: 'Owner', width: 150, render: (_, row) => <div><div>{row.ownerType}</div><div className="cell-meta">{row.ownerId}</div></div> },
            { title: '余额桶', dataIndex: 'balanceBucket', width: 96 },
            { title: '方向', width: 56, render: (_, row) => sideLabels[row.entrySide] },
            { title: '金额', width: 110, align: 'right', render: (_, row) => amount(row.amount.amount) },
            { title: '', width: 80, render: (_, row) => <a onClick={() => onOpenAccount(posting, row)}>账户流水</a> },
          ]} />
      </section>
      <div className="finance-entry-totals">
        <span>借方合计 {amount(totals.debit)}</span><span>贷方合计 {amount(totals.credit)}</span>
        <Tag color="green">{posting.entries.length} 行流水</Tag>
      </div>
    </>}
  </Drawer>;
}
