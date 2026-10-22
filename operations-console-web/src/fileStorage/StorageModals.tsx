import { useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Button, Drawer, Form, Input, InputNumber, Modal, Select, Space, Switch } from 'antd';
import {
  allowedCredentialModes, bucketCheckValidMillis, bucketObjectCode, bucketNamePattern, bucketPath, checkResultLabels, connectTimeoutRange, createInstanceBody,
  createRouteBody, credentialBody, credentialModeLabels, decodeBucketCheck, decodeMutation, defaultSourceSystem, instanceCodePattern,
  instancePath, readTimeoutRange, regionPattern, routePath, scanPolicyCodePattern, routeTargets, sourceSystemLabel, sourceSystemPattern, storageAdminPath, storageTypeLabels,
  storageTypes, targetValue, updateInstanceBody, updateRouteBody, uploadSecondsRange,
} from './fileStorage';
import type {
  BucketCheck, CredentialFormValues, InstanceFormValues, RouteFormValues, StorageInstance, StorageMutation, StorageRoute, StorageType,
} from './fileStorage';
import { useStorageCommand } from './useStorageCommand';

const date = (value: string) => new Date(value).toLocaleString('zh-CN', { hour12: false });
const reasonItem = <Form.Item name="reason" label="原因" rules={[{ required: true, whitespace: true, message: '请填写原因' }]}>
  <Input.TextArea rows={2} maxLength={500} showCount /></Form.Item>;
const conflictBanner = (conflict: boolean) => conflict
  ? <Alert type="error" showIcon title="该配置已被他人修改，请关闭后刷新再操作。" style={{ marginTop: 12 }} /> : null;

interface CommandProps { onClose: () => void; onDone: () => void; onAuthorizationChanged: () => void }

/** 密钥输入：密码框、不自动填充；只在固定密钥方式下出现。 */
function SecretFields() {
  return <>
    <Form.Item name="accessKey" label="Access Key" rules={[{ required: true, message: '请填写 Access Key' }]}>
      <Input.Password maxLength={128} autoComplete="off" className="mono" /></Form.Item>
    <Form.Item name="secretKey" label="Secret Key" rules={[{ required: true, message: '请填写 Secret Key' }]}>
      <Input.Password maxLength={256} autoComplete="new-password" className="mono" /></Form.Item>
  </>;
}

/**
 * 新建（instance 为空）或编辑存储实例的抽屉
 * <li>新建时选择类型与凭据方式；MinIO 只能固定密钥且地址必填。编辑时代码、类型与凭据方式只读，凭据在「更换密钥」中维护。</li>
 * <li>提交后清空密钥字段；密钥不回显、不保存在页面状态中。</li>
 */
