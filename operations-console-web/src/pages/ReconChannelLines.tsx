import { Alert, Button, Form, Input, Select, Table } from 'antd';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { FinanceNote, LoadFailure } from '../finance/FinanceParts';
import { useT } from '../i18n/react';
import { useOpenDetail } from '../reconciliation/ReconDetail';
import {
  channelLineMatchStatusEnum, decodeChannelLine, directionEnum, lineTypeEnum, pageOf, reconLabel, reconOptions, reconPaths, statementStatusEnum,
} from '../reconciliation/reconModel';
import type { ChannelLineView } from '../reconciliation/reconModel';
import { EmptyHint, Money, ReconTag } from '../reconciliation/ReconParts';
import { channelLineFilterKeys, channelLinePageSizes, channelLineQuery, platformTxnsOfLine, reconRoutes } from '../reconciliation/reconRoutes';
import { useClearingAccounts } from '../reconciliation/useReconData';
import { resetForm, useReconListQuery } from '../reconciliation/useReconListQuery';
import '../configuration/configuration.css';
import '../finance/finance.css';
import '../reconciliation/recon.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }

const decodePage = pageOf(decodeChannelLine);
const datePattern = { pattern: /^\d{4}-\d{2}-\d{2}$/, message: 'YYYY-MM-DD' };

/**
 * 渠道流水（AC-05）：跨渠道账单列出账单行，按交易日期倒序分页；点行进入所属渠道账单详情并高亮该行
 * <li>账单状态不选时由 reconciliation 排除已作废、已拒绝、接收中的账单；外部交易号、交易号类型为精确查询。</li>
 * <li>筛选与页码存于 URL 查询串；每页最多 100 条（接口上限）。只读页面，不需要按钮资源。</li>
 */
