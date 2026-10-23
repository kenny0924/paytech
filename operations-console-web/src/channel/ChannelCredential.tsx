import { useState } from 'react';
import { Button, Empty, Form, Input } from 'antd';
import { useNavigate } from 'react-router-dom';
import { useT } from '../i18n/react';
import { channelPaths, decodeAccount, editable, lifecycleActions, localTime } from './channel';
import type { Channel, ChannelAccount, ChannelAccountSummary, LifecycleAction } from './channel';
import { workspaceRoutes } from './channelWorkspace';
import { AccountConfigurationModal } from './AccountConfigurationModal';
import { ChangeTrail, ChannelNote, CommandModal, StatusChangeModal, StatusTag } from './ChannelParts';
import { useChannelCommand } from './useChannelCommand';

interface Props {
  channel: Channel; accounts: ChannelAccountSummary[]; canManage: boolean; canConfigure: boolean; onAuthorizationChanged: () => void; onChanged: () => void;
}
type Step = { kind: 'create' } | { kind: 'configure'; account: ChannelAccountSummary } | { kind: 'status'; account: ChannelAccountSummary; action: LifecycleAction } | null;

/** 服务渠道的当前凭据：ACTIVE 优先，其次最近更新的未关闭记录。 */
export function currentCredential(accounts: readonly ChannelAccountSummary[]): ChannelAccountSummary | null {
  const open = accounts.filter(account => account.status !== 'CLOSED').toSorted((left, right) => right.updateTime.localeCompare(left.updateTime));
  return open.find(account => account.status === 'ACTIVE') ?? open[0] ?? null;
}

/**
 * 服务渠道 · 凭据（画板 05）：一份凭据、不绑定 OE；参数与凭据只写不读，只显示 Token 末 4 位。
 * 复用技术账号的接口与 Vault 结构：新建 = 创建不带 OE 的账号再填写；重新填写 = 整组替换；停用 / 恢复 = 账号状态。
 */
