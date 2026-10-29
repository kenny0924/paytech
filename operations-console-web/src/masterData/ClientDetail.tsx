import { useMemo } from 'react';
import { Button, Empty, Space, Table, Tag } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { Loading, QueryFailure, date, useDetail } from '../tenantAccess/TenantAccessView';
import { QualificationSummaryCard } from '../qualification/QualificationParts';
import { StatusDot, tone } from '../clientReview/reviewParts';
import { decodeResponsiblePartners, partnerName, reviewRoute } from '../clientReview/clientReview';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import { valueLabel } from '../onboardingConfig/schemaLabel';
import { ProfileDescriptions } from '../onboardingConfig/ProfileValues';
import { clientAdminApi, clientRoutes, decodeClientDetail } from './clients';
import { underwritingRoutes } from '../underwriting/underwriting';
import type { ClientApplication, ClientDetail, ClientPerson, ClientRelation, Values } from './clients';

/** 资料值按组展示；组 / 字段名沿用入驻审核的文案，未知名原样显示；contact 与证件只有掩码。 */
function ValueGroups({ values, jurisdictionCode }: { values: Values; jurisdictionCode: string | null }) {
  const t = useT('client-review'), tm = useT('master-data');
  const groups = Object.entries(values).filter(([, fields]) => Object.keys(fields).length > 0);
  if (groups.length === 0) return <span className="muted">{tm('client.profile.empty')}</span>;
  return <>{groups.map(([group, fields]) => <div key={group} className="client-values"><h3>{valueLabel(t, 'group', group)}</h3>
    <ProfileDescriptions group={group} fields={fields} jurisdictionCode={jurisdictionCode} /></div>)}</>;
}

/** 组织类型目录按辖区：Client 无辖区（迁移存量）时取最近一份申请的辖区。 */
const jurisdictionOf = (client: ClientDetail) => client.summary.jurisdictionCode ?? client.applications[0]?.jurisdictionCode ?? null;

/** 关系人姓名：个人资料的名与姓，缺失时用 Party ID。 */
const personName = (person: ClientPerson) => [person.values.individual?.givenName, person.values.individual?.familyName].filter(Boolean).join(' ') || person.partyId;

/**
 * Client 详情：摘要、当前主档资料（证件掩码）、关系人与关系、申请历史（链接到审核案件）、角色资格
 * <li>有已批准申请且可见风控承保菜单时提供"承保"入口（REQ-03，打开该 Client 的承保页）。</li>
 */
