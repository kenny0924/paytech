import { useState } from 'react';
import { Button, Input } from 'antd';
import { channelPaths, configEntryIssues, configurationBody, decodeAccount, localTime } from './channel';
import type { ChannelAccount, ChannelAccountSummary, ConfigEntry } from './channel';
import { ChannelNote, CommandModal } from './ChannelParts';
import { useChannelCommand } from './useChannelCommand';

interface Props { account: ChannelAccountSummary; onSaved: (account: ChannelAccount) => void; onClose: () => void; onAuthorizationChanged: () => void }

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
export function AccountConfigurationModal({ account, onSaved, onClose, onAuthorizationChanged }: Props) {
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
  return <CommandModal title={`账号配置 · ${account.accountName}（${account.entityNo}）`} command={command} okText="保存并加密" width={760}
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
