import { useMemo, useState } from 'react';
import { Alert, Button, Descriptions, Empty, Space, Table, Tag } from 'antd';
import { useNavigate } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { Loading, QueryFailure, date, useDetail } from '../tenantAccess/TenantAccessView';
import { QualificationSummaryCard } from '../qualification/QualificationParts';
import { hasButton } from '../qualification/qualification';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import { acquiringQualifications, countriesMissingDisclosure, decodeEntityDetail, entityAdminApi, masterDataButtons, masterDataRoutes } from './masterData';
import type { Scope, SellerDisclosure } from './masterData';
import { AcquiringQualificationModal, StatusTag } from './MasterDataModals';

interface Props { entityNo: string; resources: ConsoleResource[]; onAuthorizationChanged: () => void }
export const tierColors: Record<string, string> = { MERCHANT: 'default', MERCHANT_RESALE_APPROVED: 'blue', PAYFAC: 'purple' };

/**
 * 经营主体详情（RS-05 / 06）：头部、收单资质（三档说明 + 修改）、提供方、准入范围与卖方披露（只读）、主体资格。
 * 资格部分由 BFF 聚合，不可用时卡片显示可重试提示，不阻塞主档。
 */
export function OperatingEntityDetail({ entityNo, resources, onAuthorizationChanged }: Props) {
  const t = useT('master-data'), tCommon = useT('common');
  const navigate = useNavigate();
  const decode = useMemo(() => (value: unknown) => decodeEntityDetail(value, entityNo), [entityNo]);
  const detail = useDetail(`${entityAdminApi}/operating-entities/${entityNo}`, decode);
  const [editing, setEditing] = useState(false);
  if (detail.loading && !detail.data) return <Loading />;
  if (detail.failure || !detail.data) return <QueryFailure failure={detail.failure} retry={detail.refresh} />;
  const { entity, qualificationsStatus, qualifications, providerQualificationsStatus, providerQualifications } = detail.data;
  const canManage = hasButton(resources, masterDataButtons.entityManage);
  const source = entity.sourceRelation; const provider = entity.provider;
  const missing = countriesMissingDisclosure(entity);
  return <div className="configuration-page master-data-page">
    <div className="page-heading"><h1>{entity.legalName ?? entity.entityNo}</h1>
      <Space><Button onClick={() => void navigate(masterDataRoutes.entities)}>{t('detail.back')}</Button>
        <Button onClick={detail.refresh} loading={detail.loading}>{tCommon('action.refresh')}</Button></Space></div>
    <dl className="master-data-summary">
      <div><dt>{t('entity.field.entityNo')}</dt><dd className="qualification-mono">{entity.entityNo}</dd></div>
      <div><dt>{t('field.status')}</dt><dd><StatusTag status={entity.status} kind="entityStatus" /></dd></div>
      <div><dt>{t('entity.field.sourceType')}</dt><dd>{source ? enumLabel(t, 'enum.sourceType', source.sourceType) : '—'}</dd></div>
      <div><dt>{t('entity.field.operatingCountry')}</dt><dd>{entity.operatingCountry}</dd></div>
      <div><dt>{t('field.partyId')}</dt><dd className="qualification-mono">{entity.partyId}</dd></div>
      <div><dt>{t('field.changeVersion')}</dt><dd>{entity.changeVersion}</dd></div>
    </dl>
    <section className="master-data-section"><div className="master-data-section-head"><h2>{t('acquiring.title')}</h2>
      {canManage && <Button onClick={() => setEditing(true)}>{t('acquiring.change')}</Button>}</div>
      <div className="master-data-tier-list">{acquiringQualifications.map(tier => <div key={tier} className={tier === entity.acquiringQualification ? 'is-current' : undefined}>
        <div><Tag color={tierColors[tier]}>{enumLabel(t, 'enum.acquiringQualification', tier)}</Tag>{tier === entity.acquiringQualification && <span className="cell-meta">{t('acquiring.current')}</span>}</div>
        <div className="cell-meta">{t(`acquiring.description.${tier}`)}</div></div>)}</div>
      {!(acquiringQualifications as readonly string[]).includes(entity.acquiringQualification) && <Alert showIcon type="warning" style={{ marginTop: 12 }}
        title={t('acquiring.unknown', { value: entity.acquiringQualification })} />}
    </section>
    <section className="master-data-section"><h2>{t('entity.source.title')}</h2>
      {!source ? <span className="muted">{t('entity.source.none')}</span> : <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 3 }} items={[
        { key: 'type', label: t('entity.field.sourceType'), children: enumLabel(t, 'enum.sourceType', source.sourceType) },
        ...(source.sourceClientId ? [{ key: 'client', label: t('entity.source.client'), children: <span className="qualification-mono">{source.sourceClientId}</span> }] : []),
        { key: 'period', label: t('field.effectivePeriod'), children: t('field.period', { from: date(source.effectiveFrom), to: date(source.effectiveTo) }) },
      ]} />}
      {provider && <div className="master-data-provider">
        <div className="master-data-section-head"><strong>{t('entity.provider.title')}</strong>
          <Button size="small" type="link" onClick={() => void navigate(masterDataRoutes.partner(provider.localPartnerId))}>{t('entity.provider.open')}</Button></div>
        <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 3 }} items={[
          { key: 'name', label: t('partner.field.legalName'), children: provider.legalName ?? '—' },
          { key: 'no', label: t('partner.field.partnerNo'), children: <span className="qualification-mono">{provider.partnerNo}</span> },
          { key: 'status', label: t('field.status'), children: <StatusTag status={provider.status} kind="partnerStatus" /> },
          { key: 'roles', label: t('partner.field.roles'), children: <RoleTags roles={provider.roles} /> },
        ]} />
        <QualificationSummaryCard title={t('entity.provider.qualifications')} availability={providerQualificationsStatus} items={providerQualifications}
          roleType="LOCAL_PARTNER" roleId={provider.localPartnerId} onRetry={detail.refresh} />
      </div>}
      {!provider && source?.sourceType === 'PARTNER' && <Alert showIcon type="warning" style={{ marginTop: 12 }} title={t('entity.provider.missing')} />}
    </section>
    <section className="master-data-section"><h2>{t('entity.qualifications')}</h2>
      <QualificationSummaryCard title={t('entity.qualificationsSubtitle')} availability={qualificationsStatus} items={qualifications}
        roleType="OPERATING_ENTITY" roleId={entity.entityNo} onRetry={detail.refresh} /></section>
    <section className="master-data-section"><h2>{t('entity.scopes.title')}</h2>
      <Table<Scope> size="small" rowKey="scopeId" pagination={false} dataSource={entity.scopes} scroll={{ x: 760 }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('entity.scopes.empty')} /> }} columns={[
          { title: t('entity.scopes.country'), width: 110, render: (_, row) => row.consumerCountryCode },
          { title: t('entity.scopes.currency'), width: 90, render: (_, row) => row.currency },
          { title: t('entity.scopes.industry'), width: 160, render: (_, row) => <span className="qualification-mono">{row.industryCode}</span> },
          { title: 'MCC', width: 80, render: (_, row) => <span className="qualification-mono">{row.mccCode}</span> },
          { title: t('field.status'), width: 100, render: (_, row) => <StatusTag status={row.status} kind="scopeStatus" /> },
          { title: t('field.effectivePeriod'), render: (_, row) => row.effectiveFrom ? t('field.period', { from: date(row.effectiveFrom), to: date(row.effectiveTo) }) : '—' },
        ]} /></section>
    <section className="master-data-section"><h2>{t('entity.disclosures.title')}</h2>
      <Alert showIcon type="info" style={{ marginBottom: 12 }} title={t('entity.disclosures.note')} />
      {missing.length > 0 && <Alert showIcon type="warning" style={{ marginBottom: 12 }} title={t('entity.disclosures.missing', { countries: missing.join(', ') })} />}
      <Table<SellerDisclosure> size="small" rowKey="disclosureId" pagination={false} dataSource={entity.sellerDisclosures} scroll={{ x: 640 }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('entity.disclosures.empty')} /> }} columns={[
          { title: t('entity.disclosures.country'), width: 110, render: (_, row) => row.countryCode },
          { title: t('entity.disclosures.locale'), width: 110, render: (_, row) => <span className="qualification-mono">{row.locale}</span> },
          { title: t('field.status'), width: 110, render: (_, row) => <StatusTag status={row.status} kind="disclosureStatus" /> },
          { title: t('field.effectivePeriod'), render: (_, row) => t('field.period', { from: date(row.effectiveFrom), to: date(row.effectiveTo) }) },
        ]} /></section>
    {editing && <AcquiringQualificationModal entity={entity} onClose={() => setEditing(false)} onAuthorizationChanged={onAuthorizationChanged}
      onDone={() => { setEditing(false); detail.refresh(); }} onReload={() => { setEditing(false); detail.refresh(); }} />}
  </div>;
}

export function RoleTags({ roles }: { roles: readonly string[] }) {
  const t = useT('master-data');
  if (roles.length === 0) return <span className="muted">—</span>;
  return <>{roles.map(role => <Tag key={role} color={role === 'OE_PROVIDER' ? 'geekblue' : 'cyan'}>{enumLabel(t, 'enum.partnerRole', role)}</Tag>)}</>;
}
