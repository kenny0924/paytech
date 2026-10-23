import { useMemo } from 'react';
import { Empty, Table, Tag } from 'antd';
import type { PagingRequestBody } from '../api/paging';
import { usePaging } from '../hooks/usePaging';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import {
  decodeAccountEntry, entrySourceLabel, financeErrorTarget, financePaths, localTime, pageOf, postingSourceColors,
  subledgerAccountName, subledgerErrorTargets,
} from './finance';
import type { AccountEntry, OwnerAccountRef } from './finance';
import { Amount, LoadFailure, SideTag } from './FinanceParts';

/**
 * 账户明细流水（画板 14）：摘要（科目、Owner、桶、方向、当前净余额）与按批次序号、行号升序的流水
 * <li>点击批次序号打开入账批次抽屉；冲回列指向被替代事实的原流水；事实类型桶移动为空；R20 只接受分页，不提供入账时间筛选。</li>
 */
export function SubledgerEntries({ account, currency, paging, minorUnit, onQueryChange, onOpenPosting }: {
  account: OwnerAccountRef; currency?: string; paging: PagingRequestBody; minorUnit?: number;
  onQueryChange: (query: { paging: PagingRequestBody; filters: Readonly<Record<string, string>> }) => void;
  onOpenPosting: (postingId: string, entryId: string) => void;
}) {
  const t = useT('finance');
  const decode = useMemo(() => pageOf(decodeAccountEntry(currency)), [currency]);
  const page = usePaging<AccountEntry>(financePaths.ownerAccountEntries(account.accountId), decode, {}, { initialPaging: paging, onQueryChange });
  const hint = financeErrorTarget(page.failure, [], subledgerErrorTargets)?.message;
  const accountName = account.accountCode ? subledgerAccountName(account.accountCode) : null;
  return <>
    {account.accountCode && <dl className="finance-summary">
      {account.accountNo && <div><dt>{t('field.accountNo')}</dt><dd className="mono">{account.accountNo}</dd></div>}
      <div><dt>{t('field.accountCode')}</dt><dd>{account.accountCode}{accountName ? ` · ${accountName}` : ''}</dd></div>
      <div><dt>{t('field.owner')}</dt><dd>{account.ownerType} · {account.ownerId}</dd></div>
      <div><dt>{t('field.balanceBucket')}</dt><dd>{account.balanceBucket}</dd></div>
      {account.normalSide && <div><dt>{t('field.side')}</dt><dd>{enumLabel(t, 'enum.side', account.normalSide)} {account.normalSide}</dd></div>}
      {account.netBalance && <div><dt>{t('subledger.entries.currentNetBalance')}</dt><dd><strong>
        <Amount value={account.netBalance.amount} minorUnit={minorUnit} suffix={account.netBalance.currency} /></strong></dd></div>}
    </dl>}
    {page.error ? <LoadFailure message={hint ?? page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<AccountEntry> rowKey="entryId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1250 }}
        pagination={{ ...page.pagination, showTotal: total => t('common.total', { total }) }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('subledger.entries.empty')} /> }} columns={[
          { title: t('subledger.entries.column.postingSequence'), width: 100, render: (_, row) => <a className="mono" onClick={() => onOpenPosting(row.postingId, row.entryId)}>{row.postingSequence}</a> },
          { title: t('subledger.entries.column.postedAt'), width: 150, render: (_, row) => localTime(row.postedTime) },
          { title: t('subledger.entries.column.sourceType'), width: 100, render: (_, row) => <Tag color={postingSourceColors[row.sourceType]}>{enumLabel(t, 'enum.postingSource', row.sourceType)}</Tag> },
          { title: t('field.lineNo'), dataIndex: 'lineNo', width: 64 },
          { title: t('field.side'), width: 64, render: (_, row) => <SideTag side={row.entrySide} /> },
          { title: t('field.amount'), width: 140, align: 'right', render: (_, row) => <Amount value={row.amount.amount} minorUnit={minorUnit} /> },
          { title: t('subledger.entries.column.reverses'), width: 130, align: 'right', render: (_, row) => row.reversesEntryId
            ? <span title={row.reversesEntryId}>{t('subledger.entries.reversesEntry', { entry: row.reversesEntryId.slice(-4) })}</span> : '—' },
          { title: t('subledger.entries.column.factType'), width: 170, ellipsis: true, render: (_, row) => row.factType ?? '—' },
          { title: t('subledger.entries.column.source'), render: (_, row) => <div><div>{entrySourceLabel(row)}</div>
            <div className="cell-meta">{row.sourceType === 'ECONOMIC_FACT'
              ? t('subledger.entries.factLine', { lineNo: row.factLineNo ?? '—' }) : t('subledger.entries.posting', { posting: row.postingId.slice(-4) })}</div></div> },
        ]} />
    </section>}
  </>;
}
