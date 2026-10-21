import { Alert, Button, Form, Input, Select, Table } from 'antd';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { FinanceNote, LoadFailure } from '../finance/FinanceParts';
import { useOpenDetail } from '../reconciliation/ReconDetail';
import {
  channelLineMatchStatusLabels, decodeChannelLine, directionLabels, lineTypeLabels, pageOf, reconPaths, statementStatusLabels,
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
const options = (labels: Record<string, string>) => Object.entries(labels).map(([value, label]) => ({ value, label }));
const datePattern = { pattern: /^\d{4}-\d{2}-\d{2}$/, message: 'YYYY-MM-DD' };

/**
 * 渠道流水（AC-05）：跨渠道账单列出账单行，按交易日期倒序分页；点行进入所属渠道账单详情并高亮该行
 * <li>账单状态不选时由 reconciliation 排除已作废、已拒绝、接收中的账单；外部交易号、交易号类型为精确查询。</li>
 * <li>筛选与页码存于 URL 查询串；每页最多 100 条（接口上限）。只读页面，不需要按钮资源。</li>
 */
export function ReconChannelLines({ onAuthorizationChanged }: Props) {
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
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>渠道流水</h1></div>
    <Form form={form} name="recon-channel-line-filters" layout="vertical" initialValues={page.filters} onFinish={search}>
      <div className="finance-search">
        <Form.Item name="clearingAccountId" label="清算账户"><Select allowClear showSearch={{ optionFilterProp: 'label' }} placeholder="全部" loading={clearing.loading}
          options={clearing.items.map(item => ({ value: item.clearingAccountId, label: item.clearingAccountCode }))} /></Form.Item>
        <Form.Item name="statementStatus" label="账单状态" extra="不选时不含已作废、已拒绝、接收中的账单"><Select allowClear placeholder="有效账单"
          options={options(statementStatusLabels)} /></Form.Item>
        <Form.Item name="matchStatus" label="匹配状态"><Select allowClear placeholder="全部" options={options(channelLineMatchStatusLabels)} /></Form.Item>
        <Form.Item name="lineType" label="行类型"><Select allowClear placeholder="全部" options={options(lineTypeLabels)} /></Form.Item>
        <Form.Item name="referenceType" label="交易号类型" rules={[{ pattern: /^[A-Z][A-Z0-9_]{0,31}$/, message: '大写字母、数字、下划线' }]}>
          <Input allowClear maxLength={32} placeholder="NI_ORDER_REF" /></Form.Item>
        <Form.Item name="externalTransactionId" label="外部交易号" extra="精确匹配" rules={[{ pattern: /^[\x21-\x7E]{1,128}$/, message: '1–128 位可见 ASCII' }]}>
          <Input allowClear maxLength={128} /></Form.Item>
        <Form.Item name="transactionDateFrom" label="交易日起" rules={[datePattern]}><Input allowClear placeholder="YYYY-MM-DD" /></Form.Item>
        <Form.Item name="transactionDateTo" label="交易日止" rules={[datePattern]}><Input allowClear placeholder="YYYY-MM-DD" /></Form.Item>
        <div className="configuration-actions"><Button htmlType="submit">查询</Button>
          <Button onClick={() => { resetForm(form); setError(null); page.applyFilters({}); }}>重置</Button></div>
      </div>
    </Form>
    {error && <Alert type="warning" showIcon className="finance-banner" title={error} />}
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<ChannelLineView> rowKey="lineId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1500 }}
        pagination={{ ...page.pagination, showTotal: total => `共 ${total} 条，按交易日期从新到旧` }}
        onRow={row => ({ onClick: () => openDetail(reconRoutes.statement(row.statementId, row.lineNo)), className: 'clickable-row' })}
        locale={{ emptyText: <EmptyHint text={filtered ? '没有符合条件的渠道流水，可放宽筛选或选择账单状态查看已作废账单的行。'
          : '还没有渠道流水。上传渠道账单后，账单中的每一行都会出现在这里。'} /> }}
        columns={[
          { title: '交易日', width: 110, render: (_, row) => row.transactionDate ?? <span className="muted">—</span> },
          { title: '清算账户', width: 160, render: (_, row) => <span className="mono">{row.clearingAccountCode}</span> },
          { title: '渠道账单', width: 210, render: (_, row) => <span><Link className="mono" to={reconRoutes.statement(row.statementId, row.lineNo)}
            onClick={event => event.stopPropagation()}>{row.channelBatchRef}{row.revision > 1 ? ` · v${row.revision}` : ''}</Link>
            <div className="cell-meta"><ReconTag status={row.statementStatus} labels={statementStatusLabels} /> 结算 {row.settlementDate}</div></span> },
          { title: '行号', width: 70, align: 'right', dataIndex: 'lineNo' },
          { title: '类型', width: 80, render: (_, row) => lineTypeLabels[row.lineType] ?? row.lineType },
          { title: '方向', width: 90, render: (_, row) => directionLabels[row.direction] ?? row.direction },
          { title: '金额', width: 130, align: 'right', render: (_, row) => <><Money value={row.amount} /> {row.currency ?? ''}</> },
          { title: '行费用', width: 100, align: 'right', render: (_, row) => <Money value={row.feeAmount} /> },
          { title: '外部交易号', width: 200, render: (_, row) => row.externalTransactionId
            ? <span className="mono">{row.externalTransactionId}<div className="cell-meta">{row.referenceType}</div></span> : <span className="muted">—</span> },
          { title: '平台流水', width: 190, render: (_, row) => row.platformTxnId
            ? <Link className="mono" to={platformTxnsOfLine(row)} onClick={event => event.stopPropagation()}>{row.platformTxnId}
              <div className="cell-meta">{row.matchMethod === 'MANUAL' ? '人工' : '自动'}</div></Link>
            : row.resolutionId ? <span className="cell-meta">已归类（处置 {row.resolutionId}）</span> : <span className="muted">—</span> },
          { title: '实际费用', width: 110, align: 'right', render: (_, row) => <Money value={row.actualFee} /> },
          { title: '匹配状态', width: 100, render: (_, row) => <ReconTag status={row.matchStatus} labels={channelLineMatchStatusLabels} /> },
        ]} />
    </section>}
    <FinanceNote>渠道流水是各渠道账单中的行，只读；匹配、作废在所属渠道账单详情中进行。按外部交易号可找到它在哪张账单、匹配到哪笔平台流水。</FinanceNote>
  </div>;
}