export function InstanceDrawer({ instance, onClose, onDone, onAuthorizationChanged }: CommandProps & { instance: StorageInstance | null }) {
  const [form] = Form.useForm<InstanceFormValues>(); const command = useStorageCommand(onAuthorizationChanged, form);
  const storageType = Form.useWatch('storageType', form) as StorageType | undefined;
  const credentialMode = Form.useWatch('credentialMode', form);
  async function submit() {
    const values = await form.validateFields();
    const result = instance
      ? await command.run(instancePath(instance.instanceCode), 'PUT', updateInstanceBody(values, instance),
        value => decodeMutation(value, instance.instanceCode), '保存实例失败')
      : await command.run(`${storageAdminPath}/instances`, 'POST', createInstanceBody(values),
        value => decodeMutation(value, (values.instanceCode ?? '').trim()), '新建实例失败');
    form.setFieldsValue({ accessKey: '', secretKey: '' });
    if (result) onDone();
  }
  function changeType(value: StorageType) {
    if (!allowedCredentialModes(value).includes(form.getFieldValue('credentialMode'))) form.setFieldsValue({ credentialMode: 'STATIC' });
  }
  return <Drawer open title={instance ? `编辑存储实例 ${instance.instanceCode}` : '新建存储实例'} size={560} destroyOnHidden
    onClose={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }}
    extra={<Space><Button disabled={command.saving} onClick={onClose}>取消</Button>
      <Button type="primary" loading={command.saving} disabled={command.conflict} onClick={() => void submit()}>{instance ? '保存' : '创建实例'}</Button></Space>}>
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict} autoComplete="off" initialValues={{
      instanceCode: instance?.instanceCode, storageType: instance?.storageType ?? 'MINIO', endpoint: instance?.endpoint ?? '',
      region: instance?.region ?? '', pathStyleAccess: instance?.pathStyleAccess ?? true, trustedCertificatePem: instance?.trustedCertificatePem ?? '',
      credentialMode: instance?.credentialMode ?? 'STATIC', connectTimeoutMs: instance?.connectTimeoutMs ?? connectTimeoutRange.defaultValue,
      readTimeoutMs: instance?.readTimeoutMs ?? readTimeoutRange.defaultValue, reason: '' }}>
      <Form.Item name="instanceCode" label="实例代码" extra={instance ? undefined : '大写字母开头，可含数字与下划线；文件记录按此代码定位存储，创建后不可修改'}
        rules={[{ required: true, message: '请填写实例代码' }, { pattern: instanceCodePattern, message: '大写字母开头，只能包含大写字母、数字或下划线' }]}>
        <Input maxLength={64} disabled={!!instance} className="mono" /></Form.Item>
      <Form.Item name="storageType" label="类型">
        <Select disabled={!!instance} onChange={changeType} options={storageTypes.map(value => ({ value, label: storageTypeLabels[value] }))} /></Form.Item>
      <Form.Item name="endpoint" label="地址" extra={storageType === 'S3' ? 'HTTPS 根地址；留空使用该区域的 AWS 默认地址' : 'HTTPS 根地址，浏览器直传与下载也使用该地址'}
        rules={[{ required: storageType === 'MINIO', whitespace: true, message: '请填写地址' }, { type: 'url', message: '请填写 HTTPS 根地址' },
          { pattern: /^https:\/\//, message: '必须是 HTTPS 地址' }]}>
        <Input maxLength={255} placeholder="https://" className="mono" /></Form.Item>
      <Form.Item name="region" label="区域" rules={[{ required: true, whitespace: true, message: '请填写区域' },
        { pattern: regionPattern, message: '小写字母、数字或连字符，例如 me-central-1' }]}>
        <Input maxLength={64} placeholder="例如 me-central-1" /></Form.Item>
      <Form.Item name="pathStyleAccess" label="路径风格访问" valuePropName="checked" extra="MinIO 通常需要开启"><Switch /></Form.Item>
      <Form.Item name="trustedCertificatePem" label="私有 CA 证书（可选）" extra="自签名或私有 CA 的 PEM 公开证书；留空使用系统信任库">
        <Input.TextArea rows={3} className="mono" placeholder="-----BEGIN CERTIFICATE-----" /></Form.Item>
      <Space size={16} style={{ display: 'flex' }}>
        <Form.Item name="connectTimeoutMs" label="连接超时（毫秒）" rules={[{ required: true, message: '请填写连接超时' }]}>
          <InputNumber min={connectTimeoutRange.min} max={connectTimeoutRange.max} precision={0} style={{ width: 200 }} /></Form.Item>
        <Form.Item name="readTimeoutMs" label="读取超时（毫秒）" rules={[{ required: true, message: '请填写读取超时' }]}>
          <InputNumber min={readTimeoutRange.min} max={readTimeoutRange.max} precision={0} style={{ width: 200 }} /></Form.Item>
      </Space>
      {instance
        ? <div className="muted" style={{ marginBottom: 16 }}>凭据方式：{credentialModeLabels[instance.credentialMode]}，在「更换密钥」中修改。</div>
        : <>
          <Form.Item name="credentialMode" label="凭据方式" extra={storageType === 'MINIO' ? 'MinIO 只能使用固定密钥' : '生产 AWS 建议使用 IAM 角色'}>
            <Select options={allowedCredentialModes(storageType ?? 'MINIO').map(value => ({ value, label: credentialModeLabels[value] }))} /></Form.Item>
          {credentialMode === 'STATIC' && <SecretFields />}
        </>}
      {reasonItem}
    </Form>
    {!instance && <div className="muted">新建后为草稿；密钥保存在 Vault，保存后无法查看，只能更换。登记并启用至少一个桶后才能启用实例。</div>}
    {conflictBanner(command.conflict)}
  </Drawer>;
}

/** 更换密钥或切换凭据方式：密码框输入，保存后立即生效，提交后清空。 */
export function CredentialModal({ instance, onClose, onDone, onAuthorizationChanged }: CommandProps & { instance: StorageInstance }) {
  const [form] = Form.useForm<CredentialFormValues>(); const command = useStorageCommand(onAuthorizationChanged, form);
  const credentialMode = Form.useWatch('credentialMode', form);
  async function submit() {
    const values = await form.validateFields();
    const result = await command.run(`${instancePath(instance.instanceCode)}/credential`, 'PUT', credentialBody(values, instance),
      value => decodeMutation(value, instance.instanceCode), '更换密钥失败');
    form.setFieldsValue({ accessKey: '', secretKey: '' });
    if (result) onDone();
  }
  return <Modal open title={`更换密钥 · ${instance.instanceCode}`} okText="保存密钥" cancelText="取消" confirmLoading={command.saving}
    okButtonProps={{ disabled: command.conflict }} onOk={() => void submit()} onCancel={() => { if (!command.saving) onClose(); }}
    mask={{ closable: !command.saving }} destroyOnHidden>
    <Alert type="warning" showIcon style={{ marginBottom: 12 }} title="保存后立即生效：之后的上传、下载与检查都使用新凭据。建议先确认新密钥有该桶的读写权限，再停用旧密钥。" />
    <div className="muted" style={{ marginBottom: 12 }}>当前：{credentialModeLabels[instance.credentialMode]}
      {instance.credentialMode === 'STATIC' && (instance.credentialConfigured ? '，已配置' : '，未配置')}
      {instance.credentialUpdateTime && ` · 更换于 ${date(instance.credentialUpdateTime)}`}</div>
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict} autoComplete="off"
      initialValues={{ credentialMode: instance.credentialMode, reason: '' }}>
      <Form.Item name="credentialMode" label="凭据方式">
        <Select options={allowedCredentialModes(instance.storageType).map(value => ({ value, label: credentialModeLabels[value] }))} /></Form.Item>
      {credentialMode === 'STATIC' && <SecretFields />}
      {reasonItem}
    </Form>
    {conflictBanner(command.conflict)}
  </Modal>;
}

/** 启停确认：说明影响并要求原因；Platform 拒绝时（如被写入规则引用）由错误通知展示原因。 */
export function StatusModal({ title, path, code, target, version, notice, onClose, onDone, onAuthorizationChanged }: CommandProps & {
  title: string; path: string; code: string; target: string; version: number; notice?: ReactNode;
}) {
  const [form] = Form.useForm<{ reason: string }>(); const command = useStorageCommand(onAuthorizationChanged, form);
  async function submit() {
    const { reason } = await form.validateFields();
    const result = await command.run(path, 'PATCH', { status: target, expectedVersion: version, reason: reason.trim() },
      value => decodeMutation(value, code), `${title}失败`);
    if (result) onDone();
  }
  return <Modal open title={title} okText="确认" cancelText="取消" confirmLoading={command.saving}
    okButtonProps={{ danger: target === 'DISABLED', disabled: command.conflict }} onOk={() => void submit()}
    onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }} destroyOnHidden>
    {notice && <Alert type="info" showIcon title={notice} style={{ marginBottom: 16 }} />}
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict}>{reasonItem}</Form>
    {conflictBanner(command.conflict)}
  </Modal>;
}

