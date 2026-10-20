import { useMemo } from 'react';
import { Alert, App, Button, Descriptions, Drawer, Form, Input, Select, Spin, Table } from 'antd';
import { activeOnly, decimalIdPattern, entityNoPattern, localTime } from './finance';
import type { Currency } from './finance';
import {
  billingErrorTargets, billingFactActions, billingFactBody, billingFactStatusLabels, decodeBillingFact, decodeBillingFactResult, factComponentLabels,
  factDirectionLabels, factLineError, factPartyRoleLabels, factTargetBuckets, factTypeLabels, headerBoundRoles, resolvePartyId, workflowPaths,
} from './financeWorkflow';
import type { BillingFact, BillingFactFormValues, BillingFactLine, FactHeaderValues, FactLineValues } from './financeWorkflow';
import { Amount, FinanceNote } from './FinanceParts';
import { useFinanceCommand } from './useFinanceCommand';
import { ActorText, CommandFeedback, ReviewActions, useDetail, WorkflowTag } from './WorkflowParts';

const labelled = (labels: Readonly<Record<string, string>>) => Object.entries(labels).map(([value, label]) => ({ value, label }));
const formFields = ['entityNo', 'currency', 'clientId', 'localPartnerId', 'financeEntityId', 'supersedesFactId', 'ticketNo', 'lines'];
const lineKeys = ['payerRole', 'payerId', 'payeeRole', 'payeeId', 'amount', 'direction', 'targetBucket'];
const headerKeys = ['clientId', 'entityNo', 'localPartnerId', 'financeEntityId', 'currency'];
const newLine: FactLineValues = { componentType: 'CLIENT_PRINCIPAL', payerRole: 'OPERATING_ENTITY', payeeRole: 'CLIENT', direction: 'ADJUSTMENT' };

