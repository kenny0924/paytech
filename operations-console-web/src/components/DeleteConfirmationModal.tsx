import { useRef, useState } from 'react';
import type { ReactNode } from 'react';
import { App, Button, Form, Input, Modal } from 'antd';
import { ApiError, session } from '../api/session';
import type { DeletionCommand } from '../api/deletion';
import { useErrorNotification } from '../feedback/useErrorNotification';

/** 共用删除确认交互；业务入口负责可删除条件和精确请求字段。每次打开独立保存命令。 */
export function DeleteConfirmationModal({ subject, allowed, buildCommand, children, onClose, onDeleted, onRefresh }: {
  subject: string; allowed: boolean; children: ReactNode;
  buildCommand: (reason: string, previous: DeletionCommand | null) => DeletionCommand;
  onClose: () => void; onDeleted: () => void; onRefresh: () => void;
}) {
  const { message } = App.useApp();
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
      onDeleted(); void message.success(`${subject}已删除，已记录审计。`);
    } catch (failure) {
      notifyError(failure, `${subject}删除失败`);
      if (failure instanceof ApiError && failure.status === 409) setConflicted(true);
    } finally { inFlight.current = false; setSaving(false); }
  }

  return <Modal open title={`删除${subject}`} okText="确认删除" cancelText="取消"
    onOk={() => form.submit()} onCancel={() => { if (!inFlight.current) onClose(); }}
    confirmLoading={saving} okButtonProps={{ danger: true, disabled: saving || conflicted || !allowed }}
    cancelButtonProps={{ disabled: saving }} closable={!saving} keyboard={!saving} mask={{ closable: !saving }}>
    {children}
    {conflicted && <p className="muted">{subject}已发生变化，请刷新列表获取最新版本后重新操作。
      <Button type="link" onClick={onRefresh}>刷新列表</Button></p>}
    <Form form={form} layout="vertical" onFinish={remove} requiredMark={false} disabled={saving || conflicted || !allowed}>
      <Form.Item name="reason" label="操作原因" rules={[{ required: true, whitespace: true, message: '请填写操作原因' }]}>
        <Input.TextArea rows={3} maxLength={500} showCount placeholder={`说明删除此${subject}的原因`} />
      </Form.Item>
    </Form>
  </Modal>;
}