/** 登记桶：桶由基础设施预先创建，这里只登记名称；登记后为草稿，检查通过后才能启用。 */
export function BucketModal({ instances, defaultInstance, onClose, onDone, onAuthorizationChanged }: CommandProps & {
  instances: StorageInstance[]; defaultInstance?: string;
}) {
  const [form] = Form.useForm<{ instanceCode: string; bucketName: string; reason: string }>(); const command = useStorageCommand(onAuthorizationChanged, form);
  const selectable = instances.filter(instance => instance.status !== 'DISABLED');
  async function submit() {
    const values = await form.validateFields();
    const bucketName = values.bucketName.trim();
    const result = await command.run(`${instancePath(values.instanceCode)}/buckets`, 'POST', { bucketName, reason: values.reason.trim() },
      value => decodeMutation(value, bucketObjectCode(values.instanceCode, bucketName)), '登记桶失败');
    if (result) onDone();
  }
  return <Modal open title="登记桶" okText="登记" cancelText="取消" confirmLoading={command.saving} onOk={() => void submit()}
    onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }} destroyOnHidden>
    <div className="notification-note">桶由基础设施预先创建并开启版本控制与默认加密，这里只登记和检查。桶名登记后不可修改。</div>
    <Form form={form} layout="vertical" disabled={command.saving} initialValues={{ instanceCode: defaultInstance, reason: '' }}>
      <Form.Item name="instanceCode" label="存储实例" rules={[{ required: true, message: '请选择存储实例' }]}>
        <Select options={selectable.map(instance => ({ value: instance.instanceCode, label: instance.instanceCode }))} /></Form.Item>
      <Form.Item name="bucketName" label="桶名" rules={[{ required: true, message: '请填写桶名' },
        { pattern: bucketNamePattern, message: '3—63 位小写字母、数字、点或连字符，首尾为字母或数字' }]}>
        <Input maxLength={63} className="mono" /></Form.Item>
      {reasonItem}
    </Form>
  </Modal>;
}

