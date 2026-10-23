import { useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { App, Button, Form, Input, Modal } from 'antd';
import { ApiError, session } from '../api/session';
import type { DeletionCommand } from '../api/deletion';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { useT } from '../i18n/react';

/**
 * 共用删除确认交互；业务入口负责可删除条件和精确请求字段。每次打开独立保存命令。
 * `subject` 是调用方已翻译的对象名（英文用小写名词，如 "role"），会嵌入 `common.delete.*` 的句子。
 */
export function DeleteConfirmationModal({ subject, allowed, buildCommand, children, onClose, onDeleted, onRefresh }: {
  subject: string; allowed: boolean; children: ReactNode;
  buildCommand: (reason: string, previous: DeletionCommand | null) => DeletionCommand;
  onClose: () => void; onDeleted: () => void; onRefresh: () => void;
}) {
  const { message } = App.useApp();
  const t = useT('common');
  const notifyError = useErrorNotification();
  const [form] = Form.useForm<{ reason: string }>();
  const [saving, setSaving] = useState(false);
  const [conflicted, setConflicted] = useState(false);
  const inFlight = useRef(false);
  const command = useRef<DeletionCommand | null>(null);

  async function remove(values: { reason: string }) {
    if (inFlight.current || conflicted || !allowed) return;
    inFlight.current = true; setSaving(true);
    command.current = buildCommand(values.reason, command.current);
    try {
      const current = command.current;
      await session.request<void>(current.path, {
        method: 'DELETE', headers: { 'Idempotency-Key': current.key }, body: current.payload,
      });
      onDeleted(); void message.success(t('delete.succeeded', { subject }));
    } catch (failure) {
      notifyError(failure, t('delete.failed', { subject }));
      if (failure instanceof ApiError && failure.status === 409) setConflicted(true);
    } finally { inFlight.current = false; setSaving(false); }
  }

  return <Modal open title={t('delete.title', { subject })} okText={t('delete.confirm')} cancelText={t('action.cancel')}
    onOk={() => form.submit()} onCancel={() => { if (!inFlight.current) onClose(); }}
    confirmLoading={saving} okButtonProps={{ danger: true, disabled: saving || conflicted || !allowed }}
    cancelButtonProps={{ disabled: saving }} closable={!saving} keyboard={!saving} mask={{ closable: !saving }}>
    {children}
    {conflicted && <p className="muted">{t('delete.conflict', { subject })}
      <Button type="link" onClick={onRefresh}>{t('action.refreshList')}</Button></p>}
    <Form form={form} layout="vertical" onFinish={remove} requiredMark={false} disabled={saving || conflicted || !allowed}>
      <Form.Item name="reason" label={t('reason.label')} rules={[{ required: true, whitespace: true, message: t('reason.required') }]}>
        <Input.TextArea rows={3} maxLength={500} showCount placeholder={t('delete.reasonPlaceholder', { subject })} />
      </Form.Item>
    </Form>
  </Modal>;
}
