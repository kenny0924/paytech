import { useState } from 'react';
import { Button, Empty, Form, Input } from 'antd';
import { useNavigate } from 'react-router-dom';
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
  const navigate = useNavigate();
  const [step, setStep] = useState<Step>(null);
  const credential = currentCredential(accounts);
  const others = accounts.filter(account => account.status !== 'CLOSED' && account.channelAccountId !== credential?.channelAccountId);
  const routes = workspaceRoutes(channel.channelId);
  const done = () => { setStep(null); onChanged(); };
  return <div className="channel-credential">
    <div className="channel-section-head"><h2>凭据</h2><span className="channel-cell-meta">服务渠道只有一份凭据，不绑定 OE</span>
      <span className="channel-spacer" />
      {credential && editable(credential.status, canConfigure) && <Button type="primary" onClick={() => setStep({ kind: 'configure', account: credential })}>
        {credential.configured ? '重新填写' : '填写凭据'}</Button>}</div>
    {!credential ? <div className="channel-panel channel-empty">
      <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="还没有凭据。外部服务不需要鉴权时可以不配置。" />
      {editable(channel.status, canManage) && <Button type="primary" onClick={() => setStep({ kind: 'create' })}>新建凭据</Button>}
    </div> : <div className="channel-credential-layout">
      <section className="channel-panel">
        <div className="channel-title"><h3>{credential.accountName}</h3><span className="channel-cell-meta">账号 ID {credential.channelAccountId}</span>
          <StatusTag status={credential.status} /><span className="channel-spacer" />
          {lifecycleActions(credential.status, canManage).map(action => <Button key={action.targetStatus} size="small" danger={action.danger}
            onClick={() => setStep({ kind: 'status', account: credential, action })}>{action.targetStatus === 'SUSPENDED' ? '停用' : action.label}</Button>)}</div>
        <dl className="channel-kv-list">
          <div><dt>参数</dt><dd>与凭据同组加密保存，Owner 不回显名称与值；原子 API 以 ${'{'}account.params.名称{'}'} 引用</dd></div>
          <div><dt>凭据（只写不读）</dt><dd>{credential.configured
            ? <><span className="mono">••••••••••••{credential.vaultTokenSuffix ?? ''}</span> <span className="channel-cell-meta">已配置 · Vault Token 保存，仅末 4 位</span></>
            : <span className="channel-cell-meta">未填写；未配置时不能激活</span>}</dd></div>
          <div><dt>最近填写</dt><dd>{credential.configured ? localTime(credential.updateTime) : '—'}</dd></div>
          <div><dt>Vault 数据类型</dt><dd className="mono">CHANNEL_ACCOUNT_CONFIG_V1</dd></div>
          <div><dt>读取方</dt><dd><span className="mono">channel-connector-service</span>（执行时）</dd></div>
        </dl>
        {others.length > 0 && <ChannelNote tone="warning">另有 {others.length} 条未关闭的凭据记录（{others.map(item => `${item.accountName} · ${item.status}`).join('、')}）；
          同一时间只允许 1 条 ACTIVE，激活第二条会被拒绝。</ChannelNote>}
      </section>
      <aside className="channel-panel">
        <h3>只写不读</h3>
        <ChannelNote>
          <div>• 凭据在 channel-connector 事务外 Protect 到 Vault，库中只存 Token 与末 4 位。</div>
          <div>• 页面、接口响应与日志都不出现明文；重新填写时必须完整输入，不提供「显示」按钮。</div>
          <div>• 执行时由 channel-connector 以自身身份 Reveal，只在内存中使用。</div>
          <div>• 凭据被停用时，通用入口对该渠道不提供账号材料，原子 API 引用凭据的调用会失败且不发出请求。</div>
        </ChannelNote>
        <h3>最近变更</h3>
        <ChangeTrail key={`${credential.channelAccountId}:${credential.updateTime}`} targetType="CHANNEL_ACCOUNT" targetId={credential.channelAccountId} />
        <Button type="link" onClick={() => void navigate(routes.changes('CHANNEL_ACCOUNT', credential.channelAccountId))}>查看全部变更记录 →</Button>
      </aside>
    </div>}
    {step?.kind === 'create' && <CreateCredentialModal channel={channel} onClose={() => setStep(null)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={account => { onChanged(); setStep(canConfigure ? { kind: 'configure', account } : null); }} />}
    {step?.kind === 'configure' && <AccountConfigurationModal account={step.account} credential={{ channelCode: channel.channelCode }}
      onClose={() => setStep(null)} onAuthorizationChanged={onAuthorizationChanged} onSaved={done} />}
    {step?.kind === 'status' && <StatusChangeModal title={`${step.action.targetStatus === 'SUSPENDED' ? '停用' : step.action.label}凭据 · ${channel.channelCode}`}
      path={channelPaths.accountStatus(step.account.channelAccountId)} targetStatus={step.action.targetStatus}
      actionLabel={step.action.targetStatus === 'SUSPENDED' ? '停用' : step.action.label}
      description={step.action.targetStatus === 'ACTIVE' ? '激活要求渠道为 ACTIVE 且凭据已填写；服务渠道同一时间只允许 1 份 ACTIVE 凭据。'
        : step.action.targetStatus === 'CLOSED' ? '关闭为终态；关闭后可新建一份凭据。' : '停用后通用入口不再提供账号材料，引用凭据的调用会失败且不发出请求。'}
      decode={value => decodeAccount(value, step.account.channelAccountId)} onClose={() => setStep(null)} onAuthorizationChanged={onAuthorizationChanged}
      onDone={done} />}
  </div>;
}

/** 新建凭据：服务渠道账号不带 OE；创建后直接进入填写。 */
function CreateCredentialModal({ channel, onCreated, onClose, onAuthorizationChanged }: {
  channel: Channel; onCreated: (account: ChannelAccount) => void; onClose: () => void; onAuthorizationChanged: () => void;
}) {
  const [form] = Form.useForm<{ accountName: string; remark?: string }>();
  const command = useChannelCommand(onAuthorizationChanged, form);
  const submit = async () => {
    const values = await form.validateFields();
    const account = await command.run(channelPaths.accounts, 'POST', { channelId: channel.channelId, accountName: values.accountName.trim(),
      remark: values.remark?.trim() || null }, value => decodeAccount(value), '凭据创建失败');
    if (account) onCreated(account);
  };
  return <CommandModal title={`新建凭据 · ${channel.channelCode}`} command={command} okText="创建并填写" onOk={() => void submit()} onClose={onClose} onReload={onClose}>
    <Form form={form} name="channel-credential-create" layout="vertical" initialValues={{ accountName: `${channel.channelCode} 凭据` }}>
      <Form.Item name="accountName" label="名称" rules={[{ required: true, whitespace: true, message: '必填' }, { max: 128, message: '最长 128' }]}>
        <Input maxLength={128} /></Form.Item>
      <Form.Item name="remark" label="备注（可选）" rules={[{ max: 512, message: '最长 512' }]}><Input.TextArea rows={2} maxLength={512} /></Form.Item>
    </Form>
    <ChannelNote>新建为 DRAFT 且未填写；填写参数与凭据并在渠道激活后才能激活。服务渠道的凭据不绑定 OE。</ChannelNote>
  </CommandModal>;
}
