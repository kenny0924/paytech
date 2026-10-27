import { useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Button, Empty, Input, Popover, Radio, Segmented, Select, Spin, Tag, Timeline } from 'antd';
import { CheckOutlined, DeleteOutlined, ExclamationCircleOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { ApiError, session } from '../api/session';
import { date } from '../tenantAccess/TenantAccessView';
import { enumLabel } from '../i18n/index';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';
import { IndustryIcon } from '../referenceData/ReferenceDataParts';
import { decodeMccPage, industryDescription, refdataName, untranslated } from '../referenceData/referenceData';
import type { RefMcc } from '../referenceData/referenceData';
import {
  chooseCategory, chooseSubcategory, classificationChanges, classificationItems, classificationListIssues, classificationRowIssues, classificationRows,
  classificationServerIssues, decodeClassificationSet, decodeCommand, industryIndex, maxClassifications, mccMark, partnerName, reviewApi, riskFactorCodes,
  riskRatingIssues, riskRatings, rowIssueText, suggestedMcc, withOnBehalf,
} from './clientReview';
import type { ClassificationRow, IndustryPosition, ResponsiblePartner, ReviewCase, ReviewDetail, RowIssue } from './clientReview';
import { CommandFailure, StatusDot, tone } from './reviewParts';
import { HistoricalRound } from './ReviewEvidence';
import { useReviewCommand } from './useReviewCommand';

interface FactorDraft { factorCode: string; factorValue: string }

/** 手工 CRR：评级 + 因子必填；保存即整体替换当前评级与因子。 */
export function RiskPanel({ review, active, canRate, onBehalfOfPartnerId, onAuthorizationChanged, onChanged }: {
  review: ReviewCase; active: boolean; canRate: boolean; onBehalfOfPartnerId: string | null; onAuthorizationChanged: () => void; onChanged: () => void;
}) {
  const t = useT('client-review');
  const editable = active && canRate;
  const [rating, setRating] = useState<string | undefined>(review.riskRating ?? undefined);
  const [factors, setFactors] = useState<FactorDraft[]>(review.riskFactors.length
    ? review.riskFactors.map(f => ({ factorCode: f.factorCode, factorValue: f.factorValue })) : [{ factorCode: '', factorValue: '' }]);
  const [note, setNote] = useState('');
  const [dirty, setDirty] = useState(false);
  const command = useReviewCommand(onAuthorizationChanged);
  const issues = riskRatingIssues(rating, factors, note);
  const update = (index: number, patch: Partial<FactorDraft>) => { setDirty(true); setFactors(current => current.map((f, i) => i === index ? { ...f, ...patch } : f)); };
  async function save() {
    const payload = { riskRating: rating, factors: factors.map(f => ({ factorCode: f.factorCode, factorValue: f.factorValue.trim() })), note: note.trim() };
    const result = await command.run(`${reviewApi}/cases/${review.caseId}/risk-rating`, 'PUT', withOnBehalf(payload, onBehalfOfPartnerId),
      value => decodeCommand(value, review.caseId), t('risk.saved'));
    if (result) { setDirty(false); onChanged(); }
  }
  return <div className="obw-card"><div className="obw-card-title">{t('risk.title')}<span className="muted">{t('risk.subtitle')}</span></div>
    <div className="obw-risk-current">
      <div><div className="obw-label">{t('risk.current')}</div>{review.riskRating
        ? <StatusDot tone={tone(review.riskRating)}>{enumLabel(t, 'enum.riskRating', review.riskRating)}</StatusDot> : <span className="muted">{t('risk.notRated')}</span>}</div>
      <div><div className="obw-label">{t('field.tier')}</div>{review.tier ? enumLabel(t, 'enum.tier', review.tier) : '—'}</div>
      {review.riskRatedAt && <div><div className="obw-label">{t('risk.recordedAt')}</div>{date(review.riskRatedAt)}<div className="cell-meta">{review.riskRatedBy ?? ''}</div></div>}
      {review.riskRatingNote && <div className="obw-grow"><div className="obw-label">{t('field.noteInternal')}</div>{review.riskRatingNote}</div>}
    </div>
    <div className="obw-label">{t('risk.rating')}</div>
    <Segmented disabled={!editable} value={rating} onChange={value => { setDirty(true); setRating(String(value)); }}
      options={riskRatings.map(value => ({ value, label: <span className={`obw-dot is-${tone(value)}`}>{enumLabel(t, 'enum.riskRating', value)}</span> }))} />
    <div className="obw-label" style={{ marginTop: 14 }}>{t('risk.factors')}</div>
    {factors.map((factor, index) => <div key={index} className="obw-factor-row">
      <Select disabled={!editable} value={factor.factorCode || undefined} placeholder={t('risk.factorPlaceholder')}
        options={riskFactorCodes.map(value => ({ value, label: enumLabel(t, 'enum.riskFactor', value) }))} onChange={value => update(index, { factorCode: value })} />
      <Input disabled={!editable} value={factor.factorValue} maxLength={128} placeholder={t('risk.valuePlaceholder')} onChange={event => update(index, { factorValue: event.target.value })} />
      {editable && factors.length > 1 && <Button type="link" danger onClick={() => { setDirty(true); setFactors(current => current.filter((_, i) => i !== index)); }}>{t('classification.remove')}</Button>}
    </div>)}
    {editable && <>
      <Button size="small" disabled={factors.length >= 50} onClick={() => setFactors(current => [...current, { factorCode: '', factorValue: '' }])}>{t('risk.addFactor')}</Button>
      <div className="obw-label" style={{ marginTop: 14 }}>{t('field.noteInternal')}</div>
      <Input.TextArea rows={2} maxLength={1000} value={note} onChange={event => { setDirty(true); setNote(event.target.value); }} />
      <CommandFailure failure={command.failure} />
      <div className="obw-inline-foot"><span className="cell-meta">{dirty && issues.length > 0 ? issues.join(t('classification.issueSeparator')) : dirty ? t('risk.unsaved') : ''}</span>
        <Button type="primary" disabled={!dirty || issues.length > 0} loading={command.saving} onClick={() => void save()}>{t('risk.save')}</Button></div>
    </>}
    {!editable && active && <div className="cell-meta" style={{ marginTop: 8 }}>{t('risk.noPermission')}</div>}
  </div>;
}

/** 案件时间线：compliance 状态变更与操作记录，代操作时显示 PayFac。 */
export function TimelinePanel({ review, partners }: { review: ReviewCase; partners: readonly ResponsiblePartner[] }) {
  const t = useT('client-review');
  const entries = [...review.timeline].sort((a, b) => b.occurredAt.localeCompare(a.occurredAt));
  if (entries.length === 0) return <div className="obw-card"><Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('timeline.empty')} /></div>;
  return <div className="obw-card"><div className="obw-card-title">{t('timeline.title')}</div>
    <Timeline items={entries.map(entry => ({ key: entry.changeId, color: tone(entry.toStatus) === 'danger' ? 'red' : tone(entry.toStatus) === 'ok' ? 'green' : 'blue',
      content: <div className="obw-timeline-item">
        <div><strong>{enumLabel(t, 'enum.action', entry.actionType)}</strong>
          {entry.toStatus && <span className="cell-meta"> · {entry.fromStatus ? `${enumLabel(t, 'enum.caseStatus', entry.fromStatus)} → ` : ''}{enumLabel(t, 'enum.caseStatus', entry.toStatus)}</span>}</div>
        <div className="cell-meta">{[date(entry.occurredAt), entry.actorId ? t('timeline.actor', { actor: entry.actorId }) : t('timeline.system'),
          entry.operatedBy ? enumLabel(t, 'enum.operatedBy', entry.operatedBy) : null,
          entry.onBehalfOfPartnerId ? t('timeline.onBehalf', { partner: partnerName(partners, entry.onBehalfOfPartnerId) ?? '' }) : null,
          entry.reasonCode].filter(Boolean).join(' · ')}</div>
      </div> }))} />
  </div>;
}