export function ChannelCredential({ channel, accounts, canManage, canConfigure, onAuthorizationChanged, onChanged }: Props) {
  const t = useT('channel');
  const navigate = useNavigate();
  const [step, setStep] = useState<Step>(null);
  const credential = currentCredential(accounts);
  const others = accounts.filter(account => account.status !== 'CLOSED' && account.channelAccountId !== credential?.channelAccountId);
  const routes = workspaceRoutes(channel.channelId);
  const done = () => { setStep(null); onChanged(); };
  return <div className="channel-credential">
    <div className="channel-section-head"><h2>{t('workspace.tab.credential')}</h2><span className="channel-cell-meta">{t('credential.single')}</span>
      <span className="channel-spacer" />
      {credential && editable(credential.status, canConfigure) && <Button type="primary" onClick={() => setStep({ kind: 'configure', account: credential })}>
        {credential.configured ? t('credential.refill') : t('credential.fill')}</Button>}</div>
    {!credential ? <div className="channel-panel channel-empty">
      <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={t('credential.empty')} />
      {editable(channel.status, canManage) && <Button type="primary" onClick={() => setStep({ kind: 'create' })}>{t('credential.create')}</Button>}
    </div> : <div className="channel-credential-layout">
      <section className="channel-panel">
        <div className="channel-title"><h3>{credential.accountName}</h3><span className="channel-cell-meta">{t('credential.accountId', { id: credential.channelAccountId })}</span>
          <StatusTag status={credential.status} /><span className="channel-spacer" />
          {lifecycleActions(credential.status, canManage).map(action => <Button key={action.targetStatus} size="small" danger={action.danger}
            onClick={() => setStep({ kind: 'status', account: credential, action })}>{action.targetStatus === 'SUSPENDED' ? t('credential.disable') : action.label}</Button>)}</div>
        <dl className="channel-kv-list">
          <div><dt>{t('credential.parameters')}</dt><dd>{t('credential.paramsNote')}</dd></div>
          <div><dt>{t('credential.writeOnlyLabel')}</dt><dd>{credential.configured
            ? <><span className="mono">••••••••••••{credential.vaultTokenSuffix ?? ''}</span> <span className="channel-cell-meta">{t('credential.configuredMeta')}</span></>
            : <span className="channel-cell-meta">{t('credential.notFilled')}</span>}</dd></div>
          <div><dt>{t('credential.lastFilled')}</dt><dd>{credential.configured ? localTime(credential.updateTime) : '—'}</dd></div>
          <div><dt>{t('credential.vaultDataType')}</dt><dd className="mono">CHANNEL_ACCOUNT_CONFIG_V1</dd></div>
          <div><dt>{t('credential.reader')}</dt><dd><span className="mono">channel-connector-service</span>{t('credential.readerWhen')}</dd></div>
        </dl>
        {others.length > 0 && <ChannelNote tone="warning">{t('credential.othersWarning', { count: others.length,
          records: others.map(item => `${item.accountName} · ${item.status}`).join(t('list.separator')) })}</ChannelNote>}
      </section>
      <aside className="channel-panel">
        <h3>{t('credential.writeOnly')}</h3>
        <ChannelNote>
          <div>{t('credential.note.protect')}</div>
          <div>{t('credential.note.noPlaintext')}</div>
          <div>{t('credential.note.reveal')}</div>
          <div>{t('credential.note.disabled')}</div>
        </ChannelNote>
        <h3>{t('credential.recentChanges')}</h3>
        <ChangeTrail key={`${credential.channelAccountId}:${credential.updateTime}`} targetType="CHANNEL_ACCOUNT" targetId={credential.channelAccountId} />
        <Button type="link" onClick={() => void navigate(routes.changes('CHANNEL_ACCOUNT', credential.channelAccountId))}>{t('credential.viewAllChanges')}</Button>
      </aside>
    </div>}
    {step?.kind === 'create' && <CreateCredentialModal channel={channel} onClose={() => setStep(null)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={account => { onChanged(); setStep(canConfigure ? { kind: 'configure', account } : null); }} />}
    {step?.kind === 'configure' && <AccountConfigurationModal account={step.account} credential={{ channelCode: channel.channelCode }}
      onClose={() => setStep(null)} onAuthorizationChanged={onAuthorizationChanged} onSaved={done} />}
    {step?.kind === 'status' && <StatusChangeModal title={t('credential.statusTitle', { action: step.action.targetStatus === 'SUSPENDED' ? t('credential.disable') : step.action.label, code: channel.channelCode })}
      path={channelPaths.accountStatus(step.account.channelAccountId)} targetStatus={step.action.targetStatus}
      actionLabel={step.action.targetStatus === 'SUSPENDED' ? t('credential.disable') : step.action.label}
      description={step.action.targetStatus === 'ACTIVE' ? t('credential.activateNote')
        : step.action.targetStatus === 'CLOSED' ? t('credential.closeNote') : t('credential.disableNote')}
      decode={value => decodeAccount(value, step.account.channelAccountId)} onClose={() => setStep(null)} onAuthorizationChanged={onAuthorizationChanged}
      onDone={done} />}
  </div>;
}

/** 新建凭据：服务渠道账号不带 OE；创建后直接进入填写。 */
function CreateCredentialModal({ channel, onCreated, onClose, onAuthorizationChanged }: {
  channel: Channel; onCreated: (account: ChannelAccount) => void; onClose: () => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('channel');
  const [form] = Form.useForm<{ accountName: string; remark?: string }>();
  const command = useChannelCommand(onAuthorizationChanged, form);
  const submit = async () => {
    const values = await form.validateFields();
    const account = await command.run(channelPaths.accounts, 'POST', { channelId: channel.channelId, accountName: values.accountName.trim(),
      remark: values.remark?.trim() || null }, value => decodeAccount(value), t('credential.createFailed'));
    if (account) onCreated(account);
  };
  return <CommandModal title={t('credential.createTitle', { code: channel.channelCode })} command={command} okText={t('credential.createAndFill')} onOk={() => void submit()} onClose={onClose} onReload={onClose}>
    <Form form={form} name="channel-credential-create" layout="vertical" initialValues={{ accountName: t('credential.defaultName', { code: channel.channelCode }) }}>
      <Form.Item name="accountName" label={t('form.name')} rules={[{ required: true, whitespace: true, message: t('form.required') }, { max: 128, message: t('form.maxLength', { max: 128 }) }]}>
        <Input maxLength={128} /></Form.Item>
      <Form.Item name="remark" label={t('form.remarkOptional')} rules={[{ max: 512, message: t('form.maxLength', { max: 512 }) }]}><Input.TextArea rows={2} maxLength={512} /></Form.Item>
    </Form>
    <ChannelNote>{t('credential.createNote')}</ChannelNote>
  </CommandModal>;
}
