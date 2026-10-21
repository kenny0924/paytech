import { useState } from 'react';
import { Alert, App, Button, Drawer, Form, Input, Select, Table } from 'antd';
import { Navigate, useParams, useSearchParams } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { localTime } from '../finance/finance';
import { FinanceNote, LoadFailure } from '../finance/FinanceParts';
import { ClearingSettingsFields, settingsBody } from '../reconciliation/ClearingSettings';
import type { ClearingSettingsValues } from '../reconciliation/ClearingSettings';
import { ClearingAccountDetail } from '../reconciliation/detail/ClearingAccountDetail';
import { useOpenDetail } from '../reconciliation/ReconDetail';
import { clearingStatusLabels, decodeClearingAccount, decodeWithId, hasReconAction, pageOf, reconActions, reconPaths } from '../reconciliation/reconModel';
import type { CashAccount, ClearingAccountSummary } from '../reconciliation/reconModel';
import { EmptyHint, ReconCommandFeedback, ReconTag } from '../reconciliation/ReconParts';
import { compactFilters, isReconId, reconRoutes } from '../reconciliation/reconRoutes';
import { useCashAccounts, useUnassignedMids } from '../reconciliation/useReconData';
import { useReconCommand } from '../reconciliation/useReconCommand';
import { resetForm, useReconListQuery } from '../reconciliation/useReconListQuery';
import '../configuration/configuration.css';
import '../finance/finance.css';
import '../reconciliation/recon.css';

interface Props { resources: ConsoleResource[]; onAuthorizationChanged: () => void }
interface CreateValues extends ClearingSettingsValues { clearingAccountCode: string; channelCode: string; entityNo: string; currency: string }

const decodePage = pageOf(decodeWithId<ClearingAccountSummary>('clearingAccountId'));
const statusOptions = Object.entries(clearingStatusLabels).map(([value, label]) => ({ value, label }));
const filterKeys = ['entityNo', 'channelCode', 'currency', 'status'] as const;

/**
 * 清算账户（UC-R01、R17）：列表、新建，顶部提示已激活但未归属的 MID；点行进入详情页（/clearing-accounts/:clearingAccountId）
 * <li>筛选与页码存于 URL 查询串；旧链接 ?account= 转到详情页。维护按钮按「对账操作」按钮资源显示。</li>
 */
export function ReconClearingAccounts(props: Props) {
  const { clearingAccountId } = useParams();
  const [params] = useSearchParams();
  const legacy = params.get('account');
  if (clearingAccountId !== undefined) return <ClearingAccountDetail key={clearingAccountId} clearingAccountId={clearingAccountId} {...props} />;
  if (isReconId(legacy ?? undefined)) return <Navigate replace to={reconRoutes.clearingAccount(legacy!)} />;
  return <ClearingList {...props} />;
}

function ClearingList({ resources, onAuthorizationChanged }: Props) {
  const openDetail = useOpenDetail();
  const canOperate = hasReconAction(resources, reconActions.operate);
  const [creating, setCreating] = useState(false);
  const page = useReconListQuery<ClearingAccountSummary>(reconPaths.clearingAccounts, decodePage, filterKeys);
  const unassigned = useUnassignedMids(onAuthorizationChanged);
  const cash = useCashAccounts(onAuthorizationChanged);
  const [form] = Form.useForm<Record<string, string | undefined>>();
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>清算账户</h1></div>
    {unassigned.items.length > 0 && <Alert type="warning" showIcon className="finance-banner"
      title={`有 ${unassigned.items.length} 个已激活的 MID + 币种还没有归入清算账户，这些 MID 上的交易无法对账`}
      description={unassigned.items.slice(0, 5).map(item => `${item.channelCode} · ${item.entityNo} · ${item.externalMidCode} · ${item.currency}`).join('；')
        + (unassigned.items.length > 5 ? ' …' : '')} />}
    <Form form={form} name="recon-clearing-filters" layout="vertical" initialValues={page.filters} onFinish={values => page.applyFilters(compactFilters(values))}>
      <div className="finance-search">
        <Form.Item name="entityNo" label="经营主体"><Input allowClear placeholder="AE001" maxLength={5} /></Form.Item>
        <Form.Item name="channelCode" label="渠道"><Input allowClear placeholder="NI" maxLength={64} /></Form.Item>
        <Form.Item name="currency" label="币种"><Input allowClear placeholder="USD" maxLength={3} /></Form.Item>
        <Form.Item name="status" label="状态"><Select allowClear placeholder="全部" options={statusOptions} /></Form.Item>
        <div className="configuration-actions"><Button htmlType="submit">查询</Button>
          <Button onClick={() => { resetForm(form); page.applyFilters({}); }}>重置</Button>
          {canOperate && <Button type="primary" onClick={() => setCreating(true)}>新建清算账户</Button>}</div>
      </div>
    </Form>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<ClearingAccountSummary> rowKey="clearingAccountId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 980 }}
        pagination={{ ...page.pagination, showTotal: total => `共 ${total} 条` }}
        onRow={row => ({ onClick: () => openDetail(reconRoutes.clearingAccount(row.clearingAccountId)), className: 'clickable-row' })}
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
    <FinanceNote>每个 MID + 币种只能属于一个清算账户；点行进入详情维护配置与 MID。{!canOperate && ' 当前账号没有对账操作权限。'}</FinanceNote>
    {creating && <CreateClearingDrawer cash={cash.items} onClose={() => setCreating(false)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={id => { setCreating(false); openDetail(reconRoutes.clearingAccount(id)); }} />}
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
      <ClearingSettingsFields cash={cash} entityNo={entityNo} currency={currency} />
    </Form>
  </Drawer>;
}
