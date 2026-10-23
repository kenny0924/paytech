import { useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Button, Drawer, Form, Input, InputNumber, Modal, Radio, Select, Space, Switch, Tag, Typography } from 'antd';
import {
  accessLevelLabels, accessLevels, allowedCredentialModes, bucketCheckValidMillis, bucketObjectCode, bucketNamePattern, bucketPath, checkResultLabels,
  connectTimeoutRange, createInstanceBody, createRouteBody, credentialBody, credentialModeLabels, decodeBucketCheck, decodeMutation, defaultSourceSystem,
  instanceCodePattern, instancePath, privateAccessGuidance, publicBaseUrlMaxLength, publicReadGuidance, publicReadPolicyExample, readTimeoutRange,
  regionPattern, registerBucketBody, routeObjectCode, routePath, routeTargets, sourceSystemLabel, sourceSystemPattern, storageAdminPath,
  storageTypeLabels, storageTypes, targetValue, updateBucketBody, updateInstanceBody, updateRouteBody, uploadSecondsRange, validPublicBaseUrl,
} from './fileStorage';
import type {
  AccessLevel, BucketCheck, BucketFormValues, BucketRow, CredentialFormValues, InstanceFormValues, RouteFormValues, StorageInstance, StorageMutation,
  StorageRoute, StorageType,
} from './fileStorage';
import { byteSizeLabel, mimeTypeLabel, selectablePolicies } from './scanPolicy';
import type { ScanPolicyList } from './scanPolicy';
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

const publicBaseUrlRules = [{ required: true, whitespace: true, message: '请填写对外地址' }, { validator: async (_: unknown, value?: string) => {
  if (value?.trim() && !validPublicBaseUrl(value.trim())) throw new Error('HTTPS 地址，不带查询串或片段，不以 / 结尾');
} }];

/** 登记桶：桶由基础设施预先创建，这里只登记名称与访问级别；公开桶须登记对外地址。登记后为草稿，检查通过后才能启用。 */
export function BucketModal({ instances, defaultInstance, onClose, onDone, onAuthorizationChanged }: CommandProps & {
  instances: StorageInstance[]; defaultInstance?: string;
}) {
  const [form] = Form.useForm<BucketFormValues>(); const command = useStorageCommand(onAuthorizationChanged, form);
  const selectable = instances.filter(instance => instance.status !== 'DISABLED');
  const accessLevel = Form.useWatch('accessLevel', form) as AccessLevel | undefined;
  async function submit() {
    const values = await form.validateFields();
    const body = registerBucketBody(values);
    const result = await command.run(`${instancePath(values.instanceCode ?? '')}/buckets`, 'POST', body,
      value => decodeMutation(value, bucketObjectCode(values.instanceCode ?? '', body.bucketName)), '登记桶失败');
    if (result) onDone();
  }
  return <Modal open title="登记桶" okText="登记" cancelText="取消" confirmLoading={command.saving} onOk={() => void submit()}
    onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }} destroyOnHidden>
    <div className="notification-note">桶由基础设施预先创建并开启版本控制与默认加密，这里只登记和检查。桶名与访问级别登记后不可修改。</div>
    <Form form={form} layout="vertical" disabled={command.saving} initialValues={{ instanceCode: defaultInstance, accessLevel: 'PRIVATE', reason: '' }}>
      <Form.Item name="instanceCode" label="存储实例" rules={[{ required: true, message: '请选择存储实例' }]}>
        <Select options={selectable.map(instance => ({ value: instance.instanceCode, label: instance.instanceCode }))} /></Form.Item>
      <Form.Item name="bucketName" label="桶名" rules={[{ required: true, message: '请填写桶名' },
        { pattern: bucketNamePattern, message: '3—63 位小写字母、数字、点或连字符，首尾为字母或数字' }]}>
        <Input maxLength={63} className="mono" /></Form.Item>
      <Form.Item name="accessLevel" label="访问级别" extra={accessLevel === 'PUBLIC'
        ? '公开桶中的文件通过对外地址长期可读；只放本来就打算公开的内容' : '私有桶只通过平台签发的临时地址读写'}>
        <Radio.Group options={accessLevels.map(value => ({ value, label: accessLevelLabels[value] }))} /></Form.Item>
      {accessLevel === 'PUBLIC' && <Form.Item name="publicBaseUrl" label="对外地址" rules={publicBaseUrlRules}
        extra="CDN 或桶的公开地址；文件地址为「对外地址/对象名」。草稿期间可修改，启用后不能再改">
        <Input maxLength={publicBaseUrlMaxLength} placeholder="https://" className="mono" /></Form.Item>}
      {reasonItem}
    </Form>
    {conflictBanner(command.conflict)}
  </Modal>;
}

