import { useMemo, useState } from 'react';
import { Alert, Button, Empty, Space, Table } from 'antd';
import { useNavigate } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { Loading, QueryFailure, date, useDetail } from '../tenantAccess/TenantAccessView';
import { QualificationSummaryCard } from '../qualification/QualificationParts';
import { hasButton } from '../qualification/qualification';
import { useT } from '../i18n/react';
import { decodePartnerDetail, entityAdminApi, masterDataButtons, masterDataRoutes, oeProviderLocked } from './masterData';
import type { ProvidedEntity } from './masterData';
import { RolesModal, StatusTag } from './MasterDataModals';
import { RoleTags } from './OperatingEntityDetail';

interface Props { localPartnerId: string; resources: ConsoleResource[]; onAuthorizationChanged: () => void }

/** Local Partner 详情（RS-08）：头部、角色（多选维护）、提供的经营主体、主体资格（PARTNER_DUE_DILIGENCE）。 */
export function LocalPartnerDetail({ localPartnerId, resources, onAuthorizationChanged }: Props) {
  const t = useT('master-data'), tCommon = useT('common');
  const navigate = useNavigate();
  const decode = useMemo(() => (value: unknown) => decodePartnerDetail(value, localPartnerId), [localPartnerId]);
  const detail = useDetail(`${entityAdminApi}/local-partners/${localPartnerId}`, decode);
  const [editing, setEditing] = useState(false);
  if (detail.loading && !detail.data) return <Loading />;
  if (detail.failure || !detail.data) return <QueryFailure failure={detail.failure} retry={detail.refresh} />;
  const { partner, qualificationsStatus, qualifications } = detail.data;
  const canManage = hasButton(resources, masterDataButtons.partnerManage);
  return <div className="configuration-page master-data-page">
    <div className="page-heading"><h1>{partner.legalName ?? partner.partnerNo}</h1>
      <Space><Button onClick={() => void navigate(masterDataRoutes.partners)}>{t('detail.back')}</Button>
        <Button onClick={detail.refresh} loading={detail.loading}>{tCommon('action.refresh')}</Button></Space></div>
    <dl className="master-data-summary">
      <div><dt>{t('partner.field.partnerNo')}</dt><dd className="qualification-mono">{partner.partnerNo}</dd></div>
      <div><dt>{t('partner.field.localPartnerId')}</dt><dd className="qualification-mono">{partner.localPartnerId}</dd></div>
      <div><dt>{t('field.status')}</dt><dd><StatusTag status={partner.status} kind="partnerStatus" /></dd></div>
      <div><dt>{t('field.partyId')}</dt><dd className="qualification-mono">{partner.partyId}</dd></div>
      <div><dt>{t('field.changeVersion')}</dt><dd>{partner.changeVersion}</dd></div>
    </dl>
    <section className="master-data-section"><div className="master-data-section-head"><h2>{t('roles.title')}</h2>
      {canManage && <Button onClick={() => setEditing(true)}>{t('roles.edit')}</Button>}</div>
      <RoleTags roles={partner.roles} />
      {oeProviderLocked(partner) && <div className="cell-meta" style={{ marginTop: 8 }}>{t('roles.providerLocked')}</div>}
    </section>
    <section className="master-data-section"><h2>{t('partner.provided.title')}</h2>
      <Table<ProvidedEntity> size="small" rowKey="sourceRelationId" pagination={false} dataSource={partner.providedEntities} scroll={{ x: 760 }}
        onRow={row => ({ className: 'clickable-row', onClick: () => void navigate(masterDataRoutes.entity(row.entityNo)) })}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('partner.provided.empty')} /> }} columns={[
          { title: t('entity.field.entityNo'), width: 100, render: (_, row) => <span className="qualification-mono">{row.entityNo}</span> },
          { title: t('entity.field.legalName'), ellipsis: true, render: (_, row) => row.legalName ?? '—' },
          { title: t('partner.provided.entityStatus'), width: 110, render: (_, row) => <StatusTag status={row.entityStatus} kind="entityStatus" /> },
          { title: t('partner.provided.sourceStatus'), width: 110, render: (_, row) => <StatusTag status={row.sourceStatus} kind="sourceStatus" /> },
          { title: t('field.effectivePeriod'), width: 300, render: (_, row) => t('field.period', { from: date(row.effectiveFrom), to: date(row.effectiveTo) }) },
        ]} /></section>
    <section className="master-data-section"><h2>{t('partner.qualifications')}</h2>
      {partner.status === 'TERMINATED' && <Alert showIcon type="info" style={{ marginBottom: 12 }} title={t('partner.terminated')} />}
      <QualificationSummaryCard title={t('partner.qualificationsSubtitle')} availability={qualificationsStatus} items={qualifications}
        roleType="LOCAL_PARTNER" roleId={partner.localPartnerId} onRetry={detail.refresh} /></section>
    {editing && <RolesModal partner={partner} onClose={() => setEditing(false)} onAuthorizationChanged={onAuthorizationChanged}
      onDone={() => { setEditing(false); detail.refresh(); }} onReload={() => { setEditing(false); detail.refresh(); }} />}
  </div>;
}
