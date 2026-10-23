import { useState } from 'react';
import { Button, Input, Steps } from 'antd';
import { useT } from '../i18n/react';
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
  const t = useT('channel');
  const update = (index: number, patch: Partial<ConfigEntry>) => onChange(entries.map((entry, i) => i === index ? { ...entry, ...patch } : entry));
  return <>
    {entries.map((entry, index) => <div className="channel-kv-row" key={index}>
      <Input aria-label={t('form.name')} placeholder={t('accountConfig.keyPlaceholder')} maxLength={64} value={entry.key} onChange={e => update(index, { key: e.target.value.trim() })} />
      {secret ? <Input.Password aria-label={t('accountConfig.value')} placeholder={t('accountConfig.credentialValue')} autoComplete="new-password" maxLength={8192} value={entry.value}
        onChange={e => update(index, { value: e.target.value })} visibilityToggle={false} />
        : <Input aria-label={t('accountConfig.value')} placeholder={t('accountConfig.parameterValue')} maxLength={8192} value={entry.value} onChange={e => update(index, { value: e.target.value })} />}
      <Button type="link" danger onClick={() => onChange(entries.filter((_, i) => i !== index))}>{t('endpointEditor.delete')}</Button>
    </div>)}
    <Button type="link" onClick={() => onChange([...entries, { key: '', value: '' }])} disabled={entries.length >= 64}>+ {addLabel}</Button>
  </>;
}

/**
 * 账号配置（画板 04）：参数与凭据整组提交，channel-connector 在 Vault 加密后只保存 Token
 * <li>Owner 不回显参数、凭据或其名称；已配置时只显示 Token 末 4 位，t('credential.refill')即重新录入整组参数与凭据，保存后生成新 Token。</li>
 */
export function AccountConfigurationModal({ account, onSaved, onClose, onAuthorizationChanged, credential }: Props) {
  if (credential) return <CredentialWizard account={account} onSaved={onSaved} onClose={onClose} onAuthorizationChanged={onAuthorizationChanged} channelCode={credential.channelCode} />;
  return <AccountConfiguration account={account} onSaved={onSaved} onClose={onClose} onAuthorizationChanged={onAuthorizationChanged} />;
}

function AccountConfiguration({ account, onSaved, onClose, onAuthorizationChanged }: Props) {
  const t = useT('channel');
  const command = useChannelCommand(onAuthorizationChanged);
  const [editing, setEditing] = useState(!account.configured);
  const [parameters, setParameters] = useState<ConfigEntry[]>([{ key: '', value: '' }]);
  const [credentials, setCredentials] = useState<ConfigEntry[]>([{ key: '', value: '' }]);
  const issue = configEntryIssues(parameters, t('credential.parameters')) ?? configEntryIssues(credentials, t('workspace.tab.credential'))
    ?? (parameters.length + credentials.length === 0 ? t('accountConfig.entryRequired') : null);
  const save = async () => {
    if (issue) return;
    const saved = await command.run(channelPaths.accountConfiguration(account.channelAccountId), 'PUT', configurationBody(parameters, credentials),
      decodeAccount, t('accountConfig.saveFailed'));
    if (saved) onSaved(saved);
  };
  return <CommandModal title={account.entityNo ? t('accountConfig.titleWithEntity', { name: account.accountName, entityNo: account.entityNo }) : t('accountConfig.title', { name: account.accountName })} command={command} okText={t('accountConfig.saveEncrypted')} width={760}
    okDisabled={!editing || !!issue} onOk={() => void save()} onClose={onClose} onReload={onClose}>
    <div className={`channel-config-state${account.configured ? '' : ' is-empty'}`}>
      <strong>{account.configured ? t('accountConfig.configured') : t('account.notConfigured')}</strong>
      <span>{account.configured ? t('accountConfig.configuredDetail', { suffix: account.vaultTokenSuffix ?? '—', time: localTime(account.updateTime) })
        : t('accountConfig.emptyDetail')}</span>
      {account.configured && !editing && <Button size="small" type="link" onClick={() => setEditing(true)}>{t('credential.refill')}</Button>}
    </div>
    {editing ? <>
      <div className="channel-kv-head"><strong>{t('credential.parameters')}</strong><span>{t('accountConfig.paramsHint')}</span></div>
      <EntryRows entries={parameters} secret={false} onChange={setParameters} addLabel={t('accountConfig.addParameter')} />
      <div className="channel-kv-head"><strong>{t('workspace.tab.credential')}</strong><span>{t('accountConfig.credentialsHint')}</span></div>
      <EntryRows entries={credentials} secret onChange={setCredentials} addLabel={t('accountConfig.addCredential')} />
      {issue && <ul className="channel-issues"><li>{issue}</li></ul>}
    </> : <ChannelNote>{t('accountConfig.savedNote')}</ChannelNote>}
    <ChannelNote>{t('accountConfig.saveNote')}</ChannelNote>
  </CommandModal>;
}