/** 桶检查：事务外访问存储，结果写回桶并显示在弹窗内；检查通过后 10 分钟内可启用。 */
export function BucketCheckModal({ instanceCode, bucketName, onClose, onDone, onAuthorizationChanged }: CommandProps & {
  instanceCode: string; bucketName: string;
}) {
  const command = useStorageCommand(onAuthorizationChanged); const [result, setResult] = useState<BucketCheck | null>(null);
  async function check() {
    setResult(null);
    const checked = await command.run(`${bucketPath(instanceCode, bucketName)}/check`, 'POST', null,
      value => decodeBucketCheck(value, instanceCode, bucketName), '桶检查失败');
    if (checked) { setResult(checked); onDone(); }
  }
  return <Modal open title={`检查桶 · ${instanceCode} / ${bucketName}`} onCancel={() => { if (!command.saving) onClose(); }}
    mask={{ closable: !command.saving }} destroyOnHidden footer={[
      <Button key="close" disabled={command.saving} onClick={onClose}>关闭</Button>,
      <Button key="check" type="primary" loading={command.saving} onClick={() => void check()}>{result ? '重新检查' : '开始检查'}</Button>]}>
    <div className="muted" style={{ marginBottom: 12 }}>依次确认桶存在、已开启版本控制与默认加密，并写入、读取、删除一个探测对象。实例为草稿时也可检查。</div>
    {result && <Alert type={result.result === 'OK' ? 'success' : 'error'} showIcon title={checkResultLabels[result.result]}
      description={`检查时间 ${date(result.checkedAt)}${result.result === 'OK' ? `，${bucketCheckValidMillis / 60_000} 分钟内可启用` : ''}`} />}
  </Modal>;
}

