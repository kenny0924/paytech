import { useState } from 'react';
import { Alert, Button, Form, Input } from 'antd';
import { session } from '../api/session';
import type { SessionContextResponseBody } from '../api/types';
import { useErrorNotification } from '../feedback/useErrorNotification';
import { devLoginDefaults } from './devLogin';

const devDefaults = devLoginDefaults(import.meta.env);

export function Login({ onLogin, initialMessage }: { onLogin: (context: SessionContextResponseBody) => void; initialMessage: string }) {
  const notifyError = useErrorNotification();
  const [loading, setLoading] = useState(false);
  const [form] = Form.useForm();
  async function login(values: { loginIdentifier: string; password: string }) {
    setLoading(true);
    try { onLogin(await session.login(values.loginIdentifier, values.password)); }
    catch (error) {
      notifyError(error, '登录失败');
    }
    finally { form.setFieldValue('password', devDefaults.password ?? ''); setLoading(false); }
  }
  return <main className="login-page">
    <section className="login-main"><div className="login-form">
      <div className="brand"><span className="brand-mark">P</span><span>PAYTECH</span></div>
      <h1>登录 Operations</h1>
      <p className="muted">使用你的内部人员账号继续。</p>
      {initialMessage && <Alert type="success" title={initialMessage} showIcon className="form-alert" />}
      <Form form={form} layout="vertical" onFinish={login} requiredMark={false} initialValues={devDefaults}>
        <Form.Item label="Email / 初始化账号" name="loginIdentifier" rules={[{ required: true, message: '请输入账号' }]}>
          <Input autoComplete="username" placeholder="name@company.com" size="large" maxLength={320} />
        </Form.Item>
        <Form.Item label="密码" name="password" rules={[{ required: true, message: '请输入密码' }]}>
          <Input.Password autoComplete="current-password" size="large" maxLength={512} />
        </Form.Item>
        <Button block type="primary" htmlType="submit" loading={loading} size="large">登录</Button>
      </Form><p className="login-help">需要开通账号或重置密码？请联系平台管理员。</p>
    </div><footer>Paytech Operations</footer></section>
  </main>;
}