const wizardSteps = ['credential.parameters', 'workspace.tab.credential', 'accountConfig.wizard.confirm'] as const;

/**
 * 服务渠道凭据（画板 06）：一份凭据、不绑定 OE；参数与凭据分组逐步填写，确认页只列名称与输入长度，不回显值。
 * 提交仍是整组替换（与技术账号配置同一接口与 Vault 结构），保存后只显示新 Token 末 4 位。
 */
function CredentialWizard({ account, channelCode, onSaved, onClose, onAuthorizationChanged }: Omit<Props, 'credential'> & { channelCode: string }) {
  const t = useT('channel'), tCommon = useT('common');
  const command = useChannelCommand(onAuthorizationChanged);
  const [step, setStep] = useState(0);
  const [parameters, setParameters] = useState<ConfigEntry[]>([]);
  const [credentials, setCredentials] = useState<ConfigEntry[]>([{ key: '', value: '' }]);
  const stepIssue = step === 0 ? configEntryIssues(parameters, t('credential.parameters'))
    : step === 1 ? configEntryIssues(credentials, t('workspace.tab.credential')) ?? (parameters.length + credentials.length === 0 ? t('accountConfig.entryRequired') : null) : null;
  const save = async () => {
    const saved = await command.run(channelPaths.accountConfiguration(account.channelAccountId), 'PUT', configurationBody(parameters, credentials),
      decodeAccount, t('accountConfig.credentialSaveFailed'));
    if (saved) onSaved(saved);
  };
  const footer = <div className="channel-wizard-foot">
    <Button onClick={onClose} disabled={command.saving}>{tCommon('action.cancel')}</Button>
    {step > 0 && <Button onClick={() => setStep(step - 1)} disabled={command.saving}>{t('accountConfig.wizard.back')}</Button>}
    {step < 2 ? <Button type="primary" disabled={!!stepIssue} onClick={() => setStep(step + 1)}>{t('accountConfig.wizard.next')}</Button>
      : <Button type="primary" loading={command.saving} disabled={command.conflict} onClick={() => void save()}>{t('accountConfig.saveEncrypted')}</Button>}
  </div>;
  return <CommandModal title={account.configured ? t('accountConfig.wizard.refillTitle', { code: channelCode }) : t('accountConfig.wizard.fillTitle', { code: channelCode })} command={command} width={720} footer={footer}
    onOk={() => void save()} onClose={onClose} onReload={onClose}>
    <Steps size="small" current={step} items={wizardSteps.map(key => ({ title: t(key) }))} className="channel-wizard-steps" />
    {step === 0 && <>
      <div className="channel-kv-head"><strong>{t('credential.parameters')}</strong><span>{t('accountConfig.wizard.paramsHint')}</span></div>
      <EntryRows entries={parameters} secret={false} onChange={setParameters} addLabel={t('accountConfig.wizard.addParameter')} />
    </>}
    {step === 1 && <>
      <div className="channel-kv-head"><strong>{t('workspace.tab.credential')}</strong><span>{account.configured ? t('accountConfig.wizard.credentialHintCurrent', { suffix: account.vaultTokenSuffix ?? '' }) : t('accountConfig.wizard.credentialHint')}</span></div>
      <EntryRows entries={credentials} secret onChange={setCredentials} addLabel={t('accountConfig.wizard.addCredentialField')} />
    </>}
    {step === 2 && <div className="channel-wizard-confirm">
      <div><strong>{t('credential.parameters')}</strong>{parameters.length ? parameters.map(entry => <div key={entry.key} className="mono">{entry.key}</div>) : <div className="channel-cell-meta">{t('accountConfig.wizard.none')}</div>}</div>
      <div><strong>{t('workspace.tab.credential')}</strong>{credentials.map(entry => <div key={entry.key}><span className="mono">{entry.key}</span>
        <span className="channel-cell-meta"> {t('accountConfig.wizard.entered', { count: entry.value.length })}</span></div>)}</div>
    </div>}
    {stepIssue && <ul className="channel-issues"><li>{stepIssue}</li></ul>}
    <ChannelNote>
      <div>{t('accountConfig.wizard.note.replace')}</div>
      <div>{t('accountConfig.wizard.note.oldToken')}</div>
      <div>{t('accountConfig.wizard.note.writeOnly')}</div>
    </ChannelNote>
  </CommandModal>;
}
