import { useState } from 'react';
import { Button, Form, Input, Select, Table } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/session';
import type { PagingFilters } from '../api/paging';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { QueryFailure } from '../tenantAccess/TenantAccessView';
import { AgreementDetail } from '../commercial/AgreementDetail';
import { CreateAgreementModal } from '../commercial/AgreementModals';
import { ClientIdInput } from '../commercial/ClientIdInput';
import {
  agreementStatuses, agreementTypes, commercialButtons, commercialRoutes, entityNoPattern, hasButton, idPattern, isId, localTime,
} from '../commercial/commercial';
import { browserStorage, consolePaths, decodeAgreementPage, listFilters, rememberClient } from '../commercial/commercialConsole';
import type { AgreementListItem } from '../commercial/commercialConsole';
import { CommercialNote, StatusTag } from '../commercial/CommercialParts';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import '../configuration/configuration.css';
import '../commercial/commercial.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
interface FilterValues { clientId?: string; agreementNo?: string; agreementType?: string; status?: string[]; entityNo?: string; localPartnerId?: string }

/** 合同：/commercial/agreements 列表与新建，/commercial/agreements/:agreementId 详情。 */
export function CommercialAgreements(props: Props) {
  const location = useLocation();
  const parts = location.pathname.replace(/\/$/, '').split('/').slice(3);
  if (parts.length > 1 || (parts.length === 1 && !isId(parts[0]))) return <QueryFailure failure={new ApiError(404, 'NOT_FOUND')} />;
  return parts.length === 1 ? <AgreementDetail key={parts[0]} agreementId={parts[0]} {...props} /> : <AgreementList {...props} />;
}

/** 合同列表：Client / 合同号前缀 / 类型 / 状态（多选）/ OE / Local Partner 筛选，按创建时间倒序分页；行点击进入详情。 */
function AgreementList({ resources, onAuthorizationChanged }: Props) {
  const t = useT('commercial'), tNav = useT('nav');
  const navigate = useNavigate();
  const [form] = Form.useForm<FilterValues>();
  const [creating, setCreating] = useState(false);
  const page = usePaging<AgreementListItem, PagingFilters>(consolePaths.agreements, decodeAgreementPage, {}, { notifyOnError: false });
  const canManage = hasButton(resources, commercialButtons.agreementManage);
  const search = (values: FilterValues) => {
    if (values.clientId?.trim()) rememberClient(browserStorage(), values.clientId);
    page.applyFilters(listFilters({ clientId: values.clientId, agreementNo: values.agreementNo, agreementType: values.agreementType,
      status: values.status, entityNo: values.entityNo?.toUpperCase(), localPartnerId: values.localPartnerId }));
  };
  return <div className="configuration-page commercial-page">
    <div className="page-heading"><h1>{tNav('resource.OPERATIONS_COMMERCIAL_AGREEMENTS')}</h1></div>
    <div className="commercial-toolbar">
      <Form form={form} name="commercial-agreement-filter" layout="vertical" onFinish={search}>
        <div className="commercial-filters">
          <Form.Item name="clientId" label="Client" rules={[{ pattern: idPattern, message: t('list.clientIdPattern') }]}><ClientIdInput /></Form.Item>
          <Form.Item name="agreementNo" label={t('agreements.filter.agreementNo')} rules={[{ pattern: /^[A-Za-z0-9._-]{1,64}$/, message: t('form.codePattern') }]}>
            <Input allowClear maxLength={64} placeholder="AGR-AE001" /></Form.Item>
          <Form.Item name="agreementType" label={t('agreement.field.type')}><Select allowClear options={agreementTypes.map(value => ({ value, label: enumLabel(t, 'enum.agreementType', value) }))} /></Form.Item>
          <Form.Item name="status" label={t('shared.column.status')}><Select mode="multiple" allowClear maxTagCount="responsive"
            options={agreementStatuses.map(value => ({ value, label: enumLabel(t, 'enum.agreementStatus', value) }))} /></Form.Item>
          <Form.Item name="entityNo" label="OE" rules={[{ pattern: entityNoPattern, message: t('list.entityNoPattern') }]} normalize={(v?: string) => v?.toUpperCase()}>
            <Input allowClear maxLength={5} placeholder="AE001" /></Form.Item>
          <Form.Item name="localPartnerId" label="Local Partner" rules={[{ pattern: idPattern, message: t('form.idPattern') }]}><Input allowClear maxLength={19} /></Form.Item>
          <div className="commercial-filter-actions">
            <Button type="primary" htmlType="submit">{t('list.search')}</Button>
            <Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>{t('list.reset')}</Button>
          </div>
        </div>
      </Form>
      {canManage && <Button type="primary" onClick={() => setCreating(true)}>{t('agreement.create.title')}</Button>}</div>
    {page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} /> : <section className="table-panel">
      <Table<AgreementListItem> rowKey="agreementId" size="middle" loading={page.loading} dataSource={page.data.items} pagination={page.pagination}
        scroll={{ x: 1180 }} onRow={row => ({ className: 'clickable-row', onClick: () => void navigate(commercialRoutes.agreement(row.agreementId)) })}
        locale={{ emptyText: t('agreements.empty') }} columns={[
          { title: t('agreements.column.agreementNo'), width: 170, render: (_, row) => <span className="mono">{row.agreementNo}</span> },
          { title: t('catalogDrawer.name'), dataIndex: 'agreementName', ellipsis: true },
          { title: t('agreement.field.type'), width: 110, render: (_, row) => enumLabel(t, 'enum.agreementType', row.agreementType) },
          { title: t('agreement.field.mode'), width: 200, render: (_, row) => row.businessMode ? `${row.businessMode} · ${row.fundMode ?? '—'}` : '—' },
          { title: 'Client / Partner', width: 190, render: (_, row) => <span className="mono">{row.clientId ?? row.localPartnerId ?? '—'}</span> },
          { title: 'OE', width: 80, render: (_, row) => row.entityNo ?? '—' },
          { title: t('shared.column.status'), width: 100, render: (_, row) => <StatusTag status={row.status} kind="agreementStatus" /> },
          { title: t('agreement.field.effectiveRange'), width: 260, render: (_, row) => t('shared.period', { from: localTime(row.effectiveFrom),
            to: row.effectiveTo ? localTime(row.effectiveTo) : t('shared.openEnded') }) },
          { title: t('shared.column.updatedAt'), width: 150, render: (_, row) => localTime(row.updateTime) },
        ]} />
    </section>}
    <CommercialNote>{t('agreements.note')}</CommercialNote>
    {creating && <CreateAgreementModal onClose={() => setCreating(false)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={agreementId => { setCreating(false); void navigate(commercialRoutes.agreement(agreementId)); }} />}
  </div>;
}