export function ReconChannelLines({ onAuthorizationChanged }: Props) {
  const t = useT('recon'), tNav = useT('nav');
  const openDetail = useOpenDetail();
  const clearing = useClearingAccounts(onAuthorizationChanged);
  const page = useReconListQuery<ChannelLineView>(reconPaths.channelLines, decodePage, channelLineFilterKeys, { sizes: channelLinePageSizes });
  const [form] = Form.useForm<Record<string, string | undefined>>();
  const [error, setError] = useState<string | null>(null);
  function search(values: Record<string, string | undefined>) {
    const query = channelLineQuery(values);
    setError(query.error);
    if (!query.error) page.applyFilters(query.filters);
  }
  const filtered = Object.keys(page.activeFilters).length > 0;
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>{tNav('resource.OPERATIONS_FINANCE_RECON_CHANNEL_LINES')}</h1></div>
    <Form form={form} name="recon-channel-line-filters" layout="vertical" initialValues={page.filters} onFinish={search}>
      <div className="finance-search">
        <Form.Item name="clearingAccountId" label={t('field.clearingAccount')}><Select allowClear showSearch={{ optionFilterProp: 'label' }} placeholder={t('placeholder.all')} loading={clearing.loading}
          options={clearing.items.map(item => ({ value: item.clearingAccountId, label: item.clearingAccountCode }))} /></Form.Item>
        <Form.Item name="statementStatus" label={t('channelLines.statementStatus')} extra={t('channelLines.statementStatusHint')}><Select allowClear placeholder={t('channelLines.validStatements')}
          options={reconOptions(t, statementStatusEnum)} /></Form.Item>
        <Form.Item name="matchStatus" label={t('field.matchStatus')}><Select allowClear placeholder={t('placeholder.all')} options={reconOptions(t, channelLineMatchStatusEnum)} /></Form.Item>
        <Form.Item name="lineType" label={t('field.lineType')}><Select allowClear placeholder={t('placeholder.all')} options={reconOptions(t, lineTypeEnum)} /></Form.Item>
        <Form.Item name="referenceType" label={t('field.referenceType')} rules={[{ pattern: /^[A-Z][A-Z0-9_]{0,31}$/, message: t('channelLines.referenceTypePattern') }]}>
          <Input allowClear maxLength={32} placeholder="NI_ORDER_REF" /></Form.Item>
        <Form.Item name="externalTransactionId" label={t('field.externalTransactionId')} extra={t('placeholder.exactMatch')} rules={[{ pattern: /^[\x21-\x7E]{1,128}$/, message: t('channelLines.visibleAscii') }]}>
          <Input allowClear maxLength={128} /></Form.Item>
        <Form.Item name="transactionDateFrom" label={t('channelLines.transactionDateFrom')} rules={[datePattern]}><Input allowClear placeholder="YYYY-MM-DD" /></Form.Item>
        <Form.Item name="transactionDateTo" label={t('channelLines.transactionDateTo')} rules={[datePattern]}><Input allowClear placeholder="YYYY-MM-DD" /></Form.Item>
        <div className="configuration-actions"><Button htmlType="submit">{t('common.search')}</Button>
          <Button onClick={() => { resetForm(form); setError(null); page.applyFilters({}); }}>{t('common.reset')}</Button></div>
      </div>
    </Form>
    {error && <Alert type="warning" showIcon className="finance-banner" title={error} />}
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<ChannelLineView> rowKey="lineId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1500 }}
        pagination={{ ...page.pagination, showTotal: total => t('channelLines.total', { total }) }}
        onRow={row => ({ onClick: () => openDetail(reconRoutes.statement(row.statementId, row.lineNo)), className: 'clickable-row' })}
        locale={{ emptyText: <EmptyHint text={filtered ? t('channelLines.emptyFiltered') : t('channelLines.empty')} /> }}
        columns={[
          { title: t('field.transactionDate'), width: 110, render: (_, row) => row.transactionDate ?? <span className="muted">—</span> },
          { title: t('field.clearingAccount'), width: 160, render: (_, row) => <span className="mono">{row.clearingAccountCode}</span> },
          { title: t('link.statements'), width: 210, render: (_, row) => <span><Link className="mono" to={reconRoutes.statement(row.statementId, row.lineNo)}
            onClick={event => event.stopPropagation()}>{row.channelBatchRef}{row.revision > 1 ? ` · v${row.revision}` : ''}</Link>
            <div className="cell-meta"><ReconTag status={row.statementStatus} kind={statementStatusEnum} /> {t('channelLines.settledOn', { date: row.settlementDate })}</div></span> },
          { title: t('field.lineNo'), width: 70, align: 'right', dataIndex: 'lineNo' },
          { title: t('field.type'), width: 110, render: (_, row) => reconLabel(t, lineTypeEnum, row.lineType) },
          { title: t('field.direction'), width: 90, render: (_, row) => reconLabel(t, directionEnum, row.direction) },
          { title: t('field.amount'), width: 130, align: 'right', render: (_, row) => <><Money value={row.amount} /> {row.currency ?? ''}</> },
          { title: t('field.lineFee'), width: 100, align: 'right', render: (_, row) => <Money value={row.feeAmount} /> },
          { title: t('field.externalTransactionId'), width: 200, render: (_, row) => row.externalTransactionId
            ? <span className="mono">{row.externalTransactionId}<div className="cell-meta">{row.referenceType}</div></span> : <span className="muted">—</span> },
          { title: t('link.platformTxns'), width: 190, render: (_, row) => row.platformTxnId
            ? <Link className="mono" to={platformTxnsOfLine(row)} onClick={event => event.stopPropagation()}>{row.platformTxnId}
              <div className="cell-meta">{row.matchMethod === 'MANUAL' ? t('match.manual') : t('match.auto')}</div></Link>
            : row.resolutionId ? <span className="cell-meta">{t('statementDetail.classified', { resolutionId: row.resolutionId })}</span> : <span className="muted">—</span> },
          { title: t('field.actualFee'), width: 110, align: 'right', render: (_, row) => <Money value={row.actualFee} /> },
          { title: t('field.matchStatus'), width: 100, render: (_, row) => <ReconTag status={row.matchStatus} kind={channelLineMatchStatusEnum} /> },
        ]} />
    </section>}
    <FinanceNote>{t('channelLines.note')}</FinanceNote>
  </div>;
}
