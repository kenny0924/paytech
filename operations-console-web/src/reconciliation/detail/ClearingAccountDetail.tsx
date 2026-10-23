import { useMemo, useState } from 'react';
import { App, Button, Descriptions, Form, Popconfirm, Select, Table } from 'antd';
import { Link } from 'react-router-dom';
import type { ConsoleResource } from '../../api/types';
import { localTime } from '../../finance/finance';
import { FinanceNote } from '../../finance/FinanceParts';
import { useT } from '../../i18n/react';
import { ClearingSettingsFields, settingsBody } from '../ClearingSettings';
import type { ClearingSettingsValues } from '../ClearingSettings';
import { DetailSection, ReconDetailPage } from '../ReconDetail';
import { clearingStatusEnum, decodeClearingAccount, hasReconAction, reconActions, reconPaths } from '../reconModel';
import type { ClearingAccount } from '../reconModel';
import { EmptyHint, Money, ReconCommandFeedback, ReconTag } from '../ReconParts';
import { isReconId, reconRoutes, withQuery } from '../reconRoutes';
import { useCashAccounts, useUnassignedMids } from '../useReconData';
import { useReconCommand } from '../useReconCommand';
import { useReconDetail } from '../useReconDetail';

interface Props { clearingAccountId: string; resources: ConsoleResource[]; onAuthorizationChanged: () => void }

/**
 * 清算账户详情页（/finance/reconciliation/clearing-accounts/:clearingAccountId）：基本信息、配置、所含 MID（挂 / 摘）、相关账单入口、启停
 * <li>维护按钮按「对账操作」按钮资源显示；每次写操作后重新读取详情与未归属 MID。</li>
 */
