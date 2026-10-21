import { useMemo, useState } from 'react';
import { App, Button, Descriptions, Drawer, Form, Input, Select, Table } from 'antd';
import { useSearchParams } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { localTime, replaceQuery } from '../finance/finance';
import { FinanceNote, LoadFailure } from '../finance/FinanceParts';
import { ActorText, HashText, ReviewActions, useDetail } from '../finance/WorkflowParts';
import { usePaging } from '../hooks/usePaging';
import {
  accountPurposeLabels, cashStatusLabels, decimalIdPattern, decodeWithId, entityTypeLabels, hasReconAction, pageOf, reconActions, reconPaths,
} from '../reconciliation/reconModel';
import type { CashAccount } from '../reconciliation/reconModel';
import { EmptyHint, ReconCommandFeedback, ReconTag } from '../reconciliation/ReconParts';
import { useReconCommand } from '../reconciliation/useReconCommand';
import '../configuration/configuration.css';
import '../finance/finance.css';
import '../reconciliation/recon.css';

interface Props { resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }
interface CreateValues { entityType: string; entityId: string; accountPurpose: string; bankCode: string; accountName: string; currency: string; accountNumber: string }

const decodePage = pageOf(decodeWithId<CashAccount>('cashAccountId'));
const options = (labels: Record<string, string>) => Object.entries(labels).map(([value, label]) => ({ value, label }));

/**
 * 银行账户（UC-R02）：列表、提交新建、申请关闭、复核
 * <li>新建与关闭要经复核才生效，提交人不能复核（reconciliation 判断，页面对本人隐藏复核按钮）；账号明文只在提交时发送一次，之后只显示末四位。</li>
 */
export function ReconCashAccounts({ resources, actorId, onAuthorizationChanged }: Props) {
  const [params, setParams] = useSearchParams();
  const canOperate = hasReconAction(resources, reconActions.operate);
  const canApprove = hasReconAction(resources, reconActions.approve);
  const [creating, setCreating] = useState(false);
  const page = usePaging<CashAccount>(reconPaths.cashAccounts, decodePage);
  const [form] = Form.useForm<Record<string, string | undefined>>();
  const accountId = params.get('account');
  const open = (id: string | null) => setParams(new URLSearchParams(replaceQuery(params.toString(), { account: id })));
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>银行账户</h1></div>
    <Form form={form} name="recon-cash-filters" layout="vertical" onFinish={values => page.applyFilters(Object.fromEntries(
      Object.entries(values).filter(([, value]) => value?.trim()).map(([key, value]) => [key, value!.trim()])) as Record<string, string>)}>
      <div className="finance-search">
        <Form.Item name="entityType" label="主体类型"><Select allowClear placeholder="全部" options={options(entityTypeLabels)} /></Form.Item>
        <Form.Item name="entityId" label="主体"><Input allowClear placeholder="AE001" maxLength={64} /></Form.Item>
        <Form.Item name="currency" label="币种"><Input allowClear placeholder="USD" maxLength={3} /></Form.Item>
        <Form.Item name="accountPurpose" label="用途"><Select allowClear placeholder="全部" options={options(accountPurposeLabels)} /></Form.Item>
        <Form.Item name="status" label="状态"><Select allowClear placeholder="全部" options={options(cashStatusLabels)} /></Form.Item>
        <div className="configuration-actions"><Button htmlType="submit">查询</Button>
          <Button onClick={() => { form.resetFields(); page.applyFilters({}); }}>重置</Button>
          {canOperate && <Button type="primary" onClick={() => setCreating(true)}>新建银行账户</Button>}</div>
      </div>
    </Form>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<CashAccount> rowKey="cashAccountId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1080 }}
        pagination={{ ...page.pagination, showTotal: total => `共 ${total} 条` }} onRow={row => ({ onClick: () => open(row.cashAccountId), className: 'clickable-row' })}
        locale={{ emptyText: <EmptyHint text={canOperate ? '还没有银行账户。新建并经复核后，才能作为到账账户与上传银行对账单。' : '没有符合条件的银行账户'} /> }}
        columns={[
          { title: '名称', width: 200, render: (_, row) => <a>{row.accountName}</a> },
          { title: '主体', width: 160, render: (_, row) => `${entityTypeLabels[row.entityType] ?? row.entityType} · ${row.entityId}` },
          { title: '用途', width: 100, render: (_, row) => accountPurposeLabels[row.accountPurpose] ?? row.accountPurpose },
          { title: '银行', width: 100, dataIndex: 'bankCode' },
          { title: '币种', width: 70, dataIndex: 'currency' },
          { title: '尾号', width: 80, render: (_, row) => <span className="mono">{row.accountLast4}</span> },
          { title: '状态', width: 140, render: (_, row) => <>{<ReconTag status={row.status} labels={cashStatusLabels} />}{row.pendingAction === 'CLOSE' && <ReconTag status="PENDING_VERIFICATION" labels={{ PENDING_VERIFICATION: '关闭待复核' }} />}</> },
          { title: '提交人', width: 160, render: (_, row) => <ActorText value={row.makerId} actorId={actorId} /> },
        ]} />
    </section>}
    <FinanceNote>同一主体 + 币种 + 用途只允许一个有效账户；换用途请关闭旧账户再新建。{!canOperate && ' 当前账号没有对账操作权限。'}{!canApprove && ' 当前账号没有对账审批权限。'}</FinanceNote>
    {creating && <CreateCashDrawer onClose={() => setCreating(false)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={id => { setCreating(false); page.refresh(); open(id); }} />}
    {accountId && decimalIdPattern.test(accountId) && <CashDrawer cashAccountId={accountId} canOperate={canOperate} canApprove={canApprove} actorId={actorId}
      onClose={() => open(null)} onChanged={page.refresh} onAuthorizationChanged={onAuthorizationChanged} />}
  </div>;
}

