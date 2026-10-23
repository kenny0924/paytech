import { useState } from 'react';
import { Button, Form, Input, Select, Table } from 'antd';
import { Navigate, useParams, useSearchParams } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { localTime } from '../finance/finance';
import { FinanceNote, LoadFailure } from '../finance/FinanceParts';
import { ActorText } from '../finance/WorkflowParts';
import { useT } from '../i18n/react';
import { ChannelStatementDetail } from '../reconciliation/detail/ChannelStatementDetail';
import { ImportDrawer } from '../reconciliation/ImportDrawer';
import { useOpenDetail } from '../reconciliation/ReconDetail';
import { decodeWithId, directionEnum, hasReconAction, pageOf, reconActions, reconLabel, reconOptions, reconPaths, statementStatusEnum } from '../reconciliation/reconModel';
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
  const t = useT('recon'), tNav = useT('nav');
  const openDetail = useOpenDetail();
  const canOperate = hasReconAction(resources, reconActions.operate);
  const [uploading, setUploading] = useState(false);
  const clearing = useClearingAccounts(onAuthorizationChanged);
  const page = useReconListQuery<ChannelStatement>(reconPaths.channelStatements, decodePage, filterKeys);
  const [form] = Form.useForm<Record<string, string | undefined>>();
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>{tNav('resource.OPERATIONS_FINANCE_RECON_STATEMENTS')}</h1></div>
    <Form form={form} name="recon-statement-filters" layout="vertical" initialValues={page.filters} onFinish={values => page.applyFilters(compactFilters(values))}>
      <div className="finance-search">
        <Form.Item name="clearingAccountId" label={t('field.clearingAccount')}><Select allowClear showSearch={{ optionFilterProp: 'label' }} placeholder={t('placeholder.all')} loading={clearing.loading}
          options={clearing.items.map(item => ({ value: item.clearingAccountId, label: item.clearingAccountCode }))} /></Form.Item>
        <Form.Item name="channelBatchRef" label={t('field.batchRef')}><Input allowClear maxLength={128} /></Form.Item>
        <Form.Item name="status" label={t('field.status')}><Select allowClear placeholder={t('placeholder.all')} options={reconOptions(t, statementStatusEnum)} /></Form.Item>
        <Form.Item name="settlementDateFrom" label={t('statements.settlementDateFrom')} rules={[{ pattern: /^\d{4}-\d{2}-\d{2}$/, message: 'YYYY-MM-DD' }]}><Input allowClear placeholder="YYYY-MM-DD" /></Form.Item>
        <Form.Item name="settlementDateTo" label={t('statements.settlementDateTo')} rules={[{ pattern: /^\d{4}-\d{2}-\d{2}$/, message: 'YYYY-MM-DD' }]}><Input allowClear placeholder="YYYY-MM-DD" /></Form.Item>
        <div className="configuration-actions"><Button htmlType="submit">{t('common.search')}</Button>
          <Button onClick={() => { resetForm(form); page.applyFilters({}); }}>{t('common.reset')}</Button>
          {canOperate && <Button type="primary" onClick={() => setUploading(true)}>{t('import.channelTitle')}</Button>}</div>
      </div>
    </Form>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<ChannelStatement> rowKey="statementId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1180 }}
        pagination={{ ...page.pagination, showTotal: total => t('common.total', { total }) }}
        onRow={row => ({ onClick: () => openDetail(reconRoutes.statement(row.statementId)), className: 'clickable-row' })}
        locale={{ emptyText: <EmptyHint text={canOperate ? t('statements.empty') : t('statements.emptyFiltered')} /> }}
        columns={[
          { title: t('field.batchRef'), width: 200, render: (_, row) => <a className="mono">{row.channelBatchRef}{row.revision > 1 ? ` · v${row.revision}` : ''}</a> },
          { title: t('field.clearingAccount'), width: 160, dataIndex: 'clearingAccountCode' },
          { title: t('field.settlementDate'), width: 110, dataIndex: 'settlementDate' },
          { title: t('field.netAmount'), width: 140, align: 'right', render: (_, row) => <><Money value={row.netAmount} /> {row.currency}</> },
          { title: t('field.direction'), width: 90, render: (_, row) => reconLabel(t, directionEnum, row.cashDirection) },
          { title: t('statements.lineCount'), width: 110, align: 'right', render: (_, row) => `${row.importedLineCount} / ${row.declaredLineCount}` },
          { title: t('field.status'), width: 100, render: (_, row) => <ReconTag status={row.status} kind={statementStatusEnum} /> },
          { title: t('field.uploadedBy'), width: 160, render: (_, row) => <ActorText value={row.makerId} actorId={actorId} /> },
          { title: t('field.uploadedAt'), width: 150, render: (_, row) => localTime(row.createTime) },
        ]} />
    </section>}
    <FinanceNote>{t('statementDetail.note')}{!canOperate && ' ' + t('permission.noOperate')}</FinanceNote>
    {uploading && <ImportDrawer kind="channel" onClose={() => setUploading(false)} onImported={page.refresh} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}
