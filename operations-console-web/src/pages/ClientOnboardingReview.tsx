import { Button, Empty, Form, Input, Select, Table, Tag } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { QueryFailure, date } from '../tenantAccess/TenantAccessView';
import { ClientReviewDetail } from '../clientReview/ClientReviewDetail';
import { caseStatusLabels, decodeCasePage, filters, isId, qualificationLabels, reviewApi, reviewRoute } from '../clientReview/clientReview';
import type { ReviewCase } from '../clientReview/clientReview';
import '../configuration/configuration.css';
import '../clientReview/clientReview.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
const keys = ['status', 'qualificationType', 'applicationId'];
const options = (labels: Record<string, string>) => Object.entries(labels).map(([value, label]) => ({ value, label }));

/** O01 列表与 O02 详情；路由 /client-onboarding/reviews[/:caseId]。 */
export function ClientOnboardingReview(props: Props) {
  const location = useLocation(); const parts = location.pathname.replace(/\/$/, '').split('/').slice(3);
  if (parts.length > 1 || parts.length === 1 && !isId(parts[0])) return <QueryFailure failure={new ApiError(404, 'NOT_FOUND')} />;
  return parts.length === 1 ? <ClientReviewDetail key={parts[0]} caseId={parts[0]} {...props} /> : <ReviewList key={location.search} />;
}

function ReviewList() {
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search); const [form] = Form.useForm();
  const initial = Object.fromEntries(keys.flatMap(k => query.get(k) ? [[k, query.get(k)!]] : []));
  const page = usePaging<ReviewCase>(`${reviewApi}/cases`, decodeCasePage, initial, {
    initialPaging: { page: Number(query.get('page') || 1), size: Number(query.get('size') || 20) }, notifyOnError: false,
    onQueryChange: next => { void navigate(`?${new URLSearchParams({ ...next.filters, page: String(next.paging.page), size: String(next.paging.size) })}`); } });
  return <div className="configuration-page client-review-page"><div className="page-heading"><h1>客户入驻审核</h1><Button onClick={page.refresh} loading={page.loading}>刷新</Button></div>
    <Form form={form} layout="vertical" initialValues={initial} onFinish={values => page.applyFilters(filters(values))}>
      <div className="review-search">
        <Form.Item name="applicationId" label="申请编号" rules={[{ pattern: /^[1-9][0-9]{0,18}$/, message: '请输入数字申请编号' }]}><Input allowClear maxLength={19} /></Form.Item>
        <Form.Item name="qualificationType" label="客户类型"><Select allowClear placeholder="全部类型" options={options({ INDIVIDUAL_KYC: '个人', ORGANIZATION_KYB: '组织' })} /></Form.Item>
        <Form.Item name="status" label="处理状态"><Select allowClear placeholder="全部状态" options={options(caseStatusLabels)} /></Form.Item>
        <div />
        <div className="configuration-actions"><Button type="primary" htmlType="submit">查询</Button><Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>重置</Button></div>
      </div>
    </Form>
    {page.error ? <QueryFailure failure={page.failure} retry={page.refresh} /> : <section className="table-panel">
      <Table<ReviewCase> rowKey="caseId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 980 }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={Object.keys(initial).length ? '没有符合条件的审核任务' : '暂无入驻审核任务'} /> }}
        pagination={{ ...page.pagination, pageSizeOptions: [10, 20, 50, 100], showTotal: total => `共 ${total} 个任务` }} columns={[
          { title: '审核任务', width: 220, render: (_, row) => <div><div>{row.caseNo}</div><div className="cell-meta">申请 {row.applicationId} · 客户 {row.clientId}</div></div> },
          { title: '客户类型', width: 110, render: (_, row) => qualificationLabels[row.qualificationType] ?? row.qualificationType },
          { title: '状态', width: 110, render: (_, row) => <Tag color={row.status === 'APPROVED' ? 'green' : row.status === 'REJECTED' ? 'red' : row.status === 'RETURNED' ? 'orange' : 'blue'}>{caseStatusLabels[row.status] ?? row.status}</Tag> },
          { title: '提交时间', width: 180, render: (_, row) => date(row.submittedAt) },
          { title: '结束时间', width: 180, render: (_, row) => row.decidedAt ? date(row.decidedAt) : '—' },
          { title: '操作', width: 100, fixed: 'right', render: (_, row) => <Button type="link" onClick={() => void navigate(`${reviewRoute}/${row.caseId}`)}>审核</Button> },
        ]} /></section>}
  </div>;
}