function CreateCashDrawer({ onClose, onCreated, onAuthorizationChanged }: { onClose: () => void; onCreated: (id: string) => void; onAuthorizationChanged: () => void }) {
  const { message } = App.useApp();
  const [form] = Form.useForm<CreateValues>();
  const command = useReconCommand(onAuthorizationChanged, form);
  const entityType = Form.useWatch('entityType', form);
  async function submit() {
    const values = await form.validateFields();
    const body = Object.fromEntries(Object.entries(values).map(([key, value]) => [key, typeof value === 'string' ? value.trim() : value]));
    const result = await command.run(reconPaths.cashAccounts, 'POST', body, decodeWithId<CashAccount>('cashAccountId'), '提交银行账户失败');
    if (!result) return;
    form.resetFields();
    void message.success(`已提交，尾号 ${result.accountLast4}，等待复核`);
    onCreated(result.cashAccountId);
  }
  return <Drawer open size="min(720px, 100vw)" rootClassName="finance-drawer finance-page" title="新建银行账户" destroyOnHidden
    onClose={() => { if (!command.saving) onClose(); }} footer={<div className="finance-drawer-footer"><span className="finance-spacer" />
      <Button onClick={onClose} disabled={command.saving}>取消</Button>
      <Button type="primary" loading={command.saving} onClick={() => void submit()}>提交复核</Button></div>}>
    <ReconCommandFeedback command={command} onReload={onClose} />
    <Form form={form} name="recon-create-cash" layout="vertical" disabled={command.saving} autoComplete="off"
      initialValues={{ entityType: 'OPERATING_ENTITY', accountPurpose: 'CHANNEL_SETTLEMENT' }}>
      <div className="finance-form-grid">
        <Form.Item name="entityType" label="主体类型" rules={[{ required: true }]}><Select options={options(entityTypeLabels)} /></Form.Item>
        <Form.Item name="entityId" label={entityType === 'FINANCE_ENTITY' ? '财务主体主键' : '经营主体编号'}
          rules={[{ required: true, pattern: entityType === 'FINANCE_ENTITY' ? decimalIdPattern : /^[A-Z]{2}[0-9]{3}$/,
            message: entityType === 'FINANCE_ENTITY' ? '正十进制主键' : '两位大写字母加三位数字' }]}><Input maxLength={19} /></Form.Item>
        <Form.Item name="accountPurpose" label="资金用途" extra="创建后不可改" rules={[{ required: true }]}><Select options={options(accountPurposeLabels)} /></Form.Item>
        <Form.Item name="currency" label="币种" rules={[{ required: true, pattern: /^[A-Z]{3}$/, message: '三位大写币种' }]}><Input placeholder="USD" maxLength={3} /></Form.Item>
        <Form.Item name="bankCode" label="开户银行代码" rules={[{ required: true, whitespace: true, max: 64 }]}><Input maxLength={64} /></Form.Item>
        <Form.Item name="accountName" label="账户显示名称" rules={[{ required: true, whitespace: true, max: 128 }]}><Input maxLength={128} /></Form.Item>
        <Form.Item name="accountNumber" label="银行账号" extra="提交后加密保存，页面只显示末四位"
          rules={[{ required: true, pattern: /^[A-Za-z0-9]{4,34}$/, message: '4–34 位字母或数字（含 IBAN）' }]}>
          <Input.Password visibilityToggle maxLength={34} autoComplete="off" /></Form.Item>
      </div>
    </Form>
  </Drawer>;
}

