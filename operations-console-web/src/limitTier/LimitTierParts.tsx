import { Alert } from 'antd';
import { CheckOutlined, CloseOutlined, ExclamationOutlined } from '@ant-design/icons';
import { Link } from 'react-router-dom';
import type { ApiError } from '../api/session';
import { enumLabel, formatShortDateTime, tOr } from '../i18n/index';
import { useT } from '../i18n/react';
import { Card, CrrBadge, Row } from '../underwriting/UnderwritingParts';
import { underwritingRoutes } from '../underwriting/underwriting';
import { exhausted, groupDecimal, percent, quotaRows, sourceChangeOf } from './limitTier';
import type { CheckStatus, Observation, TierChange, TierDetail, Tier, WorkspaceUnderwriting } from './limitTier';

const tierClass: Record<Tier, string> = { VERIFICATION: 'is-info', OBSERVATION: 'is-purple', STANDARD: 'is-ok', RESTRICTED: 'is-danger' };
/** 档位徽标：验证蓝 / 观察紫 / 标准绿 / 受限红；无档位灰。 */
export function TierBadge({ tier }: { tier: Tier | null }) {
  const t = useT('risk');
  return <span className={`uw-badge ${tier ? tierClass[tier] : 'is-muted'}`}>{enumLabel(t, 'limitTier.tier', tier ?? 'NONE')}</span>;
}
export function DueBadge() { const t = useT('risk'); return <span className="uw-badge is-warn">{t('limitTier.observation.dueBadge')}</span>; }

/** 进度条：kind 决定颜色；full 时变红（额度用满）。 */
export function Bar({ value, kind = 'quota', full = false, wide = false, label }: { value: number | null; kind?: 'quota' | 'days' | 'count'; full?: boolean; wide?: boolean; label: string }) {
  if (value === null) return null;
  return <div className={`lt-bar is-${kind}${full ? ' is-full' : ''}${wide ? ' is-wide' : ''}`} role="progressbar" aria-label={label}
    aria-valuemin={0} aria-valuemax={100} aria-valuenow={Math.round(value)}><span style={{ width: `${value}%` }} /></div>;
}

/** 列表中的观察期进度（原型 01）：天数条 + "11 / 30 天 · 已放行 42 / 100 笔"，到期标橙。 */
export function ObservationCell({ daysElapsed, targetDays, allowed, targetTransactions, due }: { daysElapsed: number | null; targetDays: number | null;
  allowed: number | null; targetTransactions: number | null; due: boolean }) {
  const t = useT('risk');
  if (daysElapsed === null || targetDays === null) return <span className="muted">—</span>;
  const parts = [t('limitTier.observation.daysShort', { elapsed: daysElapsed, target: targetDays }),
    allowed !== null && targetTransactions !== null ? t('limitTier.observation.allowedShort', { count: allowed, target: targetTransactions }) : null,
    due ? t('limitTier.observation.dueShort') : null].filter(Boolean);
  return <div className="lt-progress">
    <Bar kind="days" value={percent(daysElapsed, targetDays)} label={t('limitTier.observation.daysLabel')} />
    <span className={`lt-progress-text${due ? ' is-due' : ''}`}>{parts.join(' · ')}</span></div>;
}

/** 观察期进度卡（原型 03）：天数与放行笔数两条进度；笔数为近似值。 */
export function ObservationCard({ observation }: { observation: Observation }) {
  const t = useT('risk');
  const daysFull = observation.daysElapsed >= observation.targetDays, countFull = observation.allowedTransactions >= observation.targetTransactions;
  return <Card title={t('limitTier.observation.title')} extra={t('limitTier.observation.anyReached')}>
    <Row label={t('limitTier.observation.daysLabel')}><div className="lt-progress-row">
      <Bar kind="days" wide value={percent(observation.daysElapsed, observation.targetDays)} label={t('limitTier.observation.daysLabel')} />
      <span className="lt-progress-text">{t('limitTier.observation.days', { elapsed: observation.daysElapsed, target: observation.targetDays })}
        {daysFull && ` ${t('limitTier.observation.reached')}`}</span></div></Row>
    <Row label={t('limitTier.observation.transactionsLabel')}><div className="lt-progress-row">
      <Bar kind="count" wide value={percent(observation.allowedTransactions, observation.targetTransactions)} label={t('limitTier.observation.transactionsLabel')} />
      <span className="lt-progress-text">{t('limitTier.observation.transactions', { count: observation.allowedTransactions, target: observation.targetTransactions })}
        {countFull && ` ${t('limitTier.observation.reached')}`}</span></div></Row>
    <p className="uw-hint">{t('limitTier.observation.transactionsHint')} · {t('limitTier.observation.startedAt', { time: formatShortDateTime(observation.startedTime) })}</p>
  </Card>;
}

