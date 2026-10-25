import { Button, Empty, Form, Input, Select, Table, Tag } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { QueryFailure } from '../tenantAccess/TenantAccessView';
import { OperatingEntityDetail, tierColors } from '../masterData/OperatingEntityDetail';
import { StatusTag } from '../masterData/MasterDataModals';
import { decodeEntityPage, entityFilters, entityNoPattern, entityStatuses, masterDataRoutes, pageSizes, sourceTypes } from '../masterData/masterData';
import type { EntityListItem } from '../masterData/masterData';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import '../configuration/configuration.css';
import '../qualification/qualification.css';
import '../masterData/masterData.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }

/** 经营主体（RS-04 / 05）：/master-data/operating-entities 列表，/master-data/operating-entities/:entityNo 详情；不提供新建。 */
export function OperatingEntities(props: Props) {
  const location = useLocation();
  const parts = location.pathname.replace(/\/$/, '').split('/').slice(3);
  if (parts.length > 1 || (parts.length === 1 && !entityNoPattern.test(parts[0]))) return <QueryFailure failure={new ApiError(404, 'NOT_FOUND')} />;
  return parts.length === 1 ? <OperatingEntityDetail key={parts[0]} entityNo={parts[0]} {...props} /> : <EntityList key={location.search} />;
}

/** 列表：状态 / 来源 / 国家筛选，筛选与分页写入 URL；行点击进入详情。 */
function EntityList() {
  const t = useT('master-data'), tCommon = useT('common'), tNav = useT('nav');
  const location = useLocation(); const navigate = useNavigate(); const [form] = Form.useForm();
  const query = new URLSearchParams(location.search);
  const initial = entityFilters({ status: query.get('status') ?? undefined, sourceType: query.get('sourceType') ?? undefined, country: query.get('country') ?? undefined });
  const size = pageSizes.includes(Number(query.get('size'))) ? Number(query.get('size')) : 20;
  const page = usePaging<EntityListItem>(`/entity-admin/v1/operating-entities`, decodeEntityPage, initial, {
    initialPaging: { page: Math.max(1, Number(query.get('page')) || 1), size }, notifyOnError: false,
    onQueryChange: next => { void navigate(`?${new URLSearchParams({ ...next.filters, page: String(next.paging.page), size: String(next.paging.size) })}`); } });
  return <div className="configuration-page master-data-page">
    <div className="page-heading"><h1>{tNav('resource.OPERATIONS_OPERATING_ENTITIES')}</h1><Button onClick={page.refresh} loading={page.loading}>{tCommon('action.refresh')}</Button></div>
    <Form form={form} layout="vertical" initialValues={initial} onFinish={values => page.applyFilters(entityFilters(values))}>
      <div className="master-data-search">
        <Form.Item name="status" label={t('field.status')}><Select allowClear placeholder={t('list.allStatuses')}
          options={entityStatuses.map(value => ({ value, label: enumLabel(t, 'enum.entityStatus', value) }))} /></Form.Item>
        <Form.Item name="sourceType" label={t('entity.field.sourceType')}><Select allowClear placeholder={t('list.allSources')}
          options={sourceTypes.map(value => ({ value, label: enumLabel(t, 'enum.sourceType', value) }))} /></Form.Item>
        <Form.Item name="country" label={t('entity.field.operatingCountry')} normalize={(v?: string) => v?.toUpperCase()}
          rules={[{ pattern: /^[A-Z]{2}$/, message: t('list.countryInvalid') }]}><Input allowClear maxLength={2} placeholder="AE" /></Form.Item>
        <div className="configuration-actions"><Button type="primary" htmlType="submit">{t('list.search')}</Button>
          <Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>{t('list.reset')}</Button></div>
      </div>
    </Form>
    {page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} /> : <section className="table-panel">
      <Table<EntityListItem> rowKey="entityNo" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1040 }}
        onRow={row => ({ className: 'clickable-row', onClick: () => void navigate(masterDataRoutes.entity(row.entityNo)) })}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={Object.keys(initial).length ? t('list.noMatch') : t('entity.list.empty')} /> }}
        pagination={{ ...page.pagination, pageSizeOptions: pageSizes, showTotal: total => t('list.total', { total }) }} columns={[
          { title: t('entity.field.entityNo'), width: 100, render: (_, row) => <span className="qualification-mono">{row.entityNo}</span> },
          { title: t('entity.field.legalName'), ellipsis: true, render: (_, row) => row.legalName ?? '—' },
          { title: t('entity.field.operatingCountry'), width: 100, render: (_, row) => row.operatingCountry },
          { title: t('entity.field.sourceType'), width: 110, render: (_, row) => row.sourceType ? enumLabel(t, 'enum.sourceType', row.sourceType) : '—' },
          { title: t('acquiring.title'), width: 200, render: (_, row) => <Tag color={tierColors[row.acquiringQualification] ?? 'default'}>{enumLabel(t, 'enum.acquiringQualification', row.acquiringQualification)}</Tag> },
          { title: t('entity.provider.title'), width: 200, ellipsis: true, render: (_, row) => row.provider ? <Button type="link" size="small" style={{ padding: 0 }}
            onClick={event => { event.stopPropagation(); void navigate(masterDataRoutes.partner(row.provider!.localPartnerId)); }}>{row.provider.legalName ?? row.provider.localPartnerId}</Button> : '—' },
          { title: t('field.status'), width: 110, render: (_, row) => <StatusTag status={row.status} kind="entityStatus" /> },
        ]} /></section>}
  </div>;
}
