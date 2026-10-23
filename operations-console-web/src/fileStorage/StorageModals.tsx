import { useState } from 'react';
import type { ReactNode } from 'react';
import { Alert, Button, Drawer, Form, Input, InputNumber, Modal, Radio, Select, Space, Switch, Tag, Typography } from 'antd';
import {
  accessLevelLabel, accessLevels, allowedCredentialModes, bucketCheckValidMillis, bucketObjectCode, bucketNamePattern, bucketPath, checkResultLabel,
  connectTimeoutRange, createInstanceBody, createRouteBody, credentialBody, credentialModeLabel, decodeBucketCheck, decodeMutation, defaultSourceSystem,
  instanceCodePattern, instancePath, joinList, privateAccessGuidance, publicBaseUrlMaxLength, publicReadGuidance, publicReadPolicyExample, readTimeoutRange,
  regionPattern, registerBucketBody, routeObjectCode, routePath, routeTargets, sourceSystemLabel, sourceSystemPattern, storageAdminPath,
  storageTypeLabel, storageTypes, targetValue, updateBucketBody, updateInstanceBody, updateRouteBody, uploadSecondsRange, validPublicBaseUrl,
} from './fileStorage';
import type {
  AccessLevel, BucketCheck, BucketFormValues, BucketRow, CredentialFormValues, InstanceFormValues, RouteFormValues, StorageInstance, StorageMutation,
  StorageRoute, StorageType,
} from './fileStorage';
import { byteSizeLabel, mimeTypeLabel, selectablePolicies } from './scanPolicy';
import type { ScanPolicyList } from './scanPolicy';
import { useStorageCommand } from './useStorageCommand';
import { formatDateTime } from '../i18n/index';
import type { Translate } from '../i18n/index';
import { useT } from '../i18n/react';

/** 原因：必填，写入审计。 */
function ReasonItem() {
  const t = useT('file');
  return <Form.Item name="reason" label={t('common.reason')} rules={[{ required: true, whitespace: true, message: t('common.reasonRequired') }]}>
    <Input.TextArea rows={2} maxLength={500} showCount /></Form.Item>;
}
function ConflictBanner({ conflict }: { conflict: boolean }) {
  const t = useT('file');
  return conflict ? <Alert type="error" showIcon title={t('common.conflict')} style={{ marginTop: 12 }} /> : null;
}

interface CommandProps { onClose: () => void; onDone: () => void; onAuthorizationChanged: () => void }

