import { useState } from 'react';
import { Button, Form, Input, Select, Table } from 'antd';
import { Navigate, useParams, useSearchParams } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { localTime } from '../finance/finance';
import { FinanceNote, LoadFailure } from '../finance/FinanceParts';
import { ActorText } from '../finance/WorkflowParts';
import { ChannelStatementDetail } from '../reconciliation/detail/ChannelStatementDetail';
import { ImportDrawer } from '../reconciliation/ImportDrawer';
import { useOpenDetail } from '../reconciliation/ReconDetail';
import { decodeWithId, directionLabels, hasReconAction, pageOf, reconActions, reconPaths, statementStatusLabels } from '../reconciliation/reconModel';
import type { ChannelStatement } from '../reconciliation/reconModel';
import { EmptyHint, Money, ReconTag } from '../reconciliation/ReconParts';
import { compactFilters, isReconId, reconRoutes } from '../reconciliation/reconRoutes';
import { useClearingAccounts } from '../reconciliation/useReconData';
import { resetForm, useReconListQuery } from '../reconciliation/useReconListQuery';
import '../configuration/configuration.css';
import '../finance/finance.css';
import '../reconciliation/recon.css';

interface Props { resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }

const decodePage = pageOf(decodeWithId<ChannelStatement>('statementId'));
const options = (labels: Record<string, string>) => Object.entries(labels).map(([value, label]) => ({ value, label }));
const filterKeys = ['clearingAccountId', 'channelBatchRef', 'status', 'settlementDateFrom', 'settlementDateTo'] as const;

/**
 * 渠道账单（UC-R06–R09）：上传（分段进度、被拒原因表）、列表；点行进入详情页（/statements/:statementId）看全部账单行、门禁与作废
 * <li>筛选与页码存于 URL 查询串；旧链接 ?statement= 转到详情页。上传按「对账操作」按钮资源显示。</li>
 */
export function ReconStatements(props: Props) {
  const { statementId } = useParams();
  const [params] = useSearchParams();
  const legacy = params.get('statement');
  if (statementId !== undefined) return <ChannelStatementDetail key={statementId} statementId={statementId} {...props} />;
  if (isReconId(legacy ?? undefined)) return <Navigate replace to={reconRoutes.statement(legacy!)} />;
  return <StatementList {...props} />;
}

function StatementList({ resources, actorId, onAuthorizationChanged }: Props) {
  const openDetail = useOpenDetail();
  const canOperate = hasReconAction(resources, reconActions.operate);
  const [uploading, setUploading] = useState(false);
  const clearing = useClearingAccounts(onAuthorizationChanged);
  const page = useReconListQuery<ChannelStatement>(reconPaths.channelStatements, decodePage, filterKeys);
  const [form] = Form.useForm<Record<string, string | undefined>>();
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>渠道账单</h1></div>
    <Form form={form} name="recon-statement-filters" layout="vertical" initialValues={page.filters} onFinish={values => page.applyFilters(compactFilters(values))}>
      <div className="finance-search">
        <Form.Item name="clearingAccountId" label="清算账户"><Select allowClear showSearch={{ optionFilterProp: 'label' }} placeholder="全部" loading={clearing.loading}
          options={clearing.items.map(item => ({ value: item.clearingAccountId, label: item.clearingAccountCode }))} /></Form.Item>
        <Form.Item name="channelBatchRef" label="批次号"><Input allowClear maxLength={128} /></Form.Item>
        <Form.Item name="status" label="状态"><Select allowClear placeholder="全部" options={options(statementStatusLabels)} /></Form.Item>
        <Form.Item name="settlementDateFrom" label="结算日起" rules={[{ pattern: /^\d{4}-\d{2}-\d{2}$/, message: 'YYYY-MM-DD' }]}><Input allowClear placeholder="YYYY-MM-DD" /></Form.Item>
        <Form.Item name="settlementDateTo" label="结算日止" rules={[{ pattern: /^\d{4}-\d{2}-\d{2}$/, message: 'YYYY-MM-DD' }]}><Input allowClear placeholder="YYYY-MM-DD" /></Form.Item>
        <div className="configuration-actions"><Button htmlType="submit">查询</Button>
          <Button onClick={() => { resetForm(form); page.applyFilters({}); }}>重置</Button>
          {canOperate && <Button type="primary" onClick={() => setUploading(true)}>上传渠道账单</Button>}</div>
      </div>
    </Form>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<ChannelStatement> rowKey="statementId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1180 }}
        pagination={{ ...page.pagination, showTotal: total => `共 ${total} 条` }}
        onRow={row => ({ onClick: () => openDetail(reconRoutes.statement(row.statementId)), className: 'clickable-row' })}
        locale={{ emptyText: <EmptyHint text={canOperate ? '还没有渠道账单。按标准 CSV 上传后，系统逐行匹配平台流水。' : '没有符合条件的渠道账单'} /> }}
        columns={[
          { title: '批次号', width: 200, render: (_, row) => <a className="mono">{row.channelBatchRef}{row.revision > 1 ? ` · v${row.revision}` : ''}</a> },
          { title: '清算账户', width: 160, dataIndex: 'clearingAccountCode' },
          { title: '结算日', width: 110, dataIndex: 'settlementDate' },
          { title: '净额', width: 140, align: 'right', render: (_, row) => <><Money value={row.netAmount} /> {row.currency}</> },
          { title: '方向', width: 90, render: (_, row) => directionLabels[row.cashDirection] ?? row.cashDirection },
          { title: '行数', width: 110, align: 'right', render: (_, row) => `${row.importedLineCount} / ${row.declaredLineCount}` },
          { title: '状态', width: 100, render: (_, row) => <ReconTag status={row.status} labels={statementStatusLabels} /> },
          { title: '上传人', width: 160, render: (_, row) => <ActorText value={row.makerId} actorId={actorId} /> },
          { title: '上传时间', width: 150, render: (_, row) => localTime(row.createTime) },
        ]} />
    </section>}
    <FinanceNote>同一清算账户 + 批次号只允许一个未作废版本：要更正，先作废匹配中的版本再重新上传；已对平的账单不能更正或作废。{!canOperate && ' 当前账号没有对账操作权限。'}</FinanceNote>
    {uploading && <ImportDrawer kind="channel" onClose={() => setUploading(false)} onImported={page.refresh} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}
