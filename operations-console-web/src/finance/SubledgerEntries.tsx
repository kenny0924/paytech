import { useMemo } from 'react';
import { Empty, Table, Tag } from 'antd';
import type { PagingRequestBody } from '../api/paging';
import { usePaging } from '../hooks/usePaging';
import {
  decodeAccountEntry, entrySourceLabel, financeErrorTarget, financePaths, localTime, pageOf, postingSourceColors, postingSourceLabels, sideLabels,
  subledgerAccountNames, subledgerErrorTargets,
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
  const decode = useMemo(() => pageOf(decodeAccountEntry(currency)), [currency]);
  const page = usePaging<AccountEntry>(financePaths.ownerAccountEntries(account.accountId), decode, {}, { initialPaging: paging, onQueryChange });
  const hint = financeErrorTarget(page.failure, [], subledgerErrorTargets)?.message;
  return <>
    {account.accountCode && <dl className="finance-summary">
      {account.accountNo && <div><dt>账户号</dt><dd className="mono">{account.accountNo}</dd></div>}
      <div><dt>科目</dt><dd>{account.accountCode}{subledgerAccountNames[account.accountCode] ? ` · ${subledgerAccountNames[account.accountCode]}` : ''}</dd></div>
      <div><dt>Owner</dt><dd>{account.ownerType} · {account.ownerId}</dd></div>
      <div><dt>余额桶</dt><dd>{account.balanceBucket}</dd></div>
      {account.normalSide && <div><dt>方向</dt><dd>{sideLabels[account.normalSide]} {account.normalSide}</dd></div>}
      {account.netBalance && <div><dt>当前净余额</dt><dd><strong>
        <Amount value={account.netBalance.amount} minorUnit={minorUnit} suffix={account.netBalance.currency} /></strong></dd></div>}
    </dl>}
    {page.error ? <LoadFailure message={hint ?? page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<AccountEntry> rowKey="entryId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1250 }}
        pagination={{ ...page.pagination, showTotal: total => `共 ${total} 条` }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="该账户还没有流水" /> }} columns={[
          { title: '批次序号', width: 100, render: (_, row) => <a className="mono" onClick={() => onOpenPosting(row.postingId, row.entryId)}>{row.postingSequence}</a> },
          { title: '入账时间', width: 150, render: (_, row) => localTime(row.postedTime) },
          { title: '来源类型', width: 100, render: (_, row) => <Tag color={postingSourceColors[row.sourceType]}>{postingSourceLabels[row.sourceType]}</Tag> },
          { title: '行号', dataIndex: 'lineNo', width: 64 },
          { title: '方向', width: 64, render: (_, row) => <SideTag side={row.entrySide} /> },
          { title: '金额', width: 140, align: 'right', render: (_, row) => <Amount value={row.amount.amount} minorUnit={minorUnit} /> },
          { title: '冲回关系', width: 130, align: 'right', render: (_, row) => row.reversesEntryId
            ? <span title={row.reversesEntryId}>← 流水 …{row.reversesEntryId.slice(-4)}</span> : '—' },
          { title: '事实类型', width: 170, ellipsis: true, render: (_, row) => row.factType ?? '—' },
          { title: '来源', render: (_, row) => <div><div>{entrySourceLabel(row)}</div>
            <div className="cell-meta">{row.sourceType === 'ECONOMIC_FACT' ? `事实行 ${row.factLineNo ?? '—'}` : `批次 …${row.postingId.slice(-4)}`}</div></div> },
        ]} />
    </section>}
  </>;
}