/** 修改草稿公开桶的对外地址；改完需要重新检查才能启用。 */
export function BucketUrlModal({ bucket, onClose, onDone, onAuthorizationChanged }: CommandProps & { bucket: BucketRow }) {
  const [form] = Form.useForm<{ publicBaseUrl: string; reason: string }>(); const command = useStorageCommand(onAuthorizationChanged, form);
  async function submit() {
    const values = await form.validateFields();
    const result = await command.run(bucketPath(bucket.instanceCode, bucket.bucketName), 'PUT', updateBucketBody(values, bucket),
      value => decodeMutation(value, bucketObjectCode(bucket.instanceCode, bucket.bucketName)), '保存对外地址失败');
    if (result) onDone();
  }
  return <Modal open title={`编辑对外地址 · ${bucket.instanceCode} / ${bucket.bucketName}`} okText="保存" cancelText="取消" confirmLoading={command.saving}
    okButtonProps={{ disabled: command.conflict }} onOk={() => void submit()} onCancel={() => { if (!command.saving) onClose(); }}
    mask={{ closable: !command.saving }} destroyOnHidden>
    <div className="notification-note">只有草稿状态的公开桶可以修改对外地址；保存后请重新检查，检查通过后再启用。</div>
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict} initialValues={{ publicBaseUrl: bucket.publicBaseUrl ?? '', reason: '' }}>
      <Form.Item name="publicBaseUrl" label="对外地址" rules={publicBaseUrlRules}>
        <Input maxLength={publicBaseUrlMaxLength} placeholder="https://" className="mono" /></Form.Item>
      {reasonItem}
    </Form>
    {conflictBanner(command.conflict)}
  </Modal>;
}

/** 检查未通过时的排查指引（静态文案）：公开桶给出 AWS 公开只读桶策略示例，私有桶说明须关闭匿名读取。 */
function CheckGuidance({ bucket, result }: { bucket: BucketRow; result: BucketCheck }) {
  if (result.result === 'PUBLIC_READ_FAILED') return <div className="storage-check-guidance">
    <p>{publicReadGuidance}</p>
    <div className="muted">AWS S3 公开只读桶策略示例（在 AWS 控制台配置，平台不修改桶策略）</div>
    <Typography.Paragraph className="mono storage-policy-example" copyable={{ text: publicReadPolicyExample(bucket.bucketName), tooltips: ['复制', '已复制'] }}>
      {publicReadPolicyExample(bucket.bucketName)}</Typography.Paragraph>
  </div>;
  if (result.result === 'PUBLIC_ACCESS_ALLOWED') return <div className="storage-check-guidance"><p>{privateAccessGuidance}</p></div>;
  return null;
}

/** 桶检查：事务外访问存储，结果写回桶并显示在弹窗内；检查通过后 10 分钟内可启用。未通过时给出排查指引。 */
export function BucketCheckModal({ bucket, onEditUrl, onClose, onDone, onAuthorizationChanged }: CommandProps & {
  bucket: BucketRow; onEditUrl?: () => void;
}) {
  const { instanceCode, bucketName } = bucket;
  const command = useStorageCommand(onAuthorizationChanged); const [result, setResult] = useState<BucketCheck | null>(null);
  async function check() {
    setResult(null);
    const checked = await command.run(`${bucketPath(instanceCode, bucketName)}/check`, 'POST', null,
      value => decodeBucketCheck(value, instanceCode, bucketName), '桶检查失败');
    if (checked) { setResult(checked); onDone(); }
  }
  const failed = result && result.result !== 'OK';
  return <Modal open title={`检查桶 · ${instanceCode} / ${bucketName}`} width={640} onCancel={() => { if (!command.saving) onClose(); }}
    mask={{ closable: !command.saving }} destroyOnHidden footer={[
      <Button key="close" disabled={command.saving} onClick={onClose}>关闭</Button>,
      ...(failed && onEditUrl && result.result === 'PUBLIC_READ_FAILED'
        ? [<Button key="url" disabled={command.saving} onClick={onEditUrl}>编辑对外地址</Button>] : []),
      <Button key="check" type="primary" loading={command.saving} onClick={() => void check()}>{result ? '重新检查' : '开始检查'}</Button>]}>
    <div className="storage-check-head"><Tag color={bucket.accessLevel === 'PUBLIC' ? 'blue' : 'default'}>{accessLevelLabels[bucket.accessLevel]}</Tag>
      {bucket.publicBaseUrl && <span className="muted">对外地址 <span className="mono">{bucket.publicBaseUrl}</span></span>}</div>
    <div className="muted" style={{ marginBottom: 12 }}>依次检查桶存在、版本控制、默认加密，写入、读取、删除一个探测对象，
      {bucket.accessLevel === 'PUBLIC' ? '再用对外地址匿名读取该对象' : '并确认不带凭据访问被拒绝'}。实例为草稿时也可检查。</div>
    {result && <Alert type={result.result === 'OK' ? 'success' : 'error'} showIcon
      title={result.result === 'OK' ? checkResultLabels.OK : `检查未通过：${checkResultLabels[result.result]}${bucket.status === 'DRAFT' ? '。桶保持草稿，不能启用。' : ''}`}
      description={`检查时间 ${date(result.checkedAt)}${result.result === 'OK' ? `，${bucketCheckValidMillis / 60_000} 分钟内可启用` : ' · 结果已记录到桶'}`} />}
    {result && <CheckGuidance bucket={bucket} result={result} />}
  </Modal>;
}

