import { useState } from 'react';
import { Button, Input, Select } from 'antd';
import { useLocation, useNavigate } from 'react-router-dom';
import { changeTargetLabel, idPattern } from './channel';
import type { CallGrant, Channel, ChangeTargetType, ChannelAccountSummary } from './channel';
import { workspaceRoutes } from './channelWorkspace';
import { ChangeList, ChannelNote } from './ChannelParts';
import { useT } from '../i18n/react';

/** 按渠道类型可选的变更对象类型；协议对象的行 ID 由 Owner 管理，需手工输入。 */
const changeFilterTypes: Record<Channel['channelType'], ChangeTargetType[]> = {
  PAYMENT: ['CHANNEL', 'CHANNEL_ACCOUNT', 'CHANNEL_API', 'CHANNEL_SCRIPT', 'CHANNEL_IMPLEMENTATION'],
  SERVICE: ['CHANNEL', 'CHANNEL_ACCOUNT', 'CHANNEL_CALL_GRANT', 'CHANNEL_API', 'CHANNEL_SCRIPT', 'CHANNEL_IMPLEMENTATION'],
};
const manualIdTypes: readonly string[] = ['CHANNEL_API', 'CHANNEL_SCRIPT', 'CHANNEL_IMPLEMENTATION'];

/**
 * 变更记录页签：按目标类型与对象过滤（?targetType=&targetId=），授权「留痕」跳到这里时预置条件。
 * 渠道、技术账号 / 凭据与调用授权从本渠道数据中选择；协议对象按 Owner 行 ID 查询。
 */
export function ChannelChanges({ channel, accounts, grants }: { channel: Channel; accounts: ChannelAccountSummary[] | undefined; grants: CallGrant[] | undefined }) {
  const t = useT('channel');
  const location = useLocation(); const navigate = useNavigate();
  const query = new URLSearchParams(location.search);
  const types = changeFilterTypes[channel.channelType];
  const queryType = query.get('targetType') ?? 'CHANNEL';
  const targetType = (types as string[]).includes(queryType) ? queryType as ChangeTargetType : 'CHANNEL';
  const queryId = query.get('targetId');
  const targetId = targetType === 'CHANNEL' ? channel.channelId : queryId && idPattern.test(queryId) ? queryId : null;
  const [manualId, setManualId] = useState(manualIdTypes.includes(targetType) ? targetId ?? '' : '');
  const routes = workspaceRoutes(channel.channelId);
  const apply = (type: ChangeTargetType, id: string | null) => void navigate(type === 'CHANNEL' ? routes.tab('changes') : routes.changes(type, id ?? ''), { replace: true });
  const objectOptions = targetType === 'CHANNEL_ACCOUNT'
    ? (accounts ?? []).map(account => ({ value: account.channelAccountId, label: `${account.accountName} · ${account.channelAccountId}` }))
    : targetType === 'CHANNEL_CALL_GRANT'
      ? (grants ?? []).map(grant => ({ value: grant.callGrantId, label: `${grant.callerService} → ${grant.standardApiCode} · ${grant.callGrantId}` }))
      : [];
  return <>
    <div className="channel-section-head"><h2>{t('workspace.tab.changes')}</h2><span className="channel-spacer" />
      <Select aria-label={t('changes.targetType')} value={targetType} style={{ width: 180 }} options={types.map(value => ({ value, label: changeTargetLabel(value) }))}
        onChange={value => { setManualId(''); apply(value, null); }} />
      {(targetType === 'CHANNEL_ACCOUNT' || targetType === 'CHANNEL_CALL_GRANT') && <Select aria-label={t('changes.object')} showSearch optionFilterProp="label"
        value={targetId ?? undefined} placeholder={t('changes.selectObject')} style={{ width: 320 }} options={objectOptions} onChange={value => apply(targetType, value)} />}
      {manualIdTypes.includes(targetType) && <Input.Search aria-label={t('changes.objectId')} value={manualId} placeholder={t('changes.objectIdPlaceholder')} style={{ width: 260 }}
        onChange={event => setManualId(event.target.value.trim())} enterButton={t('changes.search')}
        onSearch={value => idPattern.test(value.trim()) && apply(targetType, value.trim())} />}
      {targetType !== 'CHANNEL' && <Button onClick={() => apply('CHANNEL', null)}>{t('changes.viewChannel')}</Button>}
    </div>
    {targetId ? <ChangeList key={`${targetType}:${targetId}`} targetType={targetType} targetId={targetId} />
      : <ChannelNote>{t('changes.pickObject')}</ChannelNote>}
    {manualIdTypes.includes(targetType) && <ChannelNote>{t('changes.manualIdNote')}</ChannelNote>}
  </>;
}
