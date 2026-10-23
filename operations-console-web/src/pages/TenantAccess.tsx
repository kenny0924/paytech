import { useMemo, useState } from 'react';
import { Alert, Button, Dropdown, Empty, Form, Input, Select, Space, Table, Tabs, Tag } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { enumLabel } from '../i18n/index';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';
import { buildMenuTree, menuPages } from '../navigation/menu';
import { decodeTenant, decodeMember, decodePage, filters, isId, memberStatuses, memberTypes, ownerStatuses, replaceQuery, securityStatuses, tenantApi, tenantTypes } from '../tenantAccess/tenantAccess';
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
const options = (t: Translate, prefix: string, codes: readonly string[]) => codes.map(value => ({ value, label: enumLabel(t, prefix, value) }));
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
  const t = useT('access'), tCommon = useT('common'), tNav = useT('nav');
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search); const [form] = Form.useForm();
  const initial = Object.fromEntries(tenantKeys.flatMap(k => query.get(k) ? [[k, query.get(k)!]] : []));
  const page = usePaging<Tenant>(tenantApi, tenantPage, initial, { initialPaging: { page: Number(query.get('page') || 1), size: Number(query.get('size') || 10) },
    onQueryChange: next => { void navigate(`?${new URLSearchParams({ ...next.filters, page: String(next.paging.page), size: String(next.paging.size) })}`); } });
  const [changing, setChanging] = useState<Tenant | null>(null); const [saved, setSaved] = useState(false);
  const canManage = has(resources, 'OPERATIONS_TENANT_ACCESS_MANAGE');
  return <div className="configuration-page tenant-access-page"><div className="page-heading"><h1>{tNav('resource.OPERATIONS_TENANT_ACCESS')}</h1><Button onClick={page.refresh} loading={page.loading}>{tCommon('action.refresh')}</Button></div>
    <Form form={form} layout="vertical" initialValues={initial} onFinish={values => page.applyFilters(filters(values))}>
      <div className="tenant-search"><Form.Item name="tenantType" label={t('tenants.filter.tenantType')}><Select allowClear placeholder={t('tenants.filter.allTypes')} options={options(t, 'enum.tenantType', tenantTypes)} /></Form.Item>
        <Form.Item name="tenantName" label={t('tenants.filter.name')}><Input allowClear maxLength={255} placeholder={t('tenants.filter.namePlaceholder')} /></Form.Item>
        <Form.Item name="ownerStatus" label={t('tenant.ownerStatus')}><Select allowClear placeholder={t('tenants.filter.allStatuses')} options={options(t, 'enum.tenantOwnerStatus', ownerStatuses)} /></Form.Item>
        <Form.Item name="securityStatus" label={t('tenant.securityStatus')}><Select allowClear placeholder={t('tenants.filter.allStatuses')} options={options(t, 'enum.tenantSecurityStatus', securityStatuses)} /></Form.Item>
        <div className="configuration-actions"><Button type="primary" htmlType="submit">{t('tenants.filter.search')}</Button><Button onClick={() => page.applyFilters({})}>{t('tenants.filter.reset')}</Button></div>
      </div><div className="tenant-id-search"><Form.Item name="tenantId" label={t('tenants.filter.tenantId')}><Input allowClear maxLength={19} /></Form.Item>
        <Form.Item name="ownerRefId" label={t('tenants.filter.ownerRefId')}><Input allowClear maxLength={19} /></Form.Item></div>
    </Form>
    {page.error ? <QueryFailure failure={page.failure} retry={page.refresh} completed={saved} /> : <section className="table-panel">
      <Table<Tenant> rowKey="tenantId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1120 }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={Object.keys(initial).length ? t('tenants.noMatch') : t('tenants.empty')} /> }}
        pagination={{ ...page.pagination, pageSizeOptions: [10, 20, 50, 100], showTotal: total => t('tenants.total', { total }) }} columns={[
          { title: t('tenants.column.tenant'), width: 265, ellipsis: true, render: (_, row) => <div><div className="tenant-ellipsis" title={row.tenantName}>{row.tenantName}</div><div className="cell-meta">{t('tenants.rowMeta', { tenantId: row.tenantId, ownerRefId: row.ownerRefId })}</div></div> },
          { title: t('tenants.column.type'), width: 105, render: (_, row) => enumLabel(t, 'enum.tenantType', row.tenantType) },
          { title: t('tenant.ownerStatus'), width: 120, render: (_, row) => enumLabel(t, 'enum.tenantOwnerStatus', row.ownerStatus) },
          { title: t('tenant.securityStatus'), width: 100, render: (_, row) => enumLabel(t, 'enum.tenantSecurityStatus', row.securityStatus) },
          { title: t('tenants.column.accessConditions'), width: 300, render: (_, row) => <div><Tag color={row.tenantAccessAllowed ? 'green' : 'orange'}>{row.tenantAccessAllowed ? t('tenants.met') : t('tenants.notMet')}</Tag><Reasons reasons={row.restrictionReasons} /></div> },
          { title: t('tenants.column.actions'), width: 100, fixed: 'right', render: (_, row) => <Dropdown trigger={['click', 'hover']} menu={{ items: [
            { key: 'detail', label: t('tenants.action.details'), onClick: () => void navigate(`/tenant-access/${row.tenantId}`) },
            ...(canManage ? [{ key: 'security', label: row.securityStatus === 'ACTIVE' ? t('tenants.action.suspend') : t('tenants.action.restore'), danger: row.securityStatus === 'ACTIVE', onClick: () => setChanging(row) }] : []),
          ] }}><Button size="small">{t('tenants.action.menu')}</Button></Dropdown> },
        ]} /></section>}
    {changing && <ChangeSecurity tenant={changing} onClose={() => setChanging(null)} onSaved={() => { setChanging(null); setSaved(true); page.refresh(); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}
function TenantDetails({ resources, onAuthorizationChanged, tenantId }: Props & { tenantId: string }) {
  const t = useT('access'), tCommon = useT('common');
  const location = useLocation(); const navigate = useNavigate(); const decode = useMemo(() => (v: unknown) => decodeTenant(v, tenantId), [tenantId]);
  const detail = useDetail(`${tenantApi}/${tenantId}`, decode); const [changing, setChanging] = useState(false); const [saved, setSaved] = useState(false);
  const canManage = has(resources, 'OPERATIONS_TENANT_ACCESS_MANAGE'); const canMembers = has(resources, 'OPERATIONS_TENANT_MEMBER_READ');
  const canAudit = menuPages(buildMenuTree(resources)).some(m => m.componentKey === 'OPERATIONS_AUDIT');
  const tab = new URLSearchParams(location.search).get('tab') === 'audit' && canAudit ? 'audit' : 'members';
  if (detail.loading) return <Loading />;
  if (detail.failure || !detail.data) return <QueryFailure failure={detail.failure} retry={detail.refresh} completed={saved} />;
  const tenant = detail.data;
  return <div className="configuration-page tenant-access-page"><div className="page-heading"><h1>{t('tenantDetails.title')}</h1><Space>
    <Button onClick={detail.refresh}>{tCommon('action.refresh')}</Button>{canManage && <Button danger={tenant.securityStatus === 'ACTIVE'} onClick={() => setChanging(true)}>{tenant.securityStatus === 'ACTIVE' ? t('tenants.action.suspend') : t('tenants.action.restore')}</Button>}</Space></div>
    <TenantSummary tenant={tenant} />
    <Tabs activeKey={tab} onChange={tab => void navigate(replaceQuery(location.search, { tab }))} items={[{ key: 'members', label: t('tenantDetails.tab.members') }, ...(canAudit ? [{ key: 'audit', label: t('tenantDetails.tab.audit') }] : [])]} />
    {tab === 'audit' ? <Audit objectFilter={{ resourceType: 'ACCESS_TENANT', resourceId: tenantId }} embedded /> : canMembers
      ? <TenantMembers key={location.search} tenantId={tenantId} /> : <Alert showIcon type="info" title={t('tenantDetails.noMemberAccess')} description={t('tenantDetails.noMemberAccessHint')} />}
    {changing && <ChangeSecurity tenant={tenant} onClose={() => setChanging(false)} onSaved={() => { setChanging(false); setSaved(true); detail.refresh(); }} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}
function TenantMembers({ tenantId }: { tenantId: string }) {
  const t = useT('access');
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search); const [form] = Form.useForm();
  const initial = Object.fromEntries(memberKeys.flatMap(k => query.get(k) ? [[k, query.get(k)!]] : []));
  const decode = useMemo(() => (value: unknown) => decodePage(value, v => decodeMember(v, tenantId), v => v.memberId), [tenantId]);
  const page = usePaging<Member>(`${tenantApi}/${tenantId}/members`, decode, initial, {
    initialPaging: { page: Number(query.get('page') || 1), size: Number(query.get('size') || 10) },
    onQueryChange: next => void navigate(replaceQuery(location.search, { ...Object.fromEntries(memberKeys.map(k => [k, null])), ...next.filters, page: String(next.paging.page), size: String(next.paging.size) })),
  });
  return <><Form form={form} layout="vertical" initialValues={initial} onFinish={values => page.applyFilters(filters(values))}>
    <div className="tenant-member-search"><Form.Item name="emailMasked" label={t('members.filter.emailMasked')}><Input allowClear maxLength={255} placeholder={t('members.filter.emailMaskedPlaceholder')} /></Form.Item>
      <Form.Item name="accountId" label={t('member.accountId')}><Input allowClear maxLength={19} /></Form.Item>
      <Form.Item name="memberType" label={t('member.memberType')}><Select allowClear placeholder={t('members.filter.all')} options={options(t, 'enum.memberType', memberTypes)} /></Form.Item>
      <Form.Item name="memberStatus" label={t('member.memberStatus')}><Select allowClear placeholder={t('members.filter.all')} options={options(t, 'enum.memberStatus', memberStatuses)} /></Form.Item>
      <div className="configuration-actions"><Button type="primary" htmlType="submit">{t('tenants.filter.search')}</Button><Button onClick={() => page.applyFilters({})}>{t('tenants.filter.reset')}</Button></div></div></Form>
    {page.error ? <QueryFailure failure={page.failure} retry={page.refresh} /> : <Table<Member> rowKey="memberId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 950 }}
      pagination={{ ...page.pagination, pageSizeOptions: [10, 20, 50, 100], showTotal: total => t('members.total', { total }) }} locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={Object.keys(initial).length ? t('members.noMatch') : t('members.empty')} /> }} columns={[
        { title: t('members.column.emailOrAccount'), width: 270, ellipsis: true, render: (_, row) => <div><div className="tenant-ellipsis" title={row.emailMasked || t('members.noEmail')}>{row.emailMasked || t('members.noEmail')}</div><div className="cell-meta">{row.accountId}</div></div> },
        { title: t('member.memberId'), dataIndex: 'memberId', width: 200, ellipsis: true }, { title: t('tenants.column.type'), width: 100, render: (_, row) => enumLabel(t, 'enum.memberType', row.memberType) },
        { title: t('member.accountStatus'), width: 125, render: (_, row) => <Status value={row.accountStatus} /> }, { title: t('member.memberStatus'), width: 125, render: (_, row) => <Status value={row.memberStatus} /> },
        { title: t('tenants.column.actions'), width: 115, fixed: 'right', render: (_, row) => <Button type="link" onClick={() => void navigate(`/tenant-access/${tenantId}/members/${row.memberId}`)}>{t('members.action.viewPermissions')}</Button> },
      ]} />}
  </>;
}
