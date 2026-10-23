import { useCallback, useEffect, useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, App, Button, Form, Input, Modal, Spin, Typography } from 'antd';
import { Link } from 'react-router-dom';
import { ApiError, session } from '../api/session';
import { requestPage } from '../api/paging';
import type { PageDecoder } from '../api/paging';
import type { ConsoleResource } from '../api/types';
import { useUnsavedRuleGuard } from '../risk/useUnsavedRuleGuard';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { useT } from '../i18n/react';
import { richText } from '../i18n/richText';
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
export function LoadFailure({ failure, retry, title, keptFilters }: {
  failure: unknown; retry?: () => void; title?: string; keptFilters?: boolean;
}) {
  const t = useT('channel'), tCommon = useT('common');
  if (failure instanceof ApiError && failure.status === 404) {
    return <div className="xapi-panel xapi-state"><span className="xapi-state-icon">∅</span><h3>{t('load.notFound')}</h3>
      <p>{t('load.notFoundHint')}</p></div>;
  }
  const requestId = failure instanceof ApiError ? failure.requestId : '';
  return <div className="xapi-panel xapi-state"><span className="xapi-state-icon warn">☁︎</span><h3>{title ?? t('load.failed')}</h3>
    <p>{t('load.unavailable')}</p>
    <p className="xapi-field-hint">{requestId ? richText(t, 'load.requestId', { id: <Typography.Text copyable className="xapi-mono">{requestId}</Typography.Text> }) : t('load.noRequestId')}
      {keptFilters && t('load.filtersKept')}</p>
    {retry && <Button onClick={retry}>{tCommon('action.retry')}</Button>}</div>;
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
 * 删除请求若因并发新增引用被拒（400），重新读取引用方并切换到“无法删除”；
 * 409 带 Owner 原因时（例如标准 API 实现仍有 ACTIVE 调用授权）在弹窗内显示原因。
 */
export function DeleteDialog({ target, onClose, onDeleted, onAuthorizationChanged }: {
  target: DeleteTarget; onClose: () => void; onDeleted: (mutation: Mutation) => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('channel'), tCommon = useT('common');
  const [form] = Form.useForm<{ reason: string }>();
  const notify = useErrorNotification();
  const [saving, setSaving] = useState(false);
  const [references, setReferences] = useState<Reference[] | null>(null);
  const [rejection, setRejection] = useState<string | null>(null);
  const { references: load } = target;
  const refresh = useCallback(() => {
    setReferences(null);
    load().then(setReferences).catch(error => { notify(error, t('delete.referencesFailed')); setReferences([]); });
  // t 只用于失败通知标题，不应触发重新读取
  }, [load, notify]); // eslint-disable-line react-hooks/exhaustive-deps
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
      else if (error instanceof ApiError && error.status === 409 && error.detail) setRejection(error.detail);
      else notify(error, t('delete.failed'));
    } finally { setSaving(false); }
  }
  const code = target.objectCode.split('/').pop();
  if (references === null) return <Modal open title={t('delete.title', { subject: target.title })} footer={null} onCancel={onClose}><Loading /></Modal>;
  if (references.length) {
    return <Modal open centered width={560} title={<span><span className="xapi-banner-icon">ⓘ</span> {t('delete.blockedTitle', { subject: target.title, code: code ?? '' })}</span>}
      onCancel={onClose} footer={<Button type="primary" onClick={onClose}>{t('delete.gotIt')}</Button>}>
      <p className="xapi-desc">{t('delete.referencedHint')}</p>
      <ul className="xapi-ref-list">{references.map((reference, index) => <li key={index}><span>{reference.label}</span>
        {reference.to && <Link to={reference.to} onClick={onClose}>{t('delete.view')}</Link>}</li>)}</ul>
    </Modal>;
  }
  return <Modal open centered title={t('delete.titleWithCode', { subject: target.title, code: code ?? '' })} okText={t('endpointEditor.delete')} okButtonProps={{ danger: true, loading: saving }}
    cancelText={tCommon('action.cancel')} onCancel={onClose} onOk={() => form.submit()} destroyOnHidden>
    {rejection && <Alert type="error" showIcon title={t('delete.rejected', { detail: rejection })} />}
    <p>{richText(t, 'delete.confirm', { code: <span className="xapi-mono">{target.objectCode}</span> }, { version: target.version })}</p>
    <Form form={form} layout="vertical" requiredMark={false} onFinish={submit}>
      <Form.Item name="reason" label={t('delete.reason')} rules={[{ required: true, whitespace: true, message: t('delete.reasonRequired') }]}>
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
  const t = useT('channel');
  const [saved, setSaved] = useState<SavedVersion | null>(null);
  useEffect(() => { latest().then(setSaved).catch(() => setSaved(null)); }, [latest]);
  const busy = saved && baseVersion !== undefined && saved.version === baseVersion;
  return <div className="xapi-banner" role="alert"><span className="xapi-banner-icon">⚠</span>
    <div className="xapi-banner-body">
      <div className="xapi-banner-title">{busy ? t('conflict.busyTitle') : t('conflict.changedTitle')}</div>
      <div className="xapi-banner-detail">{saved && !busy
        ? t('conflict.savedBy', { account: saved.updatedBy ?? '—', time: formatTime(saved.updateTime), code, from: baseVersion ?? '—', to: saved.version })
        : busy ? t('conflict.retryLater') : null}
        {busy ? t('conflict.keptBusy') : t('conflict.kept')}</div>
    </div>
    <Button onClick={onCopy}>{t('conflict.copy')}</Button>
    {!busy && <Button type="primary" onClick={onReload}>{t('conflict.reload')}</Button>}
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
  const t = useT('channel');
  const { modal, message } = App.useApp(); const notify = useErrorNotification();
  const [dirty, setDirty] = useState(false); const [saving, setSaving] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<ApiError['fieldErrors']>([]);
  const last = useRef<{ body: string; key: string } | null>(null);
  const saveForLeave = useRef<(() => Promise<boolean>) | null>(null);
  const confirmLeave = useCallback(() => new Promise<boolean>(resolve => {
    let settled = false;
    const finish = (value: boolean) => { if (!settled) { settled = true; instance.destroy(); resolve(value); } };
    const instance = modal.confirm({ title: t('leave.title'), width: 520, centered: true, icon: <span className="xapi-leave-icon">!</span>,
      content: leaveDetail ? t('leave.contentWithDetail', { detail: leaveDetail }) : t('leave.content'),
      onCancel: () => finish(false),
      footer: <div className="xapi-leave-foot">
        <Button danger onClick={() => finish(true)}>{t('leave.discard')}</Button>
        <Button onClick={() => finish(false)}>{t('leave.keepEditing')}</Button>
        <Button type="primary" disabled={!saveForLeave.current} onClick={() => {
          instance.destroy(); settled = true;
          void (saveForLeave.current?.() ?? Promise.resolve(false)).then(resolve);
        }}>{t('leave.saveAndLeave')}</Button></div> });
  // t 随语言包刷新换新，确认框在调用时取文案
  }), [modal, leaveDetail]); // eslint-disable-line react-hooks/exhaustive-deps
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
      void message.success(t('save.succeeded'));
      return result;
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.status === 403) onAuthorizationChanged();
        if (error.status === 409) { setConflict(true); return null; }
        setFieldErrors(error.fieldErrors);
      }
      notify(error, t('save.failed')); return null;
    } finally { setSaving(false); }
  }
  return { dirty, setDirty, saving, conflict, fieldErrors, save, setSaveForLeave: value => { saveForLeave.current = value; } };
}

/** 复制当前编辑内容到剪贴板，供冲突后对照。 */
export function useCopyDraft() {
  const t = useT('channel');
  const { message } = App.useApp();
  return (draft: unknown) => {
    void navigator.clipboard.writeText(JSON.stringify(draft, null, 2))
      .then(() => message.success(t('copy.succeeded')), () => message.warning(t('copy.blocked')));
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
