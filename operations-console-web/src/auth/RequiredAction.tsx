import { useState } from 'react';
import { Button, Form, Input } from 'antd';
import { session } from '../api/session';
import type { SessionContextResponseBody } from '../api/types';
import { useErrorNotification } from '../feedback/useErrorNotification';

export const passwordRules = [{ required: true, message: '请输入密码' }, {
  validator: (_: unknown, value: string) => {
    const normalized = (value || '').normalize('NFC');
    return [...normalized].length >= 15 && [...normalized].length <= 128 && new TextEncoder().encode(normalized).length <= 512
      ? Promise.resolve() : Promise.reject(new Error('密码需要 15–128 个字符，且不超过 512 UTF-8 字节'));
  },
}];

export function RequiredAction({ context, onComplete }: { context: SessionContextResponseBody; onComplete: () => void }) {
  const notifyError = useErrorNotification();
  const bootstrap = !context.passwordChangeRequired && context.bootstrapAdministratorRequired;
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm();
  async function submit(values: Record<string, string>) {
    setLoading(true);
    try {
      await session.request(bootstrap ? '/auth/v1/bootstrap/administrator' : '/auth/v1/password', {
        method: 'POST', body: JSON.stringify(bootstrap
          ? { email: values.email, initialPassword: values.newPassword }
          : { currentPassword: values.currentPassword, newPassword: values.newPassword }),
      });
      onComplete();
    } catch (error) { notifyError(error, bootstrap ? '创建管理员失败' : '修改密码失败'); }
    finally { form.resetFields(['currentPassword', 'newPassword', 'confirmPassword']); setLoading(false); }
  }
  return <main className="action-page"><section className="action-card">
    <div className="brand"><span className="brand-mark">P</span><span>OPERATIONS</span></div>
    <h2>{bootstrap ? '创建首位平台管理员' : '设置你的新密码'}</h2>
    <p className="muted">{bootstrap ? '完成后初始化账号会永久停用。请使用新管理员账号重新登录。' : '你的账号需要更新密码。完成后请使用新密码重新登录。'}</p>
    <Form layout="vertical" form={form} onFinish={submit} requiredMark={false}>
      {bootstrap ? <Form.Item name="email" label="管理员 Email" rules={[{ required: true, type: 'email', message: '请输入有效 Email' }]}>
        <Input autoComplete="username" maxLength={320} />
      </Form.Item> : <Form.Item name="currentPassword" label="当前密码" rules={[{ required: true, message: '请输入当前密码' }]}>
        <Input.Password autoComplete="current-password" />
      </Form.Item>}
      <Form.Item name="newPassword" label={bootstrap ? '一次性初始密码' : '新密码'} rules={passwordRules}>
        <Input.Password autoComplete="new-password" maxLength={512} />
      </Form.Item>
      <Form.Item name="confirmPassword" label="确认密码" dependencies={['newPassword']} rules={[{ required: true, message: '请再次输入密码' }, ({ getFieldValue }) => ({
        validator: (_, value) => value === getFieldValue('newPassword') ? Promise.resolve() : Promise.reject(new Error('两次密码不一致')),
      })]}><Input.Password autoComplete="new-password" maxLength={512} /></Form.Item>
      <Button type="primary" htmlType="submit" block loading={loading}>{bootstrap ? '创建管理员' : '更新密码'}</Button>
    </Form>
    <Button type="link" onClick={() => void session.logout().catch(error => notifyError(error, '退出登录失败'))}>退出登录</Button>
  </section></main>;
}
