import { useState } from 'react';
import { Form, Input, Modal } from 'antd';
import { useNavigate } from 'react-router-dom';
import { ApiError, session } from '../../api/session';
import { useErrorNotification } from '../../feedback/useErrorNotification';
import { decodeRunStart, verifyPath } from './verifyModel';
import { workspaceRoutes } from '../../channel/channelWorkspace';
import type { Tone } from './verifyModel';

/** 状态标签：中性灰、运行中蓝、通过绿、未通过红、需重新验证 / 已中断橙。 */
export function VerifyTag({ label, tone }: { label: string; tone: Tone }) {
  return <span className={`xapi-verify-tag xapi-verify-tag-${tone}`}>{label}</span>;
}

/** 格式化的 JSON 只读块；超长内容在块内滚动。 */
export function JsonBlock({ value, empty = '—' }: { value: unknown; empty?: string }) {
  if (value === null || value === undefined) return <span className="xapi-desc">{empty}</span>;
  const text = typeof value === 'string' ? value : JSON.stringify(value, null, 2);
  return <pre className="xapi-json-block">{text}</pre>;
}

/**
 * 发起一次沙箱验证运行：填写运行原因后提交，成功后进入运行详情页轮询结果。
 * 运行会真实调用渠道沙箱，弹窗中说明这一点。
 */
export function StartRunDialog({ channelId, integrationCode, caseCode, onClose, onAuthorizationChanged }: {
  channelId: string; integrationCode: string; caseCode: string; onClose: () => void; onAuthorizationChanged: () => void;
}) {
  const [form] = Form.useForm<{ reason: string }>();
  const [saving, setSaving] = useState(false);
  const navigate = useNavigate(); const notify = useErrorNotification();
  async function submit(values: { reason: string }) {
    setSaving(true);
    try {
      const run = await session.request(`${verifyPath(integrationCode)}/verify-cases/${caseCode}/runs`, {
        method: 'POST', headers: { 'Content-Type': 'application/json', 'Idempotency-Key': crypto.randomUUID() },
        body: JSON.stringify({ reason: values.reason.trim() }) }, decodeRunStart);
      onClose();
      void navigate(workspaceRoutes(channelId).verifyRun(run.runCode));
    } catch (error) {
      if (error instanceof ApiError && error.status === 403) onAuthorizationChanged();
      notify(error, '运行未发起');
    } finally { setSaving(false); }
  }
  return <Modal open centered title={`运行验证用例 ${caseCode}`} okText="运行" okButtonProps={{ loading: saving }}
    cancelText="取消" onCancel={onClose} onOk={() => form.submit()} destroyOnHidden>
    <p className="xapi-desc">将使用用例绑定的验证账号，真实调用渠道标记为沙箱的出站地址；未标记沙箱时不会发出请求。运行在后台执行，结果与报文证据会保存。</p>
    <Form form={form} layout="vertical" requiredMark={false} onFinish={values => void submit(values)}>
      <Form.Item name="reason" label="运行原因" rules={[{ required: true, whitespace: true, message: '请填写运行原因' }]}>
        <Input.TextArea rows={2} maxLength={500} showCount autoFocus placeholder="例如：渠道升级后回归" disabled={saving} />
      </Form.Item>
    </Form>
  </Modal>;
}
