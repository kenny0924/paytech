import { useCallback, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { App, Button, Form, Input, Modal, Spin, Typography } from 'antd';
import { Link } from 'react-router-dom';
import { ApiError, session } from '../api/session';
import { requestPage } from '../api/paging';
import type { PageDecoder } from '../api/paging';
import type { ConsoleResource } from '../api/types';
import { useUnsavedRuleGuard } from '../risk/useUnsavedRuleGuard';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { decodeMutation } from './externalApiModel';
import type { Mutation } from './externalApiModel';
import { formatTime } from './ui';

/** 当前人员是否持有指定按钮资源。 */
export const hasButton = (resources: ConsoleResource[], code: string) =>
  resources.some(resource => resource.resourceType === 'BUTTON' && resource.resourceCode === code);

export interface Detail<T> { data?: T; loading: boolean; failure?: unknown; refresh: () => void }

/** 读取详情；失败时由页面内的失败卡片展示（含请求编号），不弹浮层，不清空调用方草稿。 */
export function useDetail<T>(path: string | null, decode: (value: unknown) => T): Detail<T> {
  const [data, setData] = useState<T>();
  const [loading, setLoading] = useState(path !== null);
  const [failure, setFailure] = useState<unknown>();
  const [reload, setReload] = useState(0);
  useEffect(() => {
    if (path === null) { setLoading(false); return; }
    const abort = new AbortController();
    setLoading(true); setFailure(undefined);
    session.request<T>(path, { signal: abort.signal }, decode)
      .then(value => { if (!abort.signal.aborted) setData(value); })
      .catch(error => { if (!abort.signal.aborted) setFailure(error); })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, [path, decode, reload]);
  return { data, loading, failure, refresh: () => setReload(value => value + 1) };
}

export function Loading() {
  return <div className="content-loading"><Spin /></div>;
}

/** 加载失败：页面内居中卡片，显示请求编号与重试；不存在单独提示。 */
export function LoadFailure({ failure, retry, title = '加载失败', keptFilters }: {
  failure: unknown; retry?: () => void; title?: string; keptFilters?: boolean;
}) {
  if (failure instanceof ApiError && failure.status === 404) {
    return <div className="xapi-panel xapi-state"><span className="xapi-state-icon">∅</span><h3>配置不存在</h3>
      <p>可能已被删除，请返回列表刷新。</p></div>;
  }
  const requestId = failure instanceof ApiError ? failure.requestId : '';
  return <div className="xapi-panel xapi-state"><span className="xapi-state-icon warn">☁︎</span><h3>{title}</h3>
    <p>服务暂时不可用，请稍后重试。</p>
    <p className="xapi-field-hint">{requestId ? <>请求编号 <Typography.Text copyable className="xapi-mono">{requestId}</Typography.Text></> : '未返回请求编号'}
      {keptFilters && ' · 筛选条件已保留'}</p>
    {retry && <Button onClick={retry}>重试</Button>}</div>;
}

/** 被引用方：说明文字与跳转地址。 */
export interface Reference { label: ReactNode; to?: string }

export interface DeleteTarget {
  title: string; path: string; objectType: string; objectCode: string; version: number;
  /** 读取当前引用方；有引用时不允许删除，直接显示引用清单。 */
  references: () => Promise<Reference[]>;
}

/**
 * 删除：先读取引用方，被引用时显示“无法删除”与引用清单；否则填写原因后按当前版本删除。
 * 删除请求若因并发新增引用被拒（400），重新读取引用方并切换到“无法删除”。
 */
export function DeleteDialog({ target, onClose, onDeleted, onAuthorizationChanged }: {
  target: DeleteTarget; onClose: () => void; onDeleted: (mutation: Mutation) => void; onAuthorizationChanged: () => void;
}) {
  const [form] = Form.useForm<{ reason: string }>();
  const notify = useErrorNotification();
  const [saving, setSaving] = useState(false);
  const [references, setReferences] = useState<Reference[] | null>(null);
  const { references: load } = target;
  const refresh = useCallback(() => {
    setReferences(null);
    load().then(setReferences).catch(error => { notify(error, '引用关系读取失败'); setReferences([]); });
  }, [load, notify]);
  useEffect(refresh, [refresh]);
  async function submit(values: { reason: string }) {
    setSaving(true);
    try {
      const mutation = await session.request<Mutation>(target.path, {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() },
        body: JSON.stringify({ expectedVersion: target.version, reason: values.reason.trim() }),
      }, value => decodeMutation(value, target.objectType, target.objectCode));
      onDeleted(mutation);
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      if (error instanceof ApiError && error.status === 400 && error.code === 'BUSINESS_CHECK_FAILED') refresh();
      else notify(error, '删除未完成');
    } finally { setSaving(false); }
  }
  const code = target.objectCode.split('/').pop();
  if (references === null) return <Modal open title={`删除${target.title}`} footer={null} onCancel={onClose}><Loading /></Modal>;
  if (references.length) {
    return <Modal open centered width={560} title={<span><span className="xapi-banner-icon">ⓘ</span> 无法删除{target.title} {code}</span>}
      onCancel={onClose} footer={<Button type="primary" onClick={onClose}>知道了</Button>}>
      <p className="xapi-desc">以下配置仍在引用它。请先在这些位置移除引用，再删除。</p>
      <ul className="xapi-ref-list">{references.map((reference, index) => <li key={index}><span>{reference.label}</span>
        {reference.to && <Link to={reference.to} onClick={onClose}>查看</Link>}</li>)}</ul>
    </Modal>;
  }
  return <Modal open centered title={`删除${target.title} ${code}`} okText="删除" okButtonProps={{ danger: true, loading: saving }}
    cancelText="取消" onCancel={onClose} onOk={() => form.submit()} destroyOnHidden>
    <p>将删除 <span className="xapi-mono">{target.objectCode}</span>（当前版本 {target.version}），删除后不可恢复。</p>
    <Form form={form} layout="vertical" requiredMark={false} onFinish={submit}>
      <Form.Item name="reason" label="删除原因" rules={[{ required: true, whitespace: true, message: '请填写删除原因' }]}>
        <Input.TextArea rows={3} maxLength={500} showCount disabled={saving} />
      </Form.Item>
    </Form>
  </Modal>;
}

/** 当前已保存版本的摘要，用于冲突横幅。 */
export interface SavedVersion { version: number; updatedBy?: string; updateTime: string }

/**
 * 保存冲突横幅：重新读取最新版本，说明谁在何时保存了新版本；本地修改保留，可复制或重新加载。
 * 最新版本与本地基线相同说明对方仍在保存，提示稍后重试。
 */
export function ConflictBanner({ code, baseVersion, latest, onReload, onCopy }: {
  code: string; baseVersion?: number; latest: () => Promise<SavedVersion>; onReload: () => void; onCopy: () => void;
}) {
  const [saved, setSaved] = useState<SavedVersion | null>(null);
  useEffect(() => { latest().then(setSaved).catch(() => setSaved(null)); }, [latest]);
  const busy = saved && baseVersion !== undefined && saved.version === baseVersion;
  return <div className="xapi-banner" role="alert"><span className="xapi-banner-icon">⚠</span>
    <div className="xapi-banner-body">
      <div className="xapi-banner-title">{busy ? '保存失败：该配置正在被他人保存' : '保存失败：该配置已被他人修改'}</div>
      <div className="xapi-banner-detail">{saved && !busy
        ? <>账号 {saved.updatedBy ?? '—'} 于 {formatTime(saved.updateTime)} 保存了 {code}（版本 {baseVersion ?? '—'} → {saved.version}）。</>
        : busy ? '请稍后再次保存。' : null}
        你的修改仍保留在页面上，未被覆盖。{busy ? '' : '可以重新加载最新配置后再编辑。'}</div>
    </div>
    <Button onClick={onCopy}>复制我的修改</Button>
    {!busy && <Button type="primary" onClick={onReload}>重新加载</Button>}
  </div>;
}

export interface EditorSave { dirty: boolean; setDirty: (dirty: boolean) => void; saving: boolean; conflict: boolean;
  fieldErrors: ApiError['fieldErrors']; save: <T>(path: string, method: 'POST' | 'PUT', payload: unknown, decode: (value: unknown) => T) => Promise<T | null>;
  /** 页面登记“保存并离开”使用的保存流程（打开原因弹窗并等待保存结果）。 */
  setSaveForLeave: (save: (() => Promise<boolean>) | null) => void }

/**
 * 外部 API 编辑页的保存：幂等键按请求体复用，409 显示冲突横幅（不再弹通知），422 返回字段错误。
 * 离开未保存页面时提供“放弃修改 / 继续编辑 / 保存并离开”。
 */
export function useEditorSave(onAuthorizationChanged: () => void, leaveDetail?: string): EditorSave {
  const { modal, message } = App.useApp(); const notify = useErrorNotification();
  const [dirty, setDirty] = useState(false); const [saving, setSaving] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<ApiError['fieldErrors']>([]);
  const last = useRef<{ body: string; key: string } | null>(null);
  const saveForLeave = useRef<(() => Promise<boolean>) | null>(null);
  const confirmLeave = useCallback(() => new Promise<boolean>(resolve => {
    let settled = false;
    const finish = (value: boolean) => { if (!settled) { settled = true; instance.destroy(); resolve(value); } };
    const instance = modal.confirm({ title: '有未保存的修改', width: 520, centered: true, icon: <span className="xapi-leave-icon">!</span>,
      content: `离开后本页修改将丢失。${leaveDetail ?? ''}`,
      onCancel: () => finish(false),
      footer: <div className="xapi-leave-foot">
        <Button danger onClick={() => finish(true)}>放弃修改</Button>
        <Button onClick={() => finish(false)}>继续编辑</Button>
        <Button type="primary" disabled={!saveForLeave.current} onClick={() => {
          instance.destroy(); settled = true;
          void (saveForLeave.current?.() ?? Promise.resolve(false)).then(resolve);
        }}>保存并离开</Button></div> });
  }), [modal, leaveDetail]);
  useUnsavedRuleGuard(dirty, confirmLeave);
  async function save<T>(path: string, method: 'POST' | 'PUT', payload: unknown, decode: (value: unknown) => T) {
    if (saving) return null;
    const body = JSON.stringify(payload);
    if (!last.current || last.current.body !== body) last.current = { body, key: crypto.randomUUID() };
    setSaving(true);
    try {
      const result = await session.request<T>(path, { method,
        headers: { 'Content-Type': 'application/json', 'Idempotency-Key': last.current.key }, body }, decode);
      setDirty(false); setConflict(false); setFieldErrors([]); last.current = null;
      void message.success('已保存，对新调用立即生效');
      return result;
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.status === 403) onAuthorizationChanged();
        if (error.status === 409) { setConflict(true); return null; }
        setFieldErrors(error.fieldErrors);
      }
      notify(error, '配置保存失败'); return null;
    } finally { setSaving(false); }
  }
  return { dirty, setDirty, saving, conflict, fieldErrors, save, setSaveForLeave: value => { saveForLeave.current = value; } };
}

/** 复制当前编辑内容到剪贴板，供冲突后对照。 */
export function useCopyDraft() {
  const { message } = App.useApp();
  return (draft: unknown) => {
    void navigator.clipboard.writeText(JSON.stringify(draft, null, 2))
      .then(() => message.success('已复制当前修改'), () => message.warning('浏览器不允许写入剪贴板'));
  };
}

/** 读取全部页（每页 100，BFF 的分页上限），总数变化或中途失败时不返回部分结果。 */
export async function loadAllPages<T>(path: string, decode: PageDecoder<T>, signal: AbortSignal): Promise<T[]> {
  const items: T[] = [];
  for (let page = 1; ; page++) {
    const result = await requestPage(path, { page, size: 100 }, signal, session, {}, decode);
    items.push(...result.items);
    if (items.length >= result.total || result.items.length === 0) {
      if (items.length !== result.total) throw new ApiError(502, 'INVALID_RESPONSE');
      return items;
    }
  }
}