/**
 * 新建（route 为空）或修改写入规则
 * <li>编辑时来源系统与访问级别只读；要换级别请新建规则。</li>
 * <li>目标桶只列同一访问级别的启用桶；检查策略只列有生效修订的策略。</li>
 */
export function RouteModal({ route, sourceSystem, accessLevel, instances, policies, onClose, onDone, onAuthorizationChanged }: CommandProps & {
  route: StorageRoute | null; sourceSystem?: string; accessLevel?: AccessLevel; instances: StorageInstance[]; policies: ScanPolicyList;
}) {
  const [form] = Form.useForm<RouteFormValues>(); const command = useStorageCommand(onAuthorizationChanged, form);
  const level = (Form.useWatch('accessLevel', form) as AccessLevel | undefined) ?? route?.accessLevel ?? accessLevel ?? 'PRIVATE';
  const targets = routeTargets(instances, level);
  const selectable = selectablePolicies(policies.items);
  const values = Form.useWatch([], form) as RouteFormValues | undefined;
  const chosenPolicy = policies.items.find(item => item.policyCode === values?.scanPolicyCode);
  async function submit() {
    const current = await form.validateFields();
    const result = route
      ? await command.run(routePath(route.sourceSystem, route.accessLevel), 'PUT', updateRouteBody(current, route),
        value => decodeMutation(value, routeObjectCode(route.sourceSystem, route.accessLevel)), '保存写入规则失败')
      : await command.run(`${storageAdminPath}/routes`, 'POST', createRouteBody(current),
        value => decodeMutation(value, routeObjectCode((current.sourceSystem ?? '').trim(), current.accessLevel ?? 'PRIVATE')), '新建写入规则失败');
    if (result) onDone();
  }
  const policyOptions = [
    ...(route && !selectable.some(item => item.policyCode === route.scanPolicyCode)
      ? [{ value: route.scanPolicyCode, label: `${route.scanPolicyCode}（当前，没有生效修订）`, disabled: true }] : []),
    ...policies.items.map(item => ({ value: item.policyCode, disabled: item.activeRevisionNo === null,
      label: item.activeRevisionNo === null ? `${item.policyCode}（没有生效修订，不能选择）`
        : `${item.policyCode} r${item.activeRevisionNo} · ${item.allowedMimeTypes.map(mimeTypeLabel).join('、')} · ≤ ${byteSizeLabel(item.maxSizeBytes)}` })),
  ];
  const target = values?.target ? values.target.replace('/', ' / ') : null;
  return <Modal open title={route ? `编辑写入规则 · ${sourceSystemLabel(route.sourceSystem)} · ${accessLevelLabels[route.accessLevel]}` : '新建写入规则'}
    okText="保存" cancelText="取消" width={600} confirmLoading={command.saving} okButtonProps={{ disabled: command.conflict }} onOk={() => void submit()}
    onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }} destroyOnHidden>
    <div className="notification-note">写入规则只影响之后开始的上传；已上传的文件始终从原实例和桶读取。</div>
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict} initialValues={{
      sourceSystem: route?.sourceSystem ?? sourceSystem, accessLevel: route?.accessLevel ?? accessLevel ?? 'PRIVATE',
      target: route ? targetValue(route.instanceCode, route.bucketName) : undefined,
      scanPolicyCode: route?.scanPolicyCode, maxUploadSeconds: route?.maxUploadSeconds ?? uploadSecondsRange.defaultValue, reason: '' }}>
      {route
        ? <div className="muted" style={{ marginBottom: 16 }}>来源系统：<span className="mono">{sourceSystemLabel(route.sourceSystem)}</span> · 访问级别：
          {accessLevelLabels[route.accessLevel]}。编辑时不能改来源和访问级别；要换级别请新建规则。</div>
        : <>
          <Form.Item name="sourceSystem" label="来源系统" extra="内部调用身份中的服务名，例如 client-master-service；* 为默认规则"
            rules={[{ required: true, message: '请填写来源系统' }, { validator: async (_, value?: string) => {
              if (value && value.trim() !== defaultSourceSystem && !sourceSystemPattern.test(value.trim())) throw new Error('小写字母开头，只能包含小写字母、数字或连字符');
            } }]}><Input maxLength={64} className="mono" disabled={!!sourceSystem} /></Form.Item>
          <Form.Item name="accessLevel" label="访问级别">
            <Radio.Group disabled={!!accessLevel} onChange={() => form.setFieldValue('target', undefined)}
              options={accessLevels.map(value => ({ value, label: accessLevelLabels[value] }))} /></Form.Item>
        </>}
      <Form.Item name="target" label="存储桶" extra={`只能选择同为${accessLevelLabels[level]}、已启用的桶`} rules={[{ required: true, message: '请选择存储桶' }]}>
        <Select options={targets.map(t => ({ value: targetValue(t.instanceCode, t.bucketName), label: `${t.instanceCode} / ${t.bucketName}` }))}
          notFoundContent={`没有${accessLevelLabels[level]}的启用桶`} /></Form.Item>
      <Form.Item name="scanPolicyCode" label="检查策略" extra="只能选择有生效修订的策略" rules={[{ required: true, message: '请选择检查策略' }]}>
        <Select showSearch options={policyOptions} notFoundContent="没有可选的检查策略" /></Form.Item>
      <Form.Item name="maxUploadSeconds" label="上传期限（秒）" extra={`${uploadSecondsRange.min}—${uploadSecondsRange.max} 秒`} rules={[{ required: true, message: '请填写上传期限' }]}>
        <InputNumber min={uploadSecondsRange.min} max={uploadSecondsRange.max} precision={0} style={{ width: 200 }} /></Form.Item>
      {reasonItem}
    </Form>
    {target && chosenPolicy?.activeRevisionNo && <div className="notification-note">保存后：{sourceSystemLabel((values?.sourceSystem ?? route?.sourceSystem ?? '').trim())}
      的{accessLevelLabels[level]}上传 → {target}，按 {chosenPolicy.policyCode} r{chosenPolicy.activeRevisionNo} 检查，上传期限 {values?.maxUploadSeconds ?? '—'} 秒。</div>}
    {conflictBanner(command.conflict)}
  </Modal>;
}

