import { useEffect, useState } from 'react';
import { Button, Empty, Form, Input, Modal, Segmented, Select, Switch, Table } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import { useLocation, useNavigate } from 'react-router-dom';
import { ApiError, session } from '../api/session';
import type { ConsoleResource } from '../api/types';
import { usePaging } from '../hooks/usePaging';
import { QueryFailure } from '../tenantAccess/TenantAccessView';
import { enumLabel, formatShortDateTime, tOr } from '../i18n/index';
import { useT } from '../i18n/react';
import { CrrBadge, HighRiskMark, StatusBadge, mccText } from '../underwriting/UnderwritingParts';
import { UnderwritingDetail } from '../underwriting/UnderwritingDetail';
import { NewUnderwriting } from '../underwriting/UnderwritingWorkspace';
import {
  crrs, decisionSources, decodeStatistics, decodeSummaryPage, isUnderwritingId, listFilters, listTabs, money, parseListQuery, permissionsOf, underwritingPaths, underwritingRoutes,
} from '../underwriting/underwriting';
import type { ListQuery, ListTab, Statistics, UnderwritingSummary } from '../underwriting/underwriting';
import '../underwriting/underwriting.css';

interface Props { resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }

/** 风控 · 承保（REQ-03）：/risk/underwriting 列表，/new?clientId= 新建，/:underwritingId 版本详情（草稿编辑、复核、只读）。 */
export function RiskUnderwriting(props: Props) {
  const location = useLocation();
  const parts = location.pathname.replace(/\/$/, '').split('/').slice(3);
  if (parts.length > 1 || (parts.length === 1 && parts[0] !== 'new' && !isUnderwritingId(parts[0]))) return <QueryFailure failure={new ApiError(404, 'NOT_FOUND')} />;
  if (parts[0] === 'new') {
    const clientId = new URLSearchParams(location.search).get('clientId') ?? '';
    return isUnderwritingId(clientId) ? <NewUnderwriting key={clientId} clientId={clientId} {...props} /> : <QueryFailure failure={new ApiError(404, 'NOT_FOUND')} />;
  }
  return parts.length === 1 ? <UnderwritingDetail key={parts[0]} underwritingId={parts[0]} {...props} /> : <UnderwritingList key={location.search} {...props} />;
}

const statKeys = ['effective', 'draft', 'submitted', 'approvedThisMonth', 'declinedThisMonth'] as const;
const statTone: Record<typeof statKeys[number], string> = { effective: '', draft: '', submitted: 'is-warn', approvedThisMonth: 'is-ok', declinedThisMonth: 'is-danger' };
/** 可直接切到对应页签的统计卡；"本月"两项没有对应的列表筛选，只展示数字。 */
const statTab: Partial<Record<typeof statKeys[number], ListTab>> = { effective: 'APPROVED', draft: 'DRAFT', submitted: 'SUBMITTED' };

/** 顶部统计（U12）：读取失败时显示占位，不影响列表。 */
function useStatistics() {
  const [data, setData] = useState<Statistics | null>(null);
  useEffect(() => {
    const controller = new AbortController();
    session.request(underwritingPaths.statistics, { signal: controller.signal }, decodeStatistics).then(setData).catch(() => undefined);
    return () => controller.abort();
  }, []);
  return data;
}

