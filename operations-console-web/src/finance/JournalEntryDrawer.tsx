import { useEffect, useState } from 'react';
import { Alert, Descriptions, Drawer, Spin, Table, Tag } from 'antd';
import { Link } from 'react-router-dom';
import { ApiError, session } from '../api/session';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import { decodeJournalEntryDetail, financeErrorTarget, financePaths, journalTotals, subledgerPostingLink } from './finance';
import type { BookRef, JournalEntryDetail, JournalEntryLine } from './finance';
import { Amount } from './FinanceParts';

/**
 * 分录详情抽屉：分录头、指令来源（自动来源事件键、account 批次或人工调整单号）、全部分录行与借贷合计
 * <li>ACCOUNT_POSTING 来源的控制科目行带 account 流水主键，「流水 …后 4 位 ↗」跳到明细账批次抽屉并高亮该流水。</li>
 */
export function JournalEntryDrawer({ book, bookLabel, journalEntryId, minorUnit, onClose, onAuthorizationChanged }: {
  book: BookRef; bookLabel: string; journalEntryId: string; minorUnit?: number; onClose: () => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('finance');
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
        setFailure(hint ?? t('journal.unavailable'));
        if (!hint) notify(error, t('journal.loadFailed'));
      });
    return () => abort.abort();
    // 回调由上层每次渲染传入，只在分录变化时重新加载。
  // t 随语言包刷新换新，只用于失败文案，不应触发重新加载
  }, [book.ledgerBookId, journalEntryId, notify]);
  const totals = detail ? journalTotals(detail.lines) : null;
  const amount = (value: string) => <Amount value={value} minorUnit={minorUnit} />;
  return <Drawer open size={640} rootClassName="finance-drawer" onClose={onClose} destroyOnHidden
    title={detail ? t('journal.titleWithSequence', { sequence: detail.journalSequence }) : t('journal.title')}>
    {failure ? <Alert type="warning" showIcon title={failure} /> : !detail || !totals ? <Spin /> : <>
      <Descriptions column={1} size="small" className="finance-detail-head" items={[
        { key: 'id', label: t('journal.field.journalEntryId'), children: <span className="mono">{detail.journalEntryId}</span> },
        { key: 'book', label: t('field.book'), children: t('common.labelWithId', { label: bookLabel, id: detail.ledgerBookId }) },
        { key: 'period', label: t('field.accountingPeriod'), children: <span className="mono">{detail.accountingPeriodId}</span> },
        { key: 'date', label: t('field.postingDate'), children: detail.postingDate },
        { key: 'type', label: t('field.entryType'), children: t('common.codeWithLabel', { code: detail.entryType, label: enumLabel(t, 'enum.entryType', detail.entryType) }) },
        { key: 'reversal', label: t('journal.field.reversalOf'), children: detail.reversalOfEntryId ? <span className="mono">{detail.reversalOfEntryId}</span> : '—' },
      ]} />
      <h3 className="finance-subtitle">{t('journal.instructionSource')}</h3>
      <Descriptions column={1} size="small" items={[
        { key: 'instruction', label: t('journal.field.instructionId'), children: <span className="mono">{detail.instructionId}</span> },
        { key: 'instructionType', label: t('journal.field.instructionType'), children: detail.instructionType },
        { key: 'origin', label: t('subledger.entries.column.source'), children: [detail.originType, detail.sourceSystem].filter(Boolean).join(' · ') },
        { key: 'event', label: t('journal.field.sourceEventKey'), children: detail.sourceEventKey
          ? (detail.originType === 'ACCOUNT_POSTING' ? t('journal.sourceEventKeyPosting', { key: detail.sourceEventKey }) : detail.sourceEventKey) : '—' },
        ...(detail.accountPostingId ? [{ key: 'posting', label: t('journal.field.accountPosting'), children: <span>
          <span className="mono">{detail.accountPostingId}</span> · {t('journal.accountPostingMeta', { sequence: String(detail.accountPostingSequence), book: bookLabel,
            count: detail.lines.filter(line => line.accountEntryId).length })}</span> }] : []),
        { key: 'adjustment', label: t('journal.field.manualAdjustment'), children: detail.adjustmentNo
          ? (detail.manualAdjustmentId ? t('common.labelWithId', { label: detail.adjustmentNo, id: detail.manualAdjustmentId }) : detail.adjustmentNo) : '—' },
      ]} />
      <section className="table-panel finance-entry-lines">
        <Table<JournalEntryLine> rowKey="lineNo" size="small" tableLayout="fixed" dataSource={detail.lines} pagination={false} scroll={{ x: 600 }} columns={[
          { title: t('field.lineNo'), dataIndex: 'lineNo', width: 56 },
          { title: t('field.accountNoAndCode'), render: (_, row) => <div><div className="mono">{row.accountNo}</div>
            <div className="cell-meta">{row.accountCode} · {row.accountName}</div></div> },
          { title: t('field.accountingDimension'), width: 170, render: (_, row) => <div><div>{row.ownerType}</div><div className="cell-meta">{row.ownerId}</div>
            {row.accountEntryId && detail.accountPostingId && <Link className="finance-entry-link" to={subledgerPostingLink(detail.accountPostingId, row.accountEntryId)}>
              {t('journal.entryLink', { entry: row.accountEntryId.slice(-4) })}</Link>}</div> },
          { title: t('field.balanceBucket'), dataIndex: 'balanceBucket', width: 96 },
          { title: t('field.side'), width: 56, render: (_, row) => enumLabel(t, 'enum.side', row.entrySide) },
          { title: t('field.amount'), width: 120, align: 'right', render: (_, row) => amount(row.amount) },
        ]} />
      </section>
      <div className="finance-entry-totals">
        <span>{t('field.debitTotal')} {amount(totals.debit)}</span><span>{t('field.creditTotal')} {amount(totals.credit)}</span>
        <Tag color={totals.balanced ? 'green' : 'red'}>{totals.balanced ? t('journal.balanced') : t('journal.unbalanced')}</Tag>
      </div>
    </>}
  </Drawer>;
}
