import { useState } from 'react';
import { App, Button, Form, Input, Popconfirm, Select, Table, Tabs } from 'antd';
import { Link, Navigate, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { localTime, replaceQuery } from '../finance/finance';
import { FinanceNote, LoadFailure } from '../finance/FinanceParts';
import { useT } from '../i18n/react';
import { BankLineDetail } from '../reconciliation/detail/BankLineDetail';
import { BankStatementDetail } from '../reconciliation/detail/BankStatementDetail';
import { ImportDrawer } from '../reconciliation/ImportDrawer';
import { useOpenDetail } from '../reconciliation/ReconDetail';
import {
  bankStatementStatusEnum, decodeWithId, directionEnum, lineMatchStatusEnum, movementStatusEnum, pageOf, reconActions, reconLabel, reconOptions, reconPaths, hasReconAction,
} from '../reconciliation/reconModel';
import type { BankStatement, BankStatementLine, CashAccount, ExpectedMovement, ReconException } from '../reconciliation/reconModel';
import { EmptyHint, Money, ReconCommandFeedback, ReconTag } from '../reconciliation/ReconParts';
import { compactFilters, isReconId, movementSourceLink, reconRoutes } from '../reconciliation/reconRoutes';
import { cashAccountLabel, useCashAccounts } from '../reconciliation/useReconData';
import { useReconCommand } from '../reconciliation/useReconCommand';
import { resetForm, useReconListQuery } from '../reconciliation/useReconListQuery';
import '../configuration/configuration.css';
import '../finance/finance.css';
import '../reconciliation/recon.css';

interface Props { resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }
type BankTab = 'lines' | 'statements' | 'movements';

const statementPage = pageOf(decodeWithId<BankStatement>('bankStatementId'));
const linePage = pageOf(decodeWithId<BankStatementLine>('bankStatementLineId'));
const movementPage = pageOf(decodeWithId<ExpectedMovement>('expectedMovementId'));

const lineKeys = ['matchStatus', 'direction', 'valueDateFrom', 'valueDateTo'] as const;
const lineDefaults = { matchStatus: 'UNMATCHED' };
const movementKeys = ['status'] as const;
/** 切换银行账户或页签时清掉各页签的筛选与页码。 */
const tabQueryReset = Object.fromEntries([...lineKeys, ...movementKeys, 'page', 'size'].map(key => [key, null])) as Record<string, null>;

/**
 * 银行对账（UC-R11–R13）：先选银行账户，再看银行流水、上传对账单、预期银行流水；
 * 点流水进入银行流水详情页（/bank/lines/:bankStatementLineId）处理匹配与开异常，点对账单进入对账单详情页（/bank/statements/:bankStatementId）
 * <li>银行账户、页签、页签内筛选与页码存于 URL；旧链接 ?line= 转到流水详情页。写操作按「对账操作」按钮资源显示。</li>
 */
export function ReconBank(props: Props) {
  const { bankStatementId, bankStatementLineId } = useParams();
  const [params] = useSearchParams();
  const legacy = params.get('line');
  if (bankStatementId !== undefined) return <BankStatementDetail key={bankStatementId} bankStatementId={bankStatementId} {...props} />;
  if (bankStatementLineId !== undefined) return <BankLineDetail key={bankStatementLineId} bankStatementLineId={bankStatementLineId} {...props} />;
  if (isReconId(legacy ?? undefined)) return <Navigate replace to={reconRoutes.bankLine(legacy!)} />;
  return <BankOverview {...props} />;
}

function BankOverview({ resources, onAuthorizationChanged }: Props) {
  const t = useT('recon'), tNav = useT('nav');
  const [params, setParams] = useSearchParams();
  const canOperate = hasReconAction(resources, reconActions.operate);
  const cash = useCashAccounts(onAuthorizationChanged);
  const accountId = params.get('account');
  const account = accountId ? cash.byId.get(accountId) : undefined;
  const tab: BankTab = params.get('tab') === 'statements' ? 'statements' : params.get('tab') === 'movements' ? 'movements' : 'lines';
  const go = (updates: Record<string, string | null>) => setParams(new URLSearchParams(replaceQuery(params.toString(), updates)));
  const selectable = cash.items.filter(item => item.status === 'ACTIVE' || item.status === 'SUSPENDED');
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>{tNav('resource.OPERATIONS_FINANCE_RECON_BANK')}</h1></div>
    <Form layout="vertical" name="recon-bank-account"><div className="finance-trial-head">
      <Form.Item label={t('field.cashAccount')}><Select style={{ minWidth: 420 }} showSearch={{ optionFilterProp: 'label' }} placeholder={t('clearingSettings.selectCashAccount')} loading={cash.loading}
        value={account?.cashAccountId} onChange={value => go({ ...tabQueryReset, account: value })}
        options={selectable.map(item => ({ value: item.cashAccountId, label: cashAccountLabel(item) }))} /></Form.Item>
    </div></Form>
    {cash.error ? <LoadFailure message={cash.error} onRetry={cash.reload} />
      : !account ? <div className="finance-empty"><EmptyHint text={selectable.length ? t('bank.selectAccount') : t('bank.noAccount')} /></div>
      : <Tabs activeKey={tab} onChange={next => go({ ...tabQueryReset, tab: next === 'lines' ? null : next })} destroyOnHidden items={[
        { key: 'lines', label: t('link.bankLines'), children: <LinesTab key={account.cashAccountId} account={account} /> },
        { key: 'statements', label: t('bank.tab.statements'), children: <StatementsTab key={account.cashAccountId} account={account} canOperate={canOperate}
          onAuthorizationChanged={onAuthorizationChanged} /> },
        { key: 'movements', label: t('link.expectedMovements'), children: <MovementsTab key={account.cashAccountId} account={account} canOperate={canOperate}
          onAuthorizationChanged={onAuthorizationChanged} /> },
      ]} />}
    <FinanceNote>{t('bankLine.note')}{!canOperate && ' ' + t('permission.noOperate')}</FinanceNote>
  </div>;
}

function StatementsTab({ account, canOperate, onAuthorizationChanged }: { account: CashAccount; canOperate: boolean; onAuthorizationChanged: () => void }) {
  const t = useT('recon');
  const openDetail = useOpenDetail();
  const [uploading, setUploading] = useState(false);
  const page = useReconListQuery<BankStatement>(reconPaths.bankStatements, statementPage, [], { fixed: { cashAccountId: account.cashAccountId } });
  return <>
    {canOperate && <div className="finance-search-inline"><Button type="primary" disabled={account.status !== 'ACTIVE'} onClick={() => setUploading(true)}>{t('bank.uploadStatement')}</Button></div>}
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<BankStatement> rowKey="bankStatementId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1100 }}
        pagination={{ ...page.pagination, showTotal: total => t('common.total', { total }) }}
        onRow={row => ({ onClick: () => openDetail(reconRoutes.bankStatement(row.bankStatementId)), className: 'clickable-row' })}
        locale={{ emptyText: <EmptyHint text={t('bank.statementsEmpty')} /> }} columns={[
          { title: t('bank.column.statementRef'), width: 180, render: (_, row) => <a className="mono">{row.statementRef}</a> },
          { title: t('field.period'), width: 200, render: (_, row) => `${row.periodStart} — ${row.periodEnd}` },
          { title: t('bank.column.opening'), width: 120, align: 'right', render: (_, row) => <Money value={row.openingBalance} /> },
          { title: t('bank.column.closing'), width: 120, align: 'right', render: (_, row) => <Money value={row.closingBalance} /> },
          { title: t('bank.column.lines'), width: 130, align: 'right', render: (_, row) => t('common.labelWithId', { label: String(row.importedLineCount), id: String(row.duplicateLineCount) }) },
          { title: t('field.status'), width: 100, render: (_, row) => <>{<ReconTag status={row.status} kind={bankStatementStatusEnum} />}{row.rejectReason && <div className="cell-meta">{row.rejectReason}</div>}</> },
          { title: t('field.uploadedAt'), width: 150, render: (_, row) => localTime(row.createTime) },
        ]} />
    </section>}
    {uploading && <ImportDrawer kind="bank" cashAccount={account} onClose={() => setUploading(false)} onImported={page.refresh} onAuthorizationChanged={onAuthorizationChanged} />}
  </>;
}

