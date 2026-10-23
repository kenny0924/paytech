import { useState } from 'react';
import { Alert, Button, Form, Input } from 'antd';
import { session } from '../api/session';
import type { SessionContextResponseBody } from '../api/types';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { LanguageSwitcher } from '../i18n/LanguageSwitcher';
import { useT } from '../i18n/react';
import { devLoginDefaults } from './devLogin';

const devDefaults = devLoginDefaults(import.meta.env);

export function Login({ onLogin, initialMessage }: { onLogin: (context: SessionContextResponseBody) => void; initialMessage: string }) {
  const notifyError = useErrorNotification();
  const t = useT('auth');
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm();
  async function login(values: { loginIdentifier: string; password: string }) {
    setLoading(true);
    try { onLogin(await session.login(values.loginIdentifier, values.password)); }
    catch (error) {
      notifyError(error, t('login.failed'));
    }
    finally { form.setFieldValue('password', devDefaults.password ?? ''); setLoading(false); }
  }
  return <main className="login-page">
    <section className="login-main"><LanguageSwitcher className="login-language" /><div className="login-form">
      <div className="brand"><span className="brand-mark">P</span><span>PAYTECH</span></div>
      <h1>{t('login.title')}</h1>
      <p className="muted">{t('login.subtitle')}</p>
      {initialMessage && <Alert type="success" title={initialMessage} showIcon className="form-alert" />}
      <Form form={form} layout="vertical" onFinish={login} requiredMark={false} initialValues={devDefaults}>
        <Form.Item label={t('login.identifier')} name="loginIdentifier" rules={[{ required: true, message: t('login.identifierRequired') }]}>
          <Input autoComplete="username" placeholder="name@company.com" size="large" maxLength={320} />
        </Form.Item>
        <Form.Item label={t('login.password')} name="password" rules={[{ required: true, message: t('login.passwordRequired') }]}>
          <Input.Password autoComplete="current-password" size="large" maxLength={512} />
        </Form.Item>
        <Button block type="primary" htmlType="submit" loading={loading} size="large">{t('login.submit')}</Button>
      </Form><p className="login-help">{t('login.help')}</p>
    </div><footer>Paytech Operations</footer></section>
  </main>;
}
