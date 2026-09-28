import { useMemo, useState } from 'react';
import { Alert, Button, Dropdown, Empty, Form, Input, Select, Space, Table, Tabs, Tag } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { buildMenuTree, menuPages } from '../navigation/menu';
import { decodeTenant, decodeMember, decodePage, filters, isId, memberLabels, ownerLabels, replaceQuery, securityLabels, tenantApi } from '../tenantAccess/tenantAccess';
import type { Member, Tenant } from '../tenantAccess/tenantAccess';
import { ChangeSecurity, Loading, QueryFailure, Reasons, Status, TenantSummary, useDetail } from '../tenantAccess/TenantAccessView';
import { TenantMemberAccess } from '../tenantAccess/TenantMemberAccess';
import { Audit } from './Audit';
import '../configuration/configuration.css';
import '../tenantAccess/tenantAccess.css';
interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
const tenantKeys = ['tenantType', 'tenantName', 'ownerStatus', 'securityStatus', 'tenantId', 'ownerRefId'];
const memberKeys = ['accountId', 'emailMasked', 'memberType', 'memberStatus'];
const has = (resources: ConsoleResource[], code: string) => resources.some(r => r.resourceType === 'BUTTON' && r.resourceCode === code);
const options = (labels: Record<string, string>) => Object.entries(labels).map(([value, label]) => ({ value, label }));
const tenantPage = (value: unknown) => decodePage(value, v => decodeTenant(v), v => v.tenantId);