/** 密钥输入：密码框、不自动填充；只在固定密钥方式下出现。 */
function SecretFields() {
  const t = useT('file');
  return <>
    <Form.Item name="accessKey" label="Access Key" rules={[{ required: true, message: t('instance.accessKeyRequired') }]}>
      <Input.Password maxLength={128} autoComplete="off" className="mono" /></Form.Item>
    <Form.Item name="secretKey" label="Secret Key" rules={[{ required: true, message: t('instance.secretKeyRequired') }]}>
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
  const t = useT('file'), tCommon = useT('common');
  async function submit() {
    const values = await form.validateFields();
    const result = instance
      ? await command.run(instancePath(instance.instanceCode), 'PUT', updateInstanceBody(values, instance),
        value => decodeMutation(value, instance.instanceCode), t('instance.saveFailed'))
      : await command.run(`${storageAdminPath}/instances`, 'POST', createInstanceBody(values),
        value => decodeMutation(value, (values.instanceCode ?? '').trim()), t('instance.createFailed'));
    form.setFieldsValue({ accessKey: '', secretKey: '' });
    if (result) onDone();
  }
  function changeType(value: StorageType) {
    if (!allowedCredentialModes(value).includes(form.getFieldValue('credentialMode'))) form.setFieldsValue({ credentialMode: 'STATIC' });
  }
  return <Drawer open title={instance ? t('instance.editTitle', { code: instance.instanceCode }) : t('instance.createTitle')} size={560} destroyOnHidden
    onClose={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }}
    extra={<Space><Button disabled={command.saving} onClick={onClose}>{tCommon('action.cancel')}</Button>
      <Button type="primary" loading={command.saving} disabled={command.conflict} onClick={() => void submit()}>{instance ? tCommon('action.save') : t('instance.createOk')}</Button></Space>}>
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict} autoComplete="off" initialValues={{
      instanceCode: instance?.instanceCode, storageType: instance?.storageType ?? 'MINIO', endpoint: instance?.endpoint ?? '',
      region: instance?.region ?? '', pathStyleAccess: instance?.pathStyleAccess ?? true, trustedCertificatePem: instance?.trustedCertificatePem ?? '',
      credentialMode: instance?.credentialMode ?? 'STATIC', connectTimeoutMs: instance?.connectTimeoutMs ?? connectTimeoutRange.defaultValue,
      readTimeoutMs: instance?.readTimeoutMs ?? readTimeoutRange.defaultValue, reason: '' }}>
      <Form.Item name="instanceCode" label={t('instance.code')} extra={instance ? undefined : t('instance.codeHint')}
        rules={[{ required: true, message: t('instance.codeRequired') }, { pattern: instanceCodePattern, message: t('common.codePattern') }]}>
        <Input maxLength={64} disabled={!!instance} className="mono" /></Form.Item>
      <Form.Item name="storageType" label={t('instance.storageType')}>
        <Select disabled={!!instance} onChange={changeType} options={storageTypes.map(value => ({ value, label: storageTypeLabel(value) }))} /></Form.Item>
      <Form.Item name="endpoint" label={t('instance.endpoint')} extra={storageType === 'S3' ? t('instance.endpointHintS3') : t('instance.endpointHintMinio')}
        rules={[{ required: storageType === 'MINIO', whitespace: true, message: t('instance.endpointRequired') }, { type: 'url', message: t('instance.endpointUrl') },
          { pattern: /^https:\/\//, message: t('instance.endpointHttps') }]}>
        <Input maxLength={255} placeholder="https://" className="mono" /></Form.Item>
      <Form.Item name="region" label={t('instance.region')} rules={[{ required: true, whitespace: true, message: t('instance.regionRequired') },
        { pattern: regionPattern, message: t('instance.regionPattern') }]}>
        <Input maxLength={64} placeholder={t('instance.regionPlaceholder')} /></Form.Item>
      <Form.Item name="pathStyleAccess" label={t('instance.pathStyleAccess')} valuePropName="checked" extra={t('instance.pathStyleAccessHint')}><Switch /></Form.Item>
      <Form.Item name="trustedCertificatePem" label={t('instance.trustedCertificate')} extra={t('instance.trustedCertificateHint')}>
        <Input.TextArea rows={3} className="mono" placeholder="-----BEGIN CERTIFICATE-----" /></Form.Item>
      <Space size={16} style={{ display: 'flex' }}>
        <Form.Item name="connectTimeoutMs" label={t('instance.connectTimeout')} rules={[{ required: true, message: t('instance.connectTimeoutRequired') }]}>
          <InputNumber min={connectTimeoutRange.min} max={connectTimeoutRange.max} precision={0} style={{ width: 200 }} /></Form.Item>
        <Form.Item name="readTimeoutMs" label={t('instance.readTimeout')} rules={[{ required: true, message: t('instance.readTimeoutRequired') }]}>
          <InputNumber min={readTimeoutRange.min} max={readTimeoutRange.max} precision={0} style={{ width: 200 }} /></Form.Item>
      </Space>
      {instance
        ? <div className="muted" style={{ marginBottom: 16 }}>{t('instance.credentialModeReadonly', { mode: credentialModeLabel(instance.credentialMode) })}</div>
        : <>
          <Form.Item name="credentialMode" label={t('instance.credentialMode')} extra={storageType === 'MINIO' ? t('instance.credentialModeHintMinio') : t('instance.credentialModeHintS3')}>
            <Select options={allowedCredentialModes(storageType ?? 'MINIO').map(value => ({ value, label: credentialModeLabel(value) }))} /></Form.Item>
          {credentialMode === 'STATIC' && <SecretFields />}
        </>}
      <ReasonItem />
    </Form>
    {!instance && <div className="muted">{t('instance.createNote')}</div>}
    <ConflictBanner conflict={command.conflict} />
  </Drawer>;
}

/** 当前凭据说明：方式、固定密钥是否已配置、最近更换时间。 */
function currentCredentialText(t: Translate, instance: StorageInstance): string {
  const mode = credentialModeLabel(instance.credentialMode);
  const time = instance.credentialUpdateTime ? formatDateTime(instance.credentialUpdateTime) : null;
  if (instance.credentialMode !== 'STATIC') return time ? t('credential.currentReplaced', { mode, time }) : t('credential.current', { mode });
  if (instance.credentialConfigured) return time ? t('credential.currentConfiguredReplaced', { mode, time }) : t('credential.currentConfigured', { mode });
  return time ? t('credential.currentNotConfiguredReplaced', { mode, time }) : t('credential.currentNotConfigured', { mode });
}

/** 更换密钥或切换凭据方式：密码框输入，保存后立即生效，提交后清空。 */
export function CredentialModal({ instance, onClose, onDone, onAuthorizationChanged }: CommandProps & { instance: StorageInstance }) {
  const [form] = Form.useForm<CredentialFormValues>(); const command = useStorageCommand(onAuthorizationChanged, form);
  const credentialMode = Form.useWatch('credentialMode', form);
  const t = useT('file'), tCommon = useT('common');
  async function submit() {
    const values = await form.validateFields();
    const result = await command.run(`${instancePath(instance.instanceCode)}/credential`, 'PUT', credentialBody(values, instance),
      value => decodeMutation(value, instance.instanceCode), t('credential.failed'));
    form.setFieldsValue({ accessKey: '', secretKey: '' });
    if (result) onDone();
  }
  return <Modal open title={t('credential.title', { code: instance.instanceCode })} okText={t('credential.ok')} cancelText={tCommon('action.cancel')} confirmLoading={command.saving}
    okButtonProps={{ disabled: command.conflict }} onOk={() => void submit()} onCancel={() => { if (!command.saving) onClose(); }}
    mask={{ closable: !command.saving }} destroyOnHidden>
    <Alert type="warning" showIcon style={{ marginBottom: 12 }} title={t('credential.warning')} />
    <div className="muted" style={{ marginBottom: 12 }}>{currentCredentialText(t, instance)}</div>
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict} autoComplete="off"
      initialValues={{ credentialMode: instance.credentialMode, reason: '' }}>
      <Form.Item name="credentialMode" label={t('instance.credentialMode')}>
        <Select options={allowedCredentialModes(instance.storageType).map(value => ({ value, label: credentialModeLabel(value) }))} /></Form.Item>
      {credentialMode === 'STATIC' && <SecretFields />}
      <ReasonItem />
    </Form>
    <ConflictBanner conflict={command.conflict} />
  </Modal>;
}

/** 启停确认：说明影响并要求原因；Platform 拒绝时（如被写入规则引用）由错误通知展示原因。 */
export function StatusModal({ title, failureTitle, path, code, target, version, notice, onClose, onDone, onAuthorizationChanged }: CommandProps & {
  title: string; failureTitle: string; path: string; code: string; target: string; version: number; notice?: ReactNode;
}) {
  const [form] = Form.useForm<{ reason: string }>(); const command = useStorageCommand(onAuthorizationChanged, form);
  const t = useT('file'), tCommon = useT('common');
  async function submit() {
    const { reason } = await form.validateFields();
    const result = await command.run(path, 'PATCH', { status: target, expectedVersion: version, reason: reason.trim() },
      value => decodeMutation(value, code), failureTitle);
    if (result) onDone();
  }
  return <Modal open title={title} okText={t('common.confirm')} cancelText={tCommon('action.cancel')} confirmLoading={command.saving}
    okButtonProps={{ danger: target === 'DISABLED', disabled: command.conflict }} onOk={() => void submit()}
    onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }} destroyOnHidden>
    {notice && <Alert type="info" showIcon title={notice} style={{ marginBottom: 16 }} />}
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict}><ReasonItem /></Form>
    <ConflictBanner conflict={command.conflict} />
  </Modal>;
}

