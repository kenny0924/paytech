import { useEffect, useMemo, useState } from 'react';
import { session } from '../api/session';
import { Button, Segmented, Select, Space } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { hasMenu, underwritingMenu, underwritingRoutes } from '../underwriting/underwriting';
import type { ConsoleResource } from '../api/types';
import { Loading, QueryFailure, date, useDetail } from '../tenantAccess/TenantAccessView';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import { activeStatuses, decodeCasePage, decodeDetail, decodeResponsiblePartners, latestCaseId, partnerName, reviewApi, reviewButtons, reviewRoute, workbenchCounts } from './clientReview';
import type { ResponsiblePartner } from './clientReview';
import { ApplicationPanel, MaterialsPanel, displayName } from './ReviewEvidence';
import { ChecksPanel, ScreeningPanel } from './ReviewChecks';
import { ClassificationPanel, RiskPanel, TimelinePanel } from './ReviewRisk';
import { DecisionPanel } from './ReviewDecision';
import { StatusDot, has, tone } from './reviewParts';

interface Props { caseId: string; resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }

/**
 * 申请资料不可用时查同一申请的最新案件（一次列表读取），判断是否历史轮次
 * <li>undefined：不需要或尚未知道；null：读不到最新案件；否则为最新案件 ID。</li>
 */
function useCurrentCase(applicationId: string | undefined, needed: boolean) {
  const [current, setCurrent] = useState<string | null | undefined>(undefined);
  useEffect(() => {
    if (!needed || !applicationId) { setCurrent(undefined); return; }
    const controller = new AbortController();
    session.request(`${reviewApi}/cases?applicationId=${applicationId}&page=1&size=1`, { signal: controller.signal }, decodeCasePage)
      .then(page => { if (!controller.signal.aborted) setCurrent(latestCaseId(page, applicationId)); })
      .catch(() => { if (!controller.signal.aborted) setCurrent(null); });
    return () => controller.abort();
  }, [applicationId, needed]);
  return current;
}
const tabs = ['application', 'materials', 'checks', 'screening', 'classification', 'risk', 'timeline'] as const;

/**
 * 案件工作台：左侧分区（申请资料、材料、检查、筛查、分类、CRR、时间线），右侧决定面板
 * <li>“代 PayFac 操作”默认案件责任方；所有 compliance 写命令附带 onBehalfOfPartnerId，平台自营案件为 null。</li>
 * <li>页面只发两个读请求（详情 + PayFac 选项），不按行扇出。</li>
 */