/** 按币种额度卡（原型 02 / 03）：单笔上限、当日（UTC）/ 近 30 天（UTC 自然日）已用 / 上限 + 条；未配置币种按本档位策略说明。 */
export function QuotaCard({ detail }: { detail: TierDetail }) {
  const t = useT('risk');
  const rows = quotaRows(detail);
  const limit = (value: string | null) => value === null ? t('limitTier.quota.notConfigured') : groupDecimal(value);
  const window = (used: string, max: string | null, label: string) => max === null ? <span className="muted">{t('limitTier.quota.notConfigured')}</span>
    : <div className="lt-used"><span>{groupDecimal(used)} / {groupDecimal(max)}</span><Bar value={percent(used, max)} full={exhausted(used, max)} label={label} /></div>;
  return <Card title={t('limitTier.quota.title')} extra={enumLabel(t, 'limitTier.tier', detail.tier)}>
    {rows.length === 0 ? <p className="lt-restricted">{t('limitTier.quota.none.' + detail.unconfiguredCurrencyPolicy)}</p>
      : <div className="lt-quota-wrap"><table className="lt-quota">
        <colgroup><col className="lt-col-currency" /><col /><col /><col /></colgroup>
        <thead><tr><th>{t('limitTier.quota.currency')}</th><th>{t('limitTier.quota.single')}</th>
          <th>{t('limitTier.quota.day')}</th><th>{t('limitTier.quota.last30Days')}</th></tr></thead>
        <tbody>{rows.map(row => row.kind === 'configured'
          ? <tr key={row.quota.currency}><td>{row.quota.currency}</td><td>{limit(row.quota.single)}</td>
            <td>{window(row.quota.usedToday, row.quota.day, t('limitTier.quota.day'))}</td>
            <td>{window(row.quota.usedLast30Days, row.quota.last30Days, t('limitTier.quota.last30Days'))}</td></tr>
          : <tr key={row.currency}><td>{row.currency}</td><td className="muted">—</td>
            <td className="muted">{t('limitTier.quota.unconfiguredCell.' + detail.unconfiguredCurrencyPolicy)}</td>
            <td className="muted">{t('limitTier.quota.unconfiguredCell.' + detail.unconfiguredCurrencyPolicy)}</td></tr>)}</tbody>
      </table></div>}
    <p className="uw-hint">{t('limitTier.quota.unconfigured.' + detail.unconfiguredCurrencyPolicy)} {t('limitTier.quota.separateCurrencies')} {t('limitTier.quota.usedHint')} {t('limitTier.quota.utcNote')}</p>
  </Card>;
}

/** 来源说明："承保初始化（承保 v1）" / "变更申请（验证档 → 观察档）" / "存量迁移"。 */
export function sourceText(t: ReturnType<typeof useT>, detail: Pick<TierDetail, 'sourceType' | 'sourceReason'>, change: TierChange | null): string {
  const source = enumLabel(t, 'limitTier.source', detail.sourceType);
  if (detail.sourceType === 'REQUEST' && change?.changeType === 'REQUEST' && change.fromTier)
    return t('limitTier.current.sourceTransition', { source, from: enumLabel(t, 'limitTier.tier', change.fromTier), to: enumLabel(t, 'limitTier.tier', change.toTier) });
  return detail.sourceReason ? t('limitTier.current.sourceWithReason', { source, reason: detail.sourceReason }) : source;
}

/** 当前档位卡（原型 02—05）：档位与说明、进入时间、来源、CRR、有效承保（链接，含观察期目标与 T+N）。 */
export function CurrentTierCard({ detail, history, underwriting }: { detail: TierDetail; history: TierChange[]; underwriting: WorkspaceUnderwriting | null }) {
  const t = useT('risk');
  return <Card title={t('limitTier.current.title')}>
    <Row label={t('limitTier.column.tier')}><span className="uw-inline"><TierBadge tier={detail.tier} /><span className="muted">{t('limitTier.tierHint.' + detail.tier)}</span></span></Row>
    <Row label={t('limitTier.current.entered')}>{formatShortDateTime(detail.effectiveTime)}</Row>
    <Row label={t('limitTier.column.source')}>{sourceText(t, detail, sourceChangeOf(history))}</Row>
    <Row label={t('limitTier.current.crr')}><CrrBadge crr={detail.crr} /></Row>
    <Row label={t('limitTier.current.underwriting')}><UnderwritingLink underwriting={underwriting} /></Row>
  </Card>;
}
export function UnderwritingLink({ underwriting }: { underwriting: WorkspaceUnderwriting | null }) {
  const t = useT('risk');
  if (!underwriting) return <span className="muted">{t('limitTier.current.noUnderwriting')}</span>;
  const parts = [t('limitTier.current.underwritingVersion', { version: underwriting.versionNo }),
    underwriting.observationDays !== null && underwriting.observationTransactions !== null
      ? t('limitTier.current.observationTarget', { days: underwriting.observationDays, count: underwriting.observationTransactions }) : null,
    underwriting.suggestedSettlementDays !== null ? `T+${underwriting.suggestedSettlementDays}` : null].filter(Boolean);
  return <Link to={underwritingRoutes.detail(underwriting.underwritingId)}>{parts.join(' · ')}</Link>;
}

