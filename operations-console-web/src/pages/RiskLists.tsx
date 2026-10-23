import { useEffect, useState } from 'react';
import { Alert, App, Button, Form, Input, Modal, Radio, Select, Space, Table, Tag } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import type { ConsoleResource, PagingResponseBody } from '../api/types';
import { ApiError } from '../api/session';
import { formatRiskTime } from '../risk/RuleSetDetails';
import { entryFilters, entryStatusLabel, entryStatuses, hasResource, listPurposeLabel, localToUtc, riskListApi } from '../risk/riskControl';
import type { AddRiskListEntry, CreateRiskList, RiskList, RiskListEntry } from '../risk/riskControl';
import { riskSubjects, subjectLabel } from '../risk/riskVariable';
import '../risk/riskVariables.css';
import { tOr, translator } from '../i18n/index';
import { useT } from '../i18n/react';

const MANAGE = 'OPERATIONS_RISK_LIST_MANAGE';
/** 主体值格式提示是 `risk.listPage.subjectHint.<主体类型>`，未知主体为空。 */
const subjectHint = (subjectType: string) => tOr(translator('risk'), 'listPage.subjectHint.' + subjectType, '');

function errorText(e: unknown): string {
  if (e instanceof ApiError) return `${e.message}${e.requestId ? ` · Request ID: ${e.requestId}` : ''}`;
  return e instanceof Error ? e.message : translator('risk')('releasePage.actionFailed');
}

/** 风控名单：名单列表与成员维护；主体值只显示 Risk 返回的脱敏键，查询输入不进入地址栏。 */
export function RiskLists({ resources, onAuthorizationChanged }: { resources: readonly ConsoleResource[]; onAuthorizationChanged: () => void }) {
  const location = useLocation(), navigate = useNavigate();
  const listCode = new URLSearchParams(location.search).get('listCode') ?? '';
  const canManage = hasResource(resources, MANAGE);
  const failure = (e: unknown) => { if (e instanceof ApiError && e.status === 403) onAuthorizationChanged(); };
  return listCode
    ? <ListEntries listCode={listCode} canManage={canManage} onBack={() => void navigate({ search: '' })} onFailure={failure} />
    : <ListCatalog canManage={canManage} onOpen={code => void navigate({ search: `?listCode=${encodeURIComponent(code)}` })} onFailure={failure} />;
}