function LinesTab({ account }: { account: CashAccount }) {
  const t = useT('recon');
  const openDetail = useOpenDetail();
  const page = useReconListQuery<BankStatementLine>(reconPaths.bankLines, linePage, lineKeys, { defaults: lineDefaults, fixed: { cashAccountId: account.cashAccountId } });
  const [form] = Form.useForm<Record<string, string | undefined>>();
  return <>
    <Form form={form} name="recon-bank-line-filters" layout="vertical" initialValues={page.filters} onFinish={values => page.applyFilters(compactFilters(values))}>
      <div className="finance-search">
        <Form.Item name="matchStatus" label={t('field.matchStatus')}><Select allowClear placeholder={t('placeholder.all')} options={reconOptions(t, lineMatchStatusEnum)} /></Form.Item>
        <Form.Item name="direction" label={t('field.direction')}><Select allowClear placeholder={t('placeholder.all')}
          options={['CREDIT', 'DEBIT'].map(value => ({ value, label: reconLabel(t, directionEnum, value) }))} /></Form.Item>
        <Form.Item name="valueDateFrom" label={t('bank.valueDateFrom')} rules={[{ pattern: /^\d{4}-\d{2}-\d{2}$/, message: 'YYYY-MM-DD' }]}><Input allowClear placeholder="YYYY-MM-DD" /></Form.Item>
        <Form.Item name="valueDateTo" label={t('bank.valueDateTo')} rules={[{ pattern: /^\d{4}-\d{2}-\d{2}$/, message: 'YYYY-MM-DD' }]}><Input allowClear placeholder="YYYY-MM-DD" /></Form.Item>
        <div className="configuration-actions"><Button htmlType="submit">{t('common.search')}</Button>
          <Button onClick={() => { resetForm(form, lineDefaults); page.applyFilters(lineDefaults); }}>{t('common.reset')}</Button></div>
      </div>
    </Form>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<BankStatementLine> rowKey="bankStatementLineId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1100 }}
        pagination={{ ...page.pagination, showTotal: total => t('common.total', { total }) }} onRow={row => ({ onClick: () => openDetail(reconRoutes.bankLine(row.bankStatementLineId)), className: 'clickable-row' })}
        locale={{ emptyText: <EmptyHint text={t('bank.linesEmpty')} /> }} columns={[
          { title: t('field.bankTransactionRef'), width: 180, render: (_, row) => <a className="mono">{row.bankTransactionRef}</a> },
          { title: t('field.valueDate'), width: 110, dataIndex: 'valueDate' },
          { title: t('field.direction'), width: 90, render: (_, row) => reconLabel(t, directionEnum, row.direction) },
          { title: t('field.amount'), width: 120, align: 'right', render: (_, row) => <><Money value={row.amount} /> {row.currency}</> },
          { title: t('field.matchedAmount'), width: 110, align: 'right', render: (_, row) => <Money value={row.matchedAmount} /> },
          { title: t('field.counterpartyReference'), render: (_, row) => <>{row.counterpartyName ?? '—'}<div className="cell-meta">{row.referenceText ?? ''}</div></> },
          { title: t('field.status'), width: 100, render: (_, row) => <ReconTag status={row.matchStatus} kind={lineMatchStatusEnum} /> },
        ]} />
    </section>}
  </>;
}

