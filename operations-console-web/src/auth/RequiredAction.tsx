import { useState } from 'react';
import { Button, Form, Input } from 'antd';
import { session } from '../api/session';
import type { SessionContextResponseBody } from '../api/types';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { translator } from '../i18n/index';
import { useT } from '../i18n/react';

const tAuth = translator('auth');
/** 密码表单规则（共用于人员管理）；调用时取文案，因此是函数而不是模块级常量。 */
export function passwordRules() {
  return [{ required: true, message: tAuth('password.required') }, {
    validator: (_: unknown, value: string) => {
      const normalized = (value || '').normalize('NFC');
      return [...normalized].length >= 15 && [...normalized].length <= 128 && new TextEncoder().encode(normalized).length <= 512
        ? Promise.resolve() : Promise.reject(new Error(tAuth('password.rule')));
    },
  }];
}

export function RequiredAction({ context, onComplete }: { context: SessionContextResponseBody; onComplete: () => void }) {
  const notifyError = useErrorNotification();
  const t = useT('auth');
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
    } catch (error) { notifyError(error, t(bootstrap ? 'requiredAction.createAdminFailed' : 'requiredAction.updatePasswordFailed')); }
    finally { form.resetFields(['currentPassword', 'newPassword', 'confirmPassword']); setLoading(false); }
  }
  return <main className="action-page"><section className="action-card">
    <div className="brand"><span className="brand-mark">P</span><span>OPERATIONS</span></div>
    <h2>{t(bootstrap ? 'requiredAction.bootstrapTitle' : 'requiredAction.passwordTitle')}</h2>
    <p className="muted">{t(bootstrap ? 'requiredAction.bootstrapHint' : 'requiredAction.passwordHint')}</p>
    <Form layout="vertical" form={form} onFinish={submit} requiredMark={false}>
      {bootstrap ? <Form.Item name="email" label={t('requiredAction.adminEmail')} rules={[{ required: true, type: 'email', message: t('requiredAction.emailInvalid') }]}>
        <Input autoComplete="username" maxLength={320} />
      </Form.Item> : <Form.Item name="currentPassword" label={t('requiredAction.currentPassword')} rules={[{ required: true, message: t('requiredAction.currentPasswordRequired') }]}>
        <Input.Password autoComplete="current-password" />
      </Form.Item>}
      <Form.Item name="newPassword" label={t(bootstrap ? 'requiredAction.initialPassword' : 'requiredAction.newPassword')} rules={passwordRules()}>
        <Input.Password autoComplete="new-password" maxLength={512} />
      </Form.Item>
      <Form.Item name="confirmPassword" label={t('requiredAction.confirmPassword')} dependencies={['newPassword']} rules={[{ required: true, message: t('requiredAction.confirmRequired') }, ({ getFieldValue }) => ({
        validator: (_, value) => value === getFieldValue('newPassword') ? Promise.resolve() : Promise.reject(new Error(t('requiredAction.mismatch'))),
      })]}><Input.Password autoComplete="new-password" maxLength={512} /></Form.Item>
      <Button type="primary" htmlType="submit" block loading={loading}>{t(bootstrap ? 'requiredAction.createAdmin' : 'requiredAction.updatePassword')}</Button>
    </Form>
    <Button type="link" onClick={() => void session.logout().catch(error => notifyError(error, t('requiredAction.signOutFailed')))}>{t('requiredAction.signOut')}</Button>
  </section></main>;
}
