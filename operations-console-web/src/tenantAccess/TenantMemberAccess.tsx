import { useMemo } from 'react';
import { Alert, Button, Empty, Space, Table, Tabs } from 'antd';
import type { TableColumnsType } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { usePaging } from '../hooks/usePaging';
import { useT } from '../i18n/react';
import type { Translate } from '../i18n/index';
import { decodeEvaluated, decodeMember, decodePermission, decodeRole, decodeSource, decodeTenant, replaceQuery, tenantApi } from './tenantAccess';
import type { EvaluatedPage, MemberPermission, PermissionSource, RoleGrant } from './tenantAccess';
import { date, Effective, Loading, MemberSummary, QueryFailure, Status, TenantSummary, useDetail } from './TenantAccessView';

export function TenantMemberAccess({ tenantId, memberId }: { tenantId: string; memberId: string }) {
  const t = useT('access'), tCommon = useT('common');
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search);
  const tenantDecoder = useMemo(() => (v: unknown) => decodeTenant(v, tenantId), [tenantId]);
  const memberDecoder = useMemo(() => (v: unknown) => decodeMember(v, tenantId, memberId), [tenantId, memberId]);
  const tenant = useDetail(`${tenantApi}/${tenantId}`, tenantDecoder); const member = useDetail(`${tenantApi}/${tenantId}/members/${memberId}`, memberDecoder);
  const tab = query.get('tab') === 'roles' ? 'roles' : 'permissions';
  function refresh() { tenant.refresh(); member.refresh(); }
  if (tenant.loading || member.loading) return <Loading />;
  if (tenant.failure || member.failure || !tenant.data || !member.data) return <QueryFailure failure={tenant.failure || member.failure} retry={refresh} />;
  return <div className="configuration-page tenant-access-page"><div className="page-heading"><h1>{t('memberAccess.title')}</h1><Button onClick={refresh}>{tCommon('action.refresh')}</Button></div>
    <TenantSummary tenant={tenant.data} compact /><MemberSummary member={member.data} />
    <Tabs activeKey={tab} onChange={tab => void navigate(replaceQuery(location.search, { tab }))} items={[{ key: 'permissions', label: t('memberAccess.tab.permissions') }, { key: 'roles', label: t('memberAccess.tab.roles') }]} />
    <div className="tenant-authorization-note">{t('memberAccess.authorizationNote')}</div>
    {tab === 'roles' ? <MemberRoles key={location.search} tenantId={tenantId} memberId={memberId} /> : <MemberPermissions key={location.search} tenantId={tenantId} memberId={memberId} />}
  </div>;
}
function roleColumns<T extends RoleGrant>(t: Translate): TableColumnsType<T> {
  return [
    { title: t('memberAccess.column.role'), width: 220, ellipsis: true, render: (_, row) => <div><div className="tenant-ellipsis" title={row.roleName}>{row.roleName}</div><div className="cell-meta tenant-ellipsis" title={row.roleCode}>{row.roleCode}</div></div> },
    { title: t('memberAccess.column.scope'), width: 95, render: (_, row) => row.roleScope === 'PLATFORM' ? t('memberAccess.platformRole') : t('memberAccess.tenantRole') },
    { title: t('memberAccess.column.roleStatus'), width: 100, render: (_, row) => <Status value={row.roleStatus} /> },
    { title: t('memberAccess.column.grantStatus'), width: 100, render: (_, row) => <Status value={row.grantStatus} /> },
    { title: t('memberAccess.column.validity'), width: 210, render: (_, row) => <div>{date(row.effectiveFrom)}<div className="cell-meta">{t('memberAccess.validUntil', { end: date(row.effectiveTo) })}</div></div> },
  ];
}
function EvaluatedTime({ value, refresh }: { value?: string; refresh: () => void }) {
  const t = useT('access'), tCommon = useT('common');
  return <div className="table-toolbar"><span className="cell-meta">{value ? t('memberAccess.evaluatedAt', { time: date(value) }) : t('memberAccess.readingCurrent')}</span><Button size="small" onClick={refresh}>{tCommon('action.refresh')}</Button></div>;
}
function MemberRoles({ tenantId, memberId }: { tenantId: string; memberId: string }) {
  const t = useT('access');
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search);
  const decode = useMemo(() => (v: unknown) => decodeEvaluated(v, row => decodeRole(row, tenantId), row => row.grantId), [tenantId]);
  const page = usePaging<RoleGrant>(`${tenantApi}/${tenantId}/members/${memberId}/roles`, decode, {}, {
    initialPaging: { page: Number(query.get('rolePage') || 1), size: Number(query.get('roleSize') || 10) },
    onQueryChange: next => void navigate(replaceQuery(location.search, { rolePage: String(next.paging.page), roleSize: String(next.paging.size) })),
  });
  if (page.error) return <QueryFailure failure={page.failure} retry={page.refresh} />;
  return <section className="table-panel"><EvaluatedTime value={(page.data as EvaluatedPage<RoleGrant>).evaluatedAt} refresh={page.refresh} />
    <Table<RoleGrant> rowKey="grantId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1040 }}
      pagination={{ ...page.pagination, pageSizeOptions: [10, 20, 50, 100], showTotal: total => t('memberAccess.roleTotal', { total }) }} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('memberAccess.noRoles')} /> }} columns={[
        ...roleColumns<RoleGrant>(t), { title: t('memberAccess.column.currentAuthorization'), width: 280, render: (_, row) => <Effective effective={row.effective} reasons={row.reasons} /> },
      ]} /></section>;
}
function MemberPermissions({ tenantId, memberId }: { tenantId: string; memberId: string }) {
  const t = useT('access');
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search); const selected = query.get('permission');
  const decode = useMemo(() => (v: unknown) => decodeEvaluated(v, decodePermission, row => row.permissionId), []);
  const page = usePaging<MemberPermission>(`${tenantApi}/${tenantId}/members/${memberId}/permissions`, decode, {}, {
    initialPaging: { page: Number(query.get('permissionPage') || 1), size: Number(query.get('permissionSize') || 10) },
    onQueryChange: next => void navigate(replaceQuery(location.search, { permissionPage: String(next.paging.page), permissionSize: String(next.paging.size), permission: null, sourcePage: null, sourceSize: null })),
  });
  if (page.error) return <QueryFailure failure={page.failure} retry={page.refresh} />;
  return <section className="table-panel"><EvaluatedTime value={(page.data as EvaluatedPage<MemberPermission>).evaluatedAt} refresh={page.refresh} />
    <Table<MemberPermission> rowKey="permissionId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1100 }}
      pagination={{ ...page.pagination, pageSizeOptions: [10, 20, 50, 100], showTotal: total => t('memberAccess.permissionTotal', { total }) }} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('memberAccess.noPermissionSources')} /> }}
      expandable={{ expandedRowKeys: selected ? [selected] : [], columnTitle: t('memberAccess.column.sources'), columnWidth: 70,
        onExpand: (expanded, row) => void navigate(replaceQuery(location.search, { permission: expanded ? row.permissionId : null, sourcePage: null, sourceSize: null })),
        expandedRowRender: row => <PermissionSources key={`${row.permissionId}:${location.search}`} tenantId={tenantId} memberId={memberId} permission={row} />,
      }} columns={[
        { title: t('memberAccess.column.permissionCode'), dataIndex: 'permissionCode', width: 250, ellipsis: true }, { title: t('memberAccess.column.resource'), dataIndex: 'resourceType', width: 180, ellipsis: true },
        { title: t('memberAccess.column.action'), dataIndex: 'actionCode', width: 140, ellipsis: true }, { title: t('memberAccess.column.catalogStatus'), width: 105, render: (_, row) => <Status value={row.permissionStatus} /> },
        { title: t('memberAccess.column.effectiveSources'), width: 140, render: (_, row) => <Space>{row.effectiveSourceCount} / {row.sourceCount}</Space> },
        { title: t('memberAccess.column.currentAuthorization'), width: 280, render: (_, row) => <Effective effective={row.effective} reasons={row.reasons} /> },
      ]} /></section>;
}
function PermissionSources({ tenantId, memberId, permission }: { tenantId: string; memberId: string; permission: MemberPermission }) {
  const t = useT('access');
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search);
  const decode = useMemo(() => (v: unknown) => decodeEvaluated(v, row => decodeSource(row, tenantId), row => `${row.grantId}:${row.rolePermissionId}`), [tenantId]);
  const page = usePaging<PermissionSource>(`${tenantApi}/${tenantId}/members/${memberId}/permissions/${permission.permissionId}/sources`, decode, {}, {
    initialPaging: { page: Number(query.get('sourcePage') || 1), size: Number(query.get('sourceSize') || 10) },
    onQueryChange: next => void navigate(replaceQuery(location.search, { sourcePage: String(next.paging.page), sourceSize: String(next.paging.size) })),
  });
  return <div className="tenant-sources"><h3 className="tenant-ellipsis" title={permission.permissionCode}>{t('memberAccess.sourcesTitle', { permissionCode: permission.permissionCode })}</h3>
    {page.error ? <QueryFailure failure={page.failure} retry={page.refresh} /> : <><EvaluatedTime value={(page.data as EvaluatedPage<PermissionSource>).evaluatedAt} refresh={page.refresh} />
      <Table<PermissionSource> size="small" rowKey={row => `${row.grantId}:${row.rolePermissionId}`} tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1260 }}
        pagination={{ ...page.pagination, pageSizeOptions: [10, 20, 50, 100] }} locale={{ emptyText: <Alert type="info" title={t('memberAccess.noSourcesOnPage')} /> }} columns={[
          ...roleColumns<PermissionSource>(t), { title: t('memberAccess.column.permissionLink'), width: 110, render: (_, row) => <Status value={row.rolePermissionStatus} /> },
          { title: t('memberAccess.column.sourceValidity'), width: 300, render: (_, row) => <Effective effective={row.effective} reasons={row.reasons} /> },
        ]} /></>}
  </div>;
}
