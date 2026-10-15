import { useEffect, useState } from 'react';
import { Alert, App, Button, Form, Input, Modal, Radio, Select, Space, Table, Tag } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import type { ConsoleResource, PagingResponseBody } from '../api/types';
import { ApiError } from '../api/session';
import { formatRiskTime } from '../risk/RuleSetDetails';
import { entryFilters, entryStatusLabels, hasResource, listPurposeLabels, localToUtc, riskListApi, statusLabel } from '../risk/riskControl';
import type { AddRiskListEntry, CreateRiskList, RiskList, RiskListEntry } from '../risk/riskControl';
import { riskSubjects, subjectLabel } from '../risk/riskVariable';
import '../risk/riskVariables.css';

const MANAGE = 'OPERATIONS_RISK_LIST_MANAGE';
const subjectHints: Record<string, string> = { IP_ADDRESS: 'IPv4 原值；IPv6 自动取 /64 前缀', CARD_BIN: '卡号前 6～8 位数字', CONSUMER: 'Client ID:消费者引用', CARD_FINGERPRINT: 'Card 生成的卡指纹', CLIENT: 'Client ID（正整数）', OPERATING_ENTITY: '5 位运营主体编号' };

function errorText(e: unknown): string {
  if (e instanceof ApiError) return `${e.message}${e.requestId ? ` · Request ID: ${e.requestId}` : ''}`;
  return e instanceof Error ? e.message : '操作失败，请重试';
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
    <div className="page-heading"><div><h1>风控名单</h1><p className="cell-meta">名单用于“名单命中”变量；命中后如何处置由规则决定，名单用途仅用于展示。</p></div>
      <Space><Button onClick={() => setRevision(r => r + 1)} loading={loading}>刷新</Button>{canManage && <Button type="primary" onClick={() => setCreating(true)}>＋ 新建名单</Button>}</Space></div>
    {error && <Alert type="error" showIcon title={error} />}
    <section className="table-panel"><Table<RiskList> rowKey="listId" loading={loading} dataSource={data?.items ?? []}
      locale={{ emptyText: error ? '名单加载失败' : '暂无名单。名单用于“名单命中”变量，命中后如何处置由规则决定。' }}
      pagination={{ current: paging.page, pageSize: paging.size, total: data?.total ?? 0, showSizeChanger: true, pageSizeOptions: [10, 20, 50, 100, 200], onChange: (page, size) => setPaging({ page: size === paging.size ? page : 1, size }) }}
      columns={[
        { title: '名单名称 / 编码', render: (_, r) => <><strong>{r.listName}</strong><div className="cell-meta">{r.listCode}</div></> },
        { title: '主体类型', render: (_, r) => subjectLabel(r.subjectType) },
        { title: '用途', render: (_, r) => <Tag color={r.purpose === 'BLOCK' ? 'red' : 'green'}>{listPurposeLabels[r.purpose]}</Tag> },
        { title: '状态', render: (_, r) => <Tag color={r.active ? 'success' : 'default'}>{r.active ? '启用' : '停用'}</Tag> },
        { title: '操作', render: (_, r) => <Button type="link" onClick={() => onOpen(r.listCode)}>成员</Button> },
      ]} /></section>
    {creating && <CreateListModal onClose={() => setCreating(false)} onCreated={() => { setCreating(false); setRevision(r => r + 1); }} onFailure={onFailure} />}
  </div>;
}

function CreateListModal({ onClose, onCreated, onFailure }: { onClose: () => void; onCreated: () => void; onFailure: (e: unknown) => void }) {
  const { message } = App.useApp();
  const [draft, setDraft] = useState<CreateRiskList>({ listCode: '', listName: '', subjectType: 'IP_ADDRESS', purpose: 'BLOCK' });
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  async function submit() {
    if (!/^[A-Z][A-Z0-9_]{0,63}$/.test(draft.listCode)) { setError('名单编码须为大写字母开头，最长 64 位大写字母、数字或下划线'); return; }
    if (!draft.listName.trim()) { setError('请填写名单名称'); return; }
    setBusy(true); setError('');
    try { await riskListApi.create({ ...draft, listName: draft.listName.trim() }); void message.success('名单已创建'); onCreated(); }
    catch (e) { setError(errorText(e)); onFailure(e); } finally { setBusy(false); }
  }
  return <Modal open title="新建名单" okText="创建" cancelText="取消" confirmLoading={busy} onOk={() => void submit()} onCancel={onClose} destroyOnHidden>
    <Form layout="vertical">
      <Form.Item label="名单编码" required extra="大写字母开头，可含数字和下划线，最长 64；创建后不可修改。"><Input aria-label="名单编码" value={draft.listCode} maxLength={64} autoComplete="off" onChange={e => setDraft({ ...draft, listCode: e.target.value })} /></Form.Item>
      <Form.Item label="名单名称" required><Input aria-label="名单名称" value={draft.listName} maxLength={255} onChange={e => setDraft({ ...draft, listName: e.target.value })} /></Form.Item>
      <Form.Item label="主体类型" required extra="需与“名单命中”变量的主体一致；创建后不可修改。"><Select aria-label="主体类型" value={draft.subjectType} options={riskSubjects.map(s => ({ value: s.value, label: s.label }))} onChange={subjectType => setDraft({ ...draft, subjectType })} /></Form.Item>
      <Form.Item label="用途" required extra="仅用于展示，命中后如何处置由规则决定。"><Radio.Group value={draft.purpose} options={[{ value: 'BLOCK', label: '拦截' }, { value: 'ALLOW', label: '放行' }]} onChange={e => setDraft({ ...draft, purpose: e.target.value })} /></Form.Item>
    </Form>
    {error && <Alert type="error" showIcon title={error} />}
  </Modal>;
}

