import { useMemo, useState } from 'react';
import { App, Button, Drawer, Form, Input, Select, Table } from 'antd';
import { Navigate, useParams, useSearchParams } from 'react-router-dom';
import type { ConsoleResource } from '../api/types';
import { FinanceNote, LoadFailure } from '../finance/FinanceParts';
import { ActorText } from '../finance/WorkflowParts';
import { useT } from '../i18n/react';
import { CashAccountDetail } from '../reconciliation/detail/CashAccountDetail';
import { useOpenDetail } from '../reconciliation/ReconDetail';
import {
  accountPurposeEnum, cashStatusEnum, closePendingEnum, decimalIdPattern, decodeWithId, entityTypeEnum, hasReconAction, pageOf, reconActions,
  reconLabel, reconOptions, reconPaths,
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
  const t = useT('recon'), tNav = useT('nav');
  const openDetail = useOpenDetail();
  const canOperate = hasReconAction(resources, reconActions.operate);
  const canApprove = hasReconAction(resources, reconActions.approve);
  const [creating, setCreating] = useState(false);
  const page = useReconListQuery<CashAccount>(reconPaths.cashAccounts, decodePage, filterKeys);
  const [form] = Form.useForm<Record<string, string | undefined>>();
  return <div className="configuration-page finance-page"><div className="page-heading"><h1>{tNav('resource.OPERATIONS_FINANCE_RECON_CASH')}</h1></div>
    <Form form={form} name="recon-cash-filters" layout="vertical" initialValues={page.filters} onFinish={values => page.applyFilters(compactFilters(values))}>
      <div className="finance-search">
        <Form.Item name="entityType" label={t('field.entityType')}><Select allowClear placeholder={t('placeholder.all')} options={reconOptions(t, entityTypeEnum)} /></Form.Item>
        <Form.Item name="entityId" label={t('field.entity')}><Input allowClear placeholder="AE001" maxLength={64} /></Form.Item>
        <Form.Item name="currency" label={t('field.currency')}><Input allowClear placeholder="USD" maxLength={3} /></Form.Item>
        <Form.Item name="accountPurpose" label={t('field.purpose')}><Select allowClear placeholder={t('placeholder.all')} options={reconOptions(t, accountPurposeEnum)} /></Form.Item>
        <Form.Item name="status" label={t('field.status')}><Select allowClear placeholder={t('placeholder.all')} options={reconOptions(t, cashStatusEnum)} /></Form.Item>
        <div className="configuration-actions"><Button htmlType="submit">{t('common.search')}</Button>
          <Button onClick={() => { resetForm(form); page.applyFilters({}); }}>{t('common.reset')}</Button>
          {canOperate && <Button type="primary" onClick={() => setCreating(true)}>{t('cash.create')}</Button>}</div>
      </div>
    </Form>
    {page.error ? <LoadFailure message={page.error} onRetry={page.refresh} /> : <section className="table-panel">
      <Table<CashAccount> rowKey="cashAccountId" tableLayout="fixed" dataSource={page.data.items} loading={page.loading} scroll={{ x: 1100 }}
        pagination={{ ...page.pagination, showTotal: total => t('common.total', { total }) }} onRow={row => ({ onClick: () => openDetail(reconRoutes.cashAccount(row.cashAccountId)), className: 'clickable-row' })}
        locale={{ emptyText: <EmptyHint text={canOperate ? t('cash.empty') : t('cash.emptyFiltered')} /> }}
        columns={[
          { title: t('cash.column.name'), width: 200, render: (_, row) => <a>{row.accountName}</a> },
          { title: t('field.entity'), width: 160, render: (_, row) => `${reconLabel(t, entityTypeEnum, row.entityType)} · ${row.entityId}` },
          { title: t('field.purpose'), width: 100, render: (_, row) => reconLabel(t, accountPurposeEnum, row.accountPurpose) },
          { title: t('cash.column.bank'), width: 100, dataIndex: 'bankCode' },
          { title: t('field.currency'), width: 90, dataIndex: 'currency' },
          { title: t('cash.column.last4'), width: 80, render: (_, row) => <span className="mono">{row.accountLast4}</span> },
          { title: t('field.status'), width: 140, render: (_, row) => <>{<ReconTag status={row.status} kind={cashStatusEnum} />}{row.pendingAction === 'CLOSE' && <ReconTag status="PENDING_VERIFICATION" kind={closePendingEnum} />}</> },
          { title: t('field.maker'), width: 160, render: (_, row) => <ActorText value={row.makerId} actorId={actorId} /> },
        ]} />
    </section>}
    <FinanceNote>{t('cash.note')}{!canOperate && ' ' + t('permission.noOperate')}{!canApprove && ' ' + t('permission.noApprove')}</FinanceNote>
    {creating && <CreateCashDrawer onClose={() => setCreating(false)} onAuthorizationChanged={onAuthorizationChanged}
      onCreated={id => { setCreating(false); openDetail(reconRoutes.cashAccount(id)); }} />}
  </div>;
}

