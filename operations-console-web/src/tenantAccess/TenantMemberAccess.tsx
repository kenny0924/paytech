import { useMemo } from 'react';
import { Alert, Button, Empty, Space, Table, Tabs } from 'antd';
import type { TableColumnsType } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { usePaging } from '../hooks/usePaging';
import { decodeEvaluated, decodeMember, decodePermission, decodeRole, decodeSource, decodeTenant, replaceQuery, tenantApi } from './tenantAccess';
import type { EvaluatedPage, MemberPermission, PermissionSource, RoleGrant } from './tenantAccess';
import { date, Effective, Loading, MemberSummary, QueryFailure, Status, TenantSummary, useDetail } from './TenantAccessView';

export function TenantMemberAccess({ tenantId, memberId }: { tenantId: string; memberId: string }) {
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search);
  const tenantDecoder = useMemo(() => (v: unknown) => decodeTenant(v, tenantId), [tenantId]);
  const memberDecoder = useMemo(() => (v: unknown) => decodeMember(v, tenantId, memberId), [tenantId, memberId]);
  const tenant = useDetail(`${tenantApi}/${tenantId}`, tenantDecoder); const member = useDetail(`${tenantApi}/${tenantId}/members/${memberId}`, memberDecoder);
  const tab = query.get('tab') === 'roles' ? 'roles' : 'permissions';
  function refresh() { tenant.refresh(); member.refresh(); }
  if (tenant.loading || member.loading) return <Loading />;
  if (tenant.failure || member.failure || !tenant.data || !member.data) return <QueryFailure failure={tenant.failure || member.failure} retry={refresh} />;
  return <div className="configuration-page tenant-access-page"><div className="page-heading"><h1>成员权限</h1><Button onClick={refresh}>刷新</Button></div>
    <TenantSummary tenant={tenant.data} compact /><MemberSummary member={member.data} />
    <Tabs activeKey={tab} onChange={tab => void navigate(replaceQuery(location.search, { tab }))} items={[{ key: 'permissions', label: '权限' }, { key: 'roles', label: '角色授予' }]} />
    <div className="tenant-authorization-note">以下展示当前 IAM 授权状态；实际业务请求还需通过会话、接口及业务对象检查。</div>
    {tab === 'roles' ? <MemberRoles key={location.search} tenantId={tenantId} memberId={memberId} /> : <MemberPermissions key={location.search} tenantId={tenantId} memberId={memberId} />}
  </div>;
}
function roleColumns<T extends RoleGrant>(): TableColumnsType<T> {
  return [
    { title: '角色', width: 220, ellipsis: true, render: (_, row) => <div><div className="tenant-ellipsis" title={row.roleName}>{row.roleName}</div><div className="cell-meta tenant-ellipsis" title={row.roleCode}>{row.roleCode}</div></div> },
    { title: '范围', width: 95, render: (_, row) => row.roleScope === 'PLATFORM' ? '平台角色' : '租户角色' },
    { title: '角色状态', width: 100, render: (_, row) => <Status value={row.roleStatus} /> },
    { title: '授予状态', width: 100, render: (_, row) => <Status value={row.grantStatus} /> },
    { title: '有效期', width: 210, render: (_, row) => <div>{date(row.effectiveFrom)}<div className="cell-meta">至 {date(row.effectiveTo)}</div></div> },
  ];
}
function EvaluatedTime({ value, refresh }: { value?: string; refresh: () => void }) {
  return <div className="table-toolbar"><span className="cell-meta">{value ? `计算时间：${date(value)}` : '读取当前授权'}</span><Button size="small" onClick={refresh}>刷新</Button></div>;
}
function MemberRoles({ tenantId, memberId }: { tenantId: string; memberId: string }) {
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search);
  const decode = useMemo(() => (v: unknown) => decodeEvaluated(v, row => decodeRole(row, tenantId), row => row.grantId), [tenantId]);
  const page = usePaging<RoleGrant>(`${tenantApi}/${tenantId}/members/${memberId}/roles`, decode, {}, {
    initialPaging: { page: Number(query.get('rolePage') || 1), size: Number(query.get('roleSize') || 10) },
    onQueryChange: next => void navigate(replaceQuery(location.search, { rolePage: String(next.paging.page), roleSize: String(next.paging.size) })),
  });
  if (page.error) return <QueryFailure failure={page.failure} retry={page.refresh} />;
  return <section className="table-panel"><EvaluatedTime value={(page.data as EvaluatedPage<RoleGrant>).evaluatedAt} refresh={page.refresh} />
    <Table<RoleGrant> rowKey="grantId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1040 }}
      pagination={{ ...page.pagination, pageSizeOptions: [10, 20, 50, 100], showTotal: total => `共 ${total} 条角色授予` }} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚未授予角色" /> }} columns={[
        ...roleColumns<RoleGrant>(), { title: '当前授权状态', width: 280, render: (_, row) => <Effective effective={row.effective} reasons={row.reasons} /> },
      ]} /></section>;
}
function MemberPermissions({ tenantId, memberId }: { tenantId: string; memberId: string }) {
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search); const selected = query.get('permission');
  const decode = useMemo(() => (v: unknown) => decodeEvaluated(v, decodePermission, row => row.permissionId), []);
  const page = usePaging<MemberPermission>(`${tenantApi}/${tenantId}/members/${memberId}/permissions`, decode, {}, {
    initialPaging: { page: Number(query.get('permissionPage') || 1), size: Number(query.get('permissionSize') || 10) },
    onQueryChange: next => void navigate(replaceQuery(location.search, { permissionPage: String(next.paging.page), permissionSize: String(next.paging.size), permission: null, sourcePage: null, sourceSize: null })),
  });
  if (page.error) return <QueryFailure failure={page.failure} retry={page.refresh} />;
  return <section className="table-panel"><EvaluatedTime value={(page.data as EvaluatedPage<MemberPermission>).evaluatedAt} refresh={page.refresh} />
    <Table<MemberPermission> rowKey="permissionId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1100 }}
      pagination={{ ...page.pagination, pageSizeOptions: [10, 20, 50, 100], showTotal: total => `共 ${total} 项权限` }} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="尚无权限来源" /> }}
      expandable={{ expandedRowKeys: selected ? [selected] : [], columnTitle: '来源', columnWidth: 70,
        onExpand: (expanded, row) => void navigate(replaceQuery(location.search, { permission: expanded ? row.permissionId : null, sourcePage: null, sourceSize: null })),
        expandedRowRender: row => <PermissionSources key={`${row.permissionId}:${location.search}`} tenantId={tenantId} memberId={memberId} permission={row} />,
      }} columns={[
        { title: '权限代码', dataIndex: 'permissionCode', width: 250, ellipsis: true }, { title: '资源', dataIndex: 'resourceType', width: 180, ellipsis: true },
        { title: '动作', dataIndex: 'actionCode', width: 140, ellipsis: true }, { title: '目录状态', width: 105, render: (_, row) => <Status value={row.permissionStatus} /> },
        { title: '有效 / 全部来源', width: 140, render: (_, row) => <Space>{row.effectiveSourceCount} / {row.sourceCount}</Space> },
        { title: '当前授权状态', width: 280, render: (_, row) => <Effective effective={row.effective} reasons={row.reasons} /> },
      ]} /></section>;
}
function PermissionSources({ tenantId, memberId, permission }: { tenantId: string; memberId: string; permission: MemberPermission }) {
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search);
  const decode = useMemo(() => (v: unknown) => decodeEvaluated(v, row => decodeSource(row, tenantId), row => `${row.grantId}:${row.rolePermissionId}`), [tenantId]);
  const page = usePaging<PermissionSource>(`${tenantApi}/${tenantId}/members/${memberId}/permissions/${permission.permissionId}/sources`, decode, {}, {
    initialPaging: { page: Number(query.get('sourcePage') || 1), size: Number(query.get('sourceSize') || 10) },
    onQueryChange: next => void navigate(replaceQuery(location.search, { sourcePage: String(next.paging.page), sourceSize: String(next.paging.size) })),
  });
  return <div className="tenant-sources"><h3 className="tenant-ellipsis" title={permission.permissionCode}>{permission.permissionCode} 的权限来源</h3>
    {page.error ? <QueryFailure failure={page.failure} retry={page.refresh} /> : <><EvaluatedTime value={(page.data as EvaluatedPage<PermissionSource>).evaluatedAt} refresh={page.refresh} />
      <Table<PermissionSource> size="small" rowKey={row => `${row.grantId}:${row.rolePermissionId}`} tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1260 }}
        pagination={{ ...page.pagination, pageSizeOptions: [10, 20, 50, 100] }} locale={{ emptyText: <Alert type="info" title="此页没有来源记录，可调整分页或刷新。" /> }} columns={[
          ...roleColumns<PermissionSource>(), { title: '权限关联', width: 110, render: (_, row) => <Status value={row.rolePermissionStatus} /> },
          { title: '来源有效性', width: 300, render: (_, row) => <Effective effective={row.effective} reasons={row.reasons} /> },
        ]} /></>}
  </div>;
}
