import { useMemo, useState } from 'react';
import { App, Button, Descriptions, Form, Popconfirm, Select, Table } from 'antd';
import { Link } from 'react-router-dom';
import type { ConsoleResource } from '../../api/types';
import { localTime } from '../../finance/finance';
import { FinanceNote } from '../../finance/FinanceParts';
import { ClearingSettingsFields, settingsBody } from '../ClearingSettings';
import type { ClearingSettingsValues } from '../ClearingSettings';
import { DetailSection, ReconDetailPage } from '../ReconDetail';
import { clearingStatusLabels, decodeClearingAccount, hasReconAction, reconActions, reconPaths } from '../reconModel';
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
  const { message } = App.useApp();
  const canOperate = hasReconAction(resources, reconActions.operate);
  const valid = isReconId(clearingAccountId);
  const decode = useMemo(() => decodeClearingAccount(clearingAccountId), [clearingAccountId]);
  const detail = useReconDetail<ClearingAccount>(valid ? reconPaths.clearingAccount(clearingAccountId) : null, decode, onAuthorizationChanged, '清算账户详情加载失败');
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
  const statusAction = item && canOperate && <Popconfirm title={item.status === 'ACTIVE' ? '停用后不再接收新的平台流水和渠道账单，也不参与解析。确认停用？' : '确认启用？'}
    okText="确认" cancelText="取消" onConfirm={() => void act(reconPaths.clearingStatus(item.clearingAccountId), 'POST',
      { status: item.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE' }, item.status === 'ACTIVE' ? '已停用' : '已启用', '修改状态失败')}>
    <Button danger={item.status === 'ACTIVE'} loading={command.saving}>{item.status === 'ACTIVE' ? '停用' : '启用'}</Button></Popconfirm>;
  return <ReconDetailPage list={{ label: '清算账户', route: reconRoutes.clearingAccounts }} objectLabel="清算账户" state={detail}
    title={item ? <>清算账户 · <span className="mono">{item.clearingAccountCode}</span></> : null} actions={statusAction}>
    {account => {
      const candidates = unassigned.items.filter(mid => mid.channelCode === account.channelCode && mid.entityNo === account.entityNo && mid.currency === account.currency);
      const settlement = account.settlementCashAccountId ? cash.byId.get(account.settlementCashAccountId) : undefined;
      return <>
        <ReconCommandFeedback command={command} onReload={() => { command.clearConflict(); detail.reload(); }} />
        <Descriptions size="small" column={3} items={[
          { key: 'channel', label: '渠道', children: account.channelCode }, { key: 'entity', label: '经营主体', children: account.entityNo },
          { key: 'currency', label: '币种', children: account.currency },
          { key: 'status', label: '状态', children: <ReconTag status={account.status} labels={clearingStatusLabels} /> },
          { key: 'cash', label: '到账银行账户', children: account.settlementCashAccountId
            ? <Link to={reconRoutes.cashAccount(account.settlementCashAccountId)}>{settlement ? `${settlement.accountName} · 尾号 ${settlement.accountLast4}` : account.settlementCashAccountId}</Link>
            : <span className="muted">未配置（不能对平）</span> },
          { key: 'tolerance', label: '渠道费容差', children: <Money value={account.costToleranceAmount} /> },
          { key: 'id', label: '主键', children: <span className="mono">{account.clearingAccountId}</span> },
          { key: 'version', label: '版本', children: account.version },
          { key: 'updated', label: '更新时间', children: localTime(account.updateTime) },
        ]} />
        <DetailSection title="相关数据">
          <div className="recon-link-list">
            <Link to={withQuery(reconRoutes.statements, { clearingAccountId: account.clearingAccountId })}>渠道账单</Link>
            <Link to={withQuery(reconRoutes.channelLines, { clearingAccountId: account.clearingAccountId })}>渠道流水</Link>
            <Link to={withQuery(reconRoutes.platformTxns, { clearingAccountId: account.clearingAccountId })}>平台流水</Link>
            <Link to={withQuery(reconRoutes.exceptions, { clearingAccountId: account.clearingAccountId })}>未结异常</Link>
          </div>
        </DetailSection>
        <DetailSection title="配置">
          <Form form={form} name="recon-clearing-settings" layout="vertical" disabled={!canOperate || command.saving} key={account.version}
            initialValues={{ settlementCashAccountId: account.settlementCashAccountId ?? undefined, unmatchedWaitDays: account.unmatchedWaitDays,
              costToleranceAmount: account.costToleranceAmount, cashGraceDays: account.cashGraceDays }}>
            <ClearingSettingsFields cash={cash.items} entityNo={account.entityNo} currency={account.currency} />
            {canOperate && <Button loading={command.saving} onClick={() => void form.validateFields().then(values =>
              act(reconPaths.clearingSettings(account.clearingAccountId), 'PUT', settingsBody(values), '配置已保存', '保存配置失败'))}>保存配置</Button>}
          </Form>
        </DetailSection>
        <DetailSection title={`MID（${account.mids.length}）`} extra={canOperate && <span className="recon-filters">
          <Select style={{ minWidth: 320 }} placeholder={candidates.length ? '选择未归属的 MID' : '没有同渠道、同主体、同币种的未归属 MID'} value={midId} onChange={setMidId}
            options={candidates.map(mid => ({ value: mid.midId, label: `${mid.externalMidCode}（${mid.midId}）` }))} />
          <Button disabled={!midId} loading={command.saving} onClick={() => midId && void act(reconPaths.clearingMids(account.clearingAccountId), 'POST',
            { midId }, '已挂入 MID', '挂入 MID 失败')}>挂入</Button></span>}>
          <section className="table-panel"><Table rowKey="midId" size="small" tableLayout="fixed" pagination={false} dataSource={account.mids}
            locale={{ emptyText: <EmptyHint text="还没有挂入 MID；挂入前该清算账户收不到平台流水。" /> }} columns={[
              { title: '外部 MID', render: (_, row) => <span className="mono">{row.externalMidCode ?? '—'}</span> },
              { title: 'MID 主键', render: (_, row) => <span className="mono">{row.midId}</span> },
              { title: 'MID 状态', width: 110, render: (_, row) => row.midStatus ?? '—' },
              { title: '挂入时间', width: 150, render: (_, row) => localTime(row.createTime) },
              ...(canOperate ? [{ title: '', width: 80, render: (_: unknown, row: { midId: string }) => <Popconfirm title="摘除后该 MID 的新交易不再归到本清算账户，已生成的平台流水不变。确认摘除？"
                okText="摘除" cancelText="取消" onConfirm={() => void act(reconPaths.clearingMid(account.clearingAccountId, row.midId), 'DELETE', undefined, '已摘除 MID', '摘除 MID 失败')}>
                <Button type="link" danger>摘除</Button></Popconfirm> }] : []),
            ]} /></section>
        </DetailSection>
        <FinanceNote>每个 MID + 币种只能属于一个清算账户；挂 MID 时对账服务向渠道配置核对 MID 已激活、渠道与经营主体一致、能力组含清算币种。{!canOperate && ' 当前账号没有对账操作权限。'}</FinanceNote>
      </>;
    }}
  </ReconDetailPage>;
}