function ListEntries({ listCode, canManage, onBack, onFailure }: { listCode: string; canManage: boolean; onBack: () => void; onFailure: (e: unknown) => void }) {
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
      <Space><Button type="link" onClick={onBack}>← 风控名单</Button></Space>
      <h1>{list?.listName ?? listCode} {list && <Tag color={list.purpose === 'BLOCK' ? 'red' : 'green'}>{listPurposeLabels[list.purpose]}</Tag>}</h1>
      <p className="cell-meta">{listCode}{list ? ` · 主体类型：${subjectLabel(list.subjectType)}（${subjectHints[list.subjectType] ?? ''}）` : ''}</p></div>
      <Space><Button onClick={() => setRevision(r => r + 1)} loading={loading}>刷新</Button>{canManage && <Button type="primary" disabled={!list?.active} onClick={() => setAdding(true)}>＋ 加入成员</Button>}</Space></div>
    {error && <Alert type="error" showIcon title={error} />}
    <section className="table-panel">
      <Form layout="inline" className="risk-filters" onFinish={search}>
        <Form.Item label="主体值"><Input aria-label="主体值" value={subjectInput} autoComplete="off" maxLength={256} placeholder="精确匹配，输入原值" style={{ width: 280 }} onChange={e => setSubjectInput(e.target.value)} /></Form.Item>
        <Form.Item label="状态"><Select aria-label="成员状态" value={status} style={{ width: 140 }} options={[{ value: '', label: '全部状态' }, ...Object.entries(entryStatusLabels).map(([value, label]) => ({ value, label }))]} onChange={value => { setStatus(value); setPaging(p => ({ ...p, page: 1 })); }} /></Form.Item>
        <Button htmlType="submit" type="primary">查询</Button>
        <Button onClick={() => { setSubjectInput(''); setAppliedSubject(''); setStatus('ACTIVE'); setPaging(p => ({ ...p, page: 1 })); }}>重置</Button>
        {appliedSubject && <Tag closable onClose={() => setAppliedSubject('')}>已按主体值筛选</Tag>}
      </Form>
      <Alert type="info" showIcon title="主体值为受限数据，页面只显示脱敏结果；查询输入不进入地址栏，提交后清空。" style={{ margin: '12px 0' }} />
      <Table<RiskListEntry> rowKey="entryId" loading={loading} dataSource={data?.items ?? []}
        locale={{ emptyText: error ? '成员加载失败' : appliedSubject ? '没有匹配的成员' : '暂无成员' }}
        pagination={{ current: paging.page, pageSize: paging.size, total: data?.total ?? 0, showSizeChanger: true, pageSizeOptions: [10, 20, 50, 100, 200], onChange: (page, size) => setPaging({ page: size === paging.size ? page : 1, size }) }}
        columns={[
          { title: '主体（脱敏）', render: (_, r) => <code>{r.maskedSubjectKey}</code> },
          { title: '状态', render: (_, r) => <Tag color={r.status === 'ACTIVE' ? 'success' : 'default'}>{statusLabel(entryStatusLabels, r.status)}</Tag> },
          { title: '生效时间', render: (_, r) => `${formatRiskTime(r.effectiveFrom)} ～ ${r.effectiveTo ? formatRiskTime(r.effectiveTo) : '永久'}` },
          { title: '原因', render: (_, r) => <>{r.reason}{r.deactivatedReason && <div className="cell-meta">失效原因：{r.deactivatedReason}{r.deactivatedBy ? ` · ${r.deactivatedBy}` : ''}</div>}</> },
          { title: '添加人 / 时间', render: (_, r) => <>{r.createdBy ?? (r.sourceType === 'ACTION' ? '规则动作' : '—')}<div className="cell-meta">{formatRiskTime(r.createTime)}</div></> },
          { title: '操作', render: (_, r) => canManage && r.status === 'ACTIVE' ? <Button type="link" danger onClick={() => setReleasing(r)}>失效</Button> : '—' },
        ]} />
    </section>
    {adding && list && <AddEntryModal list={list} onClose={() => setAdding(false)} onAdded={() => { setAdding(false); setRevision(r => r + 1); }} onFailure={onFailure} />}
    {releasing && <DeactivateModal listCode={listCode} entry={releasing} onClose={() => setReleasing(null)} onDone={() => { setReleasing(null); setRevision(r => r + 1); }} onFailure={onFailure} />}
  </div>;
}