/** 新建（route 为空）或修改写入规则；默认规则的来源系统固定为「默认」。 */
export function RouteModal({ route, instances, onClose, onDone, onAuthorizationChanged }: CommandProps & {
  route: StorageRoute | null; instances: StorageInstance[];
}) {
  const [form] = Form.useForm<RouteFormValues>(); const command = useStorageCommand(onAuthorizationChanged, form);
  const targets = routeTargets(instances);
  async function submit() {
    const values = await form.validateFields();
    const result = route
      ? await command.run(routePath(route.sourceSystem), 'PUT', updateRouteBody(values, route),
        value => decodeMutation(value, route.sourceSystem), '保存写入规则失败')
      : await command.run(`${storageAdminPath}/routes`, 'POST', createRouteBody(values),
        value => decodeMutation(value, (values.sourceSystem ?? '').trim()), '新建写入规则失败');
    if (result) onDone();
  }
  return <Modal open title={route ? `修改写入规则 · ${sourceSystemLabel(route.sourceSystem)}` : '新建写入规则'} okText="保存" cancelText="取消"
    confirmLoading={command.saving} okButtonProps={{ disabled: command.conflict }} onOk={() => void submit()}
    onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }} destroyOnHidden>
    <div className="notification-note">写入规则只影响之后的新上传；已上传的文件始终从原实例和桶读取。</div>
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict} initialValues={{
      sourceSystem: route?.sourceSystem, target: route ? targetValue(route.instanceCode, route.bucketName) : undefined,
      scanPolicyCode: route?.scanPolicyCode ?? '', maxUploadSeconds: route?.maxUploadSeconds ?? uploadSecondsRange.defaultValue, reason: '' }}>
      {route
        ? <div className="muted" style={{ marginBottom: 16 }}>来源系统：{sourceSystemLabel(route.sourceSystem)}</div>
        : <Form.Item name="sourceSystem" label="来源系统" extra="内部调用身份中的服务名，例如 client-master-service"
          rules={[{ required: true, message: '请填写来源系统' }, { validator: async (_, value?: string) => {
            if (value && value.trim() !== defaultSourceSystem && !sourceSystemPattern.test(value.trim())) throw new Error('小写字母开头，只能包含小写字母、数字或连字符');
          } }]}><Input maxLength={64} className="mono" /></Form.Item>}
      <Form.Item name="target" label="目标桶" extra="只能选择启用实例下的启用桶" rules={[{ required: true, message: '请选择目标桶' }]}>
        <Select options={targets.map(t => ({ value: targetValue(t.instanceCode, t.bucketName), label: `${t.instanceCode} / ${t.bucketName}` }))} /></Form.Item>
      <Form.Item name="scanPolicyCode" label="检查策略" extra="必须有启用中的修订，例如 CLIENT_ONBOARDING_FORMAT"
        rules={[{ required: true, whitespace: true, message: '请填写检查策略' }, { pattern: scanPolicyCodePattern, message: '大写字母开头，只能包含大写字母、数字或下划线' }]}>
        <Input maxLength={64} className="mono" /></Form.Item>
      <Form.Item name="maxUploadSeconds" label="上传期限（秒）" rules={[{ required: true, message: '请填写上传期限' }]}>
        <InputNumber min={uploadSecondsRange.min} max={uploadSecondsRange.max} precision={0} style={{ width: 200 }} /></Form.Item>
      {reasonItem}
    </Form>
    {conflictBanner(command.conflict)}
  </Modal>;
}

/** 删除写入规则：默认规则不提供删除入口；删除后该来源的新上传落到默认规则。 */
export function DeleteRouteModal({ route, onClose, onDone, onAuthorizationChanged }: CommandProps & { route: StorageRoute }) {
  const [form] = Form.useForm<{ reason: string }>(); const command = useStorageCommand(onAuthorizationChanged, form);
  async function submit() {
    const { reason } = await form.validateFields();
    const result = await command.run(routePath(route.sourceSystem), 'DELETE', { expectedVersion: route.version, reason: reason.trim() },
      (value): StorageMutation => decodeMutation(value, route.sourceSystem), '删除写入规则失败');
    if (result) onDone();
  }
  return <Modal open title={`删除写入规则 · ${route.sourceSystem}`} okText="确认删除" cancelText="取消" confirmLoading={command.saving}
    okButtonProps={{ danger: true, disabled: command.conflict }} onOk={() => void submit()}
    onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }} destroyOnHidden>
    <p>删除后，{route.sourceSystem} 的新上传将使用默认写入规则；已上传的文件不受影响。</p>
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict}>{reasonItem}</Form>
    {conflictBanner(command.conflict)}
  </Modal>;
}