/** 列表（原型 01）：状态页签、搜索、CRR / 来源筛选与"待我复核"写入地址；每客户只列当前版本由 risk-decision 决定。 */
function UnderwritingList({ resources, actorId }: Props) {
  const t = useT('risk'), tNav = useT('nav'), tMaster = useT('master-data'), tSchema = useT('onboarding-schema'), tRef = useT('refdata'), tCommon = useT('common');
  const location = useLocation(); const navigate = useNavigate();
  const query = parseListQuery(new URLSearchParams(location.search));
  const permissions = permissionsOf(resources);
  const [keyword, setKeyword] = useState(query.keyword);
  const [creating, setCreating] = useState(false);
  const statistics = useStatistics();
  const search = new URLSearchParams(location.search);
  const page = usePaging<UnderwritingSummary>(underwritingPaths.list, decodeSummaryPage, listFilters(query), {
    initialPaging: { page: Number(search.get('page')) || 1, size: Number(search.get('size')) || 20 }, notifyOnError: false,
    onQueryChange: next => go(query, next.paging) });
  function go(next: ListQuery, paging = { page: 1, size: page.pagination.pageSize }) {
    const params = new URLSearchParams();
    if (next.tab !== 'ALL') params.set('status', next.tab);
    if (next.keyword.trim()) params.set('keyword', next.keyword.trim());
    if (next.crr) params.set('crr', next.crr);
    if (next.decisionSource) params.set('decisionSource', next.decisionSource);
    if (next.pendingReviewForMe) params.set('pendingReviewForMe', 'true');
    params.set('page', String(paging.page)); params.set('size', String(paging.size));
    void navigate(`?${params}`);
  }
  const filtered = Object.keys(listFilters(query)).length > 0;
  const person = (label: string | null, id: string | null) => label ?? id ?? '—';
  const action = (row: UnderwritingSummary) => row.status === 'SUBMITTED' && permissions.approve && row.submittedBy !== actorId ? 'review'
    : row.status === 'DRAFT' && permissions.manage ? 'continue' : 'view';
  return <div className="uw-page">
    <div className="uw-head">
      <div className="uw-grow"><h1>{tNav('resource.OPERATIONS_RISK_UNDERWRITING')}</h1><p className="uw-lead">{t('underwriting.lead')}</p></div>
      {permissions.manage && <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>{t('underwriting.new')}</Button>}
    </div>
    <div className="uw-stats">{statKeys.map(key => {
      const content = <><span className="uw-stat-label">{t('underwriting.stats.' + key)}</span>
        <span className={`uw-stat-value ${statTone[key]}`}>{statistics ? statistics[key] : '—'}</span></>;
      const tab = statTab[key];
      return tab ? <button type="button" key={key} className="uw-stat is-link" onClick={() => go({ ...query, tab, pendingReviewForMe: false })}>{content}</button>
        : <div key={key} className="uw-stat">{content}</div>; })}</div>
    <section className="uw-panel">
      <div className="uw-toolbar">
        <Segmented value={query.pendingReviewForMe ? 'SUBMITTED' : query.tab} onChange={value => go({ ...query, tab: value as ListTab, pendingReviewForMe: false })}
          options={listTabs.map(tab => ({ value: tab, label: tab === 'ALL' ? t('underwriting.filter.all') : enumLabel(t, 'underwriting.status', tab) }))} />
        <Input.Search allowClear className="uw-search" placeholder={t('underwriting.filter.search')} value={keyword} maxLength={100}
          onChange={event => setKeyword(event.target.value)} onSearch={value => go({ ...query, keyword: value })} />
        <Select allowClear className="uw-filter" value={query.crr || undefined} placeholder={t('underwriting.filter.crr')}
          onChange={value => go({ ...query, crr: value ?? '' })} options={crrs.map(value => ({ value, label: `${t('underwriting.filter.crrPrefix')}${value}` }))} />
        <Select allowClear className="uw-filter" value={query.decisionSource || undefined} placeholder={t('underwriting.filter.source')}
          onChange={value => go({ ...query, decisionSource: value ?? '' })} options={decisionSources.map(value => ({ value, label: enumLabel(t, 'underwriting.source', value) }))} />
        {permissions.approve && <label className="uw-switch"><Switch size="small" checked={query.pendingReviewForMe}
          onChange={checked => go({ ...query, pendingReviewForMe: checked })} />{t('underwriting.filter.pendingReviewForMe')}</label>}
        <Button onClick={page.refresh} loading={page.loading}>{tCommon('action.refresh')}</Button>
      </div>
      {page.error ? <div className="uw-panel-body"><QueryFailure failure={page.failure} retry={page.refresh} /></div>
        : <Table<UnderwritingSummary> rowKey="underwritingId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1280 }}
          onRow={row => ({ className: 'uw-clickable', onClick: () => void navigate(underwritingRoutes.detail(row.underwritingId)) })}
          locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={filtered ? t('underwriting.list.noMatch') : t('underwriting.list.empty')} /> }}
          pagination={{ ...page.pagination, pageSizeOptions: [10, 20, 50, 100], showTotal: total => t('underwriting.list.total', { total }) }} columns={[
            { title: t('underwriting.column.client'), width: 230, render: (_, row) => <div><div className="uw-ellipsis">{row.clientDisplayName}</div>
              <div className="uw-hint">{[row.clientNo, row.jurisdictionCode, enumLabel(tMaster, 'client.enum.clientType', row.clientType)].join(' · ')}</div></div> },
            { title: 'CRR', width: 110, render: (_, row) => <CrrBadge crr={row.crr} /> },
            { title: 'MCC', width: 190, render: (_, row) => row.mccCode ? <div><div className="uw-ellipsis">{mccText(tRef, row.mccCode)}</div>
              {row.networkHighRiskMcc && <HighRiskMark />}</div> : '—' },
            { title: t('underwriting.column.status'), width: 110, render: (_, row) => <StatusBadge status={row.status} /> },
            { title: t('underwriting.column.version'), width: 86, render: (_, row) => `v${row.versionNo}` },
            { title: t('underwriting.column.source'), width: 120, render: (_, row) => row.decisionSource === 'AUTO_RULE'
              ? t('underwriting.source.autoVersion', { version: row.ruleVersionNo ?? '?' }) : enumLabel(t, 'underwriting.source', 'MANUAL') },
            { title: t('underwriting.exposure.title'), width: 190, render: (_, row) => {
              const terms = [row.fulfillmentType ? tOr(tSchema, `catalog.FULFILLMENT_TYPE.${row.fulfillmentType}`, row.fulfillmentType) : null,
                row.suggestedSettlementDays !== null ? `T+${row.suggestedSettlementDays}` : null].filter(Boolean).join(' · ');
              return row.estimatedExposure ? <div><div>{money(row.estimatedExposure.amount, row.estimatedExposure.currency, 0)}</div>{terms && <div className="uw-hint">{terms}</div>}</div>
                : <div><div>{t('underwriting.exposure.unavailable')}</div>{row.exposureUnavailableReason && <div className="uw-hint">
                  {tOr(t, 'underwriting.exposure.short.' + row.exposureUnavailableReason, row.exposureUnavailableReason)}</div>}</div>; } },
            { title: t('underwriting.column.submitter'), width: 120, ellipsis: true, render: (_, row) => person(row.submittedByLabel, row.submittedBy) },
            { title: t('underwriting.column.reviewer'), width: 120, ellipsis: true, render: (_, row) => row.decisionSource === 'AUTO_RULE' ? t('underwriting.source.system')
              : row.reviewLevel === 'HIGH' && row.decidedBy ? person(row.decidedByLabel, row.decidedBy) : '—' },
            { title: t('underwriting.column.updated'), width: 130, render: (_, row) => formatShortDateTime(row.updatedAt) },
            { title: t('underwriting.column.actions'), width: 90, fixed: 'right', render: (_, row) => <Button type="link" className="uw-link-button"
              onClick={event => { event.stopPropagation(); void navigate(underwritingRoutes.detail(row.underwritingId)); }}>{t('underwriting.action.' + action(row))}</Button> },
          ]} />}
    </section>
    <ul className="uw-guide">
      <li><strong>{t('underwriting.guide.entry')}</strong></li>
      <li>{t('underwriting.guide.auto')}</li>
      <li>{t('underwriting.guide.exposure')}</li>
    </ul>
    {creating && <NewUnderwritingModal onClose={() => setCreating(false)} onOpen={clientId => void navigate(underwritingRoutes.create(clientId))} />}
  </div>;
}

/** 按 Client ID 新建：常规入口在 Client 详情与入驻审核（已批准案件）。 */
function NewUnderwritingModal({ onClose, onOpen }: { onClose: () => void; onOpen: (clientId: string) => void }) {
  const t = useT('risk'); const [form] = Form.useForm<{ clientId: string }>();
  return <Modal open title={t('underwriting.new')} okText={t('underwriting.newModal.open')} onCancel={onClose} onOk={() => form.submit()} destroyOnHidden>
    <p className="uw-hint">{t('underwriting.newModal.hint')}</p>
    <Form form={form} layout="vertical" onFinish={values => onOpen(values.clientId.trim())}>
      <Form.Item name="clientId" label={t('underwriting.newModal.clientId')} rules={[{ required: true, message: t('underwriting.newModal.clientIdRequired') },
        { pattern: /^\s*[1-9][0-9]{0,18}\s*$/, message: t('underwriting.newModal.clientIdInvalid') }]}><Input autoFocus maxLength={19} inputMode="numeric" /></Form.Item>
    </Form>
  </Modal>;
}
