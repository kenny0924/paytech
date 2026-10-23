import { useMemo } from 'react';
import { Alert, App, Button, Descriptions, Drawer, Form, Input, Select, Spin, Table } from 'antd';
import { enumLabel } from '../i18n/index';
import { useT } from '../i18n/react';
import { activeOnly, decimalIdPattern, entityNoPattern, localTime } from './finance';
import type { Currency } from './finance';
import {
  billingErrorTargets, billingFactActions, billingFactBody, decodeBillingFact, decodeBillingFactResult, factComponentCodes,
  factDirections, factLineError, factPartyRoles, factTargetBuckets, headerBoundRoles, resolvePartyId, workflowPaths,
} from './financeWorkflow';
import type { BillingFact, BillingFactFormValues, BillingFactLine, FactHeaderValues, FactLineValues } from './financeWorkflow';
import { Amount, FinanceNote } from './FinanceParts';
import { useFinanceCommand } from './useFinanceCommand';
import { ActorText, CommandFeedback, ReviewActions, useDetail, WorkflowTag } from './WorkflowParts';

const formFields = ['entityNo', 'currency', 'clientId', 'localPartnerId', 'financeEntityId', 'supersedesFactId', 'ticketNo', 'lines'];
const lineKeys = ['payerRole', 'payerId', 'payeeRole', 'payeeId', 'amount', 'direction', 'targetBucket'];
const headerKeys = ['clientId', 'entityNo', 'localPartnerId', 'financeEntityId', 'currency'];
const newLine: FactLineValues = { componentType: 'CLIENT_PRINCIPAL', payerRole: 'OPERATING_ENTITY', payeeRole: 'CLIENT', direction: 'ADJUSTMENT' };

/** 收 / 付方标识：头部绑定角色显示头部值（不可编辑），其余角色输入。 */
function PartyIdField({ name, role, header, label }: { name: (string | number)[]; role?: string; header: FactHeaderValues; label?: string }) {
  const t = useT('finance');
  if (role && headerBoundRoles[role]) {
    const value = resolvePartyId(role, undefined, header);
    return <Form.Item label={label}><Input disabled value={value || t('billing.fillHeaderFirst')} /></Form.Item>;
  }
  return <Form.Item name={name} label={label} normalize={(value: string) => value?.trim()}><Input maxLength={19} placeholder={t('billing.partyIdPlaceholder')} /></Form.Item>;
}

/**
 * 登记 MANUAL_ADJUSTMENT 计费事实（草稿）：头部 Client / 主体 / 币种与可选合作方二元组、工单号、替代事实；明细行逐行预检
 * <li>Client、Local Partner、财务主体与经营主体角色的标识取自头部；sourceSystem 与 sourceReference 由 BFF 填入。</li>
 * <li>BFF 每次登记都生成新的 sourceReference，重复提交会产生两条草稿：命令单飞、提交中按钮与表单禁用且不能关闭抽屉，成功后关闭抽屉（下次打开为空白表单）；
 * 失败后原样重试复用同一 Idempotency-Key，由 Gateway 回放首次结果。</li>
 */
