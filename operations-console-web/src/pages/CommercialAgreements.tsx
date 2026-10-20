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
  agreementStatuses, agreementStatusLabels, agreementTypeLabels, agreementTypes, commercialButtons, commercialRoutes, entityNoPattern, hasButton, idPattern,
  isId, localTime,
} from '../commercial/commercial';
import { browserStorage, consolePaths, decodeAgreementPage, listFilters, rememberClient } from '../commercial/commercialConsole';
import type { AgreementListItem } from '../commercial/commercialConsole';
import { CommercialNote, StatusTag } from '../commercial/CommercialParts';
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
    <div className="page-heading"><h1>合同</h1>{canManage && <Button type="primary" onClick={() => setCreating(true)}>新建合同</Button>}</div>
    <Form form={form} name="commercial-agreement-filter" layout="vertical" onFinish={search}>
      <div className="commercial-filters">
        <Form.Item name="clientId" label="Client" rules={[{ pattern: idPattern, message: 'Client ID 是正十进制整数' }]}><ClientIdInput /></Form.Item>
        <Form.Item name="agreementNo" label="合同号（前缀）" rules={[{ pattern: /^[A-Za-z0-9._-]{1,64}$/, message: '字母数字及 . _ -' }]}>
          <Input allowClear maxLength={64} placeholder="AGR-AE001" /></Form.Item>
        <Form.Item name="agreementType" label="类型"><Select allowClear options={agreementTypes.map(value => ({ value, label: agreementTypeLabels[value] }))} /></Form.Item>
        <Form.Item name="status" label="状态"><Select mode="multiple" allowClear maxTagCount="responsive"
          options={agreementStatuses.map(value => ({ value, label: agreementStatusLabels[value] }))} /></Form.Item>
        <Form.Item name="entityNo" label="OE" rules={[{ pattern: entityNoPattern, message: '如 AE001' }]} normalize={(v?: string) => v?.toUpperCase()}>
          <Input allowClear maxLength={5} placeholder="AE001" /></Form.Item>
        <Form.Item name="localPartnerId" label="Local Partner" rules={[{ pattern: idPattern, message: '正十进制整数' }]}><Input allowClear maxLength={19} /></Form.Item>
        <div className="commercial-filter-actions">
          <Button type="primary" htmlType="submit">查询</Button>
          <Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>重置</Button>
        </div>
      </div>
    </Form>
    {page.failure ? <QueryFailure failure={page.failure} retry={page.refresh} /> : <section className="table-panel">
      <Table<AgreementListItem> rowKey="agreementId" size="middle" loading={page.loading} dataSource={page.data.items} pagination={page.pagination}
        scroll={{ x: 1180 }} onRow={row => ({ className: 'clickable-row', onClick: () => void navigate(commercialRoutes.agreement(row.agreementId)) })}
        locale={{ emptyText: '没有符合条件的合同' }} columns={[
          { title: '合同号', width: 170, render: (_, row) => <span className="mono">{row.agreementNo}</span> },
          { title: '名称', dataIndex: 'agreementName', ellipsis: true },
          { title: '类型', width: 110, render: (_, row) => agreementTypeLabels[row.agreementType] },
          { title: '模式', width: 200, render: (_, row) => row.businessMode ? `${row.businessMode} · ${row.fundMode ?? '—'}` : '—' },
          { title: 'Client / Partner', width: 190, render: (_, row) => <span className="mono">{row.clientId ?? row.localPartnerId ?? '—'}</span> },
          { title: 'OE', width: 80, render: (_, row) => row.entityNo ?? '—' },
          { title: '状态', width: 100, render: (_, row) => <StatusTag status={row.status} labels={agreementStatusLabels} /> },
          { title: '生效区间', width: 260, render: (_, row) => `${localTime(row.effectiveFrom)} ～ ${row.effectiveTo ? localTime(row.effectiveTo) : '未定'}` },
          { title: '更新时间', width: 150, render: (_, row) => localTime(row.updateTime) },
        ]} />
    </section>}
    <CommercialNote>Client 支持输入 ID、选择本机最近使用或审核通过的 Client；按名称搜索与 Party / OE 下拉待 client-master 提供运营查询接口。PARTNER 合同按 OE 筛选时匹配其覆盖 OE。</CommercialNote>
    {creating && <CreateAgreementModal onClose={() => setCreating(false)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={agreementId => { setCreating(false); void navigate(commercialRoutes.agreement(agreementId)); }} />}
  </div>;
}