/** 行业名：细类与大类用 refdata 文案 → Platform 英文名 → 编码；不在 ACTIVE 树中的编码原样显示。 */
function industryName(tRef: Translate, index: Map<string, IndustryPosition>, code: string): string {
  const position = index.get(code);
  const node = position?.subcategory ?? (position ? position.category : null);
  return node ? refdataName(tRef, 'industry', code, node.name) : code;
}

/**
 * 行业与 MCC（原型 入驻审核 · 风险与分类 01—02）：先选大类再选细类，带出建议 MCC，MCC 可按编码前缀或英文名搜索改选
 * <li>保存前在本页预检（只选到大类、停用 MCC、重复、主要分类）；client-master 的 422 字段路径也挂到对应行与控件，页面保留输入。</li>
 * <li>保存后确认；确认后不可改。归 client-master，不带代操作字段。行业或 Platform 参考数据不可用时只读。</li>
 */
export function ClassificationPanel({ data, active, canEdit, currentCaseId, onAuthorizationChanged, onChanged }: {
  data: ReviewDetail; active: boolean; canEdit: boolean; currentCaseId?: string | null; onAuthorizationChanged: () => void; onChanged: () => void;
}) {
  const t = useT('client-review'), tRef = useT('refdata');
  const caseId = data.review.caseId; const confirmed = data.classification?.confirmed === true;
  const saved = data.classification?.items ?? [];
  const index = useMemo(() => industryIndex(data.industries), [data.industries]);
  const initial = () => saved.length ? classificationRows(saved, index) : [{ categoryCode: '', industryCode: '', mccCode: '', primary: true }];
  const [rows, setRows] = useState<ClassificationRow[]>(initial);
  const [focus, setFocus] = useState(0); const [checked, setChecked] = useState(false);
  const [knownMccs, setKnownMccs] = useState<Map<string, RefMcc>>(() => new Map(data.mccs.map(m => [m.mccCode, m])));
  const command = useReviewCommand(onAuthorizationChanged);
  const editable = active && canEdit && !confirmed && data.classificationStatus === 'AVAILABLE' && data.industriesStatus === 'AVAILABLE';
  const changes = classificationChanges(rows, saved);
  const listIssues = classificationListIssues(rows);
  const localIssues = classificationRowIssues(rows, index, knownMccs);
  const serverIssues = command.failure?.status === 422 ? classificationServerIssues(command.failure.fieldErrors, rows, knownMccs) : new Map<number, RowIssue[]>();
  const shownIssues = checked ? merge(localIssues, serverIssues) : serverIssues;
  const issueCount = [...shownIssues.values()].reduce((sum, list) => sum + list.length, 0);
  const referenceUnavailable = command.failure?.status === 503;
  const rowRefs = useRef<(HTMLDivElement | null)[]>([]);
  const mccName = (code: string) => refdataName(tRef, 'mcc', code, knownMccs.get(code)?.name);
  const update = (i: number, next: ClassificationRow) => { command.clear(); setFocus(i);
    setRows(current => current.map((row, j) => j === i ? next : next.primary && row.primary ? { ...row, primary: false } : row)); };
  const remember = (mcc: RefMcc) => setKnownMccs(current => new Map(current).set(mcc.mccCode, mcc));
  async function save() {
    setChecked(true);
    if (localIssues.size > 0 || listIssues.length > 0) { locate(localIssues); return; }
    const result = await command.run(`${reviewApi}/cases/${caseId}/classifications`, 'PUT', { items: classificationItems(rows) },
      value => decodeClassificationSet(value, caseId), t('classification.saved'));
    if (result) { setChecked(false); onChanged(); }
  }
  async function confirm() {
    const result = await command.run(`${reviewApi}/cases/${caseId}/classification-confirmation`, 'POST', undefined, value => decodeClassificationSet(value, caseId), t('classification.confirmed'));
    if (result) onChanged();
  }
  function locate(issues: Map<number, RowIssue[]>) {
    const first = Math.min(...issues.keys());
    if (Number.isFinite(first)) { setFocus(first); rowRefs.current[first]?.scrollIntoView({ behavior: 'smooth', block: 'center' }); }
  }
  useEffect(() => { if (serverIssues.size) locate(serverIssues); }, [command.failure]); // eslint-disable-line react-hooks/exhaustive-deps

  if (data.classificationStatus === 'UNAVAILABLE' && currentCaseId !== undefined && currentCaseId !== caseId) {
    return <div className="obw-card"><div className="obw-card-title">{t('classification.title')}</div><HistoricalRound currentCaseId={currentCaseId} /></div>;
  }
  if (data.classificationStatus === 'UNAVAILABLE') {
    return <div className="obw-card"><div className="obw-card-title">{t('classification.title')}</div><Alert showIcon type="warning" title={t('classification.unavailable')} /></div>;
  }
  if (confirmed || !editable) {
    return <div className="obw-card"><div className="obw-card-title">{t('classification.title')}
      {confirmed ? <StatusDot tone="ok">{t('classification.confirmedAt', { time: date(data.classification!.confirmedAt) })}</StatusDot>
        : <Tag color="gold">{t('classification.unconfirmed')}</Tag>}</div>
      {data.industriesStatus === 'UNAVAILABLE' && !confirmed && active && canEdit && <Alert showIcon type="warning" title={t('classification.industriesUnavailable')} style={{ marginBottom: 12 }} />}
      {saved.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('classification.empty')} />
        : saved.map(item => <div key={`${item.industryCode}:${item.mccCode}`} className="obc-saved-row">
          <span>{t('classification.item', { industry: industryName(tRef, index, item.industryCode), mcc: `${item.mccCode} ${mccName(item.mccCode)}` })}</span>
          {item.primary && <Tag color="blue">{t('classification.primaryTag')}</Tag>}
          {knownMccs.get(item.mccCode)?.status === 'INACTIVE' && <Tag>{t('classification.mccInactiveTag')}</Tag>}</div>)}
    </div>;
  }
  const current = rows[Math.min(focus, rows.length - 1)];
  const footState = referenceUnavailable ? <span className="obc-error-text">{t('classification.referenceUnavailable')}</span>
    : issueCount > 0 || (checked && listIssues.length > 0) ? <span className="obc-error-text">{t('classification.saveFailed', { count: issueCount + listIssues.length })}</span>
    : changes > 0 ? <span className="muted">{t('classification.unsaved', { count: changes })}</span> : <span className="muted">{t('classification.noChanges')}</span>;
  return <div className="obw-card obc-classification">
    <div className="obw-card-title">{t('classification.title')}<Tag color="gold">{t('classification.unconfirmed')}</Tag>
      {issueCount > 0 && <button type="button" className="obc-issue-pill" onClick={() => locate(shownIssues)}><ExclamationCircleOutlined /> {t('classification.issuePill', { count: issueCount })}</button>}
      <span className="obw-grow" />
      <Button size="small" icon={<PlusOutlined />} disabled={rows.length >= maxClassifications}
        onClick={() => { command.clear(); setRows(current => [...current, { categoryCode: '', industryCode: '', mccCode: '', primary: current.length === 0 }]); setFocus(rows.length); }}>
        {t('classification.add')}</Button></div>
    <div className="obc-classification-body">
      <div className="obc-grid">
        <div className="obc-grid-head"><span>{t('classification.primary')}</span><span>{t('classification.category')}</span>
          <span>{t('classification.subcategory')}</span><span>{t('classification.mcc')}</span><span /></div>
        {rows.map((row, i) => <ClassificationRowEditor key={i} rowRef={node => { rowRefs.current[i] = node; }} row={row} index={index} focused={i === focus}
          issues={shownIssues.get(i) ?? []} categories={data.industries} mccName={mccName} canRemove={rows.length > 1}
          onFocus={() => setFocus(i)} onChange={next => update(i, next)} onMccPicked={remember}
          onRemove={() => { command.clear(); setRows(current => current.filter((_, j) => j !== i)); setFocus(0); }} />)}
        {checked && listIssues.length > 0 && <div className="obc-error-text obc-list-issues">{listIssues.join(t('classification.issueSeparator'))}</div>}
      </div>
      <aside className="obc-basis">{issueCount > 0
        ? <><div className="obc-basis-title">{t('classification.failedTitle')}</div>
          {[...shownIssues.entries()].sort(([a], [b]) => a - b).flatMap(([row, issues]) => issues.map((issue, k) =>
            <div key={`${row}:${k}`} className="obc-kv"><span className="muted">{t('classification.rowNumber', { row: row + 1 })}</span>
              <Tag color="red">{t('classification.issueTag.' + issue.code)}</Tag></div>))}
          <div className="obc-kv"><span className="muted">{t('classification.thisSave')}</span><span>{t('classification.nothingWritten')}</span></div>
          <p className="muted obc-basis-note">{t('classification.unavailableHint')}</p></>
        : current && <RowBasis row={current} rowNumber={Math.min(focus, rows.length - 1) + 1} index={index} mcc={knownMccs.get(current.mccCode)} mccName={mccName} />}
      </aside>
    </div>
    {command.failure?.status === 422 && serverIssues.size === 0 && <CommandFailure failure={command.failure} />}
    {command.failure?.status === 409 && <Alert showIcon type="warning" title={t('classification.conflict')} style={{ marginTop: 12 }} />}
    <div className="obc-foot">{footState}<span className="obw-grow" />
      <Button disabled={changes === 0 || command.saving} onClick={() => { command.clear(); setChecked(false); setRows(initial()); }}>{t('classification.discard')}</Button>
      <Button type="primary" disabled={changes === 0} loading={command.saving} onClick={() => void save()}>{t('classification.save')}</Button>
      <Button disabled={changes > 0 || !saved.length} loading={command.saving} onClick={() => void confirm()}>{t('classification.confirm')}</Button></div>
  </div>;
}

