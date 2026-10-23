import { useMemo } from 'react';
import { App, Button, Form, Input, Popconfirm, Select, Table } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { localTime } from '../finance/finance';
import { FinanceNote, LoadFailure } from '../finance/FinanceParts';
import { useT } from '../i18n/react';
import { decodeWithId, directionEnum, hasReconAction, pageOf, platformTxnStatusEnum, reconActions, reconLabel, reconOptions, reconPaths } from '../reconciliation/reconModel';
import type { PlatformTxn, ReconException } from '../reconciliation/reconModel';
import { EmptyHint, Money, ReconCommandFeedback, ReconTag } from '../reconciliation/ReconParts';
import { channelLinesOfTxn, compactFilters, reconRoutes } from '../reconciliation/reconRoutes';
import { useClearingAccounts } from '../reconciliation/useReconData';
import { useReconCommand } from '../reconciliation/useReconCommand';
import { resetForm, useReconListQuery } from '../reconciliation/useReconListQuery';
import '../configuration/configuration.css';
import '../finance/finance.css';
import '../reconciliation/recon.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }

const decodePage = pageOf(decodeWithId<PlatformTxn>('platformTxnId'));
const filterKeys = ['clearingAccountId', 'status', 'direction', 'externalTransactionId', 'businessDateFrom', 'businessDateTo'] as const;

/**
 * 平台流水（UC-R05、R17）：由 billing PAYMENT / REFUND 事实生成的待清算应收 / 应付；查询与人工开异常（D-02）
 * <li>筛选与页码存于 URL 查询串（账单行、异常等页面可带筛选链接过来）；已匹配的流水可跳到渠道流水页查看对应账单行。</li>
 * <li>人工开异常只给来源对象，异常类型（PLATFORM_ITEM_UNSETTLED）与差异金额由对账服务按未完成金额算出；开立后去「对账异常」处置。</li>
 */
