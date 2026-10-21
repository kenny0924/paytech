import { useState } from 'react';
import { App, Button, Drawer, Form, Input, Select, Table } from 'antd';
import { Navigate, useParams, useSearchParams } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { FinanceNote, LoadFailure } from '../finance/FinanceParts';
import { ActorText } from '../finance/WorkflowParts';
import { CashAccountDetail } from '../reconciliation/detail/CashAccountDetail';
import { useOpenDetail } from '../reconciliation/ReconDetail';
import {
  accountPurposeLabels, cashStatusLabels, decimalIdPattern, decodeWithId, entityTypeLabels, hasReconAction, pageOf, reconActions, reconPaths,
} from '../reconciliation/reconModel';
import type { CashAccount } from '../reconciliation/reconModel';
import { EmptyHint, ReconCommandFeedback, ReconTag } from '../reconciliation/ReconParts';
import { compactFilters, isReconId, reconRoutes } from '../reconciliation/reconRoutes';
import { useReconCommand } from '../reconciliation/useReconCommand';
import { resetForm, useReconListQuery } from '../reconciliation/useReconListQuery';
import '../configuration/configuration.css';
import '../finance/finance.css';
import '../reconciliation/recon.css';

interface Props { resources: ConsoleResource[]; actorId: string; onAuthorizationChanged: () => void }
interface CreateValues { entityType: string; entityId: string; accountPurpose: string; bankCode: string; accountName: string; currency: string; accountNumber: string }

const decodePage = pageOf(decodeWithId<CashAccount>('cashAccountId'));
const filterKeys = ['entityType', 'entityId', 'currency', 'accountPurpose', 'status'] as const;
const options = (labels: Record<string, string>) => Object.entries(labels).map(([value, label]) => ({ value, label }));

/**
 * 银行账户（UC-R02）：列表、提交新建；点行进入详情页（/cash-accounts/:cashAccountId）申请关闭与复核
 * <li>新建与关闭要经复核才生效，提交人不能复核（reconciliation 判断，页面对本人隐藏复核按钮）；账号明文只在提交时发送一次，之后只显示末四位。</li>
 * <li>筛选与页码存于 URL 查询串；旧链接 ?account= 转到详情页。</li>
 */
export function ReconCashAccounts(props: Props) {
  const { cashAccountId } = useParams();
  const [params] = useSearchParams();
  const legacy = params.get('account');
  if (cashAccountId !== undefined) return <CashAccountDetail key={cashAccountId} cashAccountId={cashAccountId} {...props} />;
  if (isReconId(legacy ?? undefined)) return <Navigate replace to={reconRoutes.cashAccount(legacy!)} />;
  return <CashList {...props} />;
}

function CashList({ resources, actorId, onAuthorizationChanged }: Props) {
  const openDetail = useOpenDetail();
  const canOperate = hasReconAction(resources, reconActions.operate);
  const canApprove = hasReconAction(resources, reconActions.approve);
  const [creating, setCreating] = useState(false);
  const page = useReconListQuery<CashAccount>(reconPaths.cashAccounts, decodePage, filterKeys);
  const [form] = Form.useForm<Record<string, string | undefined>>();
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>银行账户</h1></div>
    <Form form={form} name="recon-cash-filters" layout="vertical" initialValues={page.filters} onFinish={values => page.applyFilters(compactFilters(values))}>
      <div className="finance-search">
        <Form.Item name="entityType" label="主体类型"><Select allowClear placeholder="全部" options={options(entityTypeLabels)} /></Form.Item>
        <Form.Item name="entityId" label="主体"><Input allowClear placeholder="AE001" maxLength={64} /></Form.Item>
        <Form.Item name="currency" label="币种"><Input allowClear placeholder="USD" maxLength={3} /></Form.Item>
        <Form.Item name="accountPurpose" label="用途"><Select allowClear placeholder="全部" options={options(accountPurposeLabels)} /></Form.Item>
        <Form.Item name="status" label="状态"><Select allowClear placeholder="全部" options={options(cashStatusLabels)} /></Form.Item>
        <div className="configuration-actions"><Button htmlType="submit">查询</Button>
          <Button onClick={() => { resetForm(form); page.applyFilters({}); }}>重置</Button>
          {canOperate && <Button type="primary" onClick={() => setCreating(true)}>新建银行账户</Button>}</div>
      </div>
    </Form>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<CashAccount> rowKey="cashAccountId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1080 }}
        pagination={{ ...page.pagination, showTotal: total => `共 ${total} 条` }} onRow={row => ({ onClick: () => openDetail(reconRoutes.cashAccount(row.cashAccountId)), className: 'clickable-row' })}
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
      onCreated={id => { setCreating(false); openDetail(reconRoutes.cashAccount(id)); }} />}
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