function merge(a: Map<number, RowIssue[]>, b: Map<number, RowIssue[]>): Map<number, RowIssue[]> {
  const result = new Map(a);
  b.forEach((issues, row) => {
    const existing = result.get(row) ?? [];
    result.set(row, [...existing, ...issues.filter(issue => !existing.some(e => e.field === issue.field))]);
  });
  return result;
}

function ClassificationRowEditor({ row, index, focused, issues, categories, mccName, canRemove, rowRef, onFocus, onChange, onMccPicked, onRemove }: {
  row: ClassificationRow; index: Map<string, IndustryPosition>; focused: boolean; issues: RowIssue[]; categories: ReviewDetail['industries'];
  mccName: (code: string) => string; canRemove: boolean; rowRef: (node: HTMLDivElement | null) => void; onFocus: () => void;
  onChange: (row: ClassificationRow) => void; onMccPicked: (mcc: RefMcc) => void; onRemove: () => void;
}) {
  const t = useT('client-review'), tRef = useT('refdata');
  const category = categories.find(c => c.industryCode === row.categoryCode);
  const industryIssue = issues.find(issue => issue.field === 'industryCode'); const mccIssue = issues.find(issue => issue.field === 'mccCode');
  const mark = mccMark(row, index); const suggested = suggestedMcc(row, index);
  return <div ref={rowRef} className={`obc-row${focused ? ' is-focused' : ''}`} onClick={onFocus}>
    <Radio checked={row.primary} onChange={() => onChange({ ...row, primary: true })} aria-label={t('classification.primary')} />
    <Select className="obc-category" popupMatchSelectWidth={false} value={row.categoryCode || undefined} placeholder={t('classification.categoryPlaceholder')}
      onChange={value => onChange(chooseCategory(row, value))} options={categories.map(c => ({ value: c.industryCode,
        label: <span className="obc-option"><IndustryIcon url={c.logoPublicUrl} />{refdataName(tRef, 'industry', c.industryCode, c.name)}</span> }))} />
    <div><Select className="obc-subcategory" value={row.industryCode || undefined} status={industryIssue ? 'error' : undefined}
      placeholder={t('classification.subcategoryPlaceholder')} disabled={!category} showSearch optionFilterProp="search" popupMatchSelectWidth={false}
      onChange={value => onChange(chooseSubcategory(row, value, index))} options={(category?.children ?? []).map(c => ({ value: c.industryCode,
        search: `${c.industryCode} ${c.name ?? ''} ${refdataName(tRef, 'industry', c.industryCode, c.name)}`,
        label: <span className="obc-option" title={`${c.industryCode} ${refdataName(tRef, 'industry', c.industryCode, c.name)}`}><span className="mono">{c.industryCode}</span>
          <span className="obc-ellipsis">{refdataName(tRef, 'industry', c.industryCode, c.name)}</span></span> }))} />
      {industryIssue && <div className="obc-error"><ExclamationCircleOutlined /> {rowIssueText(industryIssue)}</div>}</div>
    <div><MccPicker value={row.mccCode} suggested={suggested} disabled={!row.industryCode && !row.mccCode} error={!!mccIssue} mccName={mccName}
      onChange={mcc => { onMccPicked(mcc); onChange({ ...row, mccCode: mcc.mccCode }); }} />
      {mccIssue && <div className="obc-error"><ExclamationCircleOutlined /> {rowIssueText(mccIssue)}</div>}</div>
    <span className="obc-row-tail">{mark?.kind === 'suggested' && <Tag color="blue">{t('classification.suggestedTag')}</Tag>}
      {mark?.kind === 'changed' && <Tag color="purple">{t('classification.changedTag', { mcc: mark.suggested })}</Tag>}
      {canRemove && <Button type="text" size="small" icon={<DeleteOutlined />} aria-label={t('classification.remove')}
        onClick={event => { event.stopPropagation(); onRemove(); }} />}</span>
  </div>;
}