export function ReconPlatformTxns({ resources, onAuthorizationChanged }: Props) {
  const t = useT('recon'), tNav = useT('nav');
  const { message } = App.useApp();
  const navigate = useNavigate();
  const statusOptions = useMemo(() => reconOptions(t, platformTxnStatusEnum), [t]);
  const canOperate = hasReconAction(resources, reconActions.operate);
  const clearing = useClearingAccounts(onAuthorizationChanged);
  const page = useReconListQuery<PlatformTxn>(reconPaths.platformTxns, decodePage, filterKeys);
  const command = useReconCommand(onAuthorizationChanged);
  const [form] = Form.useForm<Record<string, string | undefined>>();
  async function openException(row: PlatformTxn) {
    const result = await command.run(reconPaths.exceptions, 'POST', { sourceType: 'PLATFORM_TXN', sourceId: row.platformTxnId },
      decodeWithId<ReconException>('exceptionId'), t('bankLine.openExceptionFailed'));
    if (!result) return;
    void message.success(t('platformTxns.exceptionOpened', { amount: result.differenceAmount, currency: result.currency }));
    page.refresh();
    void navigate(reconRoutes.exception(result.exceptionId));
  }
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>{tNav('resource.OPERATIONS_FINANCE_RECON_PLATFORM_TXNS')}</h1></div>
    <ReconCommandFeedback command={command} onReload={() => { command.clearConflict(); page.refresh(); }} />
    <Form form={form} name="recon-platform-filters" layout="vertical" initialValues={page.filters} onFinish={values => page.applyFilters(compactFilters(values))}>
      <div className="finance-search">
        <Form.Item name="clearingAccountId" label={t('field.clearingAccount')}><Select allowClear showSearch={{ optionFilterProp: 'label' }} placeholder={t('placeholder.all')} loading={clearing.loading}
          options={clearing.items.map(item => ({ value: item.clearingAccountId, label: item.clearingAccountCode }))} /></Form.Item>
        <Form.Item name="status" label={t('field.status')}><Select allowClear placeholder={t('placeholder.all')} options={statusOptions} /></Form.Item>
        <Form.Item name="direction" label={t('field.direction')}><Select allowClear placeholder={t('placeholder.all')}
          options={['RECEIVABLE', 'PAYABLE'].map(value => ({ value, label: reconLabel(t, directionEnum, value) }))} /></Form.Item>
        <Form.Item name="externalTransactionId" label={t('field.externalTransactionId')}><Input allowClear maxLength={128} /></Form.Item>
        <Form.Item name="businessDateFrom" label={t('platformTxns.businessDateFrom')} rules={[{ pattern: /^\d{4}-\d{2}-\d{2}$/, message: 'YYYY-MM-DD' }]}><Input allowClear placeholder="YYYY-MM-DD" /></Form.Item>
        <Form.Item name="businessDateTo" label={t('platformTxns.businessDateTo')} rules={[{ pattern: /^\d{4}-\d{2}-\d{2}$/, message: 'YYYY-MM-DD' }]}><Input allowClear placeholder="YYYY-MM-DD" /></Form.Item>
        <div className="configuration-actions"><Button htmlType="submit">{t('common.search')}</Button>
          <Button onClick={() => { resetForm(form); page.applyFilters({}); }}>{t('common.reset')}</Button></div>
      </div>
    </Form>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<PlatformTxn> rowKey="platformTxnId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1390 }}
        pagination={{ ...page.pagination, showTotal: total => t('common.total', { total }) }}
        locale={{ emptyText: <EmptyHint text={t('platformTxns.empty')} /> }}
        columns={[
          { title: t('field.externalTransactionId'), width: 200, render: (_, row) => <span className="mono">{row.externalTransactionId}<div className="cell-meta">{row.referenceType}
            {!/^0(\.0+)?$/.test(row.matchedAmount) && <> · <Link to={channelLinesOfTxn(row)}>{t('link.channelLines')}</Link></>}</div></span> },
          { title: t('field.clearingAccount'), width: 150, render: (_, row) => <Link to={reconRoutes.clearingAccount(row.clearingAccountId)}>{clearing.byId.get(row.clearingAccountId)?.clearingAccountCode ?? row.clearingAccountId}</Link> },
          { title: t('field.direction'), width: 110, render: (_, row) => reconLabel(t, directionEnum, row.direction) },
          { title: t('field.source'), width: 90, dataIndex: 'txnSource' },
          { title: t('platformTxns.expectedAmount'), width: 120, align: 'right', render: (_, row) => <><Money value={row.expectedAmount} /> {row.currency}</> },
          { title: t('field.matchedAmount'), width: 110, align: 'right', render: (_, row) => <Money value={row.matchedAmount} /> },
          { title: t('platformTxns.fees'), width: 140, align: 'right', render: (_, row) => <><Money value={row.expectedFee} /> / <Money value={row.actualFee} /></> },
          { title: t('platformTxns.settledAmount'), width: 110, align: 'right', render: (_, row) => <Money value={row.settledAmount} /> },
          { title: t('field.status'), width: 100, render: (_, row) => <ReconTag status={row.status} kind={platformTxnStatusEnum} /> },
          { title: t('field.businessTime'), width: 150, render: (_, row) => localTime(row.businessTime) },
          ...(canOperate ? [{ title: '', width: 110, fixed: 'right' as const, render: (_: unknown, row: PlatformTxn) => row.status === 'SETTLED' ? null
            : <Popconfirm title={t('platformTxns.confirmOpenException')} okText={t('bankLine.open')} cancelText={t('common.cancel')}
              onConfirm={() => void openException(row)}><Button type="link" disabled={command.saving}>{t('bankLine.openException')}</Button></Popconfirm> }] : []),
        ]} />
    </section>}
    <FinanceNote>{t('platformTxns.note')}{!canOperate && ' ' + t('permission.noOperate')}</FinanceNote>
  </div>;
}