const publicBaseUrlRules = (t: Translate) => [{ required: true, whitespace: true, message: t('bucket.publicBaseUrlRequired') }, { validator: async (_: unknown, value?: string) => {
  if (value?.trim() && !validPublicBaseUrl(value.trim())) throw new Error(t('bucket.publicBaseUrlInvalid'));
} }];

/** 登记桶：桶由基础设施预先创建，这里只登记名称与访问级别；公开桶须登记对外地址。登记后为草稿，检查通过后才能启用。 */
export function BucketModal({ instances, defaultInstance, onClose, onDone, onAuthorizationChanged }: CommandProps & {
  instances: StorageInstance[]; defaultInstance?: string;
}) {
  const [form] = Form.useForm<BucketFormValues>(); const command = useStorageCommand(onAuthorizationChanged, form);
  const selectable = instances.filter(instance => instance.status !== 'DISABLED');
  const accessLevel = Form.useWatch('accessLevel', form) as AccessLevel | undefined;
  const t = useT('file'), tCommon = useT('common');
  async function submit() {
    const values = await form.validateFields();
    const body = registerBucketBody(values);
    const result = await command.run(`${instancePath(values.instanceCode ?? '')}/buckets`, 'POST', body,
      value => decodeMutation(value, bucketObjectCode(values.instanceCode ?? '', body.bucketName)), t('bucket.registerFailed'));
    if (result) onDone();
  }
  return <Modal open title={t('bucket.registerTitle')} okText={t('bucket.registerOk')} cancelText={tCommon('action.cancel')} confirmLoading={command.saving} onOk={() => void submit()}
    onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }} destroyOnHidden>
    <div className="notification-note">{t('bucket.registerNote')}</div>
    <Form form={form} layout="vertical" disabled={command.saving} initialValues={{ instanceCode: defaultInstance, accessLevel: 'PRIVATE', reason: '' }}>
      <Form.Item name="instanceCode" label={t('bucket.instance')} rules={[{ required: true, message: t('bucket.instanceRequired') }]}>
        <Select options={selectable.map(instance => ({ value: instance.instanceCode, label: instance.instanceCode }))} /></Form.Item>
      <Form.Item name="bucketName" label={t('bucket.name')} rules={[{ required: true, message: t('bucket.nameRequired') },
        { pattern: bucketNamePattern, message: t('bucket.namePattern') }]}>
        <Input maxLength={63} className="mono" /></Form.Item>
      <Form.Item name="accessLevel" label={t('bucket.accessLevel')} extra={accessLevel === 'PUBLIC' ? t('bucket.accessLevelHintPublic') : t('bucket.accessLevelHintPrivate')}>
        <Radio.Group options={accessLevels.map(value => ({ value, label: accessLevelLabel(value) }))} /></Form.Item>
      {accessLevel === 'PUBLIC' && <Form.Item name="publicBaseUrl" label={t('bucket.publicBaseUrl')} rules={publicBaseUrlRules(t)}
        extra={t('bucket.publicBaseUrlHint')}>
        <Input maxLength={publicBaseUrlMaxLength} placeholder="https://" className="mono" /></Form.Item>}
      <ReasonItem />
    </Form>
    <ConflictBanner conflict={command.conflict} />
  </Modal>;
}