function AddEntryModal({ list, onClose, onAdded, onFailure }: { list: RiskList; onClose: () => void; onAdded: () => void; onFailure: (e: unknown) => void }) {
  const { message } = App.useApp();
  const [subjectValue, setSubjectValue] = useState(''), [from, setFrom] = useState(''), [to, setTo] = useState(''), [reason, setReason] = useState('');
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  async function submit() {
    if (!subjectValue.trim() || !reason.trim()) { setError('请填写主体值和原因'); return; }
    let body: AddRiskListEntry;
    try { body = { subjectValue: subjectValue.trim(), effectiveFrom: localToUtc(from), effectiveTo: localToUtc(to), reason: reason.trim() }; }
    catch { setError('生效时间格式无效'); return; }
    setBusy(true); setError('');
    try { const added = await riskListApi.add(list.listCode, body); setSubjectValue(''); void message.success(`已加入：${added.maskedSubjectKey}`); onAdded(); }
    catch (e) { setSubjectValue(''); setError(e instanceof ApiError && e.status === 409 ? '该主体已在名单中生效，无需重复加入。' : errorText(e)); onFailure(e); }
    finally { setBusy(false); }
  }
  return <Modal open title={`加入成员 · ${list.listName}`} okText="加入" cancelText="取消" confirmLoading={busy} onOk={() => void submit()} onCancel={onClose} destroyOnHidden>
    <Form layout="vertical">
      <Form.Item label="主体值" required extra={`${subjectLabel(list.subjectType)}：${subjectHints[list.subjectType] ?? ''}。提交后输入框清空，原值不保存在浏览器。`}><Input aria-label="名单主体值" value={subjectValue} autoComplete="off" maxLength={256} onChange={e => setSubjectValue(e.target.value)} /></Form.Item>
      <Space style={{ width: '100%' }}>
        <Form.Item label="生效开始" extra="留空＝立即生效"><Input aria-label="生效开始" type="datetime-local" value={from} onChange={e => setFrom(e.target.value)} /></Form.Item>
        <Form.Item label="生效结束" extra="留空＝永久"><Input aria-label="生效结束" type="datetime-local" value={to} onChange={e => setTo(e.target.value)} /></Form.Item>
      </Space>
      <Form.Item label="原因" required><Input.TextArea aria-label="加入原因" value={reason} maxLength={500} autoSize={{ minRows: 3, maxRows: 6 }} onChange={e => setReason(e.target.value)} /></Form.Item>
    </Form>
    {error && <Alert type="error" showIcon title={error} />}
  </Modal>;
}

function DeactivateModal({ listCode, entry, onClose, onDone, onFailure }: { listCode: string; entry: RiskListEntry; onClose: () => void; onDone: () => void; onFailure: (e: unknown) => void }) {
  const { message } = App.useApp();
  const [reason, setReason] = useState(''), [busy, setBusy] = useState(false), [error, setError] = useState('');
  async function submit() {
    if (!reason.trim()) { setError('请填写失效原因'); return; }
    setBusy(true); setError('');
    try { await riskListApi.deactivate(listCode, entry.entryId, reason.trim()); void message.success('成员已失效'); onDone(); }
    catch (e) { setError(errorText(e)); onFailure(e); } finally { setBusy(false); }
  }
  return <Modal open title={`失效成员 ${entry.maskedSubjectKey}`} okText="确认失效" okButtonProps={{ danger: true }} cancelText="取消" confirmLoading={busy} onOk={() => void submit()} onCancel={onClose} destroyOnHidden>
    <p>失效后不可恢复，需要时重新加入。</p>
    <Form layout="vertical"><Form.Item label="失效原因" required><Input.TextArea aria-label="失效原因" value={reason} maxLength={500} autoSize={{ minRows: 3, maxRows: 6 }} onChange={e => setReason(e.target.value)} /></Form.Item></Form>
    {error && <Alert type="error" showIcon title={error} />}
  </Modal>;
}
