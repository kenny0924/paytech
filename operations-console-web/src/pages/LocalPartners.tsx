import { Button, Empty, Form, Select, Table } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { QueryFailure } from '../tenantAccess/TenantAccessView';
import { LocalPartnerDetail } from '../masterData/LocalPartnerDetail';
import { RoleTags } from '../masterData/OperatingEntityDetail';
import { StatusTag } from '../masterData/MasterDataModals';
import { decodePartnerPage, isId, masterDataRoutes, pageSizes, partnerFilters, partnerRoles, partnerStatuses } from '../masterData/masterData';
import type { PartnerListItem } from '../masterData/masterData';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import '../configuration/configuration.css';
import '../qualification/qualification.css';
import '../masterData/masterData.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }

/** Local Partner（RS-07 / 08）：/master-data/local-partners 列表，/master-data/local-partners/:localPartnerId 详情；不提供新建。 */
export function LocalPartners(props: Props) {
  const location = useLocation();
  const parts = location.pathname.replace(/\/$/, '').split('/').slice(3);
  if (parts.length > 1 || (parts.length === 1 && !isId(parts[0]))) return <QueryFailure failure={new ApiError(404, 'NOT_FOUND')} />;
  return parts.length === 1 ? <LocalPartnerDetail key={parts[0]} localPartnerId={parts[0]} {...props} /> : <PartnerList key={location.search} />;
}

/** 列表：状态 / 角色筛选，筛选与分页写入 URL；行点击进入详情。 */
function PartnerList() {
  const t = useT('master-data'), tCommon = useT('common'), tNav = useT('nav');
  const location = useLocation(); const navigate = useNavigate(); const [form] = Form.useForm();
  const query = new URLSearchParams(location.search);
  const initial = partnerFilters({ status: query.get('status') ?? undefined, role: query.get('role') ?? undefined });
  const size = pageSizes.includes(Number(query.get('size'))) ? Number(query.get('size')) : 20;
  const page = usePaging<PartnerListItem>(`/entity-admin/v1/local-partners`, decodePartnerPage, initial, {
    initialPaging: { page: Math.max(1, Number(query.get('page')) || 1), size }, notifyOnError: false,
    onQueryChange: next => { void navigate(`?${new URLSearchParams({ ...next.filters, page: String(next.paging.page), size: String(next.paging.size) })}`); } });
  return <div className="configuration-page master-data-page">
    <div className="page-heading"><h1>{tNav('resource.OPERATIONS_LOCAL_PARTNERS')}</h1><Button onClick={page.refresh} loading={page.loading}>{tCommon('action.refresh')}</Button></div>
    <Form form={form} layout="vertical" initialValues={initial} onFinish={values => page.applyFilters(partnerFilters(values))}>
      <div className="master-data-search">
        <Form.Item name="status" label={t('field.status')}><Select allowClear placeholder={t('list.allStatuses')}
          options={partnerStatuses.map(value => ({ value, label: enumLabel(t, 'enum.partnerStatus', value) }))} /></Form.Item>
        <Form.Item name="role" label={t('partner.field.roles')}><Select allowClear placeholder={t('list.allRoles')}
          options={partnerRoles.map(value => ({ value, label: enumLabel(t, 'enum.partnerRole', value) }))} /></Form.Item>
        <div />
        <div className="configuration-actions"><Button type="primary" htmlType="submit">{t('list.search')}</Button>
          <Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>{t('list.reset')}</Button></div>
      </div>
    </Form>
    {page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} /> : <section className="table-panel">
      <Table<PartnerListItem> rowKey="localPartnerId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 900 }}
        onRow={row => ({ className: 'clickable-row', onClick: () => void navigate(masterDataRoutes.partner(row.localPartnerId)) })}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={Object.keys(initial).length ? t('list.noMatch') : t('partner.list.empty')} /> }}
        pagination={{ ...page.pagination, pageSizeOptions: pageSizes, showTotal: total => t('list.total', { total }) }} columns={[
          { title: t('partner.field.partnerNo'), width: 140, render: (_, row) => <span className="qualification-mono">{row.partnerNo}</span> },
          { title: t('partner.field.legalName'), ellipsis: true, render: (_, row) => row.legalName ?? '—' },
          { title: t('partner.field.roles'), width: 220, render: (_, row) => <RoleTags roles={row.roles} /> },
          { title: t('partner.field.providedEntityCount'), width: 130, render: (_, row) => row.providedEntityCount },
          { title: t('field.status'), width: 110, render: (_, row) => <StatusTag status={row.status} kind="partnerStatus" /> },
        ]} /></section>}
  </div>;
}