function CreateCashDrawer({ onClose, onCreated, onAuthorizationChanged }: { onClose: () => void; onCreated: (id: string) => void; onAuthorizationChanged: () => void }) {
  const t = useT('recon');
  const { message } = App.useApp();
  const [form] = Form.useForm<CreateValues>();
  const entityTypeOptions = useMemo(() => reconOptions(t, entityTypeEnum), [t]);
  const purposeOptions = useMemo(() => reconOptions(t, accountPurposeEnum), [t]);
  const command = useReconCommand(onAuthorizationChanged, form);
  const entityType = Form.useWatch('entityType', form);
  async function submit() {
    const values = await form.validateFields();
    const body = Object.fromEntries(Object.entries(values).map(([key, value]) => [key, typeof value === 'string' ? value.trim() : value]));
    const result = await command.run(reconPaths.cashAccounts, 'POST', body, decodeWithId<CashAccount>('cashAccountId'), t('cash.createFailed'));
    if (!result) return;
    form.resetFields();
    void message.success(t('cash.created', { last4: result.accountLast4 }));
    onCreated(result.cashAccountId);
  }
  return <Drawer open size="min(720px, 100vw)" rootClassName="finance-drawer finance-page" title={t('cash.create')} destroyOnHidden
    onClose={() => { if (!command.saving) onClose(); }} footer={<div className="finance-drawer-footer"><span className="finance-spacer" />
      <Button onClick={onClose} disabled={command.saving}>{t('common.cancel')}</Button>
      <Button type="primary" loading={command.saving} onClick={() => void submit()}>{t('exception.submitForReview')}</Button></div>}>
    <ReconCommandFeedback command={command} onReload={onClose} />
    <Form form={form} name="recon-create-cash" layout="vertical" disabled={command.saving} autoComplete="off"
      initialValues={{ entityType: 'OPERATING_ENTITY', accountPurpose: 'CHANNEL_SETTLEMENT' }}>
      <div className="finance-form-grid">
        <Form.Item name="entityType" label={t('field.entityType')} rules={[{ required: true }]}><Select options={entityTypeOptions} /></Form.Item>
        <Form.Item name="entityId" label={entityType === 'FINANCE_ENTITY' ? t('cash.field.financeEntityId') : t('cash.field.entityNo')}
          rules={[{ required: true, pattern: entityType === 'FINANCE_ENTITY' ? decimalIdPattern : /^[A-Z]{2}[0-9]{3}$/,
            message: entityType === 'FINANCE_ENTITY' ? t('validation.decimalId') : t('validation.entityNo') }]}><Input maxLength={19} /></Form.Item>
        <Form.Item name="accountPurpose" label={t('cash.field.purpose')} extra={t('cash.purposeHint')} rules={[{ required: true }]}><Select options={purposeOptions} /></Form.Item>
        <Form.Item name="currency" label={t('field.currency')} rules={[{ required: true, pattern: /^[A-Z]{3}$/, message: t('validation.currency') }]}><Input placeholder="USD" maxLength={3} /></Form.Item>
        <Form.Item name="bankCode" label={t('cash.field.bankCode')} rules={[{ required: true, whitespace: true, max: 64 }]}><Input maxLength={64} /></Form.Item>
        <Form.Item name="accountName" label={t('cash.field.accountName')} rules={[{ required: true, whitespace: true, max: 128 }]}><Input maxLength={128} /></Form.Item>
        <Form.Item name="accountNumber" label={t('cash.field.accountNumber')} extra={t('cash.accountNumberHint')}
          rules={[{ required: true, pattern: /^[A-Za-z0-9]{4,34}$/, message: t('cash.accountNumberPattern') }]}>
          <Input.Password visibilityToggle maxLength={34} autoComplete="off" /></Form.Item>
      </div>
    </Form>
  </Drawer>;
}
