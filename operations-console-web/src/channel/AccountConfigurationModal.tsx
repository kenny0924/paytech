import { useState } from 'react';
import { Button, Input, Steps } from 'antd';
import { channelPaths, configEntryIssues, configurationBody, decodeAccount, localTime } from './channel';
import type { ChannelAccount, ChannelAccountSummary, ConfigEntry } from './channel';
import { ChannelNote, CommandModal } from './ChannelParts';
import { useChannelCommand } from './useChannelCommand';

interface Props {
  account: ChannelAccountSummary; onSaved: (account: ChannelAccount) => void; onClose: () => void; onAuthorizationChanged: () => void;
  /** 服务渠道的「凭据」外形（画板 06）：参数 → 凭据 → 确认三步，标题不显示 OE。 */
  credential?: { channelCode: string };
}

/** 一组键值行：参数为明文输入，凭据为密码输入（不回显、不自动填充）。 */
function EntryRows({ entries, secret, onChange, addLabel }: { entries: ConfigEntry[]; secret: boolean; onChange: (entries: ConfigEntry[]) => void; addLabel: string }) {
  const update = (index: number, patch: Partial<ConfigEntry>) => onChange(entries.map((entry, i) => i === index ? { ...entry, ...patch } : entry));
  return <>
    {entries.map((entry, index) => <div className="channel-kv-row" key={index}>
      <Input aria-label="名称" placeholder="名称，如 merchantId" maxLength={64} value={entry.key} onChange={e => update(index, { key: e.target.value.trim() })} />
      {secret ? <Input.Password aria-label="值" placeholder="凭据值" autoComplete="new-password" maxLength={8192} value={entry.value}
        onChange={e => update(index, { value: e.target.value })} visibilityToggle={false} />
        : <Input aria-label="值" placeholder="参数值" maxLength={8192} value={entry.value} onChange={e => update(index, { value: e.target.value })} />}
      <Button type="link" danger onClick={() => onChange(entries.filter((_, i) => i !== index))}>删除</Button>
    </div>)}
    <Button type="link" onClick={() => onChange([...entries, { key: '', value: '' }])} disabled={entries.length >= 64}>+ {addLabel}</Button>
  </>;
}

/**
 * 账号配置（画板 04）：参数与凭据整组提交，channel-connector 在 Vault 加密后只保存 Token
 * <li>Owner 不回显参数、凭据或其名称；已配置时只显示 Token 末 4 位，"重新填写"即重新录入整组参数与凭据，保存后生成新 Token。</li>
 */
export function AccountConfigurationModal({ account, onSaved, onClose, onAuthorizationChanged, credential }: Props) {
  if (credential) return <CredentialWizard account={account} onSaved={onSaved} onClose={onClose} onAuthorizationChanged={onAuthorizationChanged} channelCode={credential.channelCode} />;
  return <AccountConfiguration account={account} onSaved={onSaved} onClose={onClose} onAuthorizationChanged={onAuthorizationChanged} />;
}

function AccountConfiguration({ account, onSaved, onClose, onAuthorizationChanged }: Props) {
  const command = useChannelCommand(onAuthorizationChanged);
  const [editing, setEditing] = useState(!account.configured);
  const [parameters, setParameters] = useState<ConfigEntry[]>([{ key: '', value: '' }]);
  const [credentials, setCredentials] = useState<ConfigEntry[]>([{ key: '', value: '' }]);
  const issue = configEntryIssues(parameters, '参数') ?? configEntryIssues(credentials, '凭据')
    ?? (parameters.length + credentials.length === 0 ? '参数与凭据至少填写一项' : null);
  const save = async () => {
    if (issue) return;
    const saved = await command.run(channelPaths.accountConfiguration(account.channelAccountId), 'PUT', configurationBody(parameters, credentials),
      decodeAccount, '账号配置保存失败');
    if (saved) onSaved(saved);
  };
  return <CommandModal title={`账号配置 · ${account.accountName}${account.entityNo ? `（${account.entityNo}）` : ''}`} command={command} okText="保存并加密" width={760}
    okDisabled={!editing || !!issue} onOk={() => void save()} onClose={onClose} onReload={onClose}>
    <div className={`channel-config-state${account.configured ? '' : ' is-empty'}`}>
      <strong>{account.configured ? '已配置' : '未配置'}</strong>
      <span>{account.configured ? `Token 末 4 位 ${account.vaultTokenSuffix ?? '—'} · 最近更新 ${localTime(account.updateTime)} · 重新保存将生成新 Token，旧 Token 不再使用`
        : '尚未写入参数与凭据，账号不能激活'}</span>
      {account.configured && !editing && <Button size="small" type="link" onClick={() => setEditing(true)}>重新填写</Button>}
    </div>
    {editing ? <>
      <div className="channel-kv-head"><strong>参数</strong><span>非敏感业务参数，如 outletRef、merchantId；键 1–64 位，值 ≤ 8192</span></div>
      <EntryRows entries={parameters} secret={false} onChange={setParameters} addLabel="添加参数" />
      <div className="channel-kv-head"><strong>凭据</strong><span>渠道 API 密钥；保存后在 Vault 加密，页面永不回显</span></div>
      <EntryRows entries={credentials} secret onChange={setCredentials} addLabel="添加凭据" />
      {issue && <ul className="channel-issues"><li>{issue}</li></ul>}
    </> : <ChannelNote>已保存的参数与凭据不会回显。需要更换时点击「重新填写」，并完整录入全部参数与凭据（本次提交整体替换原配置）。</ChannelNote>}
    <ChannelNote>保存时 channel-connector 用 CHANNEL_ACCOUNT_CONFIG_V1 结构整体加密并写入账号的 Vault 引用；需要「账号凭据」按钮权限。</ChannelNote>
  </CommandModal>;
}

