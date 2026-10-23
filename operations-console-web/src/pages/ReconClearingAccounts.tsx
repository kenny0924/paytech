import { useMemo, useState } from 'react';
import { Alert, App, Button, Drawer, Form, Input, Select, Table } from 'antd';
import { Navigate, useParams, useSearchParams } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { localTime } from '../finance/finance';
import { FinanceNote, LoadFailure } from '../finance/FinanceParts';
import { useT } from '../i18n/react';
import { ClearingSettingsFields, settingsBody } from '../reconciliation/ClearingSettings';
import type { ClearingSettingsValues } from '../reconciliation/ClearingSettings';
import { ClearingAccountDetail } from '../reconciliation/detail/ClearingAccountDetail';
import { useOpenDetail } from '../reconciliation/ReconDetail';
import { clearingStatusEnum, decodeClearingAccount, decodeWithId, hasReconAction, pageOf, reconActions, reconOptions, reconPaths } from '../reconciliation/reconModel';
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
  const t = useT('recon'), tNav = useT('nav');
  const openDetail = useOpenDetail();
  const canOperate = hasReconAction(resources, reconActions.operate);
  const statusOptions = useMemo(() => reconOptions(t, clearingStatusEnum), [t]);
  const [creating, setCreating] = useState(false);
  const page = useReconListQuery<ClearingAccountSummary>(reconPaths.clearingAccounts, decodePage, filterKeys);
  const unassigned = useUnassignedMids(onAuthorizationChanged);
  const cash = useCashAccounts(onAuthorizationChanged);
  const [form] = Form.useForm<Record<string, string | undefined>>();
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>{tNav('resource.OPERATIONS_FINANCE_RECON_CLEARING')}</h1></div>
    {unassigned.items.length > 0 && <Alert type="warning" showIcon className="finance-banner"
      title={t('clearing.unassignedBanner', { count: unassigned.items.length })}
      description={unassigned.items.slice(0, 5).map(item => `${item.channelCode} · ${item.entityNo} · ${item.externalMidCode} · ${item.currency}`).join(t('common.listSeparator'))
        + (unassigned.items.length > 5 ? ' …' : '')} />}
    <Form form={form} name="recon-clearing-filters" layout="vertical" initialValues={page.filters} onFinish={values => page.applyFilters(compactFilters(values))}>
      <div className="finance-search">
        <Form.Item name="entityNo" label={t('field.operatingEntity')}><Input allowClear placeholder="AE001" maxLength={5} /></Form.Item>
        <Form.Item name="channelCode" label={t('field.channel')}><Input allowClear placeholder="NI" maxLength={64} /></Form.Item>
        <Form.Item name="currency" label={t('field.currency')}><Input allowClear placeholder="USD" maxLength={3} /></Form.Item>
        <Form.Item name="status" label={t('field.status')}><Select allowClear placeholder={t('placeholder.all')} options={statusOptions} /></Form.Item>
        <div className="configuration-actions"><Button htmlType="submit">{t('common.search')}</Button>
          <Button onClick={() => { resetForm(form); page.applyFilters({}); }}>{t('common.reset')}</Button>
          {canOperate && <Button type="primary" onClick={() => setCreating(true)}>{t('clearing.create')}</Button>}</div>
      </div>
    </Form>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<ClearingAccountSummary> rowKey="clearingAccountId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 980 }}
        pagination={{ ...page.pagination, showTotal: total => t('common.total', { total }) }}
        onRow={row => ({ onClick: () => openDetail(reconRoutes.clearingAccount(row.clearingAccountId)), className: 'clickable-row' })}
        locale={{ emptyText: <EmptyHint text={canOperate ? t('clearing.empty') : t('clearing.emptyFiltered')} /> }}
        columns={[
          { title: t('clearing.column.code'), width: 200, render: (_, row) => <a className="mono">{row.clearingAccountCode}</a> },
          { title: t('field.channel'), width: 100, dataIndex: 'channelCode' },
          { title: t('field.operatingEntity'), width: 100, dataIndex: 'entityNo' },
          { title: t('field.currency'), width: 90, dataIndex: 'currency' },
          { title: t('clearing.column.midCount'), width: 80, align: 'right', dataIndex: 'midCount' },
          { title: t('clearingSettings.settlementCashAccount'), width: 200, render: (_, row) => { const account = row.settlementCashAccountId ? cash.byId.get(row.settlementCashAccountId) : undefined;
            return row.settlementCashAccountId ? (account ? t('cash.nameWithLast4', { name: account.accountName, last4: account.accountLast4 }) : row.settlementCashAccountId)
              : <span className="muted">{t('clearingDetail.noSettlementAccount')}</span>; } },
          { title: t('field.status'), width: 90, render: (_, row) => <ReconTag status={row.status} kind={clearingStatusEnum} /> },
          { title: t('field.updatedAt'), width: 150, render: (_, row) => localTime(row.updateTime) },
        ]} />
    </section>}
    <FinanceNote>{t('clearing.note')}{!canOperate && ' ' + t('permission.noOperate')}</FinanceNote>
    {creating && <CreateClearingDrawer cash={cash.items} onClose={() => setCreating(false)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={id => { setCreating(false); openDetail(reconRoutes.clearingAccount(id)); }} />}
  </div>;
}

