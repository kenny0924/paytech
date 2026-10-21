import { useMemo, useState } from 'react';
import { Alert, App, Button, Descriptions, Drawer, Form, Input, InputNumber, Popconfirm, Select, Table } from 'antd';
import { useSearchParams } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { localTime, replaceQuery } from '../finance/finance';
import { FinanceNote, LoadFailure } from '../finance/FinanceParts';
import { useDetail } from '../finance/WorkflowParts';
import { usePaging } from '../hooks/usePaging';
import {
  clearingStatusLabels, decimalIdPattern, decodeClearingAccount, decodeWithId, hasReconAction, pageOf, positiveAmountError, reconActions, reconPaths,
} from '../reconciliation/reconModel';
import type { CashAccount, ClearingAccount, ClearingAccountSummary } from '../reconciliation/reconModel';
import { EmptyHint, Money, ReconCommandFeedback, ReconTag } from '../reconciliation/ReconParts';
import { cashAccountLabel, useCashAccounts, useUnassignedMids } from '../reconciliation/useReconData';
import { useReconCommand } from '../reconciliation/useReconCommand';
import '../configuration/configuration.css';
import '../finance/finance.css';
import '../reconciliation/recon.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
interface SettingsValues { settlementCashAccountId?: string; unmatchedWaitDays: number; costToleranceAmount: string; cashGraceDays: number }
interface CreateValues extends SettingsValues { clearingAccountCode: string; channelCode: string; entityNo: string; currency: string }

const decodePage = pageOf(decodeWithId<ClearingAccountSummary>('clearingAccountId'));
const statusOptions = Object.entries(clearingStatusLabels).map(([value, label]) => ({ value, label }));

/** 渠道结算用途、同主体同币种的有效银行账户（到账银行账户的可选范围）。 */
function settlementAccounts(accounts: readonly CashAccount[], entityNo?: string, currency?: string) {
  return accounts.filter(item => item.accountPurpose === 'CHANNEL_SETTLEMENT' && item.status === 'ACTIVE' && item.entityType === 'OPERATING_ENTITY'
    && (!entityNo || item.entityId === entityNo) && (!currency || item.currency === currency));
}

/** 设置请求体：到账银行账户为空表示解除。 */
function settingsBody(values: SettingsValues) {
  return { settlementCashAccountId: values.settlementCashAccountId || null, unmatchedWaitDays: values.unmatchedWaitDays,
    costToleranceAmount: values.costToleranceAmount.trim(), cashGraceDays: values.cashGraceDays };
}

/**
 * 清算账户（UC-R01、R17）：列表、新建、配置、挂 / 摘 MID、启停，顶部提示已激活但未归属的 MID
 * <li>打开的清算账户存于 URL（?account=）；维护按钮按「对账操作」按钮资源显示。</li>
 */