export function ClientDetailView({ clientId, canUnderwrite = false }: { clientId: string; canUnderwrite?: boolean }) {
  const t = useT('master-data'), tCommon = useT('common');
  const navigate = useNavigate();
  const decode = useMemo(() => (value: unknown) => decodeClientDetail(value, clientId), [clientId]);
  const detail = useDetail(`${clientAdminApi}/clients/${clientId}`, decode);
  // 责任 PayFac 名称取自 PayFac 选项（一次读取）；读不到时显示 ID。
  const partners = useDetail(`${clientAdminApi}/responsible-partners`, decodeResponsiblePartners);
  if (detail.loading && !detail.data) return <Loading />;
  if (detail.failure || !detail.data) return <QueryFailure failure={detail.failure} retry={detail.refresh} />;
  const { client, qualificationsStatus, qualifications } = detail.data; const summary = client.summary;
  const relationsOf = (partyId: string) => client.relations.filter(r => r.relatedPartyId === partyId);
  return <div className="configuration-page master-data-page">
    <div className="page-heading"><h1>{summary.legalName ?? summary.clientNo}</h1>
      <Space><Button onClick={() => void navigate(clientRoutes.list)}>{t('detail.back')}</Button>
        {canUnderwrite && client.applications.some(application => application.status === 'APPROVED')
          && <Button onClick={() => void navigate(underwritingRoutes.create(summary.clientId))}>{t('client.underwriting')}</Button>}
        <Button onClick={detail.refresh} loading={detail.loading}>{tCommon('action.refresh')}</Button></Space></div>
    <dl className="master-data-summary">
      <div><dt>{t('client.field.clientNo')}</dt><dd className="qualification-mono">{summary.clientNo}</dd></div>
      <div><dt>{t('client.field.clientType')}</dt><dd>{enumLabel(t, 'client.enum.clientType', summary.clientType)}</dd></div>
      <div><dt>{t('field.status')}</dt><dd><StatusDot tone={tone(summary.status)}>{enumLabel(t, 'client.enum.status', summary.status)}</StatusDot></dd></div>
      <div><dt>{t('client.field.jurisdiction')}</dt><dd>{summary.jurisdictionCode ?? '—'}</dd></div>
      <div><dt>{t('client.field.responsible')}</dt><dd>{summary.responsiblePartnerId
        ? <Link to={`/master-data/local-partners/${summary.responsiblePartnerId}`}>{partnerName(partners.data ?? [], summary.responsiblePartnerId)}</Link>
        : t('client.platformOwned')}</dd></div>
      <div><dt>{t('client.field.latestApplication')}</dt><dd>{summary.latestApplicationStatus ? enumLabel(t, 'client.enum.applicationStatus', summary.latestApplicationStatus) : '—'}</dd></div>
      <div><dt>{t('client.field.createdTime')}</dt><dd>{date(summary.createdTime)}</dd></div>
    </dl>
    <section className="master-data-section"><h2>{t('client.profile.title')}</h2>
      <ValueGroups values={client.values} jurisdictionCode={jurisdictionOf(client)} /><div className="cell-meta" style={{ marginTop: 8 }}>{t('client.maskedHint')}</div></section>
    <section className="master-data-section"><h2>{t('client.people.title')}</h2>
      {client.people.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('client.people.empty')} />
        : client.people.map(person => <div key={person.partyId} className="client-person">
          <div className="client-person-head"><strong>{personName(person)}</strong><span className="cell-meta qualification-mono">{person.partyId}</span>
            <Space wrap size={4}>{relationsOf(person.partyId).map((r: ClientRelation) => <Tag key={r.relationId}>{enumLabel(t, 'client.enum.relation', r.relationType)}
              {r.ownershipPercentage ? ` ${r.ownershipPercentage}%` : ''}{r.jobTitle ? ` · ${r.jobTitle}` : ''}</Tag>)}</Space></div>
          <ValueGroups values={person.values} jurisdictionCode={jurisdictionOf(client)} /></div>)}
    </section>
    <section className="master-data-section"><h2>{t('client.applications.title')}</h2>
      <Table<ClientApplication> size="small" rowKey="applicationId" pagination={false} dataSource={client.applications} scroll={{ x: 900 }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('client.applications.empty')} /> }} columns={[
          { title: t('client.applications.application'), render: (_, a) => <div>{a.applicationNo ?? a.applicationId}<div className="cell-meta">{[a.jurisdictionCode, a.organizationTypeCode,
            a.submissionRound ? t('client.applications.round', { round: a.submissionRound }) : null].filter(Boolean).join(' · ')}</div></div> },
          { title: t('field.status'), width: 140, render: (_, a) => <StatusDot tone={tone(a.status)}>{enumLabel(t, 'client.enum.applicationStatus', a.status)}</StatusDot> },
          { title: t('client.applications.submitted'), width: 170, render: (_, a) => a.submittedTime ? date(a.submittedTime) : '—' },
          { title: t('client.applications.updated'), width: 170, render: (_, a) => a.updatedTime ? date(a.updatedTime) : '—' },
          { title: t('client.applications.case'), width: 150, render: (_, a) => a.dueDiligenceCaseId ? <Link to={`${reviewRoute}/${a.dueDiligenceCaseId}`}>{t('client.applications.openCase')}</Link> : '—' },
        ]} /></section>
    <section className="master-data-section"><h2>{t('client.qualifications')}</h2>
      <QualificationSummaryCard title={t('client.qualificationsSubtitle')} availability={qualificationsStatus} items={qualifications}
        roleType="CLIENT" roleId={summary.clientId} onRetry={detail.refresh} /></section>
  </div>;
}