/** 修改草稿公开桶的对外地址；改完需要重新检查才能启用。 */
export function BucketUrlModal({ bucket, onClose, onDone, onAuthorizationChanged }: CommandProps & { bucket: BucketRow }) {
  const [form] = Form.useForm<{ publicBaseUrl: string; reason: string }>(); const command = useStorageCommand(onAuthorizationChanged, form);
  const t = useT('file'), tCommon = useT('common');
  async function submit() {
    const values = await form.validateFields();
    const result = await command.run(bucketPath(bucket.instanceCode, bucket.bucketName), 'PUT', updateBucketBody(values, bucket),
      value => decodeMutation(value, bucketObjectCode(bucket.instanceCode, bucket.bucketName)), t('bucket.urlSaveFailed'));
    if (result) onDone();
  }
  return <Modal open title={t('bucket.urlTitle', { instanceCode: bucket.instanceCode, bucketName: bucket.bucketName })} okText={tCommon('action.save')} cancelText={tCommon('action.cancel')} confirmLoading={command.saving}
    okButtonProps={{ disabled: command.conflict }} onOk={() => void submit()} onCancel={() => { if (!command.saving) onClose(); }}
    mask={{ closable: !command.saving }} destroyOnHidden>
    <div className="notification-note">{t('bucket.urlNote')}</div>
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict} initialValues={{ publicBaseUrl: bucket.publicBaseUrl ?? '', reason: '' }}>
      <Form.Item name="publicBaseUrl" label={t('bucket.publicBaseUrl')} rules={publicBaseUrlRules(t)}>
        <Input maxLength={publicBaseUrlMaxLength} placeholder="https://" className="mono" /></Form.Item>
      <ReasonItem />
    </Form>
    <ConflictBanner conflict={command.conflict} />
  </Modal>;
}