function CreateClearingDrawer({ cash, onClose, onCreated, onAuthorizationChanged }: {
  cash: readonly CashAccount[]; onClose: () => void; onCreated: (id: string) => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('recon');
  const { message } = App.useApp();
  const [form] = Form.useForm<CreateValues>();
  const command = useReconCommand(onAuthorizationChanged, form);
  const entityNo = Form.useWatch('entityNo', form); const currency = Form.useWatch('currency', form);
  async function submit() {
    const values = await form.validateFields();
    const result = await command.run(reconPaths.clearingAccounts, 'POST', { clearingAccountCode: values.clearingAccountCode.trim(),
      channelCode: values.channelCode.trim(), entityNo: values.entityNo.trim(), currency: values.currency.trim(), ...settingsBody(values) },
    decodeClearingAccount(), t('clearing.createFailed'));
    if (!result) return;
    void message.success(t('clearing.created', { code: result.clearingAccountCode }));
    onCreated(result.clearingAccountId);
  }
  return <Drawer open size="min(760px, 100vw)" rootClassName="finance-drawer finance-page" title={t('clearing.create')} destroyOnHidden
    onClose={() => { if (!command.saving) onClose(); }} footer={<div className="finance-drawer-footer"><span className="finance-spacer" />
      <Button onClick={onClose} disabled={command.saving}>{t('common.cancel')}</Button>
      <Button type="primary" loading={command.saving} onClick={() => void submit()}>{t('clearing.createButton')}</Button></div>}>
    <ReconCommandFeedback command={command} onReload={onClose} />
    <Form form={form} name="recon-create-clearing" layout="vertical" disabled={command.saving}
      initialValues={{ unmatchedWaitDays: 3, costToleranceAmount: '0.10', cashGraceDays: 2 }}>
      <div className="finance-form-grid">
        <Form.Item name="clearingAccountCode" label={t('clearing.field.code')} extra={t('clearing.codeHint')}
          rules={[{ required: true, pattern: /^[A-Z][A-Z0-9_-]{1,63}$/, message: t('clearing.codePattern') }]}>
          <Input placeholder="NI-AE001-USD" maxLength={64} /></Form.Item>
        <Form.Item name="channelCode" label={t('clearing.field.channelCode')} rules={[{ required: true, pattern: /^[A-Z][A-Z0-9_]{1,63}$/, message: t('clearing.channelCodePattern') }]}>
          <Input placeholder="NI" maxLength={64} /></Form.Item>
        <Form.Item name="entityNo" label={t('field.operatingEntity')} rules={[{ required: true, pattern: /^[A-Z]{2}[0-9]{3}$/, message: t('validation.entityNo') }]}>
          <Input placeholder="AE001" maxLength={5} /></Form.Item>
        <Form.Item name="currency" label={t('clearing.field.currency')} rules={[{ required: true, pattern: /^[A-Z]{3}$/, message: t('validation.currency') }]}>
          <Input placeholder="USD" maxLength={3} /></Form.Item>
      </div>
      <ClearingSettingsFields cash={cash} entityNo={entityNo} currency={currency} />
    </Form>
  </Drawer>;
}
