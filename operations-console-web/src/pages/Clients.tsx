import { Button, Empty, Form, Input, Select, Table } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/session';
import { usePaging } from '../hooks/usePaging';
import { QueryFailure, date, useDetail } from '../tenantAccess/TenantAccessView';
import { ClientDetailView } from '../masterData/ClientDetail';
import { StatusDot, tone } from '../clientReview/reviewParts';
import { decodeResponsiblePartners, isId, partnerName } from '../clientReview/clientReview';
import { clientAdminApi, clientFilters, clientRoutes, clientStatuses, decodeClientPage } from '../masterData/clients';
import type { ClientListItem } from '../masterData/clients';
import { pageSizes } from '../masterData/masterData';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import '../configuration/configuration.css';
import '../qualification/qualification.css';
import '../masterData/masterData.css';
import '../clientReview/clientReview.css';

/** Client：/master-data/clients 列表，/master-data/clients/:clientId 详情；只读。 */
export function Clients() {
  const location = useLocation();
  const parts = location.pathname.replace(/\/$/, '').split('/').slice(3);
  if (parts.length > 1 || (parts.length === 1 && !isId(parts[0]))) return <QueryFailure failure={new ApiError(404, 'NOT_FOUND')} />;
  return parts.length === 1 ? <ClientDetailView key={parts[0]} clientId={parts[0]} /> : <ClientList key={location.search} />;
}

/** 列表：状态、辖区、责任 PayFac 筛选写入 URL；PayFac 名称来自一次选项读取。 */
function ClientList() {
  const t = useT('master-data'), tCommon = useT('common'), tNav = useT('nav');
  const location = useLocation(); const navigate = useNavigate(); const [form] = Form.useForm();
  const query = new URLSearchParams(location.search);
  const initial = clientFilters({ status: query.get('status') ?? undefined, jurisdiction: query.get('jurisdiction') ?? undefined,
    responsiblePartnerId: query.get('responsiblePartnerId') ?? undefined });
  const size = pageSizes.includes(Number(query.get('size'))) ? Number(query.get('size')) : 20;
  const partners = useDetail(`${clientAdminApi}/responsible-partners`, decodeResponsiblePartners);
  const page = usePaging<ClientListItem>(`${clientAdminApi}/clients`, decodeClientPage, initial, {
    initialPaging: { page: Math.max(1, Number(query.get('page')) || 1), size }, notifyOnError: false,
    onQueryChange: next => { void navigate(`?${new URLSearchParams({ ...next.filters, page: String(next.paging.page), size: String(next.paging.size) })}`); } });
  const partnerItems = partners.data ?? [];
  return <div className="configuration-page master-data-page">
    <div className="page-heading"><h1>{tNav('resource.OPERATIONS_CLIENTS')}</h1><Button onClick={page.refresh} loading={page.loading}>{tCommon('action.refresh')}</Button></div>
    <Form form={form} layout="vertical" initialValues={initial} onFinish={values => page.applyFilters(clientFilters(values))}>
      <div className="master-data-search">
        <Form.Item name="status" label={t('field.status')}><Select allowClear placeholder={t('list.allStatuses')}
          options={clientStatuses.map(value => ({ value, label: enumLabel(t, 'client.enum.status', value) }))} /></Form.Item>
        <Form.Item name="jurisdiction" label={t('client.field.jurisdiction')} rules={[{ pattern: /^[A-Z][A-Z0-9-]{1,15}$/, message: t('client.jurisdictionInvalid') }]}>
          <Input allowClear maxLength={16} placeholder="AE" /></Form.Item>
        <Form.Item name="responsiblePartnerId" label={t('client.field.responsible')}><Select allowClear showSearch optionFilterProp="label" loading={partners.loading}
          placeholder={t('client.allPartners')} options={partnerItems.map(p => ({ value: p.localPartnerId, label: `${p.legalName ?? p.partnerNo} · ${p.partnerNo}` }))} /></Form.Item>
        <div className="configuration-actions"><Button type="primary" htmlType="submit">{t('list.search')}</Button>
          <Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>{t('list.reset')}</Button></div>
      </div>
    </Form>
    {page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} /> : <section className="table-panel">
      <Table<ClientListItem> rowKey="clientId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 980 }}
        onRow={row => ({ className: 'clickable-row', onClick: () => void navigate(clientRoutes.detail(row.clientId)) })}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={Object.keys(initial).length ? t('list.noMatch') : t('client.list.empty')} /> }}
        pagination={{ ...page.pagination, pageSizeOptions: pageSizes, showTotal: total => t('list.total', { total }) }} columns={[
          { title: t('client.field.clientNo'), width: 140, render: (_, row) => <span className="qualification-mono">{row.clientNo}</span> },
          { title: t('client.field.legalName'), ellipsis: true, render: (_, row) => row.legalName ?? '—' },
          { title: t('client.field.clientType'), width: 120, render: (_, row) => enumLabel(t, 'client.enum.clientType', row.clientType) },
          { title: t('client.field.jurisdiction'), width: 90, render: (_, row) => row.jurisdictionCode ?? '—' },
          { title: t('client.field.responsible'), width: 200, ellipsis: true, render: (_, row) => partnerName(partnerItems, row.responsiblePartnerId) ?? <span className="muted">{t('client.platformOwned')}</span> },
          { title: t('field.status'), width: 120, render: (_, row) => <StatusDot tone={tone(row.status)}>{enumLabel(t, 'client.enum.status', row.status)}</StatusDot> },
          { title: t('client.field.latestApplication'), width: 150, render: (_, row) => row.latestApplicationStatus ? enumLabel(t, 'client.enum.applicationStatus', row.latestApplicationStatus) : '—' },
          { title: t('client.field.createdTime'), width: 170, render: (_, row) => date(row.createdTime) },
        ]} /></section>}
  </div>;
}