/** 检查未通过时的排查指引（静态文案）：公开桶给出 AWS 公开只读桶策略示例，私有桶说明须关闭匿名读取。 */
function CheckGuidance({ bucket, result }: { bucket: BucketRow; result: BucketCheck }) {
  const t = useT('file');
  if (result.result === 'PUBLIC_READ_FAILED') return <div className="storage-check-guidance">
    <p>{publicReadGuidance()}</p>
    <div className="muted">{t('check.policyExampleTitle')}</div>
    <Typography.Paragraph className="mono storage-policy-example" copyable={{ text: publicReadPolicyExample(bucket.bucketName), tooltips: [t('check.copy'), t('check.copied')] }}>
      {publicReadPolicyExample(bucket.bucketName)}</Typography.Paragraph>
  </div>;
  if (result.result === 'PUBLIC_ACCESS_ALLOWED') return <div className="storage-check-guidance"><p>{privateAccessGuidance()}</p></div>;
  return null;
}

/** 桶检查：事务外访问存储，结果写回桶并显示在弹窗内；检查通过后 10 分钟内可启用。未通过时给出排查指引。 */
export function BucketCheckModal({ bucket, onEditUrl, onClose, onDone, onAuthorizationChanged }: CommandProps & {
  bucket: BucketRow; onEditUrl?: () => void;
}) {
  const { instanceCode, bucketName } = bucket;
  const command = useStorageCommand(onAuthorizationChanged); const [result, setResult] = useState<BucketCheck | null>(null);
  const t = useT('file');
  async function check() {
    setResult(null);
    const checked = await command.run(`${bucketPath(instanceCode, bucketName)}/check`, 'POST', null,
      value => decodeBucketCheck(value, instanceCode, bucketName), t('check.failed'));
    if (checked) { setResult(checked); onDone(); }
  }
  const failed = result && result.result !== 'OK';
  return <Modal open title={t('check.title', { instanceCode, bucketName })} width={640} onCancel={() => { if (!command.saving) onClose(); }}
    mask={{ closable: !command.saving }} destroyOnHidden footer={[
      <Button key="close" disabled={command.saving} onClick={onClose}>{t('check.close')}</Button>,
      ...(failed && onEditUrl && result.result === 'PUBLIC_READ_FAILED'
        ? [<Button key="url" disabled={command.saving} onClick={onEditUrl}>{t('bucket.editUrl')}</Button>] : []),
      <Button key="check" type="primary" loading={command.saving} onClick={() => void check()}>{result ? t('check.recheck') : t('check.start')}</Button>]}>
    <div className="storage-check-head"><Tag color={bucket.accessLevel === 'PUBLIC' ? 'blue' : 'default'}>{accessLevelLabel(bucket.accessLevel)}</Tag>
      {bucket.publicBaseUrl && <span className="muted">{t('bucket.publicBaseUrl')} <span className="mono">{bucket.publicBaseUrl}</span></span>}</div>
    <div className="muted" style={{ marginBottom: 12 }}>{bucket.accessLevel === 'PUBLIC' ? t('check.stepsPublic') : t('check.stepsPrivate')}</div>
    {result && <Alert type={result.result === 'OK' ? 'success' : 'error'} showIcon
      title={result.result === 'OK' ? checkResultLabel('OK') : bucket.status === 'DRAFT'
        ? t('check.failedResultDraft', { result: checkResultLabel(result.result) }) : t('check.failedResult', { result: checkResultLabel(result.result) })}
      description={result.result === 'OK' ? t('check.passedAt', { time: formatDateTime(result.checkedAt), minutes: bucketCheckValidMillis / 60_000 })
        : t('check.failedAt', { time: formatDateTime(result.checkedAt) })} />}
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
  const t = useT('file'), tCommon = useT('common');
  async function submit() {
    const current = await form.validateFields();
    const result = route
      ? await command.run(routePath(route.sourceSystem, route.accessLevel), 'PUT', updateRouteBody(current, route),
        value => decodeMutation(value, routeObjectCode(route.sourceSystem, route.accessLevel)), t('route.saveFailed'))
      : await command.run(`${storageAdminPath}/routes`, 'POST', createRouteBody(current),
        value => decodeMutation(value, routeObjectCode((current.sourceSystem ?? '').trim(), current.accessLevel ?? 'PRIVATE')), t('route.createFailed'));
    if (result) onDone();
  }
  const policyOptions = [
    ...(route && !selectable.some(item => item.policyCode === route.scanPolicyCode)
      ? [{ value: route.scanPolicyCode, label: t('route.policyCurrentInactive', { code: route.scanPolicyCode }), disabled: true }] : []),
    ...policies.items.map(item => ({ value: item.policyCode, disabled: item.activeRevisionNo === null,
      label: item.activeRevisionNo === null ? t('route.policyInactive', { code: item.policyCode })
        : `${item.policyCode} r${item.activeRevisionNo} · ${joinList(item.allowedMimeTypes.map(mimeTypeLabel))} · ≤ ${byteSizeLabel(item.maxSizeBytes)}` })),
  ];
  const target = values?.target ? values.target.replace('/', ' / ') : null;
  return <Modal open title={route ? t('route.editTitle', { source: sourceSystemLabel(route.sourceSystem), level: accessLevelLabel(route.accessLevel) }) : t('route.createTitle')}
    okText={tCommon('action.save')} cancelText={tCommon('action.cancel')} width={600} confirmLoading={command.saving} okButtonProps={{ disabled: command.conflict }} onOk={() => void submit()}
    onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }} destroyOnHidden>
    <div className="notification-note">{t('route.note')}</div>
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict} initialValues={{
      sourceSystem: route?.sourceSystem ?? sourceSystem, accessLevel: route?.accessLevel ?? accessLevel ?? 'PRIVATE',
      target: route ? targetValue(route.instanceCode, route.bucketName) : undefined,
      scanPolicyCode: route?.scanPolicyCode, maxUploadSeconds: route?.maxUploadSeconds ?? uploadSecondsRange.defaultValue, reason: '' }}>
      {route
        ? <div className="muted" style={{ marginBottom: 16 }}>{t('route.fixedInfo', { source: sourceSystemLabel(route.sourceSystem), level: accessLevelLabel(route.accessLevel) })}</div>
        : <>
          <Form.Item name="sourceSystem" label={t('route.sourceSystem')} extra={t('route.sourceSystemHint')}
            rules={[{ required: true, message: t('route.sourceSystemRequired') }, { validator: async (_, value?: string) => {
              if (value && value.trim() !== defaultSourceSystem && !sourceSystemPattern.test(value.trim())) throw new Error(t('route.sourceSystemPattern'));
            } }]}><Input maxLength={64} className="mono" disabled={!!sourceSystem} /></Form.Item>
          <Form.Item name="accessLevel" label={t('bucket.accessLevel')}>
            <Radio.Group disabled={!!accessLevel} onChange={() => form.setFieldValue('target', undefined)}
              options={accessLevels.map(value => ({ value, label: accessLevelLabel(value) }))} /></Form.Item>
        </>}
      <Form.Item name="target" label={t('route.bucket')} extra={t('route.bucketHint', { level })} rules={[{ required: true, message: t('route.bucketRequired') }]}>
        <Select options={targets.map(item => ({ value: targetValue(item.instanceCode, item.bucketName), label: `${item.instanceCode} / ${item.bucketName}` }))}
          notFoundContent={t('route.noBucket', { level })} /></Form.Item>
      <Form.Item name="scanPolicyCode" label={t('route.scanPolicy')} extra={t('route.scanPolicyHint')} rules={[{ required: true, message: t('route.scanPolicyRequired') }]}>
        <Select showSearch options={policyOptions} notFoundContent={t('route.noPolicy')} /></Form.Item>
      <Form.Item name="maxUploadSeconds" label={t('route.maxUploadSeconds')} extra={t('route.maxUploadSecondsHint', { min: uploadSecondsRange.min, max: uploadSecondsRange.max })}
        rules={[{ required: true, message: t('route.maxUploadSecondsRequired') }]}>
        <InputNumber min={uploadSecondsRange.min} max={uploadSecondsRange.max} precision={0} style={{ width: 200 }} /></Form.Item>
      <ReasonItem />
    </Form>
    {target && chosenPolicy?.activeRevisionNo && <div className="notification-note">{t('route.summary', {
      source: sourceSystemLabel((values?.sourceSystem ?? route?.sourceSystem ?? '').trim()), level, target, policy: chosenPolicy.policyCode,
      revisionNo: chosenPolicy.activeRevisionNo, seconds: values?.maxUploadSeconds ?? '—' })}</div>}
    <ConflictBanner conflict={command.conflict} />
  </Modal>;
}