/** 收 / 付方标识：头部绑定角色显示头部值（不可编辑），其余角色输入。 */
function PartyIdField({ name, role, header, label }: { name: (string | number)[]; role?: string; header: FactHeaderValues; label?: string }) {
  if (role && headerBoundRoles[role]) {
    const value = resolvePartyId(role, undefined, header);
    return <Form.Item label={label}><Input disabled value={value || '先在头部填写'} /></Form.Item>;
  }
  return <Form.Item name={name} label={label} normalize={(value: string) => value?.trim()}><Input maxLength={19} placeholder="正十进制 ID" /></Form.Item>;
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
  const { message } = App.useApp();
  const [form] = Form.useForm<BillingFactFormValues>();
  const command = useFinanceCommand(onAuthorizationChanged, form, formFields, billingErrorTargets);
  const values = (Form.useWatch([], form) ?? {}) as Partial<BillingFactFormValues>;
  const header: FactHeaderValues = { clientId: values.clientId, entityNo: values.entityNo, localPartnerId: values.localPartnerId, financeEntityId: values.financeEntityId };
  const minorUnit = values.currency ? minorUnits.get(values.currency) : undefined;
  async function submit() {
    const submitted = await form.validateFields();
    const result = await command.run(workflowPaths.billingFacts, billingFactBody(submitted), decodeBillingFactResult('DRAFT'), '登记计费事实失败');
    if (!result) return;
    void message.success(`已登记计费事实 ${result.factId}，等待复核`);
    onCreated(result.factId);
  }
  const lineRule = (index: number) => ({ validator: async () => {
    const line = (form.getFieldValue(['lines', index]) ?? {}) as FactLineValues;
    const error = factLineError(line, form.getFieldsValue(['clientId', 'entityNo', 'localPartnerId', 'financeEntityId']) as FactHeaderValues, minorUnit);
    if (error) throw new Error(error);
  } });
  return <Drawer open size="min(1180px, 100vw)" rootClassName="finance-drawer finance-page" title="登记计费事实 · 人工调整（MANUAL_ADJUSTMENT）"
    onClose={() => { if (!command.saving) onClose(); }} destroyOnHidden footer={<div className="finance-drawer-footer">
      <span className="finance-spacer" />
      <Button onClick={onClose} disabled={command.saving}>取消</Button>
      <Button type="primary" loading={command.saving} disabled={command.saving || command.conflict} onClick={() => void submit()}>提交复核</Button>
    </div>}>
    <CommandFeedback command={command} onReload={onClose} />
    <Form form={form} name="finance-create-billing-fact" layout="vertical" disabled={command.saving || command.conflict}
      initialValues={{ entityNo: defaults.entityNo, currency: defaults.currency, lines: [newLine] }}>
      <div className="finance-form-grid finance-form-grid-3">
        <Form.Item name="entityNo" label="经营主体（entityNo）" normalize={(value: string) => value?.toUpperCase()}
          rules={[{ required: true, message: '请填写 entityNo' }, { pattern: entityNoPattern, message: '两位大写国家码加三位序号，如 AE001' }]}><Input maxLength={5} /></Form.Item>
        <Form.Item name="currency" label="币种" rules={[{ required: true, message: '请选择币种' }]}>
          <Select options={activeOnly(currencies).map(item => ({ value: item.currency, label: item.currency }))} /></Form.Item>
        <Form.Item name="clientId" label="Client ID" rules={[{ required: true, message: '请填写 Client ID' }, { pattern: decimalIdPattern, message: '正十进制整数字符串' }]}>
          <Input maxLength={19} /></Form.Item>
        <Form.Item name="localPartnerId" label="Local Partner ID（可选）" dependencies={['financeEntityId']} rules={[{ pattern: decimalIdPattern, message: '正十进制整数字符串' },
          ({ getFieldValue }) => ({ validator: async (_, value?: string) => { if (!value !== !getFieldValue('financeEntityId')) throw new Error('Local Partner 与财务主体须同时填写或同时留空'); } })]}>
          <Input maxLength={19} /></Form.Item>
        <Form.Item name="financeEntityId" label="平台服务费收取方财务主体（可选）" dependencies={['localPartnerId']}
          rules={[{ pattern: decimalIdPattern, message: '正十进制整数字符串' }]}><Input maxLength={19} /></Form.Item>
        <Form.Item name="ticketNo" label="工单号（可选）" rules={[{ pattern: /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/, message: '字母或数字开头，最长 64 位' }]}>
          <Input maxLength={64} /></Form.Item>
        <Form.Item name="supersedesFactId" label="替代事实 ID（可选）" extra="同类型、同 Client、同主体、同币种的已批准事实"
          rules={[{ pattern: decimalIdPattern, message: '正十进制整数字符串' }]}><Input maxLength={19} /></Form.Item>
      </div>
      <h3 className="finance-subtitle">明细行{values.currency ? ` · ${values.currency}` : ''}</h3>
      <Form.List name="lines" rules={[{ validator: async (_, value: FactLineValues[]) => { if (!value?.length) throw new Error('至少需要一行明细'); } }]}>
        {(fields, { add, remove }, { errors }) => <>
          {fields.map(field => { const line = values.lines?.[field.name] ?? {}; const first = field.name === 0;
            return <div key={field.key} className="finance-line-row finance-fact-line">
              <Form.Item name={[field.name, 'componentType']} label={first ? '金额组件' : undefined} rules={[lineRule(field.name)]}
                dependencies={[...lineKeys.map(key => ['lines', field.name, key]), ...headerKeys]}>
                <Select options={labelled(factComponentLabels)} /></Form.Item>
              <Form.Item name={[field.name, 'payerRole']} label={first ? '付方角色' : undefined}><Select options={labelled(factPartyRoleLabels)} /></Form.Item>
              <PartyIdField name={[field.name, 'payerId']} role={line.payerRole} header={header} label={first ? '付方 ID' : undefined} />
              <Form.Item name={[field.name, 'payeeRole']} label={first ? '收方角色' : undefined}><Select options={labelled(factPartyRoleLabels)} /></Form.Item>
              <PartyIdField name={[field.name, 'payeeId']} role={line.payeeRole} header={header} label={first ? '收方 ID' : undefined} />
              <Form.Item name={[field.name, 'amount']} label={first ? '金额' : undefined}><Input inputMode="decimal" placeholder="0.00" /></Form.Item>
              <Form.Item name={[field.name, 'direction']} label={first ? '方向' : undefined}><Select options={labelled(factDirectionLabels)} /></Form.Item>
              <Form.Item name={[field.name, 'targetBucket']} label={first ? '目标余额桶' : undefined}>
                <Select allowClear placeholder="默认 AVAILABLE" disabled={line.payeeRole !== 'CLIENT' && line.payeeRole !== 'LOCAL_PARTNER'}
                  options={factTargetBuckets.map(value => ({ value, label: value }))} /></Form.Item>
              <Button type="text" danger className={first ? 'finance-line-remove is-first' : 'finance-line-remove'} disabled={fields.length <= 1}
                onClick={() => remove(field.name)}>删除</Button>
            </div>; })}
          <Form.ErrorList errors={errors} />
          <Button type="dashed" disabled={fields.length >= 100} onClick={() => add(newLine)}>添加明细行</Button>
        </>}
      </Form.List>
    </Form>
    <FinanceNote>每行的错误提示在「金额组件」下方。目标余额桶只用于收方为 Client 或 Local Partner 的行，留空按 AVAILABLE；分润与平台服务费需要头部合作方二元组，平台服务费收方必须是财务主体。</FinanceNote>
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
  const { message } = App.useApp();
  const decode = useMemo(() => decodeBillingFact(factId), [factId]);
  const detail = useDetail<BillingFact>(workflowPaths.billingFact(factId), decode, onAuthorizationChanged, '计费事实详情加载失败');
  const command = useFinanceCommand(onAuthorizationChanged, undefined, [], billingErrorTargets);
  const fact = detail.item;
  async function act(action: 'approve' | 'void') {
    const result = await command.run(workflowPaths.billingFactAction(factId, action), undefined,
      decodeBillingFactResult(action === 'approve' ? 'APPROVED' : 'VOIDED', factId), action === 'approve' ? '批准计费事实失败' : '作废计费事实失败');
    if (!result) return;
    void message.success(action === 'approve' ? '已批准，入账由 account / ledger 异步完成' : '已作废');
    detail.reload(); onChanged();
  }
  const minorUnit = fact ? minorUnits.get(fact.currency) : undefined;
  const actions = fact ? billingFactActions(fact, actorId, canManage, canApprove) : { approve: false, void: false };
  const selfNote = fact && canApprove && fact.status === 'DRAFT' && fact.makerId === actorId ? '你是制单人，不能批准自己登记的事实' : undefined;
  const party = (role: string, value: string) => <div><div>{factPartyRoleLabels[role] ?? role}</div><div className="cell-meta mono">{value}</div></div>;
  return <Drawer open size={980} rootClassName="finance-drawer finance-page" onClose={() => { if (!command.saving) onClose(); }} destroyOnHidden
    title={`计费事实 · ${factId}`}>
    {detail.error ? <Alert type="warning" showIcon title={detail.error} /> : !fact ? <Spin /> : <>
      <CommandFeedback command={command} onReload={detail.reload} />
      <Descriptions column={2} size="small" className="finance-detail-head" items={[
        { key: 'status', label: '状态', children: <WorkflowTag status={fact.status} labels={billingFactStatusLabels} /> },
        { key: 'type', label: '类型', children: `${factTypeLabels[fact.factType] ?? fact.factType}（${fact.originType}）` },
        { key: 'scope', label: '主体 / 币种', children: `${fact.entityNo} / ${fact.currency}` },
        { key: 'client', label: 'Client', children: fact.clientId ? <span className="mono">{fact.clientId}</span> : '—' },
        { key: 'partner', label: 'Local Partner / 财务主体', children: fact.localPartnerId ? <span className="mono">{fact.localPartnerId} / {fact.financeEntityId}</span> : '—' },
        { key: 'gross', label: '总额', children: <Amount value={fact.grossAmount} minorUnit={minorUnit} /> },
        { key: 'source', label: '来源引用', children: <span className="mono finance-code">{fact.sourceSystem} · {fact.sourceEventKey}</span> },
        { key: 'version', label: '事实版本', children: fact.factVersion },
        { key: 'supersedes', label: '替代事实', children: fact.supersedesFactId ? <span className="mono">{fact.supersedesFactId}</span> : '—' },
        { key: 'refs', label: '引用', children: fact.refs.length ? fact.refs.map(ref => `${ref.refType} ${ref.refId}`).join('；') : '—' },
        { key: 'maker', label: '制单人', children: <ActorText value={fact.makerId} actorId={actorId} /> },
        { key: 'checker', label: '复核人', children: <ActorText value={fact.checkerId} actorId={actorId} /> },
        { key: 'business', label: '业务时间', children: localTime(fact.businessTime) },
        { key: 'approved', label: '批准时间', children: fact.approvedTime ? localTime(fact.approvedTime) : '—' },
        { key: 'published', label: '入账事件', children: fact.publishedAt ? `已发布 ${localTime(fact.publishedAt)}`
          : fact.status === 'APPROVED' || fact.status === 'SUPERSEDED' ? '待发布' : '不发布' },
      ]} />
      <h3 className="finance-subtitle">明细行（{fact.lines.length}）</h3>
      <section className="table-panel">
        <Table<BillingFactLine> rowKey="lineNo" size="small" tableLayout="fixed" dataSource={fact.lines} pagination={false} scroll={{ x: 820 }} columns={[
          { title: '行号', dataIndex: 'lineNo', width: 56 },
          { title: '金额组件', width: 140, render: (_, row) => factComponentLabels[row.componentType] ?? row.componentType },
          { title: '付方', width: 150, render: (_, row) => party(row.payerRole, row.payerId) },
          { title: '收方', width: 150, render: (_, row) => party(row.payeeRole, row.payeeId) },
          { title: '方向', width: 70, render: (_, row) => factDirectionLabels[row.direction] ?? row.direction },
          { title: '目标余额桶', width: 110, render: (_, row) => row.targetBucket ?? '—' },
          { title: '金额', width: 130, align: 'right', render: (_, row) => <Amount value={row.postedAmount} minorUnit={minorUnit} /> },
        ]} />
      </section>
      <ReviewActions saving={command.saving} note={selfNote} actions={[
        ...(actions.void ? [{ key: 'void', label: '作废草稿', danger: true, confirm: '作废后不入账、不能再批准。确认作废？', onConfirm: () => void act('void') }] : []),
        ...(actions.approve ? [{ key: 'approve', label: '批准入账', primary: true, confirm: '批准后发布入账事件，Client / Partner 余额随之变化。确认批准？',
          onConfirm: () => void act('approve') }] : []),
      ]} />
    </>}
  </Drawer>;
}