export function CreateBillingFactDrawer({ defaults, currencies, minorUnits, onClose, onCreated, onAuthorizationChanged }: {
  defaults: { entityNo?: string; currency?: string }; currencies: readonly Currency[]; minorUnits: ReadonlyMap<string, number>;
  onClose: () => void; onCreated: (factId: string) => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('finance');
  const { message } = App.useApp();
  const [form] = Form.useForm<BillingFactFormValues>();
  const labelled = useMemo(() => (type: string, values: readonly string[]) => values.map(value => ({ value, label: enumLabel(t, 'enum.' + type, value) })), [t]);
  const command = useFinanceCommand(onAuthorizationChanged, form, formFields, billingErrorTargets);
  const values = (Form.useWatch([], form) ?? {}) as Partial<BillingFactFormValues>;
  const header: FactHeaderValues = { clientId: values.clientId, entityNo: values.entityNo, localPartnerId: values.localPartnerId, financeEntityId: values.financeEntityId };
  const minorUnit = values.currency ? minorUnits.get(values.currency) : undefined;
  async function submit() {
    const submitted = await form.validateFields();
    const result = await command.run(workflowPaths.billingFacts, billingFactBody(submitted), decodeBillingFactResult('DRAFT'), t('billing.createFailed'));
    if (!result) return;
    void message.success(t('billing.created', { factId: result.factId }));
    onCreated(result.factId);
  }
  const lineRule = (index: number) => ({ validator: async () => {
    const line = (form.getFieldValue(['lines', index]) ?? {}) as FactLineValues;
    const error = factLineError(line, form.getFieldsValue(['clientId', 'entityNo', 'localPartnerId', 'financeEntityId']) as FactHeaderValues, minorUnit);
    if (error) throw new Error(error);
  } });
  return <Drawer open size="min(1180px, 100vw)" rootClassName="finance-drawer finance-page" title={t('billing.createTitle')}
    onClose={() => { if (!command.saving) onClose(); }} destroyOnHidden footer={<div className="finance-drawer-footer">
      <span className="finance-spacer" />
      <Button onClick={onClose} disabled={command.saving}>{t('common.cancel')}</Button>
      <Button type="primary" loading={command.saving} disabled={command.saving || command.conflict} onClick={() => void submit()}>{t('workflow.submitForReview')}</Button>
    </div>}>
    <CommandFeedback command={command} onReload={onClose} />
    <Form form={form} name="finance-create-billing-fact" layout="vertical" disabled={command.saving || command.conflict}
      initialValues={{ entityNo: defaults.entityNo, currency: defaults.currency, lines: [newLine] }}>
      <div className="finance-form-grid finance-form-grid-3">
        <Form.Item name="entityNo" label={t('billing.field.entityNo')} normalize={(value: string) => value?.toUpperCase()}
          rules={[{ required: true, message: t('modals.entityNoRequired') }, { pattern: entityNoPattern, message: t('billing.entityNoPattern') }]}><Input maxLength={5} /></Form.Item>
        <Form.Item name="currency" label={t('field.currency')} rules={[{ required: true, message: t('validation.currencyRequired') }]}>
          <Select options={activeOnly(currencies).map(item => ({ value: item.currency, label: item.currency }))} /></Form.Item>
        <Form.Item name="clientId" label={t('billing.field.clientId')} rules={[{ required: true, message: t('billing.clientIdRequired') }, { pattern: decimalIdPattern, message: t('validation.decimalId') }]}>
          <Input maxLength={19} /></Form.Item>
        <Form.Item name="localPartnerId" label={t('billing.field.localPartnerId')} dependencies={['financeEntityId']} rules={[{ pattern: decimalIdPattern, message: t('validation.decimalId') },
          ({ getFieldValue }) => ({ validator: async (_, value?: string) => { if (!value !== !getFieldValue('financeEntityId')) throw new Error(t('billing.partnerPair')); } })]}>
          <Input maxLength={19} /></Form.Item>
        <Form.Item name="financeEntityId" label={t('billing.field.financeEntityId')} dependencies={['localPartnerId']}
          rules={[{ pattern: decimalIdPattern, message: t('validation.decimalId') }]}><Input maxLength={19} /></Form.Item>
        <Form.Item name="ticketNo" label={t('billing.field.ticketNo')} rules={[{ pattern: /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/, message: t('billing.ticketNoPattern') }]}>
          <Input maxLength={64} /></Form.Item>
        <Form.Item name="supersedesFactId" label={t('billing.field.supersedesFactId')} extra={t('billing.supersedesHint')}
          rules={[{ pattern: decimalIdPattern, message: t('validation.decimalId') }]}><Input maxLength={19} /></Form.Item>
      </div>
      <h3 className="finance-subtitle">{values.currency ? t('billing.linesTitleWithCurrency', { currency: values.currency }) : t('billing.linesTitle')}</h3>
      <Form.List name="lines" rules={[{ validator: async (_, value: FactLineValues[]) => { if (!value?.length) throw new Error(t('billing.lineRequired')); } }]}>
        {(fields, { add, remove }, { errors }) => <>
          {fields.map(field => { const line = values.lines?.[field.name] ?? {}; const first = field.name === 0;
            return <div key={field.key} className="finance-line-row finance-fact-line">
              <Form.Item name={[field.name, 'componentType']} label={first ? t('billing.field.component') : undefined} rules={[lineRule(field.name)]}
                dependencies={[...lineKeys.map(key => ['lines', field.name, key]), ...headerKeys]}>
                <Select options={labelled('factComponent', factComponentCodes)} /></Form.Item>
              <Form.Item name={[field.name, 'payerRole']} label={first ? t('billing.field.payerRole') : undefined}><Select options={labelled('factPartyRole', factPartyRoles)} /></Form.Item>
              <PartyIdField name={[field.name, 'payerId']} role={line.payerRole} header={header} label={first ? t('billing.field.payerId') : undefined} />
              <Form.Item name={[field.name, 'payeeRole']} label={first ? t('billing.field.payeeRole') : undefined}><Select options={labelled('factPartyRole', factPartyRoles)} /></Form.Item>
              <PartyIdField name={[field.name, 'payeeId']} role={line.payeeRole} header={header} label={first ? t('billing.field.payeeId') : undefined} />
              <Form.Item name={[field.name, 'amount']} label={first ? t('field.amount') : undefined}><Input inputMode="decimal" placeholder="0.00" /></Form.Item>
              <Form.Item name={[field.name, 'direction']} label={first ? t('field.side') : undefined}><Select options={labelled('factDirection', factDirections)} /></Form.Item>
              <Form.Item name={[field.name, 'targetBucket']} label={first ? t('billing.field.targetBucket') : undefined}>
                <Select allowClear placeholder={t('billing.targetBucketPlaceholder')} disabled={line.payeeRole !== 'CLIENT' && line.payeeRole !== 'LOCAL_PARTNER'}
                  options={factTargetBuckets.map(value => ({ value, label: value }))} /></Form.Item>
              <Button type="text" danger className={first ? 'finance-line-remove is-first' : 'finance-line-remove'} disabled={fields.length <= 1}
                onClick={() => remove(field.name)}>{t('workflow.remove')}</Button>
            </div>; })}
          <Form.ErrorList errors={errors} />
          <Button type="dashed" disabled={fields.length >= 100} onClick={() => add(newLine)}>{t('billing.addLine')}</Button>
        </>}
      </Form.List>
    </Form>
    <FinanceNote>{t('billing.createNote')}</FinanceNote>
  </Drawer>;
}

/**
 * 计费事实详情：头部、引用、明细行与发布状态
 * <li>草稿：有复核权限且不是制单人可批准；有制单权限可作废。两者都不带请求体。</li>
 */
export function BillingFactDrawer({ factId, minorUnits, canManage, canApprove, actorId, onClose, onChanged, onAuthorizationChanged }: {
  factId: string; minorUnits: ReadonlyMap<string, number>; canManage: boolean; canApprove: boolean; actorId: string;
  onClose: () => void; onChanged: () => void; onAuthorizationChanged: () => void;
}) {
  const t = useT('finance');
  const { message } = App.useApp();
  const decode = useMemo(() => decodeBillingFact(factId), [factId]);
  const detail = useDetail<BillingFact>(workflowPaths.billingFact(factId), decode, onAuthorizationChanged, t('billing.detailLoadFailed'));
  const command = useFinanceCommand(onAuthorizationChanged, undefined, [], billingErrorTargets);
  const fact = detail.item;
  async function act(action: 'approve' | 'void') {
    const result = await command.run(workflowPaths.billingFactAction(factId, action), undefined,
      decodeBillingFactResult(action === 'approve' ? 'APPROVED' : 'VOIDED', factId), action === 'approve' ? t('billing.approveFailed') : t('billing.voidFailed'));
    if (!result) return;
    void message.success(action === 'approve' ? t('billing.approved') : t('billing.voided'));
    detail.reload(); onChanged();
  }
  const minorUnit = fact ? minorUnits.get(fact.currency) : undefined;
  const actions = fact ? billingFactActions(fact, actorId, canManage, canApprove) : { approve: false, void: false };
  const selfNote = fact && canApprove && fact.status === 'DRAFT' && fact.makerId === actorId ? t('billing.selfReview') : undefined;
  const party = (role: string, value: string) => <div><div>{enumLabel(t, 'enum.factPartyRole', role)}</div><div className="cell-meta mono">{value}</div></div>;
  return <Drawer open size={980} rootClassName="finance-drawer finance-page" onClose={() => { if (!command.saving) onClose(); }} destroyOnHidden
    title={t('billing.detailTitle', { factId })}>
    {detail.error ? <Alert type="warning" showIcon title={detail.error} /> : !fact ? <Spin /> : <>
      <CommandFeedback command={command} onReload={detail.reload} />
      <Descriptions column={2} size="small" className="finance-detail-head" items={[
        { key: 'status', label: t('field.status'), children: <WorkflowTag status={fact.status} enumType="billingFactStatus" /> },
        { key: 'type', label: t('ledger.lines.column.type'), children: t('common.labelWithId', { label: enumLabel(t, 'enum.factType', fact.factType), id: fact.originType }) },
        { key: 'scope', label: t('billing.field.scope'), children: `${fact.entityNo} / ${fact.currency}` },
        { key: 'client', label: t('billing.column.client'), children: fact.clientId ? <span className="mono">{fact.clientId}</span> : '—' },
        { key: 'partner', label: t('billing.field.partner'), children: fact.localPartnerId ? <span className="mono">{fact.localPartnerId} / {fact.financeEntityId}</span> : '—' },
        { key: 'gross', label: t('billing.column.grossAmount'), children: <Amount value={fact.grossAmount} minorUnit={minorUnit} /> },
        { key: 'source', label: t('posting.field.movementRef'), children: <span className="mono finance-code">{fact.sourceSystem} · {fact.sourceEventKey}</span> },
        { key: 'version', label: t('posting.field.factVersion'), children: fact.factVersion },
        { key: 'supersedes', label: t('posting.field.supersedes'), children: fact.supersedesFactId ? <span className="mono">{fact.supersedesFactId}</span> : '—' },
        { key: 'refs', label: t('billing.field.refs'), children: fact.refs.length ? fact.refs.map(ref => `${ref.refType} ${ref.refId}`).join(t('common.listSeparator')) : '—' },
        { key: 'maker', label: t('workflow.maker'), children: <ActorText value={fact.makerId} actorId={actorId} /> },
        { key: 'checker', label: t('workflow.checker'), children: <ActorText value={fact.checkerId} actorId={actorId} /> },
        { key: 'business', label: t('billing.field.businessTime'), children: localTime(fact.businessTime) },
        { key: 'approved', label: t('workflow.approvedAt'), children: fact.approvedTime ? localTime(fact.approvedTime) : '—' },
        { key: 'published', label: t('billing.field.published'), children: fact.publishedAt ? t('billing.publishedAt', { time: localTime(fact.publishedAt) })
          : fact.status === 'APPROVED' || fact.status === 'SUPERSEDED' ? t('billing.pendingPublish') : t('billing.notPublished') },
      ]} />
      <h3 className="finance-subtitle">{t('billing.linesCount', { count: fact.lines.length })}</h3>
      <section className="table-panel">
        <Table<BillingFactLine> rowKey="lineNo" size="small" tableLayout="fixed" dataSource={fact.lines} pagination={false} scroll={{ x: 820 }} columns={[
          { title: t('field.lineNo'), dataIndex: 'lineNo', width: 56 },
          { title: t('billing.field.component'), width: 140, render: (_, row) => enumLabel(t, 'enum.factComponent', row.componentType) },
          { title: t('billing.column.payer'), width: 150, render: (_, row) => party(row.payerRole, row.payerId) },
          { title: t('billing.column.payee'), width: 150, render: (_, row) => party(row.payeeRole, row.payeeId) },
          { title: t('field.side'), width: 70, render: (_, row) => enumLabel(t, 'enum.factDirection', row.direction) },
          { title: t('billing.field.targetBucket'), width: 110, render: (_, row) => row.targetBucket ?? '—' },
          { title: t('field.amount'), width: 130, align: 'right', render: (_, row) => <Amount value={row.postedAmount} minorUnit={minorUnit} /> },
        ]} />
      </section>
      <ReviewActions saving={command.saving} note={selfNote} actions={[
        ...(actions.void ? [{ key: 'void', label: t('billing.voidDraft'), danger: true, confirm: t('billing.confirmVoid'), onConfirm: () => void act('void') }] : []),
        ...(actions.approve ? [{ key: 'approve', label: t('billing.approve'), primary: true, confirm: t('billing.confirmApprove'),
          onConfirm: () => void act('approve') }] : []),
      ]} />
    </>}
  </Drawer>;
}