function ListCatalog({ canManage, onOpen, onFailure }: { canManage: boolean; onOpen: (code: string) => void; onFailure: (e: unknown) => void }) {
  const t = useT('risk'), tNav = useT('nav');
  const [paging, setPaging] = useState({ page: 1, size: 10 }), [data, setData] = useState<PagingResponseBody<RiskList> | null>(null);
  const [loading, setLoading] = useState(false), [error, setError] = useState(''), [revision, setRevision] = useState(0), [creating, setCreating] = useState(false);
  useEffect(() => {
    const abort = new AbortController(); setLoading(true); setError('');
    riskListApi.list(paging, abort.signal).then(r => { if (!abort.signal.aborted) setData(r); })
      .catch(e => { if (!abort.signal.aborted) { setError(errorText(e)); onFailure(e); } })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, [paging, revision]);
  return <div className="variable-page">
    <div className="page-heading"><div><h1>{tNav('resource.OPERATIONS_RISK_LISTS')}</h1><p className="cell-meta">{t('listPage.subtitle')}</p></div>
      <Space><Button onClick={() => setRevision(r => r + 1)} loading={loading}>{t('candidateList.refresh')}</Button>{canManage && <Button type="primary" onClick={() => setCreating(true)}>{t('listPage.create')}</Button>}</Space></div>
    {error && <Alert type="error" showIcon title={error} />}
    <section className="table-panel"><Table<RiskList> rowKey="listId" loading={loading} dataSource={data?.items ?? []}
      locale={{ emptyText: error ? t('listPage.loadFailed') : t('listPage.empty') }}
      pagination={{ current: paging.page, pageSize: paging.size, total: data?.total ?? 0, showSizeChanger: true, pageSizeOptions: [10, 20, 50, 100, 200], onChange: (page, size) => setPaging({ page: size === paging.size ? page : 1, size }) }}
      columns={[
        { title: t('listPage.column.nameCode'), render: (_, r) => <><strong>{r.listName}</strong><div className="cell-meta">{r.listCode}</div></> },
        { title: t('feature.list.subjectType'), render: (_, r) => subjectLabel(r.subjectType) },
        { title: t('listPage.column.purpose'), render: (_, r) => <Tag color={r.purpose === 'BLOCK' ? 'red' : 'green'}>{listPurposeLabel(r.purpose)}</Tag> },
        { title: t('candidateList.status'), render: (_, r) => <Tag color={r.active ? 'success' : 'default'}>{r.active ? t('ruleSetDetails.enabled') : t('ruleSetDetails.disabled')}</Tag> },
        { title: t('candidateList.column.actions'), render: (_, r) => <Button type="link" onClick={() => onOpen(r.listCode)}>{t('listPage.members')}</Button> },
      ]} /></section>
    {creating && <CreateListModal onClose={() => setCreating(false)} onCreated={() => { setCreating(false); setRevision(r => r + 1); }} onFailure={onFailure} />}
  </div>;
}

function CreateListModal({ onClose, onCreated, onFailure }: { onClose: () => void; onCreated: () => void; onFailure: (e: unknown) => void }) {
  const t = useT('risk');
  const { message } = App.useApp();
  const [draft, setDraft] = useState<CreateRiskList>({ listCode: '', listName: '', subjectType: 'IP_ADDRESS', purpose: 'BLOCK' });
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  async function submit() {
    if (!/^[A-Z][A-Z0-9_]{0,63}$/.test(draft.listCode)) { setError(t('listPage.codeFormat')); return; }
    if (!draft.listName.trim()) { setError(t('listPage.nameRequired')); return; }
    setBusy(true); setError('');
    try { await riskListApi.create({ ...draft, listName: draft.listName.trim() }); void message.success(t('listPage.created')); onCreated(); }
    catch (e) { setError(errorText(e)); onFailure(e); } finally { setBusy(false); }
  }
  return <Modal open title={t('listPage.createTitle')} okText={t('listPage.createOk')} cancelText={t('ruleEditor.cancel')} confirmLoading={busy} onOk={() => void submit()} onCancel={onClose} destroyOnHidden>
    <Form layout="vertical">
      <Form.Item label={t('listPage.code')} required extra={t('listPage.codeHint')}><Input aria-label={t('listPage.code')} value={draft.listCode} maxLength={64} autoComplete="off" onChange={e => setDraft({ ...draft, listCode: e.target.value })} /></Form.Item>
      <Form.Item label={t('listPage.name')} required><Input aria-label={t('listPage.name')} value={draft.listName} maxLength={255} onChange={e => setDraft({ ...draft, listName: e.target.value })} /></Form.Item>
      <Form.Item label={t('feature.list.subjectType')} required extra={t('listPage.subjectTypeHint')}><Select aria-label={t('feature.list.subjectType')} value={draft.subjectType} options={riskSubjects.map(value => ({ value, label: subjectLabel(value) }))} onChange={subjectType => setDraft({ ...draft, subjectType })} /></Form.Item>
      <Form.Item label={t('listPage.column.purpose')} required extra={t('listPage.purposeHint')}><Radio.Group value={draft.purpose} options={[{ value: 'BLOCK', label: listPurposeLabel('BLOCK') }, { value: 'ALLOW', label: listPurposeLabel('ALLOW') }]} onChange={e => setDraft({ ...draft, purpose: e.target.value })} /></Form.Item>
    </Form>
    {error && <Alert type="error" showIcon title={error} />}
  </Modal>;
}

function ListEntries({ listCode, canManage, onBack, onFailure }: { listCode: string; canManage: boolean; onBack: () => void; onFailure: (e: unknown) => void }) {
  const t = useT('risk'), tNav = useT('nav');
  const [list, setList] = useState<RiskList | null>(null), [data, setData] = useState<PagingResponseBody<RiskListEntry> | null>(null);
  const [paging, setPaging] = useState({ page: 1, size: 10 }), [status, setStatus] = useState('ACTIVE');
  // 原值只保存在组件内存：输入框提交后清空，已应用的筛选显示为“已按主体值筛选”。
  const [subjectInput, setSubjectInput] = useState(''), [appliedSubject, setAppliedSubject] = useState('');
  const [loading, setLoading] = useState(false), [error, setError] = useState(''), [revision, setRevision] = useState(0);
  const [adding, setAdding] = useState(false), [releasing, setReleasing] = useState<RiskListEntry | null>(null);
  useEffect(() => {
    const abort = new AbortController();
    riskListApi.all(abort.signal).then(r => { if (!abort.signal.aborted) setList(r.find(l => l.listCode === listCode) ?? null); }).catch(() => undefined);
    return () => abort.abort();
  }, [listCode]);
  useEffect(() => {
    const abort = new AbortController(); setLoading(true); setError('');
    riskListApi.entries(listCode, paging, entryFilters(appliedSubject, status), abort.signal).then(r => { if (!abort.signal.aborted) setData(r); })
      .catch(e => { if (!abort.signal.aborted) { setError(errorText(e)); onFailure(e); } })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, [listCode, paging, status, appliedSubject, revision]);
  function search() { setAppliedSubject(subjectInput.trim()); setSubjectInput(''); setPaging(p => ({ ...p, page: 1 })); }
  return <div className="variable-page">
    <div className="page-heading"><div>
      <Space><Button type="link" onClick={onBack}>← {tNav('resource.OPERATIONS_RISK_LISTS')}</Button></Space>
      <h1>{list?.listName ?? listCode} {list && <Tag color={list.purpose === 'BLOCK' ? 'red' : 'green'}>{listPurposeLabel(list.purpose)}</Tag>}</h1>
      <p className="cell-meta">{listCode}{list ? ` · ${t('listPage.subjectTypeValue', { subject: subjectLabel(list.subjectType), hint: subjectHint(list.subjectType) })}` : ''}</p></div>
      <Space><Button onClick={() => setRevision(r => r + 1)} loading={loading}>{t('candidateList.refresh')}</Button>{canManage && <Button type="primary" disabled={!list?.active} onClick={() => setAdding(true)}>{t('listPage.addMember')}</Button>}</Space></div>
    {error && <Alert type="error" showIcon title={error} />}
    <section className="table-panel">
      <Form layout="inline" className="risk-filters" onFinish={search}>
        <Form.Item label={t('listPage.subjectValue')}><Input aria-label={t('listPage.subjectValue')} value={subjectInput} autoComplete="off" maxLength={256} placeholder={t('listPage.subjectPlaceholder')} style={{ width: 280 }} onChange={e => setSubjectInput(e.target.value)} /></Form.Item>
        <Form.Item label={t('candidateList.status')}><Select aria-label={t('listPage.memberStatus')} value={status} style={{ width: 140 }} options={[{ value: '', label: t('candidateList.allStatuses') }, ...entryStatuses.map(value => ({ value, label: entryStatusLabel(value) }))]} onChange={value => { setStatus(value); setPaging(p => ({ ...p, page: 1 })); }} /></Form.Item>
        <Button htmlType="submit" type="primary">{t('candidateList.search')}</Button>
        <Button onClick={() => { setSubjectInput(''); setAppliedSubject(''); setStatus('ACTIVE'); setPaging(p => ({ ...p, page: 1 })); }}>{t('flowPage.reset')}</Button>
        {appliedSubject && <Tag closable onClose={() => setAppliedSubject('')}>{t('listPage.filtered')}</Tag>}
      </Form>
      <Alert type="info" showIcon title={t('listPage.restrictedHint')} style={{ margin: '12px 0' }} />
      <Table<RiskListEntry> rowKey="entryId" loading={loading} dataSource={data?.items ?? []}
        locale={{ emptyText: error ? t('listPage.membersLoadFailed') : appliedSubject ? t('listPage.noMatchingMembers') : t('listPage.noMembers') }}
        pagination={{ current: paging.page, pageSize: paging.size, total: data?.total ?? 0, showSizeChanger: true, pageSizeOptions: [10, 20, 50, 100, 200], onChange: (page, size) => setPaging({ page: size === paging.size ? page : 1, size }) }}
        columns={[
          { title: t('listPage.column.maskedSubject'), render: (_, r) => <code>{r.maskedSubjectKey}</code> },
          { title: t('candidateList.status'), render: (_, r) => <Tag color={r.status === 'ACTIVE' ? 'success' : 'default'}>{entryStatusLabel(r.status)}</Tag> },
          { title: t('listPage.column.effective'), render: (_, r) => t('listPage.effectiveRange', { from: formatRiskTime(r.effectiveFrom), to: r.effectiveTo ? formatRiskTime(r.effectiveTo) : t('listPage.permanent') }) },
          { title: t('listPage.column.reason'), render: (_, r) => <>{r.reason}{r.deactivatedReason && <div className="cell-meta">{t('listPage.deactivatedReason', { reason: r.deactivatedReason })}{r.deactivatedBy ? ` · ${r.deactivatedBy}` : ''}</div>}</> },
          { title: t('listPage.column.added'), render: (_, r) => <>{r.createdBy ?? (r.sourceType === 'ACTION' ? t('listPage.ruleAction') : '—')}<div className="cell-meta">{formatRiskTime(r.createTime)}</div></> },
          { title: t('candidateList.column.actions'), render: (_, r) => canManage && r.status === 'ACTIVE' ? <Button type="link" danger onClick={() => setReleasing(r)}>{t('listPage.deactivate')}</Button> : '—' },
        ]} />
    </section>
    {adding && list && <AddEntryModal list={list} onClose={() => setAdding(false)} onAdded={() => { setAdding(false); setRevision(r => r + 1); }} onFailure={onFailure} />}
    {releasing && <DeactivateModal listCode={listCode} entry={releasing} onClose={() => setReleasing(null)} onDone={() => { setReleasing(null); setRevision(r => r + 1); }} onFailure={onFailure} />}
  </div>;
}

function AddEntryModal({ list, onClose, onAdded, onFailure }: { list: RiskList; onClose: () => void; onAdded: () => void; onFailure: (e: unknown) => void }) {
  const t = useT('risk');
  const { message } = App.useApp();
  const [subjectValue, setSubjectValue] = useState(''), [from, setFrom] = useState(''), [to, setTo] = useState(''), [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  async function submit() {
    if (!subjectValue.trim() || !reason.trim()) { setError(t('listPage.subjectAndReasonRequired')); return; }
    let body: AddRiskListEntry;
    try { body = { subjectValue: subjectValue.trim(), effectiveFrom: localToUtc(from), effectiveTo: localToUtc(to), reason: reason.trim() }; }
    catch { setError(t('listPage.invalidTime')); return; }
    setBusy(true); setError('');
    try { const added = await riskListApi.add(list.listCode, body); setSubjectValue(''); void message.success(t('listPage.added', { subject: added.maskedSubjectKey })); onAdded(); }
    catch (e) { setSubjectValue(''); setError(e instanceof ApiError && e.status === 409 ? t('listPage.alreadyActive') : errorText(e)); onFailure(e); }
    finally { setBusy(false); }
  }
  return <Modal open title={t('listPage.addTitle', { name: list.listName })} okText={t('listPage.addOk')} cancelText={t('ruleEditor.cancel')} confirmLoading={busy} onOk={() => void submit()} onCancel={onClose} destroyOnHidden>
    <Form layout="vertical">
      <Form.Item label={t('listPage.subjectValue')} required extra={t('listPage.subjectValueHint', { subject: subjectLabel(list.subjectType), hint: subjectHint(list.subjectType) })}><Input aria-label={t('listPage.listSubjectValue')} value={subjectValue} autoComplete="off" maxLength={256} onChange={e => setSubjectValue(e.target.value)} /></Form.Item>
      <Space style={{ width: '100%' }}>
        <Form.Item label={t('listPage.from')} extra={t('listPage.fromHint')}><Input aria-label={t('listPage.from')} type="datetime-local" value={from} onChange={e => setFrom(e.target.value)} /></Form.Item>
        <Form.Item label={t('listPage.to')} extra={t('listPage.toHint')}><Input aria-label={t('listPage.to')} type="datetime-local" value={to} onChange={e => setTo(e.target.value)} /></Form.Item>
      </Space>
      <Form.Item label={t('listPage.column.reason')} required><Input.TextArea aria-label={t('listPage.addReason')} value={reason} maxLength={500} autoSize={{ minRows: 3, maxRows: 6 }} onChange={e => setReason(e.target.value)} /></Form.Item>
    </Form>
    {error && <Alert type="error" showIcon title={error} />}
  </Modal>;
}

function DeactivateModal({ listCode, entry, onClose, onDone, onFailure }: { listCode: string; entry: RiskListEntry; onClose: () => void; onDone: () => void; onFailure: (e: unknown) => void }) {
  const t = useT('risk');
  const { message } = App.useApp();
  const [reason, setReason] = useState(''), [busy, setBusy] = useState(false), [error, setError] = useState('');
  async function submit() {
    if (!reason.trim()) { setError(t('listPage.deactivateReasonRequired')); return; }
    setBusy(true); setError('');
    try { await riskListApi.deactivate(listCode, entry.entryId, reason.trim()); void message.success(t('listPage.deactivated')); onDone(); }
    catch (e) { setError(errorText(e)); onFailure(e); } finally { setBusy(false); }
  }
  return <Modal open title={t('listPage.deactivateTitle', { subject: entry.maskedSubjectKey })} okText={t('listPage.confirmDeactivate')} okButtonProps={{ danger: true }} cancelText={t('ruleEditor.cancel')} confirmLoading={busy} onOk={() => void submit()} onCancel={onClose} destroyOnHidden>
    <p>{t('listPage.deactivateHint')}</p>
    <Form layout="vertical"><Form.Item label={t('listPage.deactivateReason')} required><Input.TextArea aria-label={t('listPage.deactivateReason')} value={reason} maxLength={500} autoSize={{ minRows: 3, maxRows: 6 }} onChange={e => setReason(e.target.value)} /></Form.Item></Form>
    {error && <Alert type="error" showIcon title={error} />}
  </Modal>;
}
