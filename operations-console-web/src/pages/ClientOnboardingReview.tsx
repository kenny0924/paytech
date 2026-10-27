import { Button, Empty, Form, Input, Select, Table } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { QueryFailure, date, useDetail } from '../tenantAccess/TenantAccessView';
import { ClientReviewDetail } from '../clientReview/ClientReviewDetail';
import { StatusDot, tone } from '../clientReview/reviewParts';
import { caseStatuses, clientQualificationTypes, decodeCasePage, decodeResponsiblePartners, filters, isId, partnerName, platformOwnedFilter, reviewApi, reviewRoute } from '../clientReview/clientReview';
import type { ReviewCase } from '../clientReview/clientReview';
import '../configuration/configuration.css';
import '../clientReview/clientReview.css';
import { enumLabel } from '../i18n/index';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';

interface Props { resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }
const keys = ['status', 'qualificationType', 'applicationId', 'responsiblePartnerId', 'jurisdictionCode'];
const options = (t: Translate, prefix: string, codes: readonly string[]) => codes.map(value => ({ value, label: enumLabel(t, prefix, value) }));

/** O01 列表与案件工作台；路由 /client-onboarding/reviews[/:caseId]。 */
export function ClientOnboardingReview(props: Props) {
  const location = useLocation(); const parts = location.pathname.replace(/\/$/, '').split('/').slice(3);
  if (parts.length > 1 || parts.length === 1 && !isId(parts[0])) return <QueryFailure failure={new ApiError(404, 'NOT_FOUND')} />;
  return parts.length === 1 ? <ClientReviewDetail key={parts[0]} caseId={parts[0]} {...props} /> : <ReviewList key={location.search} />;
}

/** 列表：状态、类型、责任 PayFac、申请 ID 筛选写入 URL；PayFac 名称来自一次选项读取，不按行请求。 */
function ReviewList() {
  const t = useT('client-review'), tCommon = useT('common'), tNav = useT('nav');
  const location = useLocation(); const navigate = useNavigate(); const query = new URLSearchParams(location.search); const [form] = Form.useForm();
  const initial = Object.fromEntries(keys.flatMap(k => query.get(k) ? [[k, query.get(k)!]] : []));
  const partners = useDetail(`${reviewApi}/responsible-partners`, decodeResponsiblePartners);
  const page = usePaging<ReviewCase>(`${reviewApi}/cases`, decodeCasePage, initial, {
    initialPaging: { page: Number(query.get('page') || 1), size: Number(query.get('size') || 20) }, notifyOnError: false,
    onQueryChange: next => { void navigate(`?${new URLSearchParams({ ...next.filters, page: String(next.paging.page), size: String(next.paging.size) })}`); } });
  const partnerItems = partners.data ?? [];
  return <div className="configuration-page client-review-page"><div className="page-heading"><h1>{tNav('resource.OPERATIONS_CLIENT_ONBOARDING_REVIEW')}</h1><Button onClick={page.refresh} loading={page.loading}>{tCommon('action.refresh')}</Button></div>
    <Form form={form} layout="vertical" initialValues={initial} onFinish={values => page.applyFilters(filters(values))}>
      <div className="review-search">
        <Form.Item name="applicationId" label={t('list.filter.applicationId')} rules={[{ pattern: /^[1-9][0-9]{0,18}$/, message: t('list.filter.applicationIdInvalid') }]}><Input allowClear maxLength={19} /></Form.Item>
        <Form.Item name="qualificationType" label={t('field.clientType')}><Select allowClear placeholder={t('list.filter.allTypes')} options={options(t, 'enum.qualification', clientQualificationTypes)} /></Form.Item>
        <Form.Item name="status" label={t('list.filter.status')}><Select allowClear placeholder={t('list.filter.allStatuses')} options={options(t, 'enum.caseStatus', caseStatuses)} /></Form.Item>
        <Form.Item name="responsiblePartnerId" label={t('list.filter.responsible')}><Select allowClear showSearch optionFilterProp="label" loading={partners.loading}
          placeholder={t('list.filter.allPartners')} options={[{ value: platformOwnedFilter, label: t('detail.platformOwnedShort') },
            ...partnerItems.map(p => ({ value: p.localPartnerId, label: `${p.legalName ?? p.partnerNo} · ${p.partnerNo}` }))]} /></Form.Item>
        <Form.Item name="jurisdictionCode" label={t('field.jurisdiction')} rules={[{ pattern: /^[A-Z][A-Z0-9-]{1,15}$/, message: t('list.filter.jurisdictionInvalid') }]}>
          <Input allowClear maxLength={16} placeholder="AE" onChange={event => form.setFieldValue('jurisdictionCode', event.target.value.toUpperCase())} /></Form.Item>
        <div className="configuration-actions"><Button type="primary" htmlType="submit">{t('list.filter.search')}</Button><Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>{t('list.filter.reset')}</Button></div>
      </div>
    </Form>
    {page.error ? <QueryFailure failure={page.failure} retry={page.refresh} /> : <section className="table-panel">
      <Table<ReviewCase> rowKey="caseId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1360 }}
        onRow={row => ({ style: { cursor: 'pointer' }, onClick: () => void navigate(`${reviewRoute}/${row.caseId}`) })}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={Object.keys(initial).length ? t('list.noMatch') : t('list.empty')} /> }}
        pagination={{ ...page.pagination, pageSizeOptions: [10, 20, 50, 100], showTotal: total => t('list.total', { total }) }} columns={[
          { title: t('field.case'), width: 220, render: (_, row) => <div><div>{row.caseNo}</div><div className="cell-meta">{t('list.caseMeta', { applicationId: row.applicationId, clientId: row.clientId })}</div></div> },
          { title: t('field.clientType'), width: 110, render: (_, row) => enumLabel(t, 'enum.qualification', row.qualificationType) },
          { title: t('field.jurisdiction'), width: 120, render: (_, row) => row.jurisdictionCode ?? '—' },
          { title: t('list.column.status'), width: 150, render: (_, row) => <div><StatusDot tone={tone(row.status)}>{enumLabel(t, 'enum.caseStatus', row.status)}</StatusDot>
            {row.roundNo && row.roundNo > 1 && <div className="cell-meta">{t('detail.round', { round: row.roundNo })}</div>}</div> },
          { title: t('field.riskRating'), width: 110, render: (_, row) => row.riskRating ? <StatusDot tone={tone(row.riskRating)}>{enumLabel(t, 'enum.riskRating', row.riskRating)}</StatusDot> : '—' },
          { title: t('list.filter.responsible'), width: 180, ellipsis: true, render: (_, row) => partnerName(partnerItems, row.responsiblePartnerId) ?? <span className="muted">{t('detail.platformOwnedShort')}</span> },
          { title: t('field.submittedAt'), width: 170, render: (_, row) => date(row.submittedAt) },
          { title: t('field.decidedAt'), width: 170, render: (_, row) => row.decidedAt ? date(row.decidedAt) : '—' },
          { title: t('list.column.actions'), width: 110, fixed: 'right', render: (_, row) => <Button type="link" onClick={() => void navigate(`${reviewRoute}/${row.caseId}`)}>{t('list.action.review')}</Button> },
        ]} /></section>}
  </div>;
}