/** 银行账户详情：申请关闭（操作）与复核（审批，提交人本人不显示）。 */
function CashDrawer({ cashAccountId, canOperate, canApprove, actorId, onClose, onChanged, onAuthorizationChanged }: {
  cashAccountId: string; canOperate: boolean; canApprove: boolean; actorId: string; onClose: () => void; onChanged: () => void; onAuthorizationChanged: () => void;
}) {
  const { message } = App.useApp();
  const decode = useMemo(() => decodeWithId<CashAccount>('cashAccountId', cashAccountId), [cashAccountId]);
  const detail = useDetail<CashAccount>(reconPaths.cashAccount(cashAccountId), decode, onAuthorizationChanged, '银行账户详情加载失败');
  const command = useReconCommand(onAuthorizationChanged);
  const item = detail.item;
  const pending = item && (item.status === 'PENDING_VERIFICATION' || item.pendingAction === 'CLOSE');
  async function act(path: string, body: unknown, success: string, failure: string) {
    const result = await command.run(path, 'POST', body, decodeWithId<CashAccount>('cashAccountId', cashAccountId), failure);
    if (!result) return;
    void message.success(success); detail.reload(); onChanged();
  }
  const actions = !item || !pending || !canApprove || item.makerId === actorId ? [] : [
    { key: 'approve', label: '通过', primary: true, confirm: item.pendingAction === 'CLOSE' ? '通过后账户关闭。确认？' : '通过后账户生效。确认？',
      onConfirm: () => void act(reconPaths.cashApproval(cashAccountId), { decision: 'APPROVED', approvedContentHash: item.contentHash }, '已通过', '复核失败') },
    { key: 'reject', label: '拒绝', danger: true, confirm: item.pendingAction === 'CLOSE' ? '拒绝后关闭申请撤销。确认？' : '拒绝后该账户关闭。确认？',
      onConfirm: () => void act(reconPaths.cashApproval(cashAccountId), { decision: 'REJECTED', approvedContentHash: item.contentHash }, '已拒绝', '复核失败') },
  ];
  return <Drawer open size="min(820px, 100vw)" rootClassName="finance-drawer finance-page" destroyOnHidden onClose={onClose}
    title={item ? `银行账户 · ${item.accountName}` : '银行账户'}>
    {detail.error ? <LoadFailure message={detail.error} onRetry={detail.reload} /> : item && <>
      <ReconCommandFeedback command={command} onReload={() => { command.clearConflict(); detail.reload(); }} />
      <Descriptions size="small" column={2} items={[
        { key: 'entity', label: '主体', children: `${entityTypeLabels[item.entityType] ?? item.entityType} · ${item.entityId}` },
        { key: 'purpose', label: '用途', children: accountPurposeLabels[item.accountPurpose] ?? item.accountPurpose },
        { key: 'bank', label: '开户银行', children: item.bankCode }, { key: 'currency', label: '币种', children: item.currency },
        { key: 'last4', label: '账号末四位', children: <span className="mono">{item.accountLast4}</span> },
        { key: 'status', label: '状态', children: <ReconTag status={item.status} labels={cashStatusLabels} /> },
        { key: 'pending', label: '待复核变更', children: item.status === 'PENDING_VERIFICATION' ? '新建' : item.pendingAction === 'CLOSE' ? '关闭' : '无' },
        { key: 'hash', label: '提交内容摘要', children: <HashText value={item.contentHash} /> },
        { key: 'maker', label: '提交人', children: <ActorText value={item.makerId} actorId={actorId} /> },
        { key: 'checker', label: '复核人', children: <ActorText value={item.checkerId} actorId={actorId} /> },
        { key: 'time', label: '更新时间', children: localTime(item.updateTime) },
      ]} />
      <ReviewActions saving={command.saving} actions={actions} note={pending && canApprove && item.makerId === actorId ? '提交人不能复核自己的提交。' : undefined} />
      {canOperate && item.status === 'ACTIVE' && !item.pendingAction && <ReviewActions saving={command.saving} actions={[{ key: 'close', label: '申请关闭', danger: true,
        confirm: '关闭申请经复核后账户关闭，清算账户如以它为到账账户需另行更换。确认申请？',
        onConfirm: () => void act(reconPaths.cashClosure(cashAccountId), undefined, '已提交关闭申请，等待复核', '申请关闭失败') }]} />}
    </>}
  </Drawer>;
}