const statusClass = { PENDING: 'is-warn', APPROVED: 'is-ok', REJECTED: 'is-danger', WITHDRAWN: 'is-muted' } as const;
/** 变更记录标题："验证档 → 观察档"；建档 / 迁移为"承保初始化 → 验证档"。 */
export function changeTitle(t: ReturnType<typeof useT>, change: Pick<TierChange, 'changeType' | 'fromTier' | 'toTier'>): string {
  const from = change.fromTier ? enumLabel(t, 'limitTier.tier', change.fromTier) : enumLabel(t, 'limitTier.source', change.changeType);
  return `${from} → ${enumLabel(t, 'limitTier.tier', change.toTier)}`;
}
const person = (labelText: string | null, id: string | null) => labelText ?? id ?? '—';

/** 变更历史（原型 02—05）：标题 + 状态，下一行时间、申请人、审批人、说明 / 理由 / 意见。 */
export function HistoryCard({ history }: { history: TierChange[] }) {
  const t = useT('risk');
  return <Card title={t('limitTier.history.title')}>
    {history.length === 0 ? <span className="muted">{t('limitTier.history.empty')}</span> : <ul className="lt-history">{history.map(change => {
      const automatic = change.changeType !== 'REQUEST';
      const status = automatic && change.status === 'APPROVED' ? t('limitTier.history.effective') : enumLabel(t, 'limitTier.changeStatus', change.status);
      const decider = change.decidedBy === null ? null : t(change.status === 'REJECTED' ? 'limitTier.history.rejectedBy' : change.status === 'WITHDRAWN'
        ? 'limitTier.history.withdrawnBy' : change.approvalLevel === 'ELEVATED' ? 'limitTier.history.approvedElevatedBy' : 'limitTier.history.approvedBy',
      { person: person(change.decidedByLabel, change.decidedBy) });
      const meta = [formatShortDateTime(change.requestedTime),
        automatic ? enumLabel(t, 'limitTier.history.changeType', change.changeType) : t('limitTier.history.requestedBy', { person: person(change.requestedByLabel, change.requestedBy) }),
        decider,
        change.goLiveNote ? t('limitTier.history.goLiveNote', { note: change.goLiveNote })
          : change.reason && !automatic ? t('limitTier.history.reason', { reason: change.reason }) : automatic && change.reason ? change.reason : null,
        change.decisionComment ? t('limitTier.history.comment', { comment: change.decisionComment }) : null].filter(Boolean);
      return <li key={change.changeId}>
        <div className="lt-history-title">{changeTitle(t, change)}<span className={`uw-badge ${statusClass[change.status]}`}>{status}</span></div>
        <div className="uw-hint">{meta.join(' · ')}</div></li>; })}</ul>}
  </Card>;
}

/** 复核清单系统项图标。 */
export function CheckIcon({ status }: { status: CheckStatus }) {
  return <span className={`lt-check-icon is-${status}`} aria-hidden>{status === 'OK' ? <CheckOutlined /> : status === 'NOT_OK' ? <CloseOutlined /> : <ExclamationOutlined />}</span>;
}

/** 命令失败（409 / 422）：已知错误码显示本地说明，否则显示服务端 detail；附请求编号。 */
export function CommandAlert({ failure }: { failure: ApiError | undefined }) {
  const t = useT('risk');
  if (!failure) return null;
  const known = tOr(t, 'limitTier.error.' + failure.code, '');
  const fields = failure.fieldErrors.map(error => `${error.field}: ${error.message}`);
  return <Alert showIcon type="warning" className="uw-alert" title={failure.status === 409 ? t('limitTier.command.conflict') : t('limitTier.command.rejected')}
    description={<>{known || failure.detail || failure.code}{fields.length > 0 && <ul className="uw-plain">{fields.map(text => <li key={text}>{text}</li>)}</ul>}
      {failure.requestId && <div className="uw-hint">{t('limitTier.command.requestId', { requestId: failure.requestId })}</div>}</>} />;
}