/**
 * MCC 搜索框（原型 01）：按编码前缀或英文名搜索 ACTIVE MCC，分页取前 10 条；细类建议的 MCC 标"细类建议"，卡组织高风险标红
 * <li>搜索走分类专用路由 /client-onboarding-admin/v1/mccs（复用分类编辑权限），300 ms 防抖，切换关键字时取消旧请求。</li>
 */
function MccPicker({ value, suggested, disabled, error, mccName, onChange }: {
  value: string; suggested: string | null; disabled: boolean; error: boolean; mccName: (code: string) => string; onChange: (mcc: RefMcc) => void;
}) {
  const t = useT('client-review'), tRef = useT('refdata');
  const [open, setOpen] = useState(false); const [keyword, setKeyword] = useState('');
  const [result, setResult] = useState<{ items: RefMcc[]; total: number } | null>(null); const [loading, setLoading] = useState(false);
  const [failure, setFailure] = useState<ApiError | null>(null);
  useEffect(() => {
    if (!open) return;
    const controller = new AbortController();
    const timer = window.setTimeout(() => {
      setLoading(true); setFailure(null);
      const query = new URLSearchParams({ page: '1', size: '10', ...(keyword.trim() ? { keyword: keyword.trim() } : {}) });
      session.request(`${reviewApi}/mccs?${query}`, { signal: controller.signal }, decodeMccPage)
        .then(page => { if (!controller.signal.aborted) setResult({ items: page.items, total: page.total }); })
        .catch(reason => { if (!controller.signal.aborted) { setResult(null); setFailure(reason instanceof ApiError ? reason : new ApiError(0, 'NETWORK')); } })
        .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    }, 300);
    return () => { controller.abort(); window.clearTimeout(timer); };
  }, [open, keyword]);
  const content = <div className="obc-mcc-popover">
    <Input autoFocus allowClear prefix={<SearchOutlined />} value={keyword} placeholder={t('classification.mccSearch')} onChange={event => setKeyword(event.target.value)} />
    <div className="obc-mcc-results">
      {loading && !result ? <Spin size="small" /> : failure ? <div className="obc-error-text">{failure.status === 503 ? t('classification.referenceUnavailable') : t('classification.mccSearchFailed')}</div>
        : result && result.items.length === 0 ? <div className="muted">{t('classification.mccNoMatch')}</div>
        : result?.items.map(mcc => <button type="button" key={mcc.mccCode} className={`obc-mcc-item${mcc.mccCode === value ? ' is-selected' : ''}`}
          onClick={() => { onChange(mcc); setOpen(false); }}>
          <strong>{mcc.mccCode}</strong>
          <span className="obw-grow"><span>{refdataName(tRef, 'mcc', mcc.mccCode, mcc.name)}</span>
            <span className="muted">{untranslated(tRef, 'mcc', mcc.mccCode, mcc.name) ? t('classification.mccNoTranslation') : mcc.name}</span></span>
          {mcc.mccCode === suggested && <Tag color="blue">{t('classification.mccSuggested')}</Tag>}
          {mcc.networkHighRisk && <Tag color="red">{t('classification.mccHighRisk')}</Tag>}
          {mcc.mccCode === value && <CheckOutlined className="obc-check" />}</button>)}
    </div>
    <div className="muted obc-mcc-foot">{result && result.total > result.items.length ? t('classification.mccMore', { total: result.total }) : t('classification.mccHint')}</div>
  </div>;
  return <Popover open={open && !disabled} onOpenChange={next => setOpen(next && !disabled)} trigger="click" placement="bottomLeft" content={content} arrow={false}>
    <button type="button" className={`obc-mcc-field${error ? ' is-error' : ''}`} disabled={disabled}>
      {value ? <><strong>{value}</strong><span className="obw-grow obc-ellipsis">{mccName(value)}</span></>
        : <span className="obw-grow muted">{disabled ? t('classification.mccAfterSubcategory') : t('classification.mccPlaceholder')}</span>}
      <SearchOutlined /></button>
  </Popover>;
}