export function TenantAccess(props: Props) {
  const location = useLocation(); const parts = location.pathname.replace(/\/$/, '').split('/').slice(2);
  if (parts.some((p, i) => i !== 1 && !isId(p)) || parts.length === 2 || parts.length > 3 || parts.length === 3 && parts[1] !== 'members')
    return <QueryFailure failure={new ApiError(404, 'NOT_FOUND')} />;
  const canMembers = has(props.resources, 'OPERATIONS_TENANT_MEMBER_READ');
  if (parts.length === 3) return canMembers ? <TenantMemberAccess key={location.pathname} tenantId={parts[0]} memberId={parts[2]} />
    : <QueryFailure failure={new ApiError(403, 'FORBIDDEN')} />;
  return parts.length === 1 ? <TenantDetails key={parts[0]} {...props} tenantId={parts[0]} /> : <TenantList key={location.search} {...props} />;
}
function TenantList({ resources, onAuthorizationChanged }: Props) {
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search); const [form] = Form.useForm();
  const initial = Object.fromEntries(tenantKeys.flatMap(k => query.get(k) ? [[k, query.get(k)!]] : []));
  const page = usePaging<Tenant>(tenantApi, tenantPage, initial, { initialPaging: { page: Number(query.get('page') || 1), size: Number(query.get('size') || 10) },
    onQueryChange: next => { void navigate(`?${new URLSearchParams({ ...next.filters, page: String(next.paging.page), size: String(next.paging.size) })}`); } });
  const [changing, setChanging] = useState<Tenant | null>(null); const [saved, setSaved] = useState(false);
  const canManage = has(resources, 'OPERATIONS_TENANT_ACCESS_MANAGE');
  return <div className="configuration-page tenant-access-page"><div className="page-heading"><h1>租户访问</h1><Button onClick={page.refresh} loading={page.loading}>刷新</Button></div>
    <Form form={form} layout="vertical" initialValues={initial} onFinish={values => page.applyFilters(filters(values))}>
      <div className="tenant-search"><Form.Item name="tenantType" label="租户类型"><Select allowClear placeholder="全部类型" options={options({ CLIENT: '客户', PARTNER: '合作伙伴' })} /></Form.Item>
        <Form.Item name="tenantName" label="名称"><Input allowClear maxLength={255} placeholder="输入客户或合作伙伴名称" /></Form.Item>
        <Form.Item name="ownerStatus" label="来源业务状态"><Select allowClear placeholder="全部状态" options={options(ownerLabels)} /></Form.Item>
        <Form.Item name="securityStatus" label="安全状态"><Select allowClear placeholder="全部状态" options={options(securityLabels)} /></Form.Item>
        <div className="configuration-actions"><Button type="primary" htmlType="submit">查询</Button><Button onClick={() => page.applyFilters({})}>重置</Button></div>
      </div><div className="tenant-id-search"><Form.Item name="tenantId" label="租户 ID"><Input allowClear maxLength={19} /></Form.Item>
        <Form.Item name="ownerRefId" label="所属客户 / 合作伙伴 ID"><Input allowClear maxLength={19} /></Form.Item></div>
    </Form>
    {page.error ? <QueryFailure failure={page.failure} retry={page.refresh} completed={saved} /> : <section className="table-panel">
      <Table<Tenant> rowKey="tenantId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1120 }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={Object.keys(initial).length ? '没有符合条件的租户' : '暂无客户或合作伙伴租户'} /> }}
        pagination={{ ...page.pagination, pageSizeOptions: [10, 20, 50, 100], showTotal: total => `共 ${total} 个租户` }} columns={[
          { title: '客户 / 合作伙伴', width: 265, ellipsis: true, render: (_, row) => <div><div className="tenant-ellipsis" title={row.tenantName}>{row.tenantName}</div><div className="cell-meta">租户 {row.tenantId} · 主体 {row.ownerRefId}</div></div> },
          { title: '类型', width: 105, render: (_, row) => row.tenantType === 'CLIENT' ? '客户' : '合作伙伴' },
          { title: '来源业务状态', width: 120, render: (_, row) => ownerLabels[row.ownerStatus] },
          { title: '安全状态', width: 100, render: (_, row) => securityLabels[row.securityStatus] },
          { title: '租户访问条件', width: 300, render: (_, row) => <div><Tag color={row.tenantAccessAllowed ? 'green' : 'orange'}>{row.tenantAccessAllowed ? '已满足' : '未满足'}</Tag><Reasons reasons={row.restrictionReasons} /></div> },
          { title: '操作', width: 100, fixed: 'right', render: (_, row) => <Dropdown trigger={['click', 'hover']} menu={{ items: [
            { key: 'detail', label: '详情', onClick: () => void navigate(`/tenant-access/${row.tenantId}`) },
            ...(canManage ? [{ key: 'security', label: row.securityStatus === 'ACTIVE' ? '暂停访问' : '恢复安全状态', danger: row.securityStatus === 'ACTIVE', onClick: () => setChanging(row) }] : []),
          ] }}><Button size="small">操作 ▾</Button></Dropdown> },
        ]} /></section>}
    {changing && <ChangeSecurity tenant={changing} onClose={() => setChanging(null)} onSaved={() => { setChanging(null); setSaved(true); page.refresh(); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}
function TenantDetails({ resources, onAuthorizationChanged, tenantId }: Props & { tenantId: string }) {
  const location = useLocation(); const navigate = useNavigate(); const decode = useMemo(() => (v: unknown) => decodeTenant(v, tenantId), [tenantId]);
  const detail = useDetail(`${tenantApi}/${tenantId}`, decode); const [changing, setChanging] = useState(false); const [saved, setSaved] = useState(false);
  const canManage = has(resources, 'OPERATIONS_TENANT_ACCESS_MANAGE'); const canMembers = has(resources, 'OPERATIONS_TENANT_MEMBER_READ');
  const canAudit = menuPages(buildMenuTree(resources)).some(m => m.componentKey === 'OPERATIONS_AUDIT');
  const tab = new URLSearchParams(location.search).get('tab') === 'audit' && canAudit ? 'audit' : 'members';
  if (detail.loading) return <Loading />;
  if (detail.failure || !detail.data) return <QueryFailure failure={detail.failure} retry={detail.refresh} completed={saved} />;
  const tenant = detail.data;
  return <div className="configuration-page tenant-access-page"><div className="page-heading"><h1>租户详情</h1><Space>
    <Button onClick={detail.refresh}>刷新</Button>{canManage && <Button danger={tenant.securityStatus === 'ACTIVE'} onClick={() => setChanging(true)}>{tenant.securityStatus === 'ACTIVE' ? '暂停访问' : '恢复安全状态'}</Button>}</Space></div>
    <TenantSummary tenant={tenant} />
    <Tabs activeKey={tab} onChange={tab => void navigate(replaceQuery(location.search, { tab }))} items={[{ key: 'members', label: '成员' }, ...(canAudit ? [{ key: 'audit', label: '操作记录' }] : [])]} />
    {tab === 'audit' ? <Audit objectFilter={{ resourceType: 'ACCESS_TENANT', resourceId: tenantId }} embedded /> : canMembers
      ? <TenantMembers key={location.search} tenantId={tenantId} /> : <Alert showIcon type="info" title="没有成员权限查询权限" description="请联系管理员分配成员查看权限。" />}
    {changing && <ChangeSecurity tenant={tenant} onClose={() => setChanging(false)} onSaved={() => { setChanging(false); setSaved(true); detail.refresh(); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}
function TenantMembers({ tenantId }: { tenantId: string }) {
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search); const [form] = Form.useForm();
  const initial = Object.fromEntries(memberKeys.flatMap(k => query.get(k) ? [[k, query.get(k)!]] : []));
  const decode = useMemo(() => (value: unknown) => decodePage(value, v => decodeMember(v, tenantId), v => v.memberId), [tenantId]);
  const page = usePaging<Member>(`${tenantApi}/${tenantId}/members`, decode, initial, {
    initialPaging: { page: Number(query.get('page') || 1), size: Number(query.get('size') || 10) },
    onQueryChange: next => void navigate(replaceQuery(location.search, { ...Object.fromEntries(memberKeys.map(k => [k, null])), ...next.filters, page: String(next.paging.page), size: String(next.paging.size) })),
  });
  return <><Form form={form} layout="vertical" initialValues={initial} onFinish={values => page.applyFilters(filters(values))}>
    <div className="tenant-member-search"><Form.Item name="emailMasked" label="脱敏邮箱"><Input allowClear maxLength={255} placeholder="按已显示的脱敏值搜索" /></Form.Item>
      <Form.Item name="accountId" label="账号 ID"><Input allowClear maxLength={19} /></Form.Item>
      <Form.Item name="memberType" label="成员类型"><Select allowClear placeholder="全部" options={options(memberLabels)} /></Form.Item>
      <Form.Item name="memberStatus" label="成员状态"><Select allowClear placeholder="全部" options={options({ ACTIVE: '有效', SUSPENDED: '已暂停', REVOKED: '已撤销' })} /></Form.Item>
      <div className="configuration-actions"><Button type="primary" htmlType="submit">查询</Button><Button onClick={() => page.applyFilters({})}>重置</Button></div></div></Form>
    {page.error ? <QueryFailure failure={page.failure} retry={page.refresh} /> : <Table<Member> rowKey="memberId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 950 }}
      pagination={{ ...page.pagination, pageSizeOptions: [10, 20, 50, 100], showTotal: total => `共 ${total} 个成员` }} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={Object.keys(initial).length ? '没有符合条件的成员' : '该租户暂无成员'} /> }} columns={[
        { title: '脱敏邮箱 / 账号', width: 270, ellipsis: true, render: (_, row) => <div><div className="tenant-ellipsis" title={row.emailMasked || '未登记脱敏邮箱'}>{row.emailMasked || '未登记脱敏邮箱'}</div><div className="cell-meta">{row.accountId}</div></div> },
        { title: '成员 ID', dataIndex: 'memberId', width: 200, ellipsis: true }, { title: '类型', width: 100, render: (_, row) => memberLabels[row.memberType] },
        { title: '账号状态', width: 125, render: (_, row) => <Status value={row.accountStatus} /> }, { title: '成员状态', width: 125, render: (_, row) => <Status value={row.memberStatus} /> },
        { title: '操作', width: 115, fixed: 'right', render: (_, row) => <Button type="link" onClick={() => void navigate(`/tenant-access/${tenantId}/members/${row.memberId}`)}>查看权限</Button> },
      ]} />}
  </>;
}
