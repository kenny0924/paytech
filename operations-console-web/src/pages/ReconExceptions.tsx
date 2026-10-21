import { useState } from 'react';
import { Alert, Button, Form, Select, Table, Tag } from 'antd';
import { Link, Navigate, useParams, useSearchParams } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { localTime } from '../finance/finance';
import { FinanceNote, LoadFailure } from '../finance/FinanceParts';
import { ExceptionDetail } from '../reconciliation/detail/ExceptionDetail';
import { useOpenDetail } from '../reconciliation/ReconDetail';
import {
  decodeWithId, exceptionStatusLabels, exceptionTypeLabels, hasReconAction, pageOf, reconActions, reconLayerLabels, reconPaths, sourceTypeLabels,
} from '../reconciliation/reconModel';
import type { ReconException } from '../reconciliation/reconModel';
import { EmptyHint, Money, ReconTag } from '../reconciliation/ReconParts';
import { compactFilters, isReconId, reconRoutes } from '../reconciliation/reconRoutes';
import { useCashAccounts, useClearingAccounts } from '../reconciliation/useReconData';
import { resetForm, useReconListQuery } from '../reconciliation/useReconListQuery';
import '../configuration/configuration.css';
import '../finance/finance.css';
import '../reconciliation/recon.css';

interface Props { resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }

const decodePage = pageOf(decodeWithId<ReconException>('exceptionId'));
const options = (labels: Record<string, string>) => Object.entries(labels).map(([value, label]) => ({ value, label }));
const filterKeys = ['status', 'exceptionType', 'reconLayer', 'clearingAccountId', 'cashAccountId', 'statementId'] as const;

/**
 * 对账异常（UC-R15、R16、AC-V4）：按账龄排序的列表（默认只看未结），按类型、状态、清算账户、银行账户、账单筛选；点行进入详情页（/exceptions/:exceptionId）
 * <li>筛选与页码存于 URL 查询串（statementId 只能经链接带入）；旧链接 ?exception= 转到详情页。</li>
 */
export function ReconExceptions(props: Props) {
  const { exceptionId } = useParams();
  const [params] = useSearchParams();
  const legacy = params.get('exception');
  if (exceptionId !== undefined) return <ExceptionDetail key={exceptionId} exceptionId={exceptionId} {...props} />;
  if (isReconId(legacy ?? undefined)) return <Navigate replace to={reconRoutes.exception(legacy!)} />;
  return <ExceptionList {...props} />;
}

function ExceptionList({ resources, onAuthorizationChanged }: Props) {
  const openDetail = useOpenDetail();
  const canOperate = hasReconAction(resources, reconActions.operate);
  const canApprove = hasReconAction(resources, reconActions.approve);
  const clearing = useClearingAccounts(onAuthorizationChanged);
  const cash = useCashAccounts(onAuthorizationChanged);
  const page = useReconListQuery<ReconException>(reconPaths.exceptions, decodePage, filterKeys);
  const [statementId, setStatementId] = useState<string | undefined>(page.filters.statementId);
  const [form] = Form.useForm<Record<string, string | undefined>>();
  const search = (values: Record<string, string | undefined>) => page.applyFilters(compactFilters({ ...values, statementId }));
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>对账异常</h1></div>
    {statementId && <Alert type="info" showIcon className="finance-banner" title={<>只看渠道账单 <Link className="mono" to={reconRoutes.statement(statementId)}>{statementId}</Link> 的异常</>}
      action={<Button size="small" onClick={() => { setStatementId(undefined); page.applyFilters(compactFilters({ ...form.getFieldsValue(), statementId: undefined })); }}>看全部</Button>} />}
    <Form form={form} name="recon-exception-filters" layout="vertical" initialValues={page.filters} onFinish={search}>
      <div className="finance-search">
        <Form.Item name="status" label="状态" extra="不选时只看未结（待处置、待复核）"><Select allowClear placeholder="未结" options={options(exceptionStatusLabels)} /></Form.Item>
        <Form.Item name="exceptionType" label="类型"><Select allowClear placeholder="全部" showSearch={{ optionFilterProp: 'label' }} options={options(exceptionTypeLabels)} /></Form.Item>
        <Form.Item name="reconLayer" label="对账层"><Select allowClear placeholder="全部" options={options(reconLayerLabels)} /></Form.Item>
        <Form.Item name="clearingAccountId" label="清算账户"><Select allowClear showSearch={{ optionFilterProp: 'label' }} placeholder="全部" loading={clearing.loading}
          options={clearing.items.map(item => ({ value: item.clearingAccountId, label: item.clearingAccountCode }))} /></Form.Item>
        <Form.Item name="cashAccountId" label="银行账户"><Select allowClear showSearch={{ optionFilterProp: 'label' }} placeholder="全部" loading={cash.loading}
          options={cash.items.map(item => ({ value: item.cashAccountId, label: `${item.accountName} · 尾号 ${item.accountLast4}` }))} /></Form.Item>
        <div className="configuration-actions"><Button htmlType="submit">查询</Button>
          <Button onClick={() => { resetForm(form); search({}); }}>重置</Button></div>
      </div>
    </Form>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<ReconException> rowKey="exceptionId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1240 }}
        pagination={{ ...page.pagination, showTotal: total => `共 ${total} 条，按账龄从长到短` }}
        onRow={row => ({ onClick: () => openDetail(reconRoutes.exception(row.exceptionId)), className: 'clickable-row' })}
        locale={{ emptyText: <EmptyHint text="没有符合条件的对账异常。系统匹配不上的账单行、费用偏差和人工开立的异常会出现在这里。" /> }} columns={[
          { title: '账龄', width: 80, align: 'right', render: (_, row) => `${row.ageDays} 天` },
          { title: '类型', width: 160, render: (_, row) => <>{exceptionTypeLabels[row.exceptionType] ?? row.exceptionType}{row.blocking && <Tag color="red" className="finance-self-tag">阻塞对平</Tag>}</> },
          { title: '对账层', width: 80, render: (_, row) => reconLayerLabels[row.reconLayer] ?? row.reconLayer },
          { title: '来源对象', width: 200, render: (_, row) => <span>{sourceTypeLabels[row.sourceType] ?? row.sourceType}<div className="cell-meta mono">{row.sourceId}</div></span> },
          { title: '账户', width: 170, render: (_, row) => row.clearingAccountId ? clearing.byId.get(row.clearingAccountId)?.clearingAccountCode ?? row.clearingAccountId
            : row.cashAccountId ? (cash.byId.get(row.cashAccountId)?.accountName ?? row.cashAccountId) : '—' },
          { title: '差异', width: 130, align: 'right', render: (_, row) => <><Money value={row.differenceAmount} /> {row.currency}</> },
          { title: '状态', width: 100, render: (_, row) => <ReconTag status={row.status} labels={exceptionStatusLabels} /> },
          { title: '开立时间', width: 150, render: (_, row) => localTime(row.openedTime) },
        ]} />
    </section>}
    <FinanceNote>处置三种：重新匹配、接受时间差、归类；点行进入详情提交处置与复核。{!canOperate && ' 当前账号没有对账操作权限。'}{!canApprove && ' 当前账号没有对账审批权限。'}</FinanceNote>
  </div>;
}
