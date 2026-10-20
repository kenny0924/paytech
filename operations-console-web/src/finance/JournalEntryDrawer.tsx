import { useEffect, useState } from 'react';
import { Alert, Descriptions, Drawer, Spin, Table, Tag } from 'antd';
import { Link } from 'react-router-dom';
import { ApiError, session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { decodeJournalEntryDetail, entryTypeLabels, financeErrorTarget, financePaths, journalTotals, sideLabels, subledgerPostingLink } from './finance';
import type { BookRef, JournalEntryDetail, JournalEntryLine } from './finance';
import { Amount } from './FinanceParts';

/**
 * 分录详情抽屉：分录头、指令来源（自动来源事件键、account 批次或人工调整单号）、全部分录行与借贷合计
 * <li>ACCOUNT_POSTING 来源的控制科目行带 account 流水主键，「流水 …后 4 位 ↗」跳到明细账批次抽屉并高亮该流水。</li>
 */
export function JournalEntryDrawer({ book, bookLabel, journalEntryId, minorUnit, onClose, onAuthorizationChanged }: {
  book: BookRef; bookLabel: string; journalEntryId: string; minorUnit?: number; onClose: () => void; onAuthorizationChanged: () => void;
}) {
  const notify = useErrorNotification();
  const [detail, setDetail] = useState<JournalEntryDetail>(); const [failure, setFailure] = useState<string | null>(null);
  useEffect(() => {
    const abort = new AbortController();
    setDetail(undefined); setFailure(null);
    session.request(financePaths.journalEntry(book.ledgerBookId, journalEntryId), { signal: abort.signal },
      value => decodeJournalEntryDetail(value, book.ledgerBookId, journalEntryId))
      .then(value => { if (!abort.signal.aborted) setDetail(value); })
      .catch(error => {
        if (abort.signal.aborted) return;
        if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
        const hint = financeErrorTarget(error)?.message;
        setFailure(hint ?? '分录详情暂时无法加载');
        if (!hint) notify(error, '分录详情加载失败');
      });
    return () => abort.abort();
    // 回调由上层每次渲染传入，只在分录变化时重新加载。
  }, [book.ledgerBookId, journalEntryId, notify]);
  const totals = detail ? journalTotals(detail.lines) : null;
  const amount = (value: string) => <Amount value={value} minorUnit={minorUnit} />;
  return <Drawer open size={640} rootClassName="finance-drawer" onClose={onClose} destroyOnHidden
    title={`分录详情${detail ? ` · 序号 ${detail.journalSequence}` : ''}`}>
    {failure ? <Alert type="warning" showIcon title={failure} /> : !detail || !totals ? <Spin /> : <>
      <Descriptions column={1} size="small" className="finance-detail-head" items={[
        { key: 'id', label: '分录主键', children: <span className="mono">{detail.journalEntryId}</span> },
        { key: 'book', label: '账簿', children: `${bookLabel}（${detail.ledgerBookId}）` },
        { key: 'period', label: '会计期间', children: <span className="mono">{detail.accountingPeriodId}</span> },
        { key: 'date', label: '过账日期', children: detail.postingDate },
        { key: 'type', label: '分录类型', children: `${detail.entryType}（${entryTypeLabels[detail.entryType]}）` },
        { key: 'reversal', label: '冲回原分录', children: detail.reversalOfEntryId ? <span className="mono">{detail.reversalOfEntryId}</span> : '—' },
      ]} />
      <h3 className="finance-subtitle">指令来源</h3>
      <Descriptions column={1} size="small" items={[
        { key: 'instruction', label: '指令主键', children: <span className="mono">{detail.instructionId}</span> },
        { key: 'instructionType', label: '指令类型', children: detail.instructionType },
        { key: 'origin', label: '来源', children: [detail.originType, detail.sourceSystem].filter(Boolean).join(' · ') },
        { key: 'event', label: '来源事件键', children: detail.sourceEventKey
          ? `${detail.sourceEventKey}${detail.originType === 'ACCOUNT_POSTING' ? '（account 批次 ID）' : ''}` : '—' },
        ...(detail.accountPostingId ? [{ key: 'posting', label: 'account 批次', children: <span>
          <span className="mono">{detail.accountPostingId}</span> · 序号 {detail.accountPostingSequence} · {bookLabel}
          · {detail.lines.filter(line => line.accountEntryId).length} 行来自 account 流水</span> }] : []),
        { key: 'adjustment', label: '人工调整', children: detail.adjustmentNo
          ? `${detail.adjustmentNo}${detail.manualAdjustmentId ? `（${detail.manualAdjustmentId}）` : ''}` : '—' },
      ]} />
      <section className="table-panel finance-entry-lines">
        <Table<JournalEntryLine> rowKey="lineNo" size="small" tableLayout="fixed" dataSource={detail.lines} pagination={false} scroll={{ x: 600 }} columns={[
          { title: '行号', dataIndex: 'lineNo', width: 56 },
          { title: '账户号 / 科目', render: (_, row) => <div><div className="mono">{row.accountNo}</div>
            <div className="cell-meta">{row.accountCode} · {row.accountName}</div></div> },
          { title: '核算对象', width: 170, render: (_, row) => <div><div>{row.ownerType}</div><div className="cell-meta">{row.ownerId}</div>
            {row.accountEntryId && detail.accountPostingId && <Link className="finance-entry-link" to={subledgerPostingLink(detail.accountPostingId, row.accountEntryId)}>
              流水 …{row.accountEntryId.slice(-4)} ↗</Link>}</div> },
          { title: '余额桶', dataIndex: 'balanceBucket', width: 96 },
          { title: '方向', width: 56, render: (_, row) => sideLabels[row.entrySide] },
          { title: '金额', width: 120, align: 'right', render: (_, row) => amount(row.amount) },
        ]} />
      </section>
      <div className="finance-entry-totals">
        <span>借方合计 {amount(totals.debit)}</span><span>贷方合计 {amount(totals.credit)}</span>
        <Tag color={totals.balanced ? 'green' : 'red'}>{totals.balanced ? '借贷平衡' : '借贷不平衡'}</Tag>
      </div>
    </>}
  </Drawer>;
}