function MovementsTab({ account, canOperate, onAuthorizationChanged }: { account: CashAccount; canOperate: boolean; onAuthorizationChanged: () => void }) {
  const t = useT('recon');
  const { message } = App.useApp();
  const navigate = useNavigate();
  const command = useReconCommand(onAuthorizationChanged);
  const page = useReconListQuery<ExpectedMovement>(reconPaths.expectedMovements, movementPage, movementKeys, { fixed: { cashAccountId: account.cashAccountId } });
  async function openException(row: ExpectedMovement) {
    const result = await command.run(reconPaths.exceptions, 'POST', { sourceType: 'EXPECTED_BANK_MOVEMENT', sourceId: row.expectedMovementId },
      decodeWithId<ReconException>('exceptionId'), t('bankLine.openExceptionFailed'));
    if (!result) return;
    void message.success(t('bank.exceptionOpened', { amount: result.differenceAmount }));
    void navigate(reconRoutes.exception(result.exceptionId));
  }
  return <>
    <ReconCommandFeedback command={command} onReload={() => { command.clearConflict(); page.refresh(); }} />
    <div className="finance-search-inline"><Select allowClear placeholder={t('field.status')} style={{ width: 180 }} options={reconOptions(t, movementStatusEnum)}
      defaultValue={page.filters.status} onChange={value => page.applyFilters(value ? { status: value } : {})} /></div>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<ExpectedMovement> rowKey="expectedMovementId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1100 }}
        pagination={{ ...page.pagination, showTotal: total => t('common.total', { total }) }}
        locale={{ emptyText: <EmptyHint text={t('bank.movementsEmpty')} /> }} columns={[
          { title: t('field.source'), width: 200, render: (_, row) => { const link = movementSourceLink(row);
            return <span className="mono">{row.movementType}<div className="cell-meta">{link ? <Link to={link}>{row.sourceId}</Link> : row.sourceId}</div></span>; } },
          { title: t('field.direction'), width: 90, render: (_, row) => reconLabel(t, directionEnum, row.direction) },
          { title: t('bank.column.expected'), width: 120, align: 'right', render: (_, row) => <><Money value={row.expectedAmount} /> {row.currency}</> },
          { title: t('bank.column.confirmed'), width: 110, align: 'right', render: (_, row) => <Money value={row.confirmedAmount} /> },
          { title: t('bank.column.classified'), width: 110, align: 'right', render: (_, row) => <Money value={row.classifiedAmount} /> },
          { title: t('field.expectedDate'), width: 110, dataIndex: 'expectedDate' },
          { title: t('field.status'), width: 100, render: (_, row) => <ReconTag status={row.status} kind={movementStatusEnum} /> },
          ...(canOperate ? [{ title: '', width: 100, render: (_: unknown, row: ExpectedMovement) => ['CONFIRMED', 'CANCELLED', 'EXCEPTION'].includes(row.status) ? null
            : <Popconfirm title={t('bank.confirmOpenException')} okText={t('bankLine.open')} cancelText={t('common.cancel')}
              onConfirm={() => void openException(row)}><Button type="link" disabled={command.saving}>{t('bankLine.openException')}</Button></Popconfirm> }] : []),
        ]} />
    </section>}
  </>;
}