export function ReconClearingAccounts({ resources, onAuthorizationChanged }: Props) {
  const [params, setParams] = useSearchParams();
  const canOperate = hasReconAction(resources, reconActions.operate);
  const [creating, setCreating] = useState(false); const [reload, setReload] = useState(0);
  const page = usePaging<ClearingAccountSummary>(reconPaths.clearingAccounts, decodePage);
  const unassigned = useUnassignedMids(onAuthorizationChanged, reload);
  const cash = useCashAccounts(onAuthorizationChanged);
  const [form] = Form.useForm<Record<string, string | undefined>>();
  const accountId = params.get('account');
  const open = (id: string | null) => setParams(new URLSearchParams(replaceQuery(params.toString(), { account: id })));
  const refresh = () => { page.refresh(); setReload(value => value + 1); };
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>清算账户</h1></div>
    {unassigned.items.length > 0 && <Alert type="warning" showIcon className="finance-banner"
      title={`有 ${unassigned.items.length} 个已激活的 MID + 币种还没有归入清算账户，这些 MID 上的交易无法对账`}
      description={unassigned.items.slice(0, 5).map(item => `${item.channelCode} · ${item.entityNo} · ${item.externalMidCode} · ${item.currency}`).join('；')
        + (unassigned.items.length > 5 ? ' …' : '')} />}
    <Form form={form} name="recon-clearing-filters" layout="vertical" onFinish={values => page.applyFilters(Object.fromEntries(
      Object.entries(values).filter(([, value]) => value?.trim()).map(([key, value]) => [key, value!.trim()])) as Record<string, string>)}>
      <div className="finance-search">
        <Form.Item name="entityNo" label="经营主体"><Input allowClear placeholder="AE001" maxLength={5} /></Form.Item>
        <Form.Item name="channelCode" label="渠道"><Input allowClear placeholder="NI" maxLength={64} /></Form.Item>
        <Form.Item name="currency" label="币种"><Input allowClear placeholder="USD" maxLength={3} /></Form.Item>
        <Form.Item name="status" label="状态"><Select allowClear placeholder="全部" options={statusOptions} /></Form.Item>
        <div className="configuration-actions"><Button htmlType="submit">查询</Button>
          <Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>重置</Button>
          {canOperate && <Button type="primary" onClick={() => setCreating(true)}>新建清算账户</Button>}</div>
      </div>
    </Form>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<ClearingAccountSummary> rowKey="clearingAccountId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 980 }}
        pagination={{ ...page.pagination, showTotal: total => `共 ${total} 条` }} onRow={row => ({ onClick: () => open(row.clearingAccountId), className: 'clickable-row' })}
        locale={{ emptyText: <EmptyHint text={canOperate ? '还没有清算账户。新建后挂入 MID，平台流水和渠道账单才能归到它下面。' : '没有符合条件的清算账户'} /> }}
        columns={[
          { title: '编码', width: 200, render: (_, row) => <a className="mono">{row.clearingAccountCode}</a> },
          { title: '渠道', width: 100, dataIndex: 'channelCode' },
          { title: '经营主体', width: 100, dataIndex: 'entityNo' },
          { title: '币种', width: 80, dataIndex: 'currency' },
          { title: 'MID 数', width: 80, align: 'right', dataIndex: 'midCount' },
          { title: '到账银行账户', width: 200, render: (_, row) => { const account = row.settlementCashAccountId ? cash.byId.get(row.settlementCashAccountId) : undefined;
            return row.settlementCashAccountId ? (account ? `${account.accountName} · 尾号 ${account.accountLast4}` : row.settlementCashAccountId) : <span className="muted">未配置（不能对平）</span>; } },
          { title: '状态', width: 90, render: (_, row) => <ReconTag status={row.status} labels={clearingStatusLabels} /> },
          { title: '更新时间', width: 150, render: (_, row) => localTime(row.updateTime) },
        ]} />
    </section>}
    <FinanceNote>每个 MID + 币种只能属于一个清算账户；挂 MID 时对账服务向渠道配置核对 MID 已激活、渠道与经营主体一致、能力组含清算币种。{!canOperate && ' 当前账号没有对账操作权限。'}</FinanceNote>
    {creating && <CreateClearingDrawer cash={cash.items} onClose={() => setCreating(false)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={id => { setCreating(false); refresh(); open(id); }} />}
    {accountId && decimalIdPattern.test(accountId) && <ClearingDrawer clearingAccountId={accountId} canOperate={canOperate} cash={cash.items}
      unassignedMids={unassigned.items} onClose={() => open(null)} onChanged={refresh} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

function SettingsFields({ cash, entityNo, currency }: { cash: readonly CashAccount[]; entityNo?: string; currency?: string }) {
  const options = settlementAccounts(cash, entityNo, currency);
  return <div className="finance-form-grid">
    <Form.Item name="settlementCashAccountId" label="到账银行账户" extra={options.length ? '为空时账单不能对平' : '没有同主体、同币种、渠道结算用途的有效银行账户'}>
      <Select allowClear placeholder="选择银行账户" options={options.map(item => ({ value: item.cashAccountId, label: cashAccountLabel(item) }))} /></Form.Item>
    <Form.Item name="unmatchedWaitDays" label="账单行等待天数" rules={[{ required: true, message: '请填写 0–365' }]} extra="期满仍未命中平台流水转为异常">
      <InputNumber min={0} max={365} precision={0} style={{ width: '100%' }} /></Form.Item>
    <Form.Item name="costToleranceAmount" label="渠道费容差" rules={[{ validator: async (_, value?: string) => {
      if (value?.trim() === '0') return; const error = positiveAmountError(value); if (error) throw new Error(error); } }]} extra="实际费用与预估之差超过它开阻塞异常">
      <Input inputMode="decimal" placeholder="0.10" /></Form.Item>
    <Form.Item name="cashGraceDays" label="到账宽限天数" rules={[{ required: true, message: '请填写 0–365' }]}>
      <InputNumber min={0} max={365} precision={0} style={{ width: '100%' }} /></Form.Item>
  </div>;
}

function CreateClearingDrawer({ cash, onClose, onCreated, onAuthorizationChanged }: {
  cash: readonly CashAccount[]; onClose: () => void; onCreated: (id: string) => void; onAuthorizationChanged: () => void;
}) {
  const { message } = App.useApp();
  const [form] = Form.useForm<CreateValues>();
  const command = useReconCommand(onAuthorizationChanged, form);
  const entityNo = Form.useWatch('entityNo', form); const currency = Form.useWatch('currency', form);
  async function submit() {
    const values = await form.validateFields();
    const result = await command.run(reconPaths.clearingAccounts, 'POST', { clearingAccountCode: values.clearingAccountCode.trim(),
      channelCode: values.channelCode.trim(), entityNo: values.entityNo.trim(), currency: values.currency.trim(), ...settingsBody(values) },
    decodeClearingAccount(), '新建清算账户失败');
    if (!result) return;
    void message.success(`已新建清算账户 ${result.clearingAccountCode}`);
    onCreated(result.clearingAccountId);
  }
  return <Drawer open size="min(760px, 100vw)" rootClassName="finance-drawer finance-page" title="新建清算账户" destroyOnHidden
    onClose={() => { if (!command.saving) onClose(); }} footer={<div className="finance-drawer-footer"><span className="finance-spacer" />
      <Button onClick={onClose} disabled={command.saving}>取消</Button>
      <Button type="primary" loading={command.saving} onClick={() => void submit()}>新建</Button></div>}>
    <ReconCommandFeedback command={command} onReload={onClose} />
    <Form form={form} name="recon-create-clearing" layout="vertical" disabled={command.saving}
      initialValues={{ unmatchedWaitDays: 3, costToleranceAmount: '0.10', cashGraceDays: 2 }}>
      <div className="finance-form-grid">
        <Form.Item name="clearingAccountCode" label="清算账户编码" extra="渠道账单文件头用它指明所属清算账户，创建后不可改"
          rules={[{ required: true, pattern: /^[A-Z][A-Z0-9_-]{1,63}$/, message: '大写字母开头，2–64 位大写字母、数字、下划线或连字符' }]}>
          <Input placeholder="NI-AE001-USD" maxLength={64} /></Form.Item>
        <Form.Item name="channelCode" label="渠道编码" rules={[{ required: true, pattern: /^[A-Z][A-Z0-9_]{1,63}$/, message: '渠道编码，如 NI' }]}>
          <Input placeholder="NI" maxLength={64} /></Form.Item>
        <Form.Item name="entityNo" label="经营主体" rules={[{ required: true, pattern: /^[A-Z]{2}[0-9]{3}$/, message: '两位大写字母加三位数字' }]}>
          <Input placeholder="AE001" maxLength={5} /></Form.Item>
        <Form.Item name="currency" label="清算币种" rules={[{ required: true, pattern: /^[A-Z]{3}$/, message: '三位大写币种' }]}>
          <Input placeholder="USD" maxLength={3} /></Form.Item>
      </div>
      <SettingsFields cash={cash} entityNo={entityNo} currency={currency} />
    </Form>
  </Drawer>;
}

/** 清算账户详情：配置、MID（补外部编码与状态）、挂 / 摘 MID、启停。 */
function ClearingDrawer({ clearingAccountId, canOperate, cash, unassignedMids, onClose, onChanged, onAuthorizationChanged }: {
  clearingAccountId: string; canOperate: boolean; cash: readonly CashAccount[]; unassignedMids: readonly { midId: string; externalMidCode: string;
    channelCode: string; entityNo: string; currency: string }[];
  onClose: () => void; onChanged: () => void; onAuthorizationChanged: () => void;
}) {
  const { message } = App.useApp();
  const decode = useMemo(() => decodeClearingAccount(clearingAccountId), [clearingAccountId]);
  const detail = useDetail<ClearingAccount>(reconPaths.clearingAccount(clearingAccountId), decode, onAuthorizationChanged, '清算账户详情加载失败');
  const [form] = Form.useForm<SettingsValues>();
  const command = useReconCommand(onAuthorizationChanged, form);
  const [midId, setMidId] = useState<string>();
  const item = detail.item;
  async function act(path: string, method: 'POST' | 'PUT' | 'DELETE', body: unknown, success: string, failure: string) {
    const result = await command.run(path, method, body, decodeClearingAccount(clearingAccountId), failure);
    if (!result) return;
    void message.success(success); setMidId(undefined); detail.reload(); onChanged();
  }
  const candidates = item ? unassignedMids.filter(mid => mid.channelCode === item.channelCode && mid.entityNo === item.entityNo && mid.currency === item.currency) : [];
  return <Drawer open size="min(980px, 100vw)" rootClassName="finance-drawer finance-page" destroyOnHidden onClose={onClose}
    title={item ? `清算账户 · ${item.clearingAccountCode}` : '清算账户'}>
    {detail.error ? <LoadFailure message={detail.error} onRetry={detail.reload} /> : item && <>
      <ReconCommandFeedback command={command} onReload={() => { command.clearConflict(); detail.reload(); }} />
      <Descriptions size="small" column={3} items={[
        { key: 'channel', label: '渠道', children: item.channelCode }, { key: 'entity', label: '经营主体', children: item.entityNo },
        { key: 'currency', label: '币种', children: item.currency },
        { key: 'status', label: '状态', children: <ReconTag status={item.status} labels={clearingStatusLabels} /> },
        { key: 'tolerance', label: '渠道费容差', children: <Money value={item.costToleranceAmount} /> },
        { key: 'version', label: '版本', children: item.version },
      ]} />
      <h3 className="finance-subtitle">配置</h3>
      <Form form={form} name="recon-clearing-settings" layout="vertical" disabled={!canOperate || command.saving} key={item.version}
        initialValues={{ settlementCashAccountId: item.settlementCashAccountId ?? undefined, unmatchedWaitDays: item.unmatchedWaitDays,
          costToleranceAmount: item.costToleranceAmount, cashGraceDays: item.cashGraceDays }}>
        <SettingsFields cash={cash} entityNo={item.entityNo} currency={item.currency} />
        {canOperate && <Button loading={command.saving} onClick={() => void form.validateFields().then(values =>
          act(reconPaths.clearingSettings(item.clearingAccountId), 'PUT', settingsBody(values), '配置已保存', '保存配置失败'))}>保存配置</Button>}
      </Form>
      <h3 className="finance-subtitle">MID（{item.mids.length}）</h3>
      {canOperate && <div className="finance-search-inline">
        <Select style={{ minWidth: 320 }} placeholder={candidates.length ? '选择未归属的 MID' : '没有同渠道、同主体、同币种的未归属 MID'} value={midId} onChange={setMidId}
          options={candidates.map(mid => ({ value: mid.midId, label: `${mid.externalMidCode}（${mid.midId}）` }))} />
        <Button disabled={!midId} loading={command.saving} onClick={() => midId && void act(reconPaths.clearingMids(item.clearingAccountId), 'POST',
          { midId }, '已挂入 MID', '挂入 MID 失败')}>挂入</Button>
      </div>}
      <section className="table-panel"><Table rowKey="midId" size="small" tableLayout="fixed" pagination={false} dataSource={item.mids}
        locale={{ emptyText: <EmptyHint text="还没有挂入 MID；挂入前该清算账户收不到平台流水。" /> }} columns={[
          { title: '外部 MID', render: (_, row) => <span className="mono">{row.externalMidCode ?? '—'}</span> },
          { title: 'MID 主键', render: (_, row) => <span className="mono">{row.midId}</span> },
          { title: 'MID 状态', width: 110, render: (_, row) => row.midStatus ?? '—' },
          { title: '挂入时间', width: 150, render: (_, row) => localTime(row.createTime) },
          ...(canOperate ? [{ title: '', width: 80, render: (_: unknown, row: { midId: string }) => <Popconfirm title="摘除后该 MID 的新交易不再归到本清算账户，已生成的平台流水不变。确认摘除？"
            okText="摘除" cancelText="取消" onConfirm={() => void act(reconPaths.clearingMid(item.clearingAccountId, row.midId), 'DELETE', undefined, '已摘除 MID', '摘除 MID 失败')}>
            <Button type="link" danger>摘除</Button></Popconfirm> }] : []),
        ]} /></section>
      {canOperate && <div className="finance-review-bar"><span className="finance-spacer" />
        <Popconfirm title={item.status === 'ACTIVE' ? '停用后不再接收新的平台流水和渠道账单，也不参与解析。确认停用？' : '确认启用？'} okText="确认" cancelText="取消"
          onConfirm={() => void act(reconPaths.clearingStatus(item.clearingAccountId), 'POST', { status: item.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE' },
            item.status === 'ACTIVE' ? '已停用' : '已启用', '修改状态失败')}>
          <Button danger={item.status === 'ACTIVE'} loading={command.saving}>{item.status === 'ACTIVE' ? '停用' : '启用'}</Button></Popconfirm>
      </div>}
    </>}
  </Drawer>;
}
