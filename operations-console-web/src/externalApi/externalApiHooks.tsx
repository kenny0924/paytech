import { useEffect, useState } from 'react';
import { Alert, Button, Form, Input, Modal, Result, Spin } from 'antd';
import { ApiError, session } from '../api/session';
import { requestPage } from '../api/paging';
import type { PageDecoder } from '../api/paging';
import type { ConsoleResource } from '../api/types';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { decodeMutation } from './externalApiModel';
import type { Mutation } from './externalApiModel';

/** 当前人员是否持有指定按钮资源。 */
export const hasButton = (resources: ConsoleResource[], code: string) =>
  resources.some(resource => resource.resourceType === 'BUTTON' && resource.resourceCode === code);

export interface Detail<T> { data?: T; loading: boolean; failure?: unknown; refresh: () => void }

/** 读取详情；失败时保留当前页面并提供重试，不清空调用方草稿。 */
export function useDetail<T>(path: string | null, decode: (value: unknown) => T): Detail<T> {
  const notify = useErrorNotification();
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
      .catch(error => { if (!abort.signal.aborted) { setFailure(error); notify(error, '加载失败'); } })
      .finally(() => { if (!abort.signal.aborted) setLoading(false); });
    return () => abort.abort();
  }, [path, decode, reload, notify]);
  return { data, loading, failure, refresh: () => setReload(value => value + 1) };
}

export function Loading() {
  return <div className="content-loading"><Spin /></div>;
}

/** 加载失败：不存在与其他失败分开提示，保留重试入口。 */
export function LoadFailure({ failure, retry }: { failure: unknown; retry?: () => void }) {
  if (failure instanceof ApiError && failure.status === 404) {
    return <Result status="404" title="配置不存在" subTitle="可能已被删除，请返回列表刷新。" />;
  }
  return <Alert type="warning" showIcon title="暂时无法加载" description="页面已保留，请稍后重试。"
    action={retry && <Button onClick={retry}>重试</Button>} />;
}

/** 保存按钮旁的常驻说明：无发布环节，保存即对新调用生效。 */
export function SaveNotice() {
  return <span className="muted external-api-save-notice">保存后对新调用立即生效</span>;
}

export interface DeleteTarget { title: string; path: string; objectType: string; objectCode: string; version: number }

/**
 * 删除确认：填写原因后按当前版本删除。被引用时服务端返回 400，说明中列出引用方，弹窗保持打开便于处理。
 */
export function DeleteDialog({ target, onClose, onDeleted, onAuthorizationChanged }: {
  target: DeleteTarget; onClose: () => void; onDeleted: (mutation: Mutation) => void; onAuthorizationChanged: () => void;
}) {
  const [form] = Form.useForm<{ reason: string }>();
  const notify = useErrorNotification();
  const [saving, setSaving] = useState(false);
  const [blocked, setBlocked] = useState('');
  async function submit(values: { reason: string }) {
    setSaving(true); setBlocked('');
    try {
      const mutation = await session.request<Mutation>(target.path, {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() },
        body: JSON.stringify({ expectedVersion: target.version, reason: values.reason.trim() }),
      }, value => decodeMutation(value, target.objectType, target.objectCode));
      onDeleted(mutation);
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      if (error instanceof ApiError && error.status === 400 && error.code === 'BUSINESS_CHECK_FAILED') setBlocked(error.message);
      else notify(error, '删除未完成');
    } finally { setSaving(false); }
  }
  return <Modal open title={`删除${target.title}`} okText="删除" okButtonProps={{ danger: true, loading: saving, disabled: !!blocked }}
    cancelText={blocked ? '关闭' : '取消'} onCancel={onClose} onOk={() => form.submit()} destroyOnHidden>
    <p>将删除 <strong>{target.objectCode}</strong>（当前版本 {target.version}），删除后不可恢复。</p>
    {blocked && <Alert type="error" showIcon title="仍被引用，不能删除" description={blocked} />}
    <Form form={form} layout="vertical" requiredMark={false} onFinish={submit}>
      <Form.Item name="reason" label="删除原因" rules={[{ required: true, whitespace: true, message: '请填写删除原因' }]}>
        <Input.TextArea rows={3} maxLength={500} showCount disabled={saving || !!blocked} />
      </Form.Item>
    </Form>
  </Modal>;
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
