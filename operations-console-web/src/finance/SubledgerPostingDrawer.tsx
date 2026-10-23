import { useEffect, useState } from 'react';
import { Alert, Descriptions, Drawer, Spin, Table, Tag } from 'antd';
import { ApiError, session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import {
  decodeAccountPosting, financeErrorTarget, financePaths, localTime, postingTotals, subledgerErrorTargets,
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
  const t = useT('finance');
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
        setFailure(hint ?? t('posting.unavailable'));
        if (!hint) notify(error, t('posting.loadFailed'));
      });
    return () => abort.abort();
    // 回调由上层每次渲染传入，只在批次变化时重新加载。
  // t 随语言包刷新换新，只用于失败文案，不应触发重新加载
  }, [postingId, notify]);
  const minorUnit = posting ? minorUnits.get(posting.currency) : undefined;
  const amount = (value: string) => <Amount value={value} minorUnit={minorUnit} />;
  const totals = posting ? postingTotals(posting.entries) : null;
  return <Drawer open size={640} rootClassName="finance-drawer" onClose={onClose} destroyOnHidden
    title={posting ? t('posting.titleWithSequence', { sequence: posting.postingSequence }) : t('posting.title')}>
    {failure ? <Alert type="warning" showIcon title={failure} /> : !posting || !totals ? <Spin /> : <>
      <Descriptions column={1} size="small" className="finance-detail-head" items={[
        { key: 'id', label: t('posting.field.postingId'), children: <span className="mono">{posting.postingId}</span> },
        { key: 'stream', label: t('posting.field.stream'), children: t('posting.streamValue', { entityType: posting.entityType, entityId: posting.entityId, currency: posting.currency, shardNo: posting.shardNo }) },
        { key: 'sequence', label: t('subledger.entries.column.postingSequence'), children: posting.postingSequence },
        { key: 'time', label: t('subledger.entries.column.postedAt'), children: localTime(posting.postedTime) },
        { key: 'source', label: t('subledger.entries.column.sourceType'), children: t('common.codeWithLabel', { code: posting.sourceType, label: enumLabel(t, 'enum.postingSource', posting.sourceType) }) },
        { key: 'published', label: t('posting.field.publishedAt'), children: posting.publishedTime ? localTime(posting.publishedTime) : t('posting.notPublished') },
      ]} />
      {posting.sourceType === 'ECONOMIC_FACT' ? <>
        <h3 className="finance-subtitle">{t('posting.sourceFact')}</h3>
        <Descriptions column={1} size="small" items={[
          { key: 'fact', label: t('posting.field.factId'), children: <span className="mono">{posting.factId}</span> },
          { key: 'factType', label: t('subledger.entries.column.factType'), children: posting.factType ?? '—' },
          { key: 'factVersion', label: t('posting.field.factVersion'), children: `v${posting.factVersion}` },
          { key: 'supersedes', label: t('posting.field.supersedes'), children: posting.supersedesFactId ? <span className="mono">{posting.supersedesFactId}</span> : '—' },
          { key: 'apply', label: t('posting.field.applyStatus'), children: posting.factApplyStatus ?? '—' },
          { key: 'financeEntity', label: t('posting.field.financeEntity'), children: posting.financeEntityId ?? '—' },
        ]} />
      </> : <>
        <h3 className="finance-subtitle">{t('posting.movement')}</h3>
        <Descriptions column={1} size="small" items={[
          { key: 'movement', label: t('posting.field.movementId'), children: posting.movementId ? <span className="mono">{posting.movementId}</span> : '—' },
          { key: 'movementType', label: t('posting.field.movementSource'), children: posting.movementSourceType ?? '—' },
          { key: 'movementRef', label: t('posting.field.movementRef'), children: posting.movementSourceRef ?? '—' },
        ]} />
      </>}
      <section className="table-panel finance-entry-lines">
        <Table<AccountPostingEntry> rowKey="entryId" size="small" tableLayout="fixed" dataSource={posting.entries} pagination={false} scroll={{ x: 600 }}
          rowClassName={row => row.entryId === highlightEntryId ? 'finance-row-selected' : ''}
          locale={{ emptyText: posting.factApplyStatus === 'SKIPPED' ? t('posting.emptySkipped') : t('posting.empty') }} columns={[
            { title: t('field.lineNo'), dataIndex: 'lineNo', width: 56 },
            { title: t('field.accountNoAndCode'), render: (_, row) => <div><div className="mono">{row.accountNo}</div>
              <div className="cell-meta finance-code">{row.accountCode}</div></div> },
            { title: t('field.owner'), width: 150, render: (_, row) => <div><div>{row.ownerType}</div><div className="cell-meta">{row.ownerId}</div></div> },
            { title: t('field.balanceBucket'), dataIndex: 'balanceBucket', width: 96 },
            { title: t('field.side'), width: 56, render: (_, row) => enumLabel(t, 'enum.side', row.entrySide) },
            { title: t('field.amount'), width: 110, align: 'right', render: (_, row) => amount(row.amount.amount) },
            { title: '', width: 80, render: (_, row) => <a onClick={() => onOpenAccount(posting, row)}>{t('posting.accountEntries')}</a> },
          ]} />
      </section>
      <div className="finance-entry-totals">
        <span>{t('field.debitTotal')} {amount(totals.debit)}</span><span>{t('field.creditTotal')} {amount(totals.credit)}</span>
        <Tag color="green">{t('posting.entryCount', { count: posting.entries.length })}</Tag>
      </div>
    </>}
  </Drawer>;
}