/** 删除写入规则：默认私有规则不提供删除入口；删除后该来源该级别的新上传落到同级别默认规则（没有时返回 503）。 */
export function DeleteRouteModal({ route, onClose, onDone, onAuthorizationChanged }: CommandProps & { route: StorageRoute }) {
  const [form] = Form.useForm<{ reason: string }>(); const command = useStorageCommand(onAuthorizationChanged, form);
  async function submit() {
    const { reason } = await form.validateFields();
    const result = await command.run(routePath(route.sourceSystem, route.accessLevel), 'DELETE', { expectedVersion: route.version, reason: reason.trim() },
      (value): StorageMutation => decodeMutation(value, routeObjectCode(route.sourceSystem, route.accessLevel)), '删除写入规则失败');
    if (result) onDone();
  }
  return <Modal open title={`删除写入规则 · ${sourceSystemLabel(route.sourceSystem)} · ${accessLevelLabels[route.accessLevel]}`} okText="确认删除" cancelText="取消" confirmLoading={command.saving}
    okButtonProps={{ danger: true, disabled: command.conflict }} onOk={() => void submit()}
    onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }} destroyOnHidden>
    <p>删除后，{sourceSystemLabel(route.sourceSystem)} 的{accessLevelLabels[route.accessLevel]}上传将使用同级别默认规则（没有时返回 503）；已上传的文件不受影响。</p>
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict}>{reasonItem}</Form>
    {conflictBanner(command.conflict)}
  </Modal>;
}