const wizardSteps = ['参数', '凭据', '确认'] as const;

/**
 * 服务渠道凭据（画板 06）：一份凭据、不绑定 OE；参数与凭据分组逐步填写，确认页只列名称与输入长度，不回显值。
 * 提交仍是整组替换（与技术账号配置同一接口与 Vault 结构），保存后只显示新 Token 末 4 位。
 */
function CredentialWizard({ account, channelCode, onSaved, onClose, onAuthorizationChanged }: Omit<Props, 'credential'> & { channelCode: string }) {
  const command = useChannelCommand(onAuthorizationChanged);
  const [step, setStep] = useState(0);
  const [parameters, setParameters] = useState<ConfigEntry[]>([]);
  const [credentials, setCredentials] = useState<ConfigEntry[]>([{ key: '', value: '' }]);
  const stepIssue = step === 0 ? configEntryIssues(parameters, '参数')
    : step === 1 ? configEntryIssues(credentials, '凭据') ?? (parameters.length + credentials.length === 0 ? '参数与凭据至少填写一项' : null) : null;
  const save = async () => {
    const saved = await command.run(channelPaths.accountConfiguration(account.channelAccountId), 'PUT', configurationBody(parameters, credentials),
      decodeAccount, '凭据保存失败');
    if (saved) onSaved(saved);
  };
  const footer = <div className="channel-wizard-foot">
    <Button onClick={onClose} disabled={command.saving}>取消</Button>
    {step > 0 && <Button onClick={() => setStep(step - 1)} disabled={command.saving}>上一步</Button>}
    {step < 2 ? <Button type="primary" disabled={!!stepIssue} onClick={() => setStep(step + 1)}>下一步</Button>
      : <Button type="primary" loading={command.saving} disabled={command.conflict} onClick={() => void save()}>保存并加密</Button>}
  </div>;
  return <CommandModal title={`${account.configured ? '重新填写' : '填写'}凭据 · ${channelCode}`} command={command} width={720} footer={footer}
    onOk={() => void save()} onClose={onClose} onReload={onClose}>
    <Steps size="small" current={step} items={wizardSteps.map(title => ({ title }))} className="channel-wizard-steps" />
    {step === 0 && <>
      <div className="channel-kv-head"><strong>参数</strong><span>非敏感参数（如 region、language），原子 API 以 ${'{'}account.params.名称{'}'} 引用；可不填</span></div>
      <EntryRows entries={parameters} secret={false} onChange={setParameters} addLabel="增加参数" />
    </>}
    {step === 1 && <>
      <div className="channel-kv-head"><strong>凭据</strong><span>{account.configured ? `当前 Token …${account.vaultTokenSuffix ?? ''}；` : ''}必须完整输入，值不回显</span></div>
      <EntryRows entries={credentials} secret onChange={setCredentials} addLabel="增加凭据字段" />
    </>}
    {step === 2 && <div className="channel-wizard-confirm">
      <div><strong>参数</strong>{parameters.length ? parameters.map(entry => <div key={entry.key} className="mono">{entry.key}</div>) : <div className="channel-cell-meta">无</div>}</div>
      <div><strong>凭据</strong>{credentials.map(entry => <div key={entry.key}><span className="mono">{entry.key}</span>
        <span className="channel-cell-meta"> 已输入 {entry.value.length} 位</span></div>)}</div>
    </div>}
    {stepIssue && <ul className="channel-issues"><li>{stepIssue}</li></ul>}
    <ChannelNote>
      <div>• 保存后立即替换：新凭据保存成功后，下一次执行即使用新值；进行中的调用不受影响。</div>
      <div>• 旧 Token 保留在 Vault 供审计，不再被读取；本次提交整体替换原参数与凭据。</div>
      <div>• 凭据只写不读：保存后页面只显示新 Token 的末 4 位。字段名只允许字母、数字与 _ . -。</div>
    </ChannelNote>
  </CommandModal>;
}