export function ClearingAccountDetail({ clearingAccountId, resources, onAuthorizationChanged }: Props) {
  const t = useT('recon');
  const { message } = App.useApp();
  const canOperate = hasReconAction(resources, reconActions.operate);
  const valid = isReconId(clearingAccountId);
  const decode = useMemo(() => decodeClearingAccount(clearingAccountId), [clearingAccountId]);
  const detail = useReconDetail<ClearingAccount>(valid ? reconPaths.clearingAccount(clearingAccountId) : null, decode, onAuthorizationChanged, t('clearingDetail.loadFailed'));
  const [reload, setReload] = useState(0);
  const cash = useCashAccounts(onAuthorizationChanged);
  const unassigned = useUnassignedMids(onAuthorizationChanged, reload);
  const [form] = Form.useForm<ClearingSettingsValues>();
  const command = useReconCommand(onAuthorizationChanged, form);
  const [midId, setMidId] = useState<string>();
  async function act(path: string, method: 'POST' | 'PUT' | 'DELETE', body: unknown, success: string, failure: string) {
    const result = await command.run(path, method, body, decodeClearingAccount(clearingAccountId), failure);
    if (!result) return;
    void message.success(success); setMidId(undefined); detail.reload(); setReload(value => value + 1);
  }
  const item = detail.item;
  const statusAction = item && canOperate && <Popconfirm title={item.status === 'ACTIVE' ? t('clearingDetail.confirmDisable') : t('clearingDetail.confirmEnable')}
    okText={t('common.confirm')} cancelText={t('common.cancel')} onConfirm={() => void act(reconPaths.clearingStatus(item.clearingAccountId), 'POST',
      { status: item.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE' }, item.status === 'ACTIVE' ? t('clearingDetail.disabled') : t('clearingDetail.enabled'), t('clearingDetail.statusFailed'))}>
    <Button danger={item.status === 'ACTIVE'} loading={command.saving}>{item.status === 'ACTIVE' ? t('action.disable') : t('action.enable')}</Button></Popconfirm>;
  return <ReconDetailPage list={{ label: t('clearing.listLabel'), route: reconRoutes.clearingAccounts }} objectLabel={t('clearing.object')} state={detail}
    title={item ? <>{t('clearingDetail.titlePrefix')}<span className="mono">{item.clearingAccountCode}</span></> : null} actions={statusAction}>
    {account => {
      const candidates = unassigned.items.filter(mid => mid.channelCode === account.channelCode && mid.entityNo === account.entityNo && mid.currency === account.currency);
      const settlement = account.settlementCashAccountId ? cash.byId.get(account.settlementCashAccountId) : undefined;
      return <>
        <ReconCommandFeedback command={command} onReload={() => { command.clearConflict(); detail.reload(); }} />
        <Descriptions size="small" column={3} items={[
          { key: 'channel', label: t('field.channel'), children: account.channelCode }, { key: 'entity', label: t('field.operatingEntity'), children: account.entityNo },
          { key: 'currency', label: t('field.currency'), children: account.currency },
          { key: 'status', label: t('field.status'), children: <ReconTag status={account.status} kind={clearingStatusEnum} /> },
          { key: 'cash', label: t('clearingSettings.settlementCashAccount'), children: account.settlementCashAccountId
            ? <Link to={reconRoutes.cashAccount(account.settlementCashAccountId)}>{settlement ? t('cash.nameWithLast4', { name: settlement.accountName, last4: settlement.accountLast4 }) : account.settlementCashAccountId}</Link>
            : <span className="muted">{t('clearingDetail.noSettlementAccount')}</span> },
          { key: 'tolerance', label: t('clearingSettings.costTolerance'), children: <Money value={account.costToleranceAmount} /> },
          { key: 'id', label: t('field.id'), children: <span className="mono">{account.clearingAccountId}</span> },
          { key: 'version', label: t('field.version'), children: account.version },
          { key: 'updated', label: t('field.updatedAt'), children: localTime(account.updateTime) },
        ]} />
        <DetailSection title={t('detail.related')}>
          <div className="recon-link-list">
            <Link to={withQuery(reconRoutes.statements, { clearingAccountId: account.clearingAccountId })}>{t('link.statements')}</Link>
            <Link to={withQuery(reconRoutes.channelLines, { clearingAccountId: account.clearingAccountId })}>{t('link.channelLines')}</Link>
            <Link to={withQuery(reconRoutes.platformTxns, { clearingAccountId: account.clearingAccountId })}>{t('link.platformTxns')}</Link>
            <Link to={withQuery(reconRoutes.exceptions, { clearingAccountId: account.clearingAccountId })}>{t('link.openExceptions')}</Link>
          </div>
        </DetailSection>
        <DetailSection title={t('clearingDetail.settings')}>
          <Form form={form} name="recon-clearing-settings" layout="vertical" disabled={!canOperate || command.saving} key={account.version}
            initialValues={{ settlementCashAccountId: account.settlementCashAccountId ?? undefined, unmatchedWaitDays: account.unmatchedWaitDays,
              costToleranceAmount: account.costToleranceAmount, cashGraceDays: account.cashGraceDays }}>
            <ClearingSettingsFields cash={cash.items} entityNo={account.entityNo} currency={account.currency} />
            {canOperate && <Button loading={command.saving} onClick={() => void form.validateFields().then(values =>
              act(reconPaths.clearingSettings(account.clearingAccountId), 'PUT', settingsBody(values), t('clearingDetail.settingsSaved'), t('clearingDetail.settingsFailed')))}>{t('clearingDetail.saveSettings')}</Button>}
          </Form>
        </DetailSection>
        <DetailSection title={t('clearingDetail.mids', { count: account.mids.length })} extra={canOperate && <span className="recon-filters">
          <Select style={{ minWidth: 320 }} placeholder={candidates.length ? t('clearingDetail.selectMid') : t('clearingDetail.noCandidateMid')} value={midId} onChange={setMidId}
            options={candidates.map(mid => ({ value: mid.midId, label: t('common.labelWithId', { label: mid.externalMidCode, id: mid.midId }) }))} />
          <Button disabled={!midId} loading={command.saving} onClick={() => midId && void act(reconPaths.clearingMids(account.clearingAccountId), 'POST',
            { midId }, t('clearingDetail.midAdded'), t('clearingDetail.midAddFailed'))}>{t('clearingDetail.addMid')}</Button></span>}>
          <section className="table-panel"><Table rowKey="midId" size="small" tableLayout="fixed" pagination={false} dataSource={account.mids}
            locale={{ emptyText: <EmptyHint text={t('clearingDetail.midsEmpty')} /> }} columns={[
              { title: t('clearingDetail.column.externalMid'), render: (_, row) => <span className="mono">{row.externalMidCode ?? '—'}</span> },
              { title: t('clearingDetail.column.midId'), render: (_, row) => <span className="mono">{row.midId}</span> },
              { title: t('clearingDetail.column.midStatus'), width: 110, render: (_, row) => row.midStatus ?? '—' },
              { title: t('clearingDetail.column.addedAt'), width: 150, render: (_, row) => localTime(row.createTime) },
              ...(canOperate ? [{ title: '', width: 80, render: (_: unknown, row: { midId: string }) => <Popconfirm title={t('clearingDetail.confirmRemoveMid')}
                okText={t('clearingDetail.removeMid')} cancelText={t('common.cancel')} onConfirm={() => void act(reconPaths.clearingMid(account.clearingAccountId, row.midId), 'DELETE', undefined, t('clearingDetail.midRemoved'), t('clearingDetail.midRemoveFailed'))}>
                <Button type="link" danger>{t('clearingDetail.removeMid')}</Button></Popconfirm> }] : []),
            ]} /></section>
        </DetailSection>
        <FinanceNote>{t('clearingDetail.note')}{!canOperate && ' ' + t('permission.noOperate')}</FinanceNote>
      </>;
    }}
  </ReconDetailPage>;
}