/** 删除写入规则：默认私有规则不提供删除入口；删除后该来源该级别的新上传落到同级别默认规则（没有时返回 503）。 */
export function DeleteRouteModal({ route, onClose, onDone, onAuthorizationChanged }: CommandProps & { route: StorageRoute }) {
  const [form] = Form.useForm<{ reason: string }>(); const command = useStorageCommand(onAuthorizationChanged, form);
  const t = useT('file'), tCommon = useT('common');
  async function submit() {
    const { reason } = await form.validateFields();
    const result = await command.run(routePath(route.sourceSystem, route.accessLevel), 'DELETE', { expectedVersion: route.version, reason: reason.trim() },
      (value): StorageMutation => decodeMutation(value, routeObjectCode(route.sourceSystem, route.accessLevel)), t('route.deleteFailed'));
    if (result) onDone();
  }
  return <Modal open title={t('route.deleteTitle', { source: sourceSystemLabel(route.sourceSystem), level: accessLevelLabel(route.accessLevel) })}
    okText={t('route.confirmDelete')} cancelText={tCommon('action.cancel')} confirmLoading={command.saving}
    okButtonProps={{ danger: true, disabled: command.conflict }} onOk={() => void submit()}
    onCancel={() => { if (!command.saving) onClose(); }} mask={{ closable: !command.saving }} destroyOnHidden>
    <p>{t('route.deleteNotice', { source: sourceSystemLabel(route.sourceSystem), level: route.accessLevel })}</p>
    <Form form={form} layout="vertical" disabled={command.saving || command.conflict}><ReasonItem /></Form>
    <ConflictBanner conflict={command.conflict} />
  </Modal>;
}