export function ClientReviewDetail({ caseId, resources, actorId, onAuthorizationChanged }: Props) {
  const t = useT('client-review'), tCommon = useT('common'); const navigate = useNavigate();
  const decode = useMemo(() => (value: unknown) => decodeDetail(value, caseId), [caseId]);
  const detail = useDetail(`${reviewApi}/cases/${caseId}`, decode);
  const partnerList = useDetail(`${reviewApi}/responsible-partners`, decodeResponsiblePartners);
  const [tab, setTab] = useState<string>('materials');
  const [onBehalf, setOnBehalf] = useState<string | null | undefined>(undefined);
  const responsible = detail.data?.review.responsiblePartnerId ?? null;
  const currentCaseId = useCurrentCase(detail.data?.review.applicationId,
    detail.data !== undefined && (detail.data.applicationStatus === 'UNAVAILABLE' || detail.data.classificationStatus === 'UNAVAILABLE'));
  useEffect(() => { if (detail.data && onBehalf === undefined) setOnBehalf(responsible); }, [detail.data, onBehalf, responsible]);
  if (detail.loading && !detail.data) return <Loading />;
  if (detail.failure || !detail.data) return <QueryFailure failure={detail.failure} retry={detail.refresh} />;
  const data = detail.data; const review = data.review; const active = activeStatuses.includes(review.status);
  const partners: ResponsiblePartner[] = partnerList.data ?? [];
  const actingFor = onBehalf === undefined ? responsible : onBehalf;
  const name = displayName(t, data.application);
  const counts = workbenchCounts(review);
  const label = (key: string, count?: number) => count ? <span>{t('tab.' + key)} <span className="obw-count">{count}</span></span> : t('tab.' + key);
  const common = { active, onBehalfOfPartnerId: actingFor, onAuthorizationChanged, onChanged: detail.refresh };
  return <div className="obw-page">
    <nav className="obw-crumbs"><Link to={reviewRoute}>{t('detail.backToList')}</Link> / {review.caseNo}</nav>
    <div className="obw-head">
      <div className="obw-grow"><h1>{name} <StatusDot tone={tone(review.status)}>{enumLabel(t, 'enum.caseStatus', review.status)}</StatusDot></h1>
        <div className="obw-meta">{[review.caseNo, t('detail.applicationMeta', { applicationId: review.applicationId }),
          enumLabel(t, 'enum.qualification', review.qualificationType), review.jurisdictionCode,
          review.tier && enumLabel(t, 'enum.tier', review.tier), review.roundNo && t('detail.round', { round: review.roundNo }),
          t('detail.submitted', { time: date(review.submittedAt) }),
          review.reviewDueTime && t('detail.dueAt', { time: date(review.reviewDueTime) })].filter(Boolean).join(' · ')}</div>
        <div className="obw-meta">{responsible ? t('detail.responsible', { partner: partnerName(partners, responsible) ?? responsible }) : t('detail.platformOwned')}
          {review.previousCaseId && <> · <Link to={`${reviewRoute}/${review.previousCaseId}`}>{t('detail.previousCase')}</Link></>}</div></div>
      <Space wrap>
        {active && <div className="obw-on-behalf"><span className="obw-label">{t('onBehalf.label')}</span>
          <Select style={{ width: 240 }} value={actingFor ?? '__PLATFORM__'} loading={partnerList.loading}
            options={[{ value: '__PLATFORM__', label: t('onBehalf.platform') }, ...partners.map(p => ({ value: p.localPartnerId, label: `${p.legalName ?? p.partnerNo} · ${p.partnerNo}` })),
              ...(responsible && !partners.some(p => p.localPartnerId === responsible) ? [{ value: responsible, label: responsible }] : [])]}
            onChange={value => setOnBehalf(value === '__PLATFORM__' ? null : value)} /></div>}
        {review.status === 'APPROVED' && hasMenu(resources, underwritingMenu)
          && <Button onClick={() => void navigate(underwritingRoutes.create(review.clientId))}>{t('detail.underwriting')}</Button>}
        <Button onClick={detail.refresh} loading={detail.loading}>{tCommon('action.refresh')}</Button></Space>
    </div>
    {active && actingFor !== responsible && <div className="obw-notice">{responsible
      ? t('onBehalf.mismatch', { partner: partnerName(partners, responsible) ?? responsible }) : t('onBehalf.notResponsible')}</div>}
    {review.status === 'RETURNED' && review.returnErrors.length > 0 && <div className="obw-notice">{t('detail.returnedWith', { count: review.returnErrors.length, time: date(review.reviewDueTime) })}</div>}
    <div className="obw-stats">
      <span>{t('stats.evidence')} <strong>{counts.evidence}</strong></span>
      <span>{t('stats.checks')} <strong>{counts.checks}</strong></span>
      <span>{t('stats.hits')} <strong className={counts.hits ? 'obw-warn-text' : undefined}>{counts.hits}</strong></span>
      <span>{t('stats.blockers')} <strong className={review.approvalBlockers.length ? 'obw-warn-text' : 'obw-ok-text'}>{review.approvalBlockers.length}</strong></span>
    </div>
    <div className="obw-split">
      <main className="obw-main">
        <Segmented value={tab} onChange={value => setTab(String(value))} options={tabs.map(key => ({ value: key,
          label: label(key, key === 'materials' ? counts.evidence : key === 'checks' ? counts.checks : key === 'screening' ? counts.hits : undefined) }))} />
        {tab === 'application' && <ApplicationPanel data={data} currentCaseId={currentCaseId} />}
        {tab === 'materials' && <MaterialsPanel caseId={caseId} subjects={review.subjects} canReview={has(resources, reviewButtons.evidence)}
          canWaiveAny={has(resources, reviewButtons.waive)} {...common} />}
        {tab === 'checks' && <ChecksPanel review={review} canRecord={has(resources, reviewButtons.evidence)} {...common} />}
        {tab === 'screening' && <ScreeningPanel review={review} canScreen={has(resources, reviewButtons.screening)} {...common} />}
        {tab === 'classification' && <ClassificationPanel key={`${data.classification?.confirmedAt}:${data.classification?.items.length}`} data={data} active={active} currentCaseId={currentCaseId}
          canEdit={has(resources, reviewButtons.classify)} onAuthorizationChanged={onAuthorizationChanged} onChanged={detail.refresh} />}
        {tab === 'risk' && <RiskPanel key={`${review.riskRating}:${review.riskFactors.length}`} review={review} canRate={has(resources, reviewButtons.riskRate)} {...common} />}
        {tab === 'timeline' && <TimelinePanel review={review} partners={partners} />}
      </main>
      <DecisionPanel review={review} actorId={actorId} name={name} canRecommend={has(resources, reviewButtons.recommend)}
        canReturn={has(resources, reviewButtons.return)} canDecide={has(resources, reviewButtons.decide)} onBehalfOfPartnerId={actingFor}
        onOpenTab={setTab} onAuthorizationChanged={onAuthorizationChanged} onChanged={detail.refresh} />
    </div>
  </div>;
}