/** 右侧"第 N 行的依据"：细类说明、建议 MCC、当前 MCC 与卡组织高风险。 */
function RowBasis({ row, rowNumber, index, mcc, mccName }: {
  row: ClassificationRow; rowNumber: number; index: Map<string, IndustryPosition>; mcc: RefMcc | undefined; mccName: (code: string) => string;
}) {
  const t = useT('client-review'), tRef = useT('refdata');
  const subcategory = index.get(row.industryCode)?.subcategory ?? null; const suggested = suggestedMcc(row, index); const mark = mccMark(row, index);
  return <>
    <div className="obc-basis-title">{t('classification.basisTitle', { row: rowNumber })}</div>
    <div className="obc-kv"><span className="muted">{t('classification.subcategoryHint')}</span><span>{subcategory ? industryDescription(tRef, subcategory.industryCode, subcategory.description) ?? '—' : '—'}</span></div>
    <div className="obc-kv"><span className="muted">{t('classification.suggestedMcc')}</span><span>{suggested ? <><strong>{suggested}</strong> {mccName(suggested)}</> : '—'}</span></div>
    <div className="obc-kv"><span className="muted">{t('classification.currentMcc')}</span><span>{row.mccCode ? <><strong>{row.mccCode}</strong> {mccName(row.mccCode)}
      {mark?.kind === 'changed' && <Tag color="purple" style={{ marginLeft: 6 }}>{t('classification.changedShort')}</Tag>}</> : '—'}</span></div>
    <div className="obc-kv"><span className="muted">{t('classification.networkHighRisk')}</span><span>{mcc ? <Tag color={mcc.networkHighRisk ? 'red' : undefined}>
      {mcc.networkHighRisk ? t('classification.yes') : t('classification.no')}</Tag> : '—'}</span></div>
    <p className="muted obc-basis-note">{t('classification.basisNote')}</p>
  </>;
}
